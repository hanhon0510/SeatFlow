package com.seatflow.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.seatflow.event.EventMapper;
import com.seatflow.event.EventNotFoundException;
import com.seatflow.event.EventRecord;
import com.seatflow.event.EventStatus;
import com.seatflow.observability.BusinessMetrics;
import com.seatflow.ticket.TicketStatus;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class CheckInServiceTests {

	private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
	private static final Instant EARLIER = Instant.parse("2026-08-09T11:00:00Z");
	private static final UUID EVENT_ID = UUID.fromString("9a2c4f10-6a1e-4c55-b3d4-1d0f6a8e7b21");
	private static final UUID OTHER_EVENT_ID = UUID.fromString("5d6f3b7a-0c1e-4b86-9b54-2f0e8a4c1d33");
	private static final UUID ADMIN_ID = UUID.fromString("7ca3110b-513f-4445-a52d-dc96b1c2da8f");
	private static final UUID TICKET_ID = UUID.fromString("f2287e38-c265-4841-b20b-57d2d7ba1b9d");
	private static final String CODE = "ticket_code_abcdefghijklmnopqrstuvwxyz123456";
	private static final String QR = "seatflow:ticket:" + TICKET_ID + ":" + CODE;

	@Mock
	private CheckInMapper checkInMapper;

	@Mock
	private EventMapper eventMapper;

	private CheckInService service;

	@BeforeEach
	void setUp() {
		service = new CheckInService(
				checkInMapper, eventMapper, new BusinessMetrics(new SimpleMeterRegistry()),
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void unknownEventIsNotFound() {
		assertThatThrownBy(() -> service.checkIn(EVENT_ID, ADMIN_ID, QR)).isInstanceOf(EventNotFoundException.class);
		assertThatThrownBy(() -> service.summary(EVENT_ID)).isInstanceOf(EventNotFoundException.class);
	}

	@Test
	void validTicketIsAdmittedOnceAndAttributedToTheAdmin() {
		givenEvent();
		when(checkInMapper.findByCode(CODE, TICKET_ID)).thenReturn(ticket(EVENT_ID, TicketStatus.ACTIVE, null));
		when(checkInMapper.markUsed(TICKET_ID, ADMIN_ID, NOW)).thenReturn(1);

		CheckInResponse response = service.checkIn(EVENT_ID, ADMIN_ID, QR);

		assertThat(response.outcome()).isEqualTo(CheckInOutcome.CHECKED_IN);
		assertThat(response.usedAt()).isEqualTo(NOW);
		assertThat(response.seatLabel()).isEqualTo("A1");
		assertThat(response.buyerEmail()).isEqualTo("buyer@example.com");
	}

	@Test
	void bareCodeWorksWithoutATicketId() {
		givenEvent();
		when(checkInMapper.findByCode(CODE, null)).thenReturn(ticket(EVENT_ID, TicketStatus.ACTIVE, null));
		when(checkInMapper.markUsed(TICKET_ID, ADMIN_ID, NOW)).thenReturn(1);

		assertThat(service.checkIn(EVENT_ID, ADMIN_ID, "  " + CODE + " ").outcome())
				.isEqualTo(CheckInOutcome.CHECKED_IN);
	}

	@Test
	void usedTicketIsRefusedWithTheTimeItWasFirstScanned() {
		givenEvent();
		when(checkInMapper.findByCode(CODE, TICKET_ID)).thenReturn(ticket(EVENT_ID, TicketStatus.USED, EARLIER));

		CheckInResponse response = service.checkIn(EVENT_ID, ADMIN_ID, QR);

		assertThat(response.outcome()).isEqualTo(CheckInOutcome.ALREADY_USED);
		assertThat(response.usedAt()).isEqualTo(EARLIER);
		verify(checkInMapper, never()).markUsed(any(), any(), any());
	}

	@Test
	void cancelledTicketIsRefused() {
		givenEvent();
		when(checkInMapper.findByCode(CODE, TICKET_ID)).thenReturn(ticket(EVENT_ID, TicketStatus.CANCELLED, null));

		assertThat(service.checkIn(EVENT_ID, ADMIN_ID, QR).outcome()).isEqualTo(CheckInOutcome.CANCELLED);
		verify(checkInMapper, never()).markUsed(any(), any(), any());
	}

	@Test
	void ticketOfAnotherEventIsRefusedWithoutRevealingIt() {
		givenEvent();
		when(checkInMapper.findByCode(CODE, TICKET_ID)).thenReturn(ticket(OTHER_EVENT_ID, TicketStatus.ACTIVE, null));

		CheckInResponse response = service.checkIn(EVENT_ID, ADMIN_ID, QR);

		assertThat(response.outcome()).isEqualTo(CheckInOutcome.WRONG_EVENT);
		assertThat(response.ticketId()).isNull();
		assertThat(response.buyerEmail()).isNull();
		verify(checkInMapper, never()).markUsed(any(), any(), any());
	}

	@Test
	void unknownAndMalformedCodesAreNotFound() {
		givenEvent();
		when(checkInMapper.findByCode(CODE, null)).thenReturn(null);

		assertThat(service.checkIn(EVENT_ID, ADMIN_ID, CODE).outcome()).isEqualTo(CheckInOutcome.NOT_FOUND);
		assertThat(service.checkIn(EVENT_ID, ADMIN_ID, "garbage").outcome()).isEqualTo(CheckInOutcome.NOT_FOUND);
		verify(checkInMapper, never()).markUsed(any(), any(), any());
	}

	@Test
	void losingTheRaceReportsWhatTheWinnerDid() {
		givenEvent();
		when(checkInMapper.findByCode(CODE, TICKET_ID)).thenReturn(
				ticket(EVENT_ID, TicketStatus.ACTIVE, null),
				ticket(EVENT_ID, TicketStatus.USED, EARLIER));
		when(checkInMapper.markUsed(TICKET_ID, ADMIN_ID, NOW)).thenReturn(0);

		CheckInResponse response = service.checkIn(EVENT_ID, ADMIN_ID, QR);

		assertThat(response.outcome()).isEqualTo(CheckInOutcome.ALREADY_USED);
		assertThat(response.usedAt()).isEqualTo(EARLIER);
	}

	@Test
	void losingTheRaceToARefundReportsTheCancellation() {
		givenEvent();
		when(checkInMapper.findByCode(CODE, TICKET_ID)).thenReturn(
				ticket(EVENT_ID, TicketStatus.ACTIVE, null),
				ticket(EVENT_ID, TicketStatus.CANCELLED, null));
		when(checkInMapper.markUsed(TICKET_ID, ADMIN_ID, NOW)).thenReturn(0);

		assertThat(service.checkIn(EVENT_ID, ADMIN_ID, QR).outcome()).isEqualTo(CheckInOutcome.CANCELLED);
	}

	@Test
	void summaryCountsWhoIsStillExpected() {
		givenEvent();
		when(checkInMapper.countsForEvent(EVENT_ID)).thenReturn(new CheckInCounts(10, 4));
		when(checkInMapper.findRecentForEvent(EVENT_ID, CheckInService.RECENT_LIMIT)).thenReturn(List.of(
				new CheckInEntry(TICKET_ID, "Orchestra", "A", "A1", "buyer@example.com", NOW)));

		CheckInSummaryResponse summary = service.summary(EVENT_ID);

		assertThat(summary.valid()).isEqualTo(10);
		assertThat(summary.checkedIn()).isEqualTo(4);
		assertThat(summary.remaining()).isEqualTo(6);
		assertThat(summary.eventName()).isEqualTo("Gala");
		assertThat(summary.recent()).hasSize(1);
	}

	private void givenEvent() {
		when(eventMapper.findById(EVENT_ID)).thenReturn(new EventRecord(
				EVENT_ID, UUID.randomUUID(), "Gala", null, NOW, NOW, NOW, EventStatus.PUBLISHED, NOW, NOW));
	}

	private static CheckInTicket ticket(UUID eventId, TicketStatus status, Instant usedAt) {
		return new CheckInTicket(TICKET_ID, status, usedAt, eventId, "Orchestra", "A", "A1", "buyer@example.com");
	}
}
