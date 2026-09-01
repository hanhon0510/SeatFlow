package com.seatflow.seating;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.dao.DuplicateKeyException;
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
import com.seatflow.venue.VenueMapper;
import com.seatflow.venue.VenueRecord;
import com.seatflow.venue.VenueStatus;

@WebMvcTest(AdminSeatingController.class)
@Import({
		SecurityConfig.class,
		JwtConfig.class,
		JwtTokenService.class,
		SeatingService.class
})
@TestPropertySource(properties = {
		"server.port=8080",
		"seatflow.jwt.secret=" + JwtTestSupport.DEFAULT_SECRET,
		"seatflow.jwt.issuer=" + JwtTestSupport.ISSUER,
		"seatflow.jwt.expires-in-seconds=900"
})
class AdminSeatingControllerSecurityTests {

	private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private VenueMapper venueMapper;

	@MockitoBean
	private VenueSectionMapper sectionMapper;

	@MockitoBean
	private SeatMapper seatMapper;

	@Test
	void adminCanCreateSection() throws Exception {
		UUID venueId = UUID.randomUUID();
		when(venueMapper.findById(venueId)).thenReturn(venue(venueId));
		when(sectionMapper.findById(any(UUID.class))).thenAnswer(invocation -> section(
				invocation.getArgument(0),
				venueId,
				"Orchestra",
				1));

		mockMvc.perform(post("/api/v1/admin/venues/{venueId}/sections", venueId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(sectionJson("Orchestra", 1)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.venueId").value(venueId.toString()))
				.andExpect(jsonPath("$.name").value("Orchestra"))
				.andExpect(jsonPath("$.displayOrder").value(1));

		ArgumentCaptor<VenueSectionRecord> insertedSection = ArgumentCaptor.forClass(VenueSectionRecord.class);
		verify(sectionMapper).insert(insertedSection.capture());
		assertThat(insertedSection.getValue().venueId()).isEqualTo(venueId);
	}

	@Test
	void adminCanUpdateAndDeleteSection() throws Exception {
		UUID sectionId = UUID.randomUUID();
		UUID venueId = UUID.randomUUID();
		when(sectionMapper.update(any(VenueSectionRecord.class))).thenReturn(1);
		when(sectionMapper.findById(sectionId)).thenReturn(section(sectionId, venueId, "Balcony", 2));
		when(sectionMapper.delete(sectionId)).thenReturn(1);

		mockMvc.perform(put("/api/v1/admin/sections/{sectionId}", sectionId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(sectionJson("Balcony", 2)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(sectionId.toString()))
				.andExpect(jsonPath("$.name").value("Balcony"))
				.andExpect(jsonPath("$.displayOrder").value(2));

		mockMvc.perform(delete("/api/v1/admin/sections/{sectionId}", sectionId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN)))
				.andExpect(status().isNoContent());
	}

	@Test
	void adminCanCreateSeat() throws Exception {
		UUID venueId = UUID.randomUUID();
		UUID sectionId = UUID.randomUUID();
		when(sectionMapper.findById(sectionId)).thenReturn(section(sectionId, venueId, "Orchestra", 1));
		when(seatMapper.findById(any(UUID.class))).thenAnswer(invocation -> seat(
				invocation.getArgument(0),
				sectionId,
				"A",
				1,
				"A1",
				false));

		mockMvc.perform(post("/api/v1/admin/sections/{sectionId}/seats", sectionId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(seatJson("A", 1, "A1", false)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.sectionId").value(sectionId.toString()))
				.andExpect(jsonPath("$.seatLabel").value("A1"))
				.andExpect(jsonPath("$.accessible").value(false));
	}

	@Test
	void adminCanBulkCreateSeats() throws Exception {
		UUID venueId = UUID.randomUUID();
		UUID sectionId = UUID.randomUUID();
		when(sectionMapper.findById(sectionId)).thenReturn(section(sectionId, venueId, "Orchestra", 1));
		when(seatMapper.findById(any(UUID.class))).thenAnswer(invocation -> seat(
				invocation.getArgument(0),
				sectionId,
				"A",
				1,
				"A1",
				false));

		mockMvc.perform(post("/api/v1/admin/sections/{sectionId}/seats/bulk", sectionId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "seats": [
								    { "rowLabel": "A", "seatNumber": 1, "seatLabel": "A1", "accessible": false },
								    { "rowLabel": "A", "seatNumber": 2, "seatLabel": "A2", "accessible": true }
								  ]
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$[0].sectionId").value(sectionId.toString()))
				.andExpect(jsonPath("$[1].sectionId").value(sectionId.toString()));

		ArgumentCaptor<List<SeatRecord>> insertedSeats = ArgumentCaptor.captor();
		verify(seatMapper).insertBatch(insertedSeats.capture());
		assertThat(insertedSeats.getValue()).hasSize(2);
	}

	@Test
	void seatIsAccessibleWhenTheFlagIsOmitted() throws Exception {
		UUID venueId = UUID.randomUUID();
		UUID sectionId = UUID.randomUUID();
		when(sectionMapper.findById(sectionId)).thenReturn(section(sectionId, venueId, "Orchestra", 1));
		when(seatMapper.findById(any(UUID.class))).thenAnswer(invocation -> seat(
				invocation.getArgument(0),
				sectionId,
				"A",
				1,
				"A1",
				true));

		mockMvc.perform(post("/api/v1/admin/sections/{sectionId}/seats", sectionId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "rowLabel": "A", "seatNumber": 1, "seatLabel": "A1" }
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.accessible").value(true));

		ArgumentCaptor<SeatRecord> insertedSeat = ArgumentCaptor.forClass(SeatRecord.class);
		verify(seatMapper).insert(insertedSeat.capture());
		assertThat(insertedSeat.getValue().accessible()).isTrue();
	}

	@Test
	void adminCanUpdateSeatAccessibility() throws Exception {
		UUID sectionId = UUID.randomUUID();
		UUID seatId = UUID.randomUUID();
		when(seatMapper.updateAccessible(seatId, false)).thenReturn(1);
		when(seatMapper.findById(seatId)).thenReturn(seat(seatId, sectionId, "A", 1, "A1", false));

		mockMvc.perform(put("/api/v1/admin/seats/{seatId}", seatId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "accessible": false }
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(seatId.toString()))
				.andExpect(jsonPath("$.seatLabel").value("A1"))
				.andExpect(jsonPath("$.accessible").value(false));

		verify(seatMapper).updateAccessible(seatId, false);
	}

	@Test
	void updatingAnUnknownSeatReturnsNotFound() throws Exception {
		UUID seatId = UUID.randomUUID();
		when(seatMapper.updateAccessible(seatId, true)).thenReturn(0);

		mockMvc.perform(put("/api/v1/admin/seats/{seatId}", seatId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "accessible": true }
								"""))
				.andExpect(status().isNotFound());
	}

	@Test
	void duplicateSeatLabelReturnsConflict() throws Exception {
		UUID venueId = UUID.randomUUID();
		UUID sectionId = UUID.randomUUID();
		when(sectionMapper.findById(sectionId)).thenReturn(section(sectionId, venueId, "Orchestra", 1));
		org.mockito.Mockito.doThrow(new DuplicateKeyException("duplicate section label"))
				.when(seatMapper)
				.insert(any(SeatRecord.class));

		mockMvc.perform(post("/api/v1/admin/sections/{sectionId}/seats", sectionId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(seatJson("A", 1, "A1", false)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.correlationId").isNotEmpty())
				.andExpect(jsonPath("$.title").value("Duplicate seat label"));
	}

	@Test
	void seatLayoutIsGroupedBySection() throws Exception {
		UUID venueId = UUID.randomUUID();
		UUID orchestraId = UUID.randomUUID();
		UUID balconyId = UUID.randomUUID();
		when(venueMapper.findById(venueId)).thenReturn(venue(venueId));
		when(seatMapper.findSeatLayoutByVenueId(venueId)).thenReturn(List.of(
				layoutRow(orchestraId, "Orchestra", 1, UUID.randomUUID(), "A", 1, "A1", false),
				layoutRow(orchestraId, "Orchestra", 1, UUID.randomUUID(), "A", 2, "A2", true),
				layoutRow(balconyId, "Balcony", 2, null, null, null, null, null)));

		mockMvc.perform(get("/api/v1/admin/venues/{venueId}/seat-layout", venueId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.venueId").value(venueId.toString()))
				.andExpect(jsonPath("$.sections[0].id").value(orchestraId.toString()))
				.andExpect(jsonPath("$.sections[0].seats[0].seatLabel").value("A1"))
				.andExpect(jsonPath("$.sections[0].seats[1].seatLabel").value("A2"))
				.andExpect(jsonPath("$.sections[1].id").value(balconyId.toString()))
				.andExpect(jsonPath("$.sections[1].seats").isEmpty());
	}

	@Test
	void normalUserCannotModifySectionsOrSeats() throws Exception {
		UUID venueId = UUID.randomUUID();
		UUID sectionId = UUID.randomUUID();

		mockMvc.perform(post("/api/v1/admin/venues/{venueId}/sections", venueId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.USER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(sectionJson("Orchestra", 1)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.title").value("Forbidden"));

		mockMvc.perform(post("/api/v1/admin/sections/{sectionId}/seats", sectionId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.USER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(seatJson("A", 1, "A1", false)))
				.andExpect(status().isForbidden());

		mockMvc.perform(put("/api/v1/admin/seats/{seatId}", UUID.randomUUID())
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.USER))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "accessible": false }
								"""))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedUserReceivesUnauthorized() throws Exception {
		mockMvc.perform(post("/api/v1/admin/venues/{venueId}/sections", UUID.randomUUID())
						.contentType(MediaType.APPLICATION_JSON)
						.content(sectionJson("Orchestra", 1)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.title").value("Unauthorized"));
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

	private static VenueRecord venue(UUID id) {
		return new VenueRecord(
				id,
				"Main Hall",
				"1 Event Street",
				"Ho Chi Minh City",
				"Vietnam",
				"Asia/Ho_Chi_Minh",
				VenueStatus.ACTIVE,
				NOW,
				NOW);
	}

	private static VenueSectionRecord section(UUID id, UUID venueId, String name, int displayOrder) {
		return new VenueSectionRecord(id, venueId, name, displayOrder, NOW);
	}

	private static SeatRecord seat(
			UUID id,
			UUID sectionId,
			String rowLabel,
			int seatNumber,
			String seatLabel,
			boolean accessible) {
		return new SeatRecord(id, sectionId, rowLabel, seatNumber, seatLabel, accessible, NOW);
	}

	private static SeatLayoutRow layoutRow(
			UUID sectionId,
			String sectionName,
			int displayOrder,
			UUID seatId,
			String rowLabel,
			Integer seatNumber,
			String seatLabel,
			Boolean accessible) {
		return new SeatLayoutRow(
				sectionId,
				sectionName,
				displayOrder,
				NOW,
				seatId,
				rowLabel,
				seatNumber,
				seatLabel,
				accessible,
				seatId == null ? null : NOW);
	}

	private static String sectionJson(String name, int displayOrder) {
		return """
				{
				  "name": "%s",
				  "displayOrder": %d
				}
				""".formatted(name, displayOrder);
	}

	private static String seatJson(String rowLabel, int seatNumber, String seatLabel, boolean accessible) {
		return """
				{
				  "rowLabel": "%s",
				  "seatNumber": %d,
				  "seatLabel": "%s",
				  "accessible": %s
				}
				""".formatted(rowLabel, seatNumber, seatLabel, accessible);
	}
}
