package com.seatflow.ticket;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/events/{eventId}/transfers")
public class AdminTicketTransferController {

	private final TicketService ticketService;

	public AdminTicketTransferController(TicketService ticketService) {
		this.ticketService = ticketService;
	}

	@GetMapping
	public List<TicketTransferRecord> list(@PathVariable UUID eventId) {
		return ticketService.listEventTransfers(eventId);
	}
}
