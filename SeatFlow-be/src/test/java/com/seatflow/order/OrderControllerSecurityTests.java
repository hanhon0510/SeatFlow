package com.seatflow.order;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.seatflow.reservation.ReservationNotFoundException;
import com.seatflow.security.JwtConfig;
import com.seatflow.security.JwtTokenService;
import com.seatflow.security.SecurityConfig;
import com.seatflow.support.JwtTestSupport;
import com.seatflow.user.UserRecord;
import com.seatflow.user.UserRole;
import com.seatflow.user.UserStatus;

@WebMvcTest({
		OrderController.class,
		UserOrderController.class
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
class OrderControllerSecurityTests {

	private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
	private static final UUID USER_ID = UUID.fromString("07998614-5cf8-4479-a130-e1bbb2819760");
	private static final UUID RESERVATION_ID = UUID.fromString("88a1dc20-70c0-4f5f-a418-c18666043549");
	private static final UUID ORDER_ID = UUID.fromString("34d00a2d-4a2b-4df2-98e0-d680f43e1ab6");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private OrderService orderService;

	@Test
	void authenticatedUserCanCreateRetrieveAndListOrders() throws Exception {
		OrderResponse order = orderResponse();
		when(orderService.createOrder(eq(USER_ID), any(OrderCreateRequest.class))).thenReturn(order);
		when(orderService.getOrder(ORDER_ID, USER_ID)).thenReturn(order);
		when(orderService.listUserOrders(USER_ID, 0, 20))
				.thenReturn(OrderPageResponse.from(List.of(orderRecord()), 0, 20, 1));

		mockMvc.perform(post("/api/v1/orders")
						.header(HttpHeaders.AUTHORIZATION, bearerToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(ORDER_ID.toString()))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.totalAmount").value(210000.75))
				.andExpect(jsonPath("$.currency").value("VND"));

		mockMvc.perform(get("/api/v1/orders/{orderId}", ORDER_ID)
						.header(HttpHeaders.AUTHORIZATION, bearerToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(ORDER_ID.toString()));

		mockMvc.perform(get("/api/v1/users/me/orders")
						.header(HttpHeaders.AUTHORIZATION, bearerToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value(ORDER_ID.toString()))
				.andExpect(jsonPath("$.totalItems").value(1));
	}

	@Test
	void unauthenticatedUserReceivesUnauthorizedForEveryOrderRoute() throws Exception {
		mockMvc.perform(post("/api/v1/orders")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody()))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/orders/{orderId}", ORDER_ID))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/users/me/orders"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void invalidRequestAndPaginationReturnBadRequest() throws Exception {
		mockMvc.perform(post("/api/v1/orders")
						.header(HttpHeaders.AUTHORIZATION, bearerToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Invalid request"));

		when(orderService.listUserOrders(USER_ID, -1, 20))
				.thenThrow(new InvalidOrderPaginationException());
		mockMvc.perform(get("/api/v1/users/me/orders?page=-1")
						.header(HttpHeaders.AUTHORIZATION, bearerToken()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Invalid pagination"));
	}

	@Test
	void invalidReservationAndExpiredReservationUseDistinctErrors() throws Exception {
		when(orderService.createOrder(eq(USER_ID), any(OrderCreateRequest.class)))
				.thenThrow(new ReservationNotFoundException())
				.thenThrow(new OrderConflictException());

		mockMvc.perform(post("/api/v1/orders")
						.header(HttpHeaders.AUTHORIZATION, bearerToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Reservation not found"));

		mockMvc.perform(post("/api/v1/orders")
						.header(HttpHeaders.AUTHORIZATION, bearerToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Order conflict"));
	}

	@Test
	void foreignOrderIsNotExposed() throws Exception {
		when(orderService.getOrder(ORDER_ID, USER_ID)).thenThrow(new OrderNotFoundException());

		mockMvc.perform(get("/api/v1/orders/{orderId}", ORDER_ID)
						.header(HttpHeaders.AUTHORIZATION, bearerToken()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Order not found"));
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
		return "{\"reservationId\":\"%s\"}".formatted(RESERVATION_ID);
	}

	private static OrderResponse orderResponse() {
		return OrderResponse.from(orderRecord());
	}

	private static OrderRecord orderRecord() {
		return new OrderRecord(
				ORDER_ID,
				RESERVATION_ID,
				USER_ID,
				OrderStatus.PENDING,
				new BigDecimal("210000.75"),
				"VND",
				NOW,
				NOW);
	}
}
