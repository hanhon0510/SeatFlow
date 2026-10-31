package com.seatflow.analytics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import com.seatflow.user.CurrentUserService;
import com.seatflow.user.UserMapper;
import com.seatflow.user.UserRecord;
import com.seatflow.user.UserRole;
import com.seatflow.user.UserStatus;

@WebMvcTest(AdminAnalyticsController.class)
@Import({
		SecurityConfig.class,
		JwtConfig.class,
		JwtTokenService.class,
		CurrentUserService.class
})
@TestPropertySource(properties = {
		"server.port=8080",
		"seatflow.jwt.secret=" + JwtTestSupport.DEFAULT_SECRET,
		"seatflow.jwt.issuer=" + JwtTestSupport.ISSUER,
		"seatflow.jwt.expires-in-seconds=900"
})
class AdminAnalyticsControllerTests {

	private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");
	private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
	private static final LocalDate TO = LocalDate.of(2026, 9, 10);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserMapper userMapper;

	@MockitoBean
	private AnalyticsService analyticsService;

	@Test
	void adminReadsTheReportForARange() throws Exception {
		UserRecord admin = admin();
		UUID event = UUID.randomUUID();
		when(analyticsService.getReport(FROM, TO, event)).thenReturn(new AnalyticsReport(
				FROM,
				TO,
				event,
				List.of(new AnalyticsCurrencyTotal("USD", 3, 5, new BigDecimal("300.00"), new BigDecimal("50.00"), 1)),
				List.of(new AnalyticsDailyPoint(TO, "USD", 3, 5, new BigDecimal("300.00"), new BigDecimal("50.00"))),
				List.of(new AnalyticsEventTotal(event, "Opening Night", "USD", 3, 5, new BigDecimal("300.00"),
						new BigDecimal("50.00"))),
				new AnalyticsCheckout(10, 3, 1, 4, 1, 1),
				new AnalyticsWaitlist(6, 3, 2, 1, 0),
				NOW));

		mockMvc.perform(get("/api/v1/admin/analytics")
						.param("from", "2026-09-01")
						.param("to", "2026-09-10")
						.param("eventId", event.toString())
						.header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.from").value("2026-09-01"))
				.andExpect(jsonPath("$.totals[0].currency").value("USD"))
				.andExpect(jsonPath("$.totals[0].gross").value(300.00))
				.andExpect(jsonPath("$.topEvents[0].eventName").value("Opening Night"))
				.andExpect(jsonPath("$.checkout.started").value(10))
				.andExpect(jsonPath("$.waitlist.fulfilled").value(2));
	}

	@Test
	void adminDownloadsTheRangeAsCsv() throws Exception {
		UserRecord admin = admin();
		when(analyticsService.exportCsv(FROM, TO, null)).thenReturn("date,event\n");
		when(analyticsService.exportFileName(FROM, TO)).thenReturn("seatflow-sales-2026-09-01-to-2026-09-10.csv");

		mockMvc.perform(get("/api/v1/admin/analytics/export")
						.param("from", "2026-09-01")
						.param("to", "2026-09-10")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8"))
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=\"seatflow-sales-2026-09-01-to-2026-09-10.csv\""))
				.andExpect(content().string("date,event\n"));
	}

	@Test
	void anInvalidRangeIsABadRequest() throws Exception {
		UserRecord admin = admin();
		when(analyticsService.getReport(any(), any(), any()))
				.thenThrow(new InvalidAnalyticsRangeException("The start date must not be after the end date."));

		mockMvc.perform(get("/api/v1/admin/analytics")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_ANALYTICS_RANGE"));
	}

	@Test
	void aMalformedDateIsABadRequest() throws Exception {
		UserRecord admin = admin();

		mockMvc.perform(get("/api/v1/admin/analytics")
						.param("from", "yesterday")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(analyticsService);
	}

	@Test
	void aBuyerIsForbiddenFromTheReportAndTheExport() throws Exception {
		String token = bearerToken(user(UserRole.USER));

		mockMvc.perform(get("/api/v1/admin/analytics").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/admin/analytics/export").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isForbidden());
		verifyNoInteractions(analyticsService);
	}

	@Test
	void anonymousRequestsAreUnauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/admin/analytics")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/admin/analytics/export")).andExpect(status().isUnauthorized());
	}

	private UserRecord admin() {
		UserRecord admin = user(UserRole.ADMIN);
		when(userMapper.findById(admin.id())).thenReturn(admin);
		return admin;
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
