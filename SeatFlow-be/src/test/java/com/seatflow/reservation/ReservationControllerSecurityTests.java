package com.seatflow.reservation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
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

@WebMvcTest(ReservationController.class)
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
class ReservationControllerSecurityTests {

	private static final UUID USER_ID = UUID.fromString("5e1870bd-3dbe-48ef-bbb7-1e4df7d1235d");
	private static final UUID HOLD_ID = UUID.fromString("15eb397b-9d6c-4ee6-9171-177e35a91958");
	private static final UUID RESERVATION_ID = UUID.fromString("191cd6b8-31f1-437e-a29d-32fba2cc499e");
	private static final UUID EVENT_ID = UUID.fromString("47157487-2622-4a53-b4b5-75917cae95e8");
	private static final Instant NOW = Instant.parse("2026-08-09T10:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private ReservationService reservationService;

	@Test
	void authenticatedUserCanCreateAndRetrieveReservation() throws Exception {
		ReservationResponse response = response();
		when(reservationService.createReservation(eq(USER_ID), any(ReservationCreateRequest.class)))
				.thenReturn(response);
		when(reservationService.getReservation(RESERVATION_ID, USER_ID)).thenReturn(response);

		mockMvc.perform(post("/api/v1/reservations")
						.header(HttpHeaders.AUTHORIZATION, bearerToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(RESERVATION_ID.toString()))
				.andExpect(jsonPath("$.holdId").value(HOLD_ID.toString()))
				.andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
				.andExpect(jsonPath("$.totalAmount").value(125000.00));

		mockMvc.perform(get("/api/v1/reservations/{reservationId}", RESERVATION_ID)
						.header(HttpHeaders.AUTHORIZATION, bearerToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(RESERVATION_ID.toString()));
	}

	@Test
	void unauthenticatedUserReceivesUnauthorized() throws Exception {
		mockMvc.perform(post("/api/v1/reservations")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.title").value("Unauthorized"));

		mockMvc.perform(get("/api/v1/reservations/{reservationId}", RESERVATION_ID))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.title").value("Unauthorized"));
	}

	@Test
	void invalidCreateRequestReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/v1/reservations")
						.header(HttpHeaders.AUTHORIZATION, bearerToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid request"));
	}

	@Test
	void foreignHoldReturnsForbidden() throws Exception {
		when(reservationService.createReservation(eq(USER_ID), any(ReservationCreateRequest.class)))
				.thenThrow(new AccessDeniedException("Hold belongs to another user"));

		mockMvc.perform(post("/api/v1/reservations")
						.header(HttpHeaders.AUTHORIZATION, bearerToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.title").value("Forbidden"));
	}

	@Test
	void missingReservationReturnsNotFound() throws Exception {
		when(reservationService.getReservation(RESERVATION_ID, USER_ID))
				.thenThrow(new ReservationNotFoundException());

		mockMvc.perform(get("/api/v1/reservations/{reservationId}", RESERVATION_ID)
						.header(HttpHeaders.AUTHORIZATION, bearerToken()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Reservation not found"));
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

	private static String requestBody() {
		return "{\"holdId\":\"%s\"}".formatted(HOLD_ID);
	}

	private static ReservationResponse response() {
		return new ReservationResponse(
				RESERVATION_ID,
				USER_ID,
				EVENT_ID,
				HOLD_ID,
				ReservationStatus.PENDING_PAYMENT,
				NOW.plusSeconds(300),
				new BigDecimal("125000.00"),
				List.of(),
				NOW,
				NOW);
	}
}
