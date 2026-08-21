package com.seatflow.ticket;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.seatflow.security.JwtConfig;
import com.seatflow.security.JwtTokenService;
import com.seatflow.security.SecurityConfig;
import com.seatflow.support.JwtTestSupport;
import com.seatflow.user.UserRecord;
import com.seatflow.user.UserRole;
import com.seatflow.user.UserStatus;

@WebMvcTest({
		TicketController.class,
		UserTicketController.class
})
@Import({
		SecurityConfig.class,
		JwtConfig.class,
		JwtTokenService.class
})
@TestPropertySource(properties = {
		"server.port=8080",
		"seatflow.jwt.secret=" + JwtTestSupport.DEFAULT_SECRET,
		"seatflow.jwt.issuer=" + JwtTestSupport.ISSUER,
		"seatflow.jwt.expires-in-seconds=900"
})
class TicketControllerSecurityTests {

	private static final Instant NOW = Instant.parse("2026-08-13T10:00:00Z");
	private static final UUID USER_ID = UUID.fromString("cc551736-e7e7-4cc0-9551-c3b2f7ebd885");
	private static final UUID TICKET_ID = UUID.fromString("ae40e0e5-0248-4de0-a7d9-d9c2ec102f91");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private TicketService ticketService;

	@Test
	void authenticatedUserCanListAndOpenTickets() throws Exception {
		TicketResponse response = ticketResponse();
		when(ticketService.listUserTickets(USER_ID)).thenReturn(List.of(response));
		when(ticketService.getTicket(TICKET_ID, USER_ID)).thenReturn(response);

		mockMvc.perform(get("/api/v1/users/me/tickets")
						.header(HttpHeaders.AUTHORIZATION, bearerToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(TICKET_ID.toString()))
				.andExpect(jsonPath("$[0].status").value("ACTIVE"))
				.andExpect(jsonPath("$[0].event.name").value("Opening Night"))
				.andExpect(jsonPath("$[0].seat.seatLabel").value("A1"))
				.andExpect(jsonPath("$[0].qrData").value("seatflow:ticket:%s:%s".formatted(
						TICKET_ID,
						"code_012345678901234567890123456789012345")));

		mockMvc.perform(get("/api/v1/tickets/{ticketId}", TICKET_ID)
						.header(HttpHeaders.AUTHORIZATION, bearerToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(TICKET_ID.toString()))
				.andExpect(jsonPath("$.event.venueName").value("Main Hall"))
				.andExpect(jsonPath("$.seat.sectionName").value("Orchestra"));
	}

	@Test
	void unauthenticatedUserReceivesUnauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/users/me/tickets"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.title").value("Unauthorized"));

		mockMvc.perform(get("/api/v1/tickets/{ticketId}", TICKET_ID))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.title").value("Unauthorized"));
	}

	@Test
	void missingOrForeignTicketUsesSafeNotFound() throws Exception {
		when(ticketService.getTicket(TICKET_ID, USER_ID)).thenThrow(new TicketNotFoundException());

		mockMvc.perform(get("/api/v1/tickets/{ticketId}", TICKET_ID)
						.header(HttpHeaders.AUTHORIZATION, bearerToken()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Ticket not found"));
	}

	private String bearerToken() {
		return "Bearer " + jwtTokenService.issueAccessToken(new UserRecord(
				USER_ID,
				"user@example.com",
				"{bcrypt}hash",
				UserRole.USER,
				UserStatus.ACTIVE,
				NOW,
				NOW)).accessToken();
	}

	private static TicketResponse ticketResponse() {
		return new TicketResponse(
				TICKET_ID,
				UUID.fromString("aabf7671-9491-48f7-b98b-c8d7e1b7125a"),
				UUID.fromString("a619a3a3-0565-4713-af8e-77d3f3630813"),
				"code_012345678901234567890123456789012345",
				TicketStatus.ACTIVE,
				NOW,
				null,
				NOW,
				new TicketEventResponse(
						UUID.fromString("be658337-2e86-4e66-bc01-88fecb2dbe27"),
						"Opening Night",
						NOW.plusSeconds(3600),
						UUID.fromString("3db0145a-1932-46ad-b02d-6789db7a8ab2"),
						"Main Hall",
						"1 Center Street",
						"Ho Chi Minh City",
						"Vietnam",
						"Asia/Ho_Chi_Minh"),
				new TicketSeatResponse(
						UUID.fromString("5cc3e977-38f1-425d-a00d-f8e2ca57939d"),
						"Orchestra",
						"A",
						1,
						"A1",
						false,
						new BigDecimal("125000.00")),
				"seatflow:ticket:%s:%s".formatted(
						TICKET_ID,
						"code_012345678901234567890123456789012345"));
	}
}
