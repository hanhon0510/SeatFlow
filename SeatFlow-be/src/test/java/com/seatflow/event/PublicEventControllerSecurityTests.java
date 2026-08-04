package com.seatflow.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.seatflow.security.JwtConfig;
import com.seatflow.security.JwtTokenService;
import com.seatflow.security.SecurityConfig;
import com.seatflow.support.JwtTestSupport;

@WebMvcTest(PublicEventController.class)
@Import({
		SecurityConfig.class,
		JwtConfig.class,
		JwtTokenService.class,
		PublicEventCatalogService.class
})
@TestPropertySource(properties = {
		"server.port=8080",
		"seatflow.jwt.secret=" + JwtTestSupport.DEFAULT_SECRET,
		"seatflow.jwt.issuer=" + JwtTestSupport.ISSUER,
		"seatflow.jwt.expires-in-seconds=900"
})
class PublicEventControllerSecurityTests {

	private static final Instant EVENT_START = Instant.parse("2026-09-01T19:00:00Z");
	private static final Instant SALES_START = Instant.parse("2026-08-01T00:00:00Z");
	private static final Instant SALES_END = Instant.parse("2026-09-01T18:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private EventMapper eventMapper;

	@Test
	void unauthenticatedUserCanBrowsePublishedEvents() throws Exception {
		UUID eventId = UUID.randomUUID();
		UUID venueId = UUID.randomUUID();
		when(eventMapper.countPublishedCatalog(any(PublicEventCatalogQuery.class))).thenReturn(1L);
		when(eventMapper.findPublishedCatalogPage(any(PublicEventCatalogQuery.class)))
				.thenReturn(List.of(publicEvent(eventId, venueId)));

		mockMvc.perform(get("/api/v1/events")
						.param("search", "opening")
						.param("venueId", venueId.toString())
						.param("startDate", "2026-09-01")
						.param("endDate", "2026-09-02")
						.param("page", "0")
						.param("size", "12")
						.param("sort", "priceAsc"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value(eventId.toString()))
				.andExpect(jsonPath("$.items[0].venueName").value("Main Hall"))
				.andExpect(jsonPath("$.items[0].venueTimezone").value("Asia/Ho_Chi_Minh"))
				.andExpect(jsonPath("$.items[0].minimumPrice").value(50000.00))
				.andExpect(jsonPath("$.totalItems").value(1));

		ArgumentCaptor<PublicEventCatalogQuery> query = ArgumentCaptor.forClass(PublicEventCatalogQuery.class);
		org.mockito.Mockito.verify(eventMapper).countPublishedCatalog(query.capture());
		assertThat(query.getValue().search()).isEqualTo("opening");
		assertThat(query.getValue().venueId()).isEqualTo(venueId);
		assertThat(query.getValue().sort()).isEqualTo("PRICE_ASC");
	}

	@Test
	void unauthenticatedUserCanReadPublishedEventDetail() throws Exception {
		UUID eventId = UUID.randomUUID();
		UUID venueId = UUID.randomUUID();
		when(eventMapper.findPublishedCatalogById(eventId)).thenReturn(publicEvent(eventId, venueId));

		mockMvc.perform(get("/api/v1/events/{eventId}", eventId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(eventId.toString()))
				.andExpect(jsonPath("$.venueName").value("Main Hall"))
				.andExpect(jsonPath("$.startTime").value("2026-09-01T19:00:00Z"))
				.andExpect(jsonPath("$.minimumPrice").value(50000.00));
	}

	@Test
	void invalidCatalogQueryReturnsBadRequest() throws Exception {
		mockMvc.perform(get("/api/v1/events").param("sort", "unsupported"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Invalid event catalog query"));
	}

	private static PublicEventCatalogRecord publicEvent(UUID eventId, UUID venueId) {
		return new PublicEventCatalogRecord(
				eventId,
				venueId,
				"Main Hall",
				"1 Event Street",
				"Ho Chi Minh City",
				"Vietnam",
				"Asia/Ho_Chi_Minh",
				"Opening Night",
				"Season opener",
				EVENT_START,
				SALES_START,
				SALES_END,
				new BigDecimal("50000.00"));
	}
}
