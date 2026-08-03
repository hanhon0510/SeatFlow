package com.seatflow.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import com.seatflow.venue.VenueMapper;
import com.seatflow.venue.VenueRecord;
import com.seatflow.venue.VenueService;
import com.seatflow.venue.VenueStatus;

@WebMvcTest(AdminEventController.class)
@Import({
		SecurityConfig.class,
		JwtConfig.class,
		JwtTokenService.class,
		VenueService.class,
		EventService.class,
		EventSectionPricingService.class,
		EventPublishService.class
})
@TestPropertySource(properties = {
		"server.port=8080",
		"seatflow.jwt.secret=" + JwtTestSupport.DEFAULT_SECRET,
		"seatflow.jwt.issuer=" + JwtTestSupport.ISSUER,
		"seatflow.jwt.expires-in-seconds=900"
})
class AdminEventControllerSecurityTests {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");
	private static final Instant EVENT_START = Instant.parse("2026-09-01T19:00:00Z");
	private static final Instant SALES_START = Instant.parse("2026-08-01T00:00:00Z");
	private static final Instant SALES_END = Instant.parse("2026-09-01T18:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private VenueMapper venueMapper;

	@MockitoBean
	private EventMapper eventMapper;

	@MockitoBean
	private EventSectionMapper eventSectionMapper;

	@MockitoBean
	private EventSeatMapper eventSeatMapper;

	@Test
	void adminCanCreateDraftEvent() throws Exception {
		UUID venueId = UUID.randomUUID();
		when(venueMapper.findById(venueId)).thenReturn(venue(venueId, VenueStatus.ACTIVE));
		when(eventMapper.findById(any(UUID.class))).thenAnswer(invocation -> event(
				invocation.getArgument(0),
				venueId,
				"Opening Night",
				EventStatus.DRAFT));

		mockMvc.perform(post("/api/v1/admin/events")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(eventJson(venueId, "Opening Night", EVENT_START, SALES_START, SALES_END)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.venueId").value(venueId.toString()))
				.andExpect(jsonPath("$.name").value("Opening Night"))
				.andExpect(jsonPath("$.status").value("DRAFT"));

		ArgumentCaptor<EventRecord> insertedEvent = ArgumentCaptor.forClass(EventRecord.class);
		verify(eventMapper).insert(insertedEvent.capture());
		org.assertj.core.api.Assertions.assertThat(insertedEvent.getValue().venueId()).isEqualTo(venueId);
		org.assertj.core.api.Assertions.assertThat(insertedEvent.getValue().status()).isNull();
	}

	@Test
	void adminCanListEvents() throws Exception {
		UUID venueId = UUID.randomUUID();
		EventRecord first = event(UUID.randomUUID(), venueId, "Alpha Event", EventStatus.DRAFT);
		EventRecord second = event(UUID.randomUUID(), venueId, "Beta Event", EventStatus.DRAFT);
		when(eventMapper.count()).thenReturn(2L);
		when(eventMapper.findPage(2, 0L)).thenReturn(List.of(first, second));

		mockMvc.perform(get("/api/v1/admin/events")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN))
						.param("page", "0")
						.param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].name").value("Alpha Event"))
				.andExpect(jsonPath("$.items[1].name").value("Beta Event"))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(2))
				.andExpect(jsonPath("$.totalItems").value(2))
				.andExpect(jsonPath("$.totalPages").value(1));
	}

