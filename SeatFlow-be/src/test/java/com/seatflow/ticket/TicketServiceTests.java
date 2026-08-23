package com.seatflow.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.seatflow.observability.BusinessMetrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class TicketServiceTests {

	private static final UUID ORDER_ID = UUID.fromString("50e03079-10e9-461d-bda9-eb68575b6e4e");
	private static final UUID EVENT_SEAT_ID = UUID.fromString("d747d250-8ed8-4e5e-a075-f73718489a01");
	private static final Instant NOW = Instant.parse("2026-08-23T12:00:00Z");

	@Mock
	private TicketMapper ticketMapper;

	@Mock
	private TicketCodeGenerator ticketCodeGenerator;

	private SimpleMeterRegistry meterRegistry;
	private TicketService ticketService;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		ticketService = new TicketService(
				ticketMapper,
				ticketCodeGenerator,
				new BusinessMetrics(meterRegistry));
	}

	@Test
	void issuedTicketIncrementsMetricOnlyOnInsert() {
		when(ticketCodeGenerator.generateCode()).thenReturn("safe-ticket-code-012345678901234567890123");
		when(ticketMapper.insert(any(), eq(ORDER_ID), eq(EVENT_SEAT_ID), any(), eq(NOW), eq(NOW)))
				.thenReturn(1);

		ticketService.issueTickets(ORDER_ID, List.of(EVENT_SEAT_ID), NOW);

		assertThat(meterRegistry.get("ticket_issued").counter().count()).isEqualTo(1);
	}

	@Test
	void duplicateTicketDoesNotIncrementIssuedMetric() {
		when(ticketMapper.findByOrderAndEventSeat(ORDER_ID, EVENT_SEAT_ID)).thenReturn(new TicketRecord(
				UUID.randomUUID(),
				ORDER_ID,
				EVENT_SEAT_ID,
				"safe-ticket-code-012345678901234567890123",
				TicketStatus.ACTIVE,
				NOW,
				null,
				NOW));

		ticketService.issueTickets(ORDER_ID, List.of(EVENT_SEAT_ID), NOW);

		verify(ticketMapper, never()).insert(any(), any(), any(), any(), any(), any());
		assertThat(meterRegistry.get("ticket_issued").counter().count()).isZero();
	}
}
