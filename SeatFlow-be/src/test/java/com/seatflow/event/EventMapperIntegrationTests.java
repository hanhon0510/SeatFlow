package com.seatflow.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.seatflow.seating.SeatMapper;
import com.seatflow.seating.SeatRecord;
import com.seatflow.seating.VenueSectionMapper;
import com.seatflow.seating.VenueSectionRecord;
import com.seatflow.support.PostgresTestContainerSupport;
import com.seatflow.venue.VenueMapper;
import com.seatflow.venue.VenueRecord;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class EventMapperIntegrationTests extends PostgresTestContainerSupport {

	private static final Instant EVENT_START = Instant.parse("2026-09-01T19:00:00Z");
	private static final Instant SALES_START = Instant.parse("2026-08-01T00:00:00Z");
	private static final Instant SALES_END = Instant.parse("2026-09-01T18:00:00Z");
	private static final Instant NOW = Instant.parse("2026-08-15T00:00:00Z");

	@Autowired
	private VenueMapper venueMapper;

	@Autowired
	private EventMapper eventMapper;

	@Autowired
	private VenueSectionMapper venueSectionMapper;

	@Autowired
	private SeatMapper seatMapper;

	@Autowired
	private EventSectionMapper eventSectionMapper;

	@Autowired
	private EventSeatMapper eventSeatMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void deleteEvents() {
		jdbcTemplate.update("DELETE FROM event_seats");
		jdbcTemplate.update("DELETE FROM event_sections");
		jdbcTemplate.update("DELETE FROM events");
		jdbcTemplate.update("DELETE FROM seats");
		jdbcTemplate.update("DELETE FROM venue_sections");
		jdbcTemplate.update("DELETE FROM venues");
	}

	@Test
	void insertsAndFindsEventWithDraftDefault() {
		VenueRecord venue = insertVenue("Main Hall");
		EventRecord event = event(venue.id(), "Opening Night", EVENT_START);

		eventMapper.insert(event);

		EventRecord found = eventMapper.findById(event.id());
		assertThat(found).isNotNull();
		assertThat(found.id()).isEqualTo(event.id());
		assertThat(found.venueId()).isEqualTo(venue.id());
		assertThat(found.name()).isEqualTo("Opening Night");
		assertThat(found.description()).isEqualTo("Season opener");
		assertThat(found.startTime()).isEqualTo(EVENT_START);
		assertThat(found.salesStartTime()).isEqualTo(SALES_START);
		assertThat(found.salesEndTime()).isEqualTo(SALES_END);
		assertThat(found.status()).isEqualTo(EventStatus.DRAFT);
		assertThat(found.createdAt()).isNotNull();
		assertThat(found.updatedAt()).isNotNull();
	}

	@Test
	void updatesDraftEventAndReturnsAffectedRows() {
		VenueRecord firstVenue = insertVenue("First Hall");
		VenueRecord secondVenue = insertVenue("Second Hall");
		EventRecord event = event(firstVenue.id(), "Old Name", EVENT_START);
		eventMapper.insert(event);
		EventRecord update = EventRecord.forUpdate(
				event.id(),
				secondVenue.id(),
				"Updated Name",
				"Updated description",
				EVENT_START.plusSeconds(3600),
				SALES_START,
				SALES_END);

		int updatedRows = eventMapper.update(update);

		EventRecord found = eventMapper.findById(event.id());
		assertThat(updatedRows).isEqualTo(1);
		assertThat(found.venueId()).isEqualTo(secondVenue.id());
		assertThat(found.name()).isEqualTo("Updated Name");
		assertThat(found.description()).isEqualTo("Updated description");
		assertThat(found.startTime()).isEqualTo(EVENT_START.plusSeconds(3600));
		assertThat(found.status()).isEqualTo(EventStatus.DRAFT);
	}

	@Test
	void updateMissingEventReturnsZeroRows() {
		VenueRecord venue = insertVenue("Main Hall");
		EventRecord update = EventRecord.forUpdate(
				UUID.randomUUID(),
				venue.id(),
				"Missing",
				null,
				EVENT_START,
				SALES_START,
				SALES_END);

		assertThat(eventMapper.update(update)).isZero();
	}

	@Test
	void listsEventsWithPaginationAndExplicitOrdering() {
		VenueRecord venue = insertVenue("Main Hall");
		EventRecord later = event(venue.id(), "Later Event", EVENT_START.plusSeconds(7200));
		EventRecord alpha = event(venue.id(), "Alpha Event", EVENT_START);
		EventRecord beta = event(venue.id(), "Beta Event", EVENT_START);
		eventMapper.insert(later);
		eventMapper.insert(beta);
		eventMapper.insert(alpha);

		List<EventRecord> firstPage = eventMapper.findPage(2, 0);
		List<EventRecord> secondPage = eventMapper.findPage(2, 2);

		assertThat(eventMapper.count()).isEqualTo(3);
		assertThat(firstPage).extracting(EventRecord::name).containsExactly("Alpha Event", "Beta Event");
		assertThat(secondPage).extracting(EventRecord::name).containsExactly("Later Event");
	}

	@Test
	void conditionalUpdateRejectsPublishedVenueChange() {
		VenueRecord firstVenue = insertVenue("First Hall");
		VenueRecord secondVenue = insertVenue("Second Hall");
		EventRecord event = event(firstVenue.id(), "Published Event", EVENT_START);
		eventMapper.insert(event);
		jdbcTemplate.update("UPDATE events SET status = 'PUBLISHED' WHERE id = ?", event.id());
		EventRecord update = EventRecord.forUpdate(
				event.id(),
				secondVenue.id(),
				"Published Event Updated",
				"New description",
				EVENT_START.plusSeconds(3600),
				SALES_START,
				SALES_END);

		int updatedRows = eventMapper.update(update);

		EventRecord found = eventMapper.findById(event.id());
		assertThat(updatedRows).isZero();
		assertThat(found.venueId()).isEqualTo(firstVenue.id());
		assertThat(found.name()).isEqualTo("Published Event");
		assertThat(found.status()).isEqualTo(EventStatus.PUBLISHED);
	}

	@Test
	void conditionalUpdateAllowsPublishedEventWhenVenueDoesNotChange() {
		VenueRecord venue = insertVenue("Main Hall");
		EventRecord event = event(venue.id(), "Published Event", EVENT_START);
		eventMapper.insert(event);
		jdbcTemplate.update("UPDATE events SET status = 'PUBLISHED' WHERE id = ?", event.id());
		EventRecord update = EventRecord.forUpdate(
				event.id(),
				venue.id(),
				"Published Event Updated",
				"New description",
				EVENT_START.plusSeconds(3600),
				SALES_START,
				SALES_END);

		int updatedRows = eventMapper.update(update);

		EventRecord found = eventMapper.findById(event.id());
		assertThat(updatedRows).isEqualTo(1);
		assertThat(found.name()).isEqualTo("Published Event Updated");
		assertThat(found.venueId()).isEqualTo(venue.id());
		assertThat(found.status()).isEqualTo(EventStatus.PUBLISHED);
	}

	@Test
	void publishDraftUsesConditionalUpdate() {
		VenueRecord venue = insertVenue("Main Hall");
		EventRecord event = event(venue.id(), "Opening Night", EVENT_START);
		eventMapper.insert(event);

		int publishedRows = eventMapper.publishDraft(event.id());
		int publishedAgainRows = eventMapper.publishDraft(event.id());

		assertThat(publishedRows).isEqualTo(1);
		assertThat(publishedAgainRows).isZero();
		assertThat(eventMapper.findById(event.id()).status()).isEqualTo(EventStatus.PUBLISHED);
	}

	@Test
	void publicCatalogFiltersCountsAndExcludesDraftEvents() {
		VenueRecord mainVenue = insertVenue("Main Hall");
		VenueSectionRecord mainSection = insertSection(mainVenue, "Orchestra");
		insertSeat(mainSection, "A", 1);
		EventRecord opening = insertPublishedEvent(
				mainVenue,
				mainSection,
				"Opening Gala",
				EVENT_START,
				new BigDecimal("50000.00"));
		EventRecord draft = event(mainVenue.id(), "Opening Draft", EVENT_START.plusSeconds(3600));
		eventMapper.insert(draft);

		VenueRecord secondVenue = insertVenue("Second Hall");
		VenueSectionRecord secondSection = insertSection(secondVenue, "Balcony");
		insertSeat(secondSection, "B", 1);
		insertPublishedEvent(
				secondVenue,
				secondSection,
				"Jazz Evening",
				EVENT_START.plusSeconds(7200),
				new BigDecimal("30000.00"));

		PublicEventCatalogQuery query = new PublicEventCatalogQuery(
				"opening",
				mainVenue.id(),
				EVENT_START.minusSeconds(60),
				EVENT_START.plusSeconds(60),
				"START_ASC",
				NOW,
				null,
				10,
				0);

		List<PublicEventCatalogRecord> events = eventMapper.findPublishedCatalogPage(query);
		PublicEventCatalogRecord detail = eventMapper.findPublishedCatalogById(opening.id(), NOW);

		assertThat(eventMapper.countPublishedCatalog(query)).isEqualTo(1);
		assertThat(events).extracting(PublicEventCatalogRecord::id).containsExactly(opening.id());
		assertThat(events.getFirst().venueName()).isEqualTo("Main Hall");
		assertThat(events.getFirst().venueTimezone()).isEqualTo("Asia/Ho_Chi_Minh");
		assertThat(events.getFirst().minimumPrice()).isEqualByComparingTo("50000.00");
		assertThat(detail).isNotNull();
		assertThat(detail.id()).isEqualTo(opening.id());
		assertThat(detail.minimumPrice()).isEqualByComparingTo("50000.00");
		assertThat(eventMapper.findPublishedCatalogById(draft.id(), NOW)).isNull();
	}

	@Test
	void publicCatalogPaginatesWithStableOrdering() {
		VenueRecord venue = insertVenue("Main Hall");
		VenueSectionRecord section = insertSection(venue, "Orchestra");
		insertSeat(section, "A", 1);
		insertPublishedEvent(venue, section, "Charlie Event", EVENT_START, new BigDecimal("70000.00"));
		insertPublishedEvent(venue, section, "Alpha Event", EVENT_START, new BigDecimal("90000.00"));
		insertPublishedEvent(venue, section, "Bravo Event", EVENT_START, new BigDecimal("80000.00"));
		PublicEventCatalogQuery firstPageQuery = new PublicEventCatalogQuery(
				null,
				null,
				null,
				null,
				"START_ASC",
				NOW,
				null,
				2,
				0);
		PublicEventCatalogQuery secondPageQuery = new PublicEventCatalogQuery(
				null,
				null,
				null,
				null,
				"START_ASC",
				NOW,
				null,
				2,
				2);

		List<PublicEventCatalogRecord> firstPage = eventMapper.findPublishedCatalogPage(firstPageQuery);
		List<PublicEventCatalogRecord> secondPage = eventMapper.findPublishedCatalogPage(secondPageQuery);

		assertThat(eventMapper.countPublishedCatalog(firstPageQuery)).isEqualTo(3);
		assertThat(firstPage).extracting(PublicEventCatalogRecord::name)
				.containsExactly("Alpha Event", "Bravo Event");
		assertThat(secondPage).extracting(PublicEventCatalogRecord::name)
				.containsExactly("Charlie Event");
	}

	@Test
	void publicCatalogDerivesSalesStatusAndPutsSellingEventsFirst() {
		VenueRecord venue = insertVenue("Main Hall");
		VenueSectionRecord section = insertSection(venue, "Orchestra");
		insertSeat(section, "A", 1);
		BigDecimal price = new BigDecimal("50000.00");
		insertPublishedEvent(
				venue,
				section,
				"On Sale",
				NOW.plus(Duration.ofDays(30)),
				NOW.minus(Duration.ofDays(5)),
				NOW.plus(Duration.ofDays(29)),
				price);
		EventRecord soldOut = insertPublishedEvent(
				venue,
				section,
				"Sold Out",
				NOW.plus(Duration.ofDays(20)),
				NOW.minus(Duration.ofDays(5)),
				NOW.plus(Duration.ofDays(19)),
				price);
		sellEverySeat(soldOut);
		insertPublishedEvent(
				venue,
				section,
				"Upcoming",
				NOW.plus(Duration.ofDays(60)),
				NOW.plus(Duration.ofDays(10)),
				NOW.plus(Duration.ofDays(59)),
				price);
		insertPublishedEvent(
				venue,
				section,
				"Sales Closed",
				NOW.plus(Duration.ofDays(10)),
				NOW.minus(Duration.ofDays(20)),
				NOW.minus(Duration.ofDays(1)),
				price);
		EventRecord ended = insertPublishedEvent(
				venue,
				section,
				"Ended",
				NOW.minus(Duration.ofDays(1)),
				NOW.minus(Duration.ofDays(20)),
				NOW.minus(Duration.ofDays(2)),
				price);

		List<PublicEventCatalogRecord> ranked = eventMapper.findPublishedCatalogPage(catalogQuery(null, 10));

		assertThat(ranked).extracting(PublicEventCatalogRecord::name)
				.containsExactly("On Sale", "Upcoming", "Sold Out", "Sales Closed", "Ended");
		assertThat(ranked.stream().collect(Collectors.toMap(
				PublicEventCatalogRecord::name,
				PublicEventCatalogRecord::salesStatus)))
				.containsExactlyInAnyOrderEntriesOf(Map.of(
						"On Sale", EventSalesStatus.ON_SALE,
						"Upcoming", EventSalesStatus.UPCOMING,
						"Sold Out", EventSalesStatus.SOLD_OUT,
						"Sales Closed", EventSalesStatus.SALES_CLOSED,
						"Ended", EventSalesStatus.ENDED));
	}

	@Test
	void publicCatalogNarrowsToTheRequestedSalesStatuses() {
		VenueRecord venue = insertVenue("Main Hall");
		VenueSectionRecord section = insertSection(venue, "Orchestra");
		insertSeat(section, "A", 1);
		BigDecimal price = new BigDecimal("50000.00");
		insertPublishedEvent(
				venue,
				section,
				"On Sale",
				NOW.plus(Duration.ofDays(30)),
				NOW.minus(Duration.ofDays(5)),
				NOW.plus(Duration.ofDays(29)),
				price);
		EventRecord ended = insertPublishedEvent(
				venue,
				section,
				"Ended",
				NOW.minus(Duration.ofDays(1)),
				NOW.minus(Duration.ofDays(20)),
				NOW.minus(Duration.ofDays(2)),
				price);

		PublicEventCatalogQuery currentOnly = catalogQuery(
				Set.of(
						EventSalesStatus.ON_SALE,
						EventSalesStatus.UPCOMING,
						EventSalesStatus.SOLD_OUT,
						EventSalesStatus.SALES_CLOSED),
				10);
		PublicEventCatalogQuery endedOnly = catalogQuery(Set.of(EventSalesStatus.ENDED), 10);

		assertThat(eventMapper.findPublishedCatalogPage(currentOnly))
				.extracting(PublicEventCatalogRecord::name)
				.containsExactly("On Sale");
		assertThat(eventMapper.countPublishedCatalog(currentOnly)).isEqualTo(1);
		assertThat(eventMapper.findPublishedCatalogPage(endedOnly))
				.extracting(PublicEventCatalogRecord::name)
				.containsExactly("Ended");
		assertThat(eventMapper.countPublishedCatalog(endedOnly)).isEqualTo(1);
		// A past event stays readable by id so a ticket holder can still open it.
		assertThat(eventMapper.findPublishedCatalogById(ended.id(), NOW).salesStatus())
				.isEqualTo(EventSalesStatus.ENDED);
	}

	private static PublicEventCatalogQuery catalogQuery(Set<EventSalesStatus> statuses, int limit) {
		return new PublicEventCatalogQuery(null, null, null, null, "RECOMMENDED", NOW, statuses, limit, 0);
	}

	private void sellEverySeat(EventRecord event) {
		jdbcTemplate.update("UPDATE event_seats SET permanent_status = 'SOLD' WHERE event_id = ?", event.id());
	}

	private VenueRecord insertVenue(String name) {
		VenueRecord venue = VenueRecord.forInsert(
				UUID.randomUUID(),
				name,
				"1 Event Street",
				"Ho Chi Minh City",
				"Vietnam",
				"Asia/Ho_Chi_Minh");
		venueMapper.insert(venue);
		return venueMapper.findById(venue.id());
	}

	private VenueSectionRecord insertSection(VenueRecord venue, String name) {
		VenueSectionRecord section = VenueSectionRecord.forInsert(UUID.randomUUID(), venue.id(), name, 1);
		venueSectionMapper.insert(section);
		return venueSectionMapper.findById(section.id());
	}

	private SeatRecord insertSeat(VenueSectionRecord section, String rowLabel, int seatNumber) {
		SeatRecord seat = SeatRecord.forInsert(
				UUID.randomUUID(),
				section.id(),
				rowLabel,
				seatNumber,
				rowLabel + seatNumber,
				false);
		seatMapper.insert(seat);
		return seatMapper.findById(seat.id());
	}

	private EventRecord insertPublishedEvent(
			VenueRecord venue,
			VenueSectionRecord section,
			String name,
			Instant startTime,
			BigDecimal price) {
		return insertPublishedEvent(venue, section, name, startTime, SALES_START, SALES_END, price);
	}

	private EventRecord insertPublishedEvent(
			VenueRecord venue,
			VenueSectionRecord section,
			String name,
			Instant startTime,
			Instant salesStartTime,
			Instant salesEndTime,
			BigDecimal price) {
		EventRecord event = EventRecord.forInsert(
				UUID.randomUUID(),
				venue.id(),
				name,
				"Season opener",
				startTime,
				salesStartTime,
				salesEndTime);
		eventMapper.insert(event);
		eventSectionMapper.insertForDraftEvent(EventSectionRecord.forInsert(
				UUID.randomUUID(),
				event.id(),
				section.id(),
				price,
				true));
		assertThat(eventSeatMapper.insertForDraftEvent(event.id())).isEqualTo(1);
		assertThat(eventMapper.publishDraft(event.id())).isEqualTo(1);
		return eventMapper.findById(event.id());
	}

	private static EventRecord event(UUID venueId, String name, Instant startTime) {
		return EventRecord.forInsert(
				UUID.randomUUID(),
				venueId,
				name,
				"Season opener",
				startTime,
				SALES_START,
				SALES_END);
	}
}