	@Test
	void adminCanReadEvent() throws Exception {
		UUID eventId = UUID.randomUUID();
		UUID venueId = UUID.randomUUID();
		when(eventMapper.findById(eventId)).thenReturn(event(eventId, venueId, "Opening Night", EventStatus.DRAFT));

		mockMvc.perform(get("/api/v1/admin/events/{eventId}", eventId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(eventId.toString()))
				.andExpect(jsonPath("$.venueId").value(venueId.toString()))
				.andExpect(jsonPath("$.name").value("Opening Night"));
	}

	@Test
	void adminCanUpdateDraftEvent() throws Exception {
		UUID eventId = UUID.randomUUID();
		UUID venueId = UUID.randomUUID();
		when(venueMapper.findById(venueId)).thenReturn(venue(venueId, VenueStatus.ACTIVE));
		when(eventMapper.update(any(EventRecord.class))).thenReturn(1);
		when(eventMapper.findById(eventId)).thenReturn(event(eventId, venueId, "Updated Event", EventStatus.DRAFT));

		mockMvc.perform(put("/api/v1/admin/events/{eventId}", eventId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(eventJson(venueId, "Updated Event", EVENT_START, SALES_START, SALES_END)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(eventId.toString()))
				.andExpect(jsonPath("$.name").value("Updated Event"))
				.andExpect(jsonPath("$.status").value("DRAFT"));

		verify(eventMapper).update(any(EventRecord.class));
	}

	@Test
	void invalidTimingReturnsBadRequest() throws Exception {
		UUID venueId = UUID.randomUUID();

		mockMvc.perform(post("/api/v1/admin/events")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(eventJson(venueId, "Opening Night", EVENT_START, EVENT_START, SALES_END)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Invalid event timing"));
	}

	@Test
	void archivedVenueReturnsConflict() throws Exception {
		UUID venueId = UUID.randomUUID();
		when(venueMapper.findById(venueId)).thenReturn(venue(venueId, VenueStatus.ARCHIVED));

		mockMvc.perform(post("/api/v1/admin/events")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(eventJson(venueId, "Opening Night", EVENT_START, SALES_START, SALES_END)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Archived venue cannot host new events"));
	}

	@Test
	void publishedVenueChangeConflictReturnsConflict() throws Exception {
		UUID eventId = UUID.randomUUID();
		UUID oldVenueId = UUID.randomUUID();
		UUID newVenueId = UUID.randomUUID();
		when(venueMapper.findById(newVenueId)).thenReturn(venue(newVenueId, VenueStatus.ACTIVE));
		when(eventMapper.update(any(EventRecord.class))).thenReturn(0);
		when(eventMapper.findById(eventId)).thenReturn(event(
				eventId,
				oldVenueId,
				"Published Event",
				EventStatus.PUBLISHED));

		mockMvc.perform(put("/api/v1/admin/events/{eventId}", eventId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(eventJson(newVenueId, "Published Event", EVENT_START, SALES_START, SALES_END)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Event state conflict"));
	}

	@Test
	void adminCanReplaceAndReadEventSections() throws Exception {
		UUID eventId = UUID.randomUUID();
		UUID venueId = UUID.randomUUID();
		UUID sectionId = UUID.randomUUID();
		EventSectionRecord configuredSection = eventSection(
				UUID.randomUUID(),
				eventId,
				sectionId,
				new BigDecimal("125000.00"),
				true);
		when(eventMapper.findByIdForUpdate(eventId)).thenReturn(event(
				eventId,
				venueId,
				"Opening Night",
				EventStatus.DRAFT));
		when(eventSectionMapper.insertForDraftEvent(any(EventSectionRecord.class))).thenReturn(1);
		when(eventSectionMapper.findByEventId(eventId)).thenReturn(List.of(configuredSection));
		when(eventMapper.findById(eventId)).thenReturn(event(eventId, venueId, "Opening Night", EventStatus.DRAFT));

		mockMvc.perform(put("/api/v1/admin/events/{eventId}/sections", eventId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(eventSectionsJson(sectionId, "125000.00", true)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eventId").value(eventId.toString()))
				.andExpect(jsonPath("$.sections[0].venueSectionId").value(sectionId.toString()))
				.andExpect(jsonPath("$.sections[0].price").value(125000.00))
				.andExpect(jsonPath("$.sections[0].salesEnabled").value(true));

		mockMvc.perform(get("/api/v1/admin/events/{eventId}/sections", eventId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eventId").value(eventId.toString()))
				.andExpect(jsonPath("$.sections[0].venueSectionId").value(sectionId.toString()));

		ArgumentCaptor<EventSectionRecord> insertedSection = ArgumentCaptor.forClass(EventSectionRecord.class);
		verify(eventSectionMapper).deleteByEventId(eventId);
		verify(eventSectionMapper).insertForDraftEvent(insertedSection.capture());
		org.assertj.core.api.Assertions.assertThat(insertedSection.getValue().eventId()).isEqualTo(eventId);
		org.assertj.core.api.Assertions.assertThat(insertedSection.getValue().venueSectionId()).isEqualTo(sectionId);
		org.assertj.core.api.Assertions.assertThat(insertedSection.getValue().price())
				.isEqualByComparingTo("125000.00");
	}

	@Test
	void invalidEventSectionReturnsBadRequest() throws Exception {
		UUID eventId = UUID.randomUUID();
		UUID venueId = UUID.randomUUID();
		UUID invalidSectionId = UUID.randomUUID();
		when(eventMapper.findByIdForUpdate(eventId)).thenReturn(event(
				eventId,
				venueId,
				"Opening Night",
				EventStatus.DRAFT));
		when(eventSectionMapper.insertForDraftEvent(any(EventSectionRecord.class))).thenReturn(0);

		mockMvc.perform(put("/api/v1/admin/events/{eventId}/sections", eventId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(eventSectionsJson(invalidSectionId, "125000.00", true)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Invalid event section"));
	}

	@Test
	void negativeEventSectionPriceReturnsBadRequest() throws Exception {
		UUID eventId = UUID.randomUUID();
		UUID sectionId = UUID.randomUUID();

		mockMvc.perform(put("/api/v1/admin/events/{eventId}/sections", eventId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(eventSectionsJson(sectionId, "-1.00", true)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Invalid request"));
	}

	@Test
	void publishedEventSectionReplacementReturnsConflict() throws Exception {
		UUID eventId = UUID.randomUUID();
		UUID venueId = UUID.randomUUID();
		UUID sectionId = UUID.randomUUID();
		when(eventMapper.findByIdForUpdate(eventId)).thenReturn(event(
				eventId,
				venueId,
				"Published Event",
				EventStatus.PUBLISHED));

		mockMvc.perform(put("/api/v1/admin/events/{eventId}/sections", eventId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(eventSectionsJson(sectionId, "125000.00", true)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Event state conflict"));
	}

	@Test
	void adminCanPublishEvent() throws Exception {
		UUID eventId = UUID.randomUUID();
		UUID venueId = UUID.randomUUID();
		when(eventMapper.findByIdForUpdate(eventId)).thenReturn(event(
				eventId,
				venueId,
				"Opening Night",
				EventStatus.DRAFT));
		when(eventSeatMapper.countSourceSeatsForEvent(eventId)).thenReturn(2L);
		when(eventSeatMapper.countMissingPricedSeatsForEvent(eventId)).thenReturn(0L);
		when(eventSeatMapper.insertForDraftEvent(eventId)).thenReturn(2);
		when(eventMapper.publishDraft(eventId)).thenReturn(1);
		when(eventSeatMapper.countByEventId(eventId)).thenReturn(2L);

		mockMvc.perform(post("/api/v1/admin/events/{eventId}/publish", eventId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eventId").value(eventId.toString()))
				.andExpect(jsonPath("$.status").value("PUBLISHED"))
				.andExpect(jsonPath("$.inventoryCount").value(2));

		verify(eventSeatMapper).insertForDraftEvent(eventId);
		verify(eventMapper).publishDraft(eventId);
	}

	@Test
	void duplicatePublishReturnsCurrentInventoryCount() throws Exception {
		UUID eventId = UUID.randomUUID();
		UUID venueId = UUID.randomUUID();
		when(eventMapper.findByIdForUpdate(eventId)).thenReturn(event(
				eventId,
				venueId,
				"Opening Night",
				EventStatus.PUBLISHED));
		when(eventSeatMapper.countByEventId(eventId)).thenReturn(2L);

		mockMvc.perform(post("/api/v1/admin/events/{eventId}/publish", eventId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eventId").value(eventId.toString()))
				.andExpect(jsonPath("$.status").value("PUBLISHED"))
				.andExpect(jsonPath("$.inventoryCount").value(2));
	}

	@Test
	void missingPricingPublishReturnsConflict() throws Exception {
		UUID eventId = UUID.randomUUID();
		UUID venueId = UUID.randomUUID();
		when(eventMapper.findByIdForUpdate(eventId)).thenReturn(event(
				eventId,
				venueId,
				"Opening Night",
				EventStatus.DRAFT));
		when(eventSeatMapper.countSourceSeatsForEvent(eventId)).thenReturn(2L);
		when(eventSeatMapper.countMissingPricedSeatsForEvent(eventId)).thenReturn(1L);

		mockMvc.perform(post("/api/v1/admin/events/{eventId}/publish", eventId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Event section pricing is incomplete"));
	}

	@Test
	void noSeatsPublishReturnsConflict() throws Exception {
		UUID eventId = UUID.randomUUID();
		UUID venueId = UUID.randomUUID();
		when(eventMapper.findByIdForUpdate(eventId)).thenReturn(event(
				eventId,
				venueId,
				"Opening Night",
				EventStatus.DRAFT));
		when(eventSeatMapper.countSourceSeatsForEvent(eventId)).thenReturn(0L);

		mockMvc.perform(post("/api/v1/admin/events/{eventId}/publish", eventId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.ADMIN)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Event has no seats"));
	}

	@Test
	void normalUserCannotManageEvents() throws Exception {
		UUID eventId = UUID.randomUUID();
		UUID venueId = UUID.randomUUID();

		mockMvc.perform(post("/api/v1/admin/events")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.USER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(eventJson(venueId, "Opening Night", EVENT_START, SALES_START, SALES_END)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("Forbidden"));

		mockMvc.perform(get("/api/v1/admin/events")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.USER)))
				.andExpect(status().isForbidden());

		mockMvc.perform(put("/api/v1/admin/events/{eventId}", eventId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.USER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(eventJson(venueId, "Opening Night", EVENT_START, SALES_START, SALES_END)))
				.andExpect(status().isForbidden());

		mockMvc.perform(put("/api/v1/admin/events/{eventId}/sections", eventId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.USER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(eventSectionsJson(UUID.randomUUID(), "125000.00", true)))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/admin/events/{eventId}/sections", eventId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.USER)))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/v1/admin/events/{eventId}/publish", eventId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.USER)))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedUserReceivesUnauthorized() throws Exception {
		mockMvc.perform(post("/api/v1/admin/events")
						.contentType(MediaType.APPLICATION_JSON)
						.content(eventJson(UUID.randomUUID(), "Opening Night", EVENT_START, SALES_START, SALES_END)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Unauthorized"));
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

	private static EventRecord event(UUID id, UUID venueId, String name, EventStatus status) {
		return new EventRecord(
				id,
				venueId,
				name,
				"Season opener",
				EVENT_START,
				SALES_START,
				SALES_END,
				status,
				NOW,
				NOW);
	}

	private static EventSectionRecord eventSection(
			UUID id,
			UUID eventId,
			UUID sectionId,
			BigDecimal price,
			boolean salesEnabled) {
		return new EventSectionRecord(id, eventId, sectionId, price, salesEnabled, NOW, NOW);
	}

	private static VenueRecord venue(UUID id, VenueStatus status) {
		return new VenueRecord(
				id,
				"Main Hall",
				"1 Event Street",
				"Ho Chi Minh City",
				"Vietnam",
				"Asia/Ho_Chi_Minh",
				status,
				NOW,
				NOW);
	}

	private static String eventJson(
			UUID venueId,
			String name,
			Instant startTime,
			Instant salesStartTime,
			Instant salesEndTime) {
		return """
				{
				  "venueId": "%s",
				  "name": "%s",
				  "description": "Season opener",
				  "startTime": "%s",
				  "salesStartTime": "%s",
				  "salesEndTime": "%s"
				}
				""".formatted(venueId, name, startTime, salesStartTime, salesEndTime);
	}

	private static String eventSectionsJson(UUID sectionId, String price, boolean salesEnabled) {
		return """
				{
				  "sections": [
				    {
				      "venueSectionId": "%s",
				      "price": %s,
				      "salesEnabled": %s
				    }
				  ]
				}
				""".formatted(sectionId, price, salesEnabled);
	}
}
