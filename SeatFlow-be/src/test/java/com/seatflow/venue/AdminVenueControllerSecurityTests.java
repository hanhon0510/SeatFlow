package com.seatflow.venue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

@WebMvcTest(AdminVenueController.class)
@Import({
		SecurityConfig.class,
		JwtConfig.class,
		JwtTokenService.class,
		VenueService.class
})
@TestPropertySource(properties = {
		"server.port=8080",
		"seatflow.jwt.secret=" + JwtTestSupport.DEFAULT_SECRET,
		"seatflow.jwt.issuer=" + JwtTestSupport.ISSUER,
		"seatflow.jwt.expires-in-seconds=900"
})
class AdminVenueControllerSecurityTests {

	private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private VenueMapper venueMapper;

	@Test
	void adminCanCreateVenue() throws Exception {
		when(venueMapper.findById(any(UUID.class))).thenAnswer(invocation -> venue(invocation.getArgument(0), "Main Hall"));

		mockMvc.perform(post("/api/v1/admin/venues")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(venueJson("Main Hall", "Asia/Ho_Chi_Minh")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.name").value("Main Hall"))
				.andExpect(jsonPath("$.timezone").value("Asia/Ho_Chi_Minh"))
				.andExpect(jsonPath("$.status").value("ACTIVE"));

		ArgumentCaptor<VenueRecord> insertedVenue = ArgumentCaptor.forClass(VenueRecord.class);
		verify(venueMapper).insert(insertedVenue.capture());
		org.assertj.core.api.Assertions.assertThat(insertedVenue.getValue().timezone()).isEqualTo("Asia/Ho_Chi_Minh");
	}

	@Test
	void adminCanListVenues() throws Exception {
		VenueRecord first = venue(UUID.randomUUID(), "Alpha Hall");
		VenueRecord second = venue(UUID.randomUUID(), "Beta Hall");
		when(venueMapper.count()).thenReturn(2L);
		when(venueMapper.findPage(2, 0L)).thenReturn(List.of(first, second));

		mockMvc.perform(get("/api/v1/admin/venues")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN))
						.param("page", "0")
						.param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].name").value("Alpha Hall"))
				.andExpect(jsonPath("$.items[1].name").value("Beta Hall"))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(2))
				.andExpect(jsonPath("$.totalItems").value(2))
				.andExpect(jsonPath("$.totalPages").value(1));
	}

	@Test
	void adminCanReadVenue() throws Exception {
		UUID venueId = UUID.randomUUID();
		when(venueMapper.findById(venueId)).thenReturn(venue(venueId, "Main Hall"));

		mockMvc.perform(get("/api/v1/admin/venues/{venueId}", venueId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(venueId.toString()))
				.andExpect(jsonPath("$.name").value("Main Hall"));
	}

	@Test
	void adminCanUpdateVenue() throws Exception {
		UUID venueId = UUID.randomUUID();
		when(venueMapper.update(any(VenueRecord.class))).thenReturn(1);
		when(venueMapper.findById(venueId)).thenReturn(venue(venueId, "Updated Hall", "UTC"));

		mockMvc.perform(put("/api/v1/admin/venues/{venueId}", venueId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(venueJson("Updated Hall", "UTC")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(venueId.toString()))
				.andExpect(jsonPath("$.name").value("Updated Hall"))
				.andExpect(jsonPath("$.timezone").value("UTC"));

		verify(venueMapper).update(any(VenueRecord.class));
	}

	@Test
	void adminCanArchiveVenue() throws Exception {
		UUID venueId = UUID.randomUUID();
		when(venueMapper.archive(venueId)).thenReturn(1);
		when(venueMapper.findById(venueId)).thenReturn(archivedVenue(venueId));

		mockMvc.perform(post("/api/v1/admin/venues/{venueId}/archive", venueId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(venueId.toString()))
				.andExpect(jsonPath("$.status").value("ARCHIVED"));
	}

	@Test
	void invalidTimezoneReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/v1/admin/venues")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(venueJson("Main Hall", "Mars/Phobos")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.correlationId").isNotEmpty())
				.andExpect(jsonPath("$.title").value("Invalid timezone"));
	}

	@Test
	void pageSizeAboveMaximumReturnsBadRequest() throws Exception {
		mockMvc.perform(get("/api/v1/admin/venues")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN))
						.param("size", String.valueOf(VenueService.MAX_PAGE_SIZE + 1)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid pagination"));
	}

	@Test
	void normalUserCannotModifyVenues() throws Exception {
		UUID venueId = UUID.randomUUID();

		mockMvc.perform(post("/api/v1/admin/venues")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.USER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(venueJson("Main Hall", "UTC")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.title").value("Forbidden"));

		mockMvc.perform(put("/api/v1/admin/venues/{venueId}", venueId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.USER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(venueJson("Updated Hall", "UTC")))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/v1/admin/venues/{venueId}/archive", venueId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.USER)))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedUserReceivesUnauthorized() throws Exception {
		mockMvc.perform(post("/api/v1/admin/venues")
						.contentType(MediaType.APPLICATION_JSON)
						.content(venueJson("Main Hall", "UTC")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.title").value("Unauthorized"));
	}

	@Test
	void destructiveDeletionIsNotSupported() throws Exception {
		mockMvc.perform(delete("/api/v1/admin/venues/{venueId}", UUID.randomUUID())
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN)))
				.andExpect(status().isMethodNotAllowed());
	}

	private String bearerToken(UserRole role) {
		UserRecord user = new UserRecord(
				UUID.randomUUID(),
				"%s@example.com".formatted(role.name().toLowerCase()),
				"password-hash",
				role,
				UserStatus.ACTIVE,
				NOW,
				NOW);
		return "Bearer " + jwtTokenService.issueAccessToken(user).accessToken();
	}

	private static VenueRecord venue(UUID id, String name) {
		return venue(id, name, "Asia/Ho_Chi_Minh");
	}

	private static VenueRecord venue(UUID id, String name, String timezone) {
		return new VenueRecord(
				id,
				name,
				"1 Event Street",
				"Ho Chi Minh City",
				"Vietnam",
				timezone,
				VenueStatus.ACTIVE,
				NOW,
				NOW);
	}

	private static VenueRecord archivedVenue(UUID id) {
		return new VenueRecord(
				id,
				"Main Hall",
				"1 Event Street",
				"Ho Chi Minh City",
				"Vietnam",
				"Asia/Ho_Chi_Minh",
				VenueStatus.ARCHIVED,
				NOW,
				NOW);
	}

	private static String venueJson(String name, String timezone) {
		return """
				{
				  "name": "%s",
				  "address": "1 Event Street",
				  "city": "Ho Chi Minh City",
				  "country": "Vietnam",
				  "timezone": "%s"
				}
				""".formatted(name, timezone);
	}
}
