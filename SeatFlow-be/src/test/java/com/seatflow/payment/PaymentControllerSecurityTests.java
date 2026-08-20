package com.seatflow.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.seatflow.order.OrderNotFoundException;
import com.seatflow.ratelimit.RateLimitService;
import com.seatflow.security.JwtConfig;
import com.seatflow.security.JwtTokenService;
import com.seatflow.security.SecurityConfig;
import com.seatflow.support.JwtTestSupport;
import com.seatflow.user.UserRecord;
import com.seatflow.user.UserRole;
import com.seatflow.user.UserStatus;

@WebMvcTest(PaymentController.class)
@Import({
		SecurityConfig.class,
		JwtConfig.class,
		JwtTokenService.class
})
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
		"server.port=8080",
		"seatflow.jwt.secret=" + JwtTestSupport.DEFAULT_SECRET,
		"seatflow.jwt.issuer=" + JwtTestSupport.ISSUER,
		"seatflow.jwt.expires-in-seconds=900"
})
class PaymentControllerSecurityTests {

	private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
	private static final UUID USER_ID = UUID.fromString("91557b43-1a8f-4450-aaca-353371b40f42");
	private static final UUID ORDER_ID = UUID.fromString("dc79974b-adc6-42cf-b751-e71811f1812d");
	private static final UUID PAYMENT_ID = UUID.fromString("47724f4b-d36b-4fad-8fa5-78175767f8a5");
	private static final String IDEMPOTENCY_KEY = "payment-attempt-1";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private PaymentIdempotencyService paymentIdempotencyService;

	@MockitoBean
	private RateLimitService rateLimitService;

	@Test
	void authenticatedUserCanCreatePaymentAndTokenIsRedacted(CapturedOutput output) throws Exception {
		when(paymentIdempotencyService.createPayment(
				eq(ORDER_ID),
				eq(USER_ID),
				eq(IDEMPOTENCY_KEY),
				any(PaymentCreateRequest.class)))
				.thenReturn(new IdempotentPaymentResult(201, paymentResponse()));

		mockMvc.perform(post("/api/v1/orders/{orderId}/payments", ORDER_ID)
						.header(HttpHeaders.AUTHORIZATION, bearerToken())
						.header("Idempotency-Key", IDEMPOTENCY_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody("tok_success")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(PAYMENT_ID.toString()))
				.andExpect(jsonPath("$.status").value("SUCCEEDED"))
				.andExpect(jsonPath("$.amount").value(210000.75));

		assertThat(output.getAll()).doesNotContain("tok_success");
		assertThat(new PaymentCreateRequest("tok_success").toString()).doesNotContain("tok_success");
	}

	@Test
	void unauthenticatedUserReceivesUnauthorized() throws Exception {
		mockMvc.perform(post("/api/v1/orders/{orderId}/payments", ORDER_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody("tok_success")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Unauthorized"));
	}

	@Test
	void blankAndUnknownTokensReturnBadRequest() throws Exception {
		mockMvc.perform(post("/api/v1/orders/{orderId}/payments", ORDER_ID)
						.header(HttpHeaders.AUTHORIZATION, bearerToken())
						.header("Idempotency-Key", IDEMPOTENCY_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody("")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Invalid request"));

		when(paymentIdempotencyService.createPayment(
				eq(ORDER_ID),
				eq(USER_ID),
				eq(IDEMPOTENCY_KEY),
				any(PaymentCreateRequest.class)))
				.thenThrow(new InvalidPaymentTokenException());
		mockMvc.perform(post("/api/v1/orders/{orderId}/payments", ORDER_ID)
						.header(HttpHeaders.AUTHORIZATION, bearerToken())
						.header("Idempotency-Key", IDEMPOTENCY_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody("unknown")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Invalid payment token"));
	}

	@Test
	void wrongOwnerAndPaidOrderUseSafeErrors() throws Exception {
		when(paymentIdempotencyService.createPayment(
				eq(ORDER_ID),
				eq(USER_ID),
				eq(IDEMPOTENCY_KEY),
				any(PaymentCreateRequest.class)))
				.thenThrow(new OrderNotFoundException())
				.thenThrow(new PaymentConflictException());

		mockMvc.perform(post("/api/v1/orders/{orderId}/payments", ORDER_ID)
						.header(HttpHeaders.AUTHORIZATION, bearerToken())
						.header("Idempotency-Key", IDEMPOTENCY_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody("tok_success")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Order not found"));

		mockMvc.perform(post("/api/v1/orders/{orderId}/payments", ORDER_ID)
						.header(HttpHeaders.AUTHORIZATION, bearerToken())
						.header("Idempotency-Key", IDEMPOTENCY_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody("tok_success")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Payment conflict"));
	}

	@Test
	void missingIdempotencyKeyReturnsBadRequest() throws Exception {
		when(paymentIdempotencyService.createPayment(
				eq(ORDER_ID),
				eq(USER_ID),
				eq(null),
				any(PaymentCreateRequest.class)))
				.thenThrow(new com.seatflow.idempotency.InvalidIdempotencyKeyException());

		mockMvc.perform(post("/api/v1/orders/{orderId}/payments", ORDER_ID)
						.header(HttpHeaders.AUTHORIZATION, bearerToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody("tok_success")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Idempotency-Key header is required"));
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

	private static String requestBody(String token) {
		return "{\"token\":\"%s\"}".formatted(token);
	}

	private static PaymentResponse paymentResponse() {
		return new PaymentResponse(
				PAYMENT_ID,
				ORDER_ID,
				PaymentStatus.SUCCEEDED,
				new BigDecimal("210000.75"),
				"sim_" + PAYMENT_ID,
				null,
				NOW,
				NOW);
	}
}
