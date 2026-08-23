package com.seatflow.ticket;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seatflow.observability.BusinessMetrics;

@Service
public class TicketService {

	private static final int CODE_GENERATION_ATTEMPTS = 5;

	private final TicketMapper ticketMapper;
	private final TicketCodeGenerator ticketCodeGenerator;
	private final BusinessMetrics businessMetrics;

	public TicketService(
			TicketMapper ticketMapper,
			TicketCodeGenerator ticketCodeGenerator,
			BusinessMetrics businessMetrics) {
		this.ticketMapper = ticketMapper;
		this.ticketCodeGenerator = ticketCodeGenerator;
		this.businessMetrics = businessMetrics;
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
		TicketDetailRecord ticket = ticketMapper.findDetailByIdAndUser(ticketId, userId);
		if (ticket == null) {
			throw new TicketNotFoundException();
		}
		return TicketResponse.from(ticket);
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
