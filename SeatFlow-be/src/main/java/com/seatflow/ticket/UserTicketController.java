package com.seatflow.ticket;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seatflow.auth.AuthenticationFailedException;

@RestController
@RequestMapping("/api/v1/users/me/tickets")
public class UserTicketController {

	private final TicketService ticketService;

	public UserTicketController(TicketService ticketService) {
		this.ticketService = ticketService;
	}

	@GetMapping
	public List<TicketResponse> listTickets(@AuthenticationPrincipal Jwt jwt) {
		return ticketService.listUserTickets(userId(jwt));
	}

	private static UUID userId(Jwt jwt) {
		if (jwt == null) {
			throw new AuthenticationFailedException();
		}
		try {
			return UUID.fromString(jwt.getSubject());
		}
		catch (IllegalArgumentException ex) {
			throw new AuthenticationFailedException();
		}
	}
}
