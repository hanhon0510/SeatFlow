package com.seatflow.ticket;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seatflow.notification.EmailDeliveryException;
import com.seatflow.notification.EmailGateway;
import com.seatflow.notification.TicketEmailTemplates;
import com.seatflow.observability.BusinessMetrics;
import com.seatflow.user.UserMapper;
import com.seatflow.user.UserRecord;
import com.seatflow.user.UserStatus;

@Service
public class TicketService {

	private static final Logger log = LoggerFactory.getLogger(TicketService.class);
	private static final int CODE_GENERATION_ATTEMPTS = 5;

	private final TicketMapper ticketMapper;
	private final TicketCodeGenerator ticketCodeGenerator;
	private final BusinessMetrics businessMetrics;
	private final UserMapper userMapper;
	private final TicketEmailTemplates emailTemplates;
	private final EmailGateway emailGateway;
	private final Clock clock;

	public TicketService(
			TicketMapper ticketMapper,
			TicketCodeGenerator ticketCodeGenerator,
			BusinessMetrics businessMetrics,
			UserMapper userMapper,
			TicketEmailTemplates emailTemplates,
			EmailGateway emailGateway,
			Clock clock) {
		this.ticketMapper = ticketMapper;
		this.ticketCodeGenerator = ticketCodeGenerator;
		this.businessMetrics = businessMetrics;
		this.userMapper = userMapper;
		this.emailTemplates = emailTemplates;
		this.emailGateway = emailGateway;
		this.clock = clock;
	}

	@Transactional
	public void issueTickets(UUID orderId, List<UUID> eventSeatIds, Instant issuedAt) {
		if (eventSeatIds.isEmpty()
				|| eventSeatIds.stream().distinct().count() != eventSeatIds.size()) {
			throw new TicketIssuanceException();
		}

		for (UUID eventSeatId : eventSeatIds) {
			issueTicket(orderId, eventSeatId, issuedAt);
		}
	}

	@Transactional(readOnly = true)
	public List<TicketResponse> listUserTickets(UUID userId) {
		return ticketMapper.findDetailsByUser(userId).stream()
				.map(TicketResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public TicketResponse getTicket(UUID ticketId, UUID userId) {
		return TicketResponse.from(requireHeldTicket(ticketId, userId));
	}

	/**
	 * Hands the ticket to the account behind {@code recipientEmail}. The ticket gets a new code, so the
	 * QR the sender already has stops working at the door.
	 */
	@Transactional
	public void transferTicket(UUID ticketId, UUID userId, String recipientEmail) {
		TicketDetailRecord ticket = requireHeldTicket(ticketId, userId);
		Instant now = clock.instant();
		if (ticket.status() != TicketStatus.ACTIVE
				|| !ticket.eventTransfersAllowed()
				|| !ticket.eventStartTime().isAfter(now)) {
			throw new TicketNotTransferableException();
		}
		UserRecord recipient = userMapper.findByNormalizedEmail(recipientEmail.trim().toLowerCase(Locale.ROOT));
		if (recipient == null || recipient.status() != UserStatus.ACTIVE) {
			throw new TransferRecipientNotFoundException();
		}
		if (recipient.id().equals(userId)) {
			throw new TicketNotTransferableException();
		}
		// 0 rows means it was used, refunded or given away since we read it.
		if (ticketMapper.transfer(ticketId, userId, recipient.id(), ticketCodeGenerator.generateCode()) != 1) {
			throw new TicketNotTransferableException();
		}
		ticketMapper.insertTransfer(UUID.randomUUID(), ticketId, userId, recipient.id(), now);

		UserRecord sender = userMapper.findById(userId);
		try {
			emailGateway.send(emailTemplates.ticketReceived(
					recipient.email(),
					sender.email(),
					ticketId,
					ticket.eventName(),
					ticket.eventStartTime(),
					ticket.venueTimezone(),
					ticket.venueName(),
					ticket.sectionName() + ", row " + ticket.rowLabel() + ", seat " + ticket.seatLabel()));
		}
		catch (EmailDeliveryException ex) {
			log.warn("Could not tell user {} about the ticket {} they received", recipient.id(), ticketId, ex);
		}
	}

	@Transactional(readOnly = true)
	public List<TicketTransferRecord> listEventTransfers(UUID eventId) {
		return ticketMapper.findTransfersByEvent(eventId);
	}

	private TicketDetailRecord requireHeldTicket(UUID ticketId, UUID userId) {
		TicketDetailRecord ticket = ticketMapper.findDetailByIdAndUser(ticketId, userId);
		if (ticket == null) {
			throw new TicketNotFoundException();
		}
		return ticket;
	}

	private void issueTicket(UUID orderId, UUID eventSeatId, Instant issuedAt) {
		TicketRecord existing = ticketMapper.findByOrderAndEventSeat(orderId, eventSeatId);
		if (existing != null) {
			return;
		}

		for (int attempt = 0; attempt < CODE_GENERATION_ATTEMPTS; attempt++) {
			int insertedRows = ticketMapper.insert(
					UUID.randomUUID(),
					orderId,
					eventSeatId,
					ticketCodeGenerator.generateCode(),
					issuedAt,
					issuedAt);
			if (insertedRows == 1) {
				businessMetrics.ticketIssued();
				return;
			}

			existing = ticketMapper.findByOrderAndEventSeat(orderId, eventSeatId);
			if (existing != null) {
				return;
			}
		}

		throw new TicketIssuanceException();
	}
}
