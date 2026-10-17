package com.seatflow.checkin;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seatflow.event.EventMapper;
import com.seatflow.event.EventNotFoundException;
import com.seatflow.event.EventRecord;
import com.seatflow.observability.BusinessMetrics;
import com.seatflow.ticket.TicketStatus;

/** Admits ticket holders at the door of one event. */
@Service
public class CheckInService {

	static final int RECENT_LIMIT = 15;

	private final CheckInMapper checkInMapper;
	private final EventMapper eventMapper;
	private final BusinessMetrics businessMetrics;
	private final Clock clock;

	public CheckInService(
			CheckInMapper checkInMapper,
			EventMapper eventMapper,
			BusinessMetrics businessMetrics,
			Clock clock) {
		this.checkInMapper = checkInMapper;
		this.eventMapper = eventMapper;
		this.businessMetrics = businessMetrics;
		this.clock = clock;
	}

	@Transactional
	public CheckInResponse checkIn(UUID eventId, UUID adminId, String scanned) {
		requireEvent(eventId);

		ScannedCode code = ScannedCode.parse(scanned).orElse(null);
		CheckInTicket ticket = code == null ? null : checkInMapper.findByCode(code.code(), code.ticketId());
		if (ticket == null) {
			return CheckInResponse.withoutTicket(CheckInOutcome.NOT_FOUND);
		}
		// A real ticket for some other event is refused without admitting or describing it.
		if (!ticket.eventId().equals(eventId)) {
			return CheckInResponse.withoutTicket(CheckInOutcome.WRONG_EVENT);
		}
		if (ticket.status() == TicketStatus.ACTIVE) {
			Instant now = clock.instant();
			if (checkInMapper.markUsed(ticket.ticketId(), adminId, now) == 1) {
				businessMetrics.ticketCheckedIn();
				return CheckInResponse.of(CheckInOutcome.CHECKED_IN, ticket, now);
			}
			// Lost the race: someone scanned, or the order was refunded, between our read and update.
			ticket = checkInMapper.findByCode(code.code(), code.ticketId());
			if (ticket == null) {
				return CheckInResponse.withoutTicket(CheckInOutcome.NOT_FOUND);
			}
		}
		return switch (ticket.status()) {
			case USED -> CheckInResponse.of(CheckInOutcome.ALREADY_USED, ticket, ticket.usedAt());
			case CANCELLED -> CheckInResponse.of(CheckInOutcome.CANCELLED, ticket, null);
			// Still ACTIVE after a failed update cannot happen inside this transaction's view.
			case ACTIVE -> throw new IllegalStateException("Ticket %s is active but could not be checked in"
					.formatted(ticket.ticketId()));
		};
	}

	@Transactional(readOnly = true)
	public CheckInSummaryResponse summary(UUID eventId) {
		EventRecord event = requireEvent(eventId);
		CheckInCounts counts = checkInMapper.countsForEvent(eventId);
		List<CheckInEntry> recent = checkInMapper.findRecentForEvent(eventId, RECENT_LIMIT);
		return new CheckInSummaryResponse(
				eventId,
				event.name(),
				counts.valid(),
				counts.checkedIn(),
				counts.valid() - counts.checkedIn(),
				recent);
	}

	private EventRecord requireEvent(UUID eventId) {
		EventRecord event = eventMapper.findById(eventId);
		if (event == null) {
			throw new EventNotFoundException();
		}
		return event;
	}
}
