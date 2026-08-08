package com.seatflow.hold;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

@WebMvcTest(SeatHoldController.class)
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
class SeatHoldControllerSecurityTests {

	private static final UUID EVENT_ID = UUID.fromString("5d2b80f6-4a0e-4f91-9676-e2c2d8b59e42");
	private static final UUID EVENT_SEAT_ID = UUID.fromString("0ab96cb6-0b8b-4db4-855e-1bd12f3fc0e5");
	private static final UUID USER_ID = UUID.fromString("c144397b-1b17-4a45-a1ef-b30ef84d5a79");
	private static final UUID HOLD_ID = UUID.fromString("0ffae24a-c058-4e4e-8783-b556a70e097e");
	private static final Instant EXPIRES_AT = Instant.parse("2026-08-08T10:05:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private SeatHoldService seatHoldService;

	@Test
	void authenticatedUserCanHoldSeat() throws Exception {
		when(seatHoldService.createHold(eq(EVENT_ID), eq(USER_ID), any(SeatHoldRequest.class)))
				.thenReturn(new SeatHoldResponse(HOLD_ID, EVENT_ID, EVENT_SEAT_ID, USER_ID, EXPIRES_AT));

		mockMvc.perform(post("/api/v1/events/{eventId}/holds", EVENT_ID)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.USER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody(EVENT_SEAT_ID)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.holdId").value(HOLD_ID.toString()))
				.andExpect(jsonPath("$.eventId").value(EVENT_ID.toString()))
				.andExpect(jsonPath("$.eventSeatId").value(EVENT_SEAT_ID.toString()))
				.andExpect(jsonPath("$.userId").value(USER_ID.toString()))
				.andExpect(jsonPath("$.expiresAt").value("2026-08-08T10:05:00Z"));
	}

	@Test
	void unauthenticatedUserCannotHoldSeat() throws Exception {
		mockMvc.perform(post("/api/v1/events/{eventId}/holds", EVENT_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody(EVENT_SEAT_ID)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Unauthorized"));
	}

	@Test
	void invalidRequestReturnsValidationError() throws Exception {
		mockMvc.perform(post("/api/v1/events/{eventId}/holds", EVENT_ID)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.USER))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Invalid request"));
	}

	@Test
	void holdConflictReturnsConflict() throws Exception {
		when(seatHoldService.createHold(eq(EVENT_ID), eq(USER_ID), any(SeatHoldRequest.class)))
				.thenThrow(new SeatHoldConflictException());

		mockMvc.perform(post("/api/v1/events/{eventId}/holds", EVENT_ID)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.USER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody(EVENT_SEAT_ID)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Seat hold conflict"));
	}

	private String bearerToken(UserRole role) {
		return "Bearer " + jwtTokenService.issueAccessToken(new UserRecord(
				USER_ID,
				"user@example.com",
				"{bcrypt}hash",
				role,
				UserStatus.ACTIVE,
				Instant.parse("2026-08-08T09:00:00Z"),
				Instant.parse("2026-08-08T09:00:00Z"))).accessToken();
	}

	private static String requestBody(UUID eventSeatId) {
		return "{\"eventSeatId\":\"%s\"}".formatted(eventSeatId);
	}
}
