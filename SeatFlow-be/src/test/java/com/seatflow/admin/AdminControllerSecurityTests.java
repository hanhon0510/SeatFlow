package com.seatflow.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

import com.seatflow.event.EventStatus;
import com.seatflow.security.JwtConfig;
import com.seatflow.security.JwtTokenService;
import com.seatflow.security.SecurityConfig;
import com.seatflow.support.JwtTestSupport;
import com.seatflow.user.CurrentUserService;
import com.seatflow.user.UserMapper;
import com.seatflow.user.UserRecord;
import com.seatflow.user.UserRole;
import com.seatflow.user.UserStatus;

@WebMvcTest(AdminController.class)
@Import({
		SecurityConfig.class,
		JwtConfig.class,
		JwtTokenService.class,
		AdminService.class,
		AdminDashboardService.class,
		CurrentUserService.class
})
@TestPropertySource(properties = {
		"server.port=8080",
		"seatflow.jwt.secret=" + JwtTestSupport.DEFAULT_SECRET,
		"seatflow.jwt.issuer=" + JwtTestSupport.ISSUER,
		"seatflow.jwt.expires-in-seconds=900"
})
class AdminControllerSecurityTests {

	private static final Instant NOW = Instant.parse("2026-07-29T00:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserMapper userMapper;

	@MockitoBean
	private AdminDashboardMapper adminDashboardMapper;

	@Test
	void adminCanAccessAdminEndpoint() throws Exception {
		UserRecord admin = user(UserRole.ADMIN);
		when(userMapper.findById(admin.id())).thenReturn(admin);

		mockMvc.perform(get("/api/v1/admin")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(admin.id().toString()))
				.andExpect(jsonPath("$.email").value(admin.email()))
				.andExpect(jsonPath("$.role").value("ADMIN"));
	}

	@Test
	void normalUserReceivesForbiddenAtAdminEndpoint() throws Exception {
		UserRecord user = user(UserRole.USER);

		mockMvc.perform(get("/api/v1/admin")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.correlationId").isNotEmpty())
				.andExpect(jsonPath("$.title").value("Forbidden"));
	}

	@Test
	void adminDashboardSummarisesVenuesEventsAndSales() throws Exception {
		UserRecord admin = user(UserRole.ADMIN);
		when(userMapper.findById(admin.id())).thenReturn(admin);
		when(adminDashboardMapper.findVenueSummary())
				.thenReturn(new AdminDashboardVenues(3, 2, 1, 5, 120));
		when(adminDashboardMapper.findEventSummary(any(), any()))
				.thenReturn(new AdminDashboardEvents(6, 2, 3, 1, 0, 2, 1));
		when(adminDashboardMapper.countOrdersByStatus("PAID")).thenReturn(9L);
		when(adminDashboardMapper.countOrdersByStatus("PENDING")).thenReturn(4L);
		when(adminDashboardMapper.countTicketsByStatus("ACTIVE")).thenReturn(11L);
		when(adminDashboardMapper.countTicketsByStatus("USED")).thenReturn(7L);
		when(adminDashboardMapper.findRevenueByCurrency()).thenReturn(List.of(
				new AdminDashboardRevenue("VND", new BigDecimal("1500000.00"), 9)));
		when(adminDashboardMapper.findUpcomingEvents(any(), anyInt())).thenReturn(List.of(
				new AdminDashboardUpcomingEvent(
						UUID.randomUUID(),
						"Season Opener",
						"Main Hall",
						NOW,
						NOW,
						EventStatus.PUBLISHED,
						100,
						42)));

		mockMvc.perform(get("/api/v1/admin/dashboard")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.venues.total").value(3))
				.andExpect(jsonPath("$.venues.seats").value(120))
				.andExpect(jsonPath("$.events.published").value(3))
				.andExpect(jsonPath("$.events.onSaleNow").value(2))
				.andExpect(jsonPath("$.sales.paidOrders").value(9))
				.andExpect(jsonPath("$.sales.ticketsUsed").value(7))
				.andExpect(jsonPath("$.sales.revenue[0].currency").value("VND"))
				.andExpect(jsonPath("$.upcomingEvents[0].name").value("Season Opener"))
				.andExpect(jsonPath("$.upcomingEvents[0].seatsSold").value(42))
				.andExpect(jsonPath("$.generatedAt").isNotEmpty());
	}

	@Test
	void normalUserReceivesForbiddenAtAdminDashboard() throws Exception {
		mockMvc.perform(get("/api/v1/admin/dashboard")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(user(UserRole.USER))))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.title").value("Forbidden"));
	}

	@Test
	void unauthenticatedRequestReceivesUnauthorizedAtAdminEndpoint() throws Exception {
		mockMvc.perform(get("/api/v1/admin"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.correlationId").isNotEmpty())
				.andExpect(jsonPath("$.title").value("Unauthorized"));
	}

	@Test
	void adminClaimIsRejectedWhenPersistedUserIsNotAdmin() throws Exception {
		UserRecord tokenUser = user(UserRole.ADMIN);
		UserRecord persistedUser = new UserRecord(
				tokenUser.id(),
				tokenUser.email(),
				tokenUser.passwordHash(),
				UserRole.USER,
				tokenUser.status(),
				tokenUser.createdAt(),
				tokenUser.updatedAt());
		when(userMapper.findById(tokenUser.id())).thenReturn(persistedUser);

		mockMvc.perform(get("/api/v1/admin")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(tokenUser)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.title").value("Forbidden"));
	}

	private String bearerToken(UserRecord user) {
		return "Bearer " + jwtTokenService.issueAccessToken(user).accessToken();
	}

	private static UserRecord user(UserRole role) {
		return new UserRecord(
				UUID.randomUUID(),
				"%s@example.com".formatted(role.name().toLowerCase()),
				"password-hash",
				role,
				UserStatus.ACTIVE,
				NOW,
				NOW);
	}
}
