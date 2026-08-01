package com.seatflow.seating;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.seatflow.support.PostgresTestContainerSupport;
import com.seatflow.venue.VenueMapper;
import com.seatflow.venue.VenueRecord;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class SeatingMapperIntegrationTests extends PostgresTestContainerSupport {

	@Autowired
	private VenueMapper venueMapper;

	@Autowired
	private VenueSectionMapper sectionMapper;

	@Autowired
	private SeatMapper seatMapper;

	@Autowired
	private SeatingService seatingService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void deleteSeatingData() {
		jdbcTemplate.update("DELETE FROM events");
		jdbcTemplate.update("DELETE FROM seats");
		jdbcTemplate.update("DELETE FROM venue_sections");
		jdbcTemplate.update("DELETE FROM venues");
	}

	@Test
	void sectionMapperCreatesUpdatesListsAndDeletesSections() {
		VenueRecord venue = insertVenue();
		VenueSectionRecord section = insertSection(venue.id(), "Balcony", 2);

		VenueSectionRecord update = VenueSectionRecord.forUpdate(section.id(), "Updated Balcony", 1);
		int updatedRows = sectionMapper.update(update);

		VenueSectionRecord found = sectionMapper.findById(section.id());
		assertThat(updatedRows).isEqualTo(1);
		assertThat(found.name()).isEqualTo("Updated Balcony");
		assertThat(found.displayOrder()).isEqualTo(1);
		assertThat(sectionMapper.findByVenueId(venue.id()))
				.extracting(VenueSectionRecord::name)
				.containsExactly("Updated Balcony");

		assertThat(sectionMapper.delete(section.id())).isEqualTo(1);
		assertThat(sectionMapper.findById(section.id())).isNull();
	}

	@Test
	void batchInsertsSeatsWithStableOrdering() {
		VenueSectionRecord section = insertSection(insertVenue().id(), "Orchestra", 1);

		seatMapper.insertBatch(List.of(
				seat(section.id(), "A", 2, "A2", false),
				seat(section.id(), "A", 1, "A1", true)));

		assertThat(seatMapper.countBySectionId(section.id())).isEqualTo(2);
		assertThat(seatMapper.findBySectionId(section.id()))
				.extracting(SeatRecord::seatLabel)
				.containsExactly("A1", "A2");
	}

	@Test
	void duplicateSeatLabelFailsWithinSameSection() {
		VenueSectionRecord section = insertSection(insertVenue().id(), "Orchestra", 1);
		seatMapper.insert(seat(section.id(), "A", 1, "A1", false));

		assertThatThrownBy(() -> seatMapper.insert(seat(section.id(), "A", 99, "A1", true)))
				.isInstanceOf(RuntimeException.class);
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void failedBulkSeatCreateRollsBack() {
		VenueSectionRecord section = insertSection(insertVenue().id(), "Orchestra", 1);

		assertThatThrownBy(() -> seatingService.createSeatsBulk(
				section.id(),
				new BulkSeatCreateRequest(List.of(
						new SeatCreateRequest("A", 1, "A1", false),
						new SeatCreateRequest("A", 2, "A1", true)))))
				.isInstanceOf(DuplicateSeatLabelException.class);
		assertThat(seatMapper.countBySectionId(section.id())).isZero();
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void seatLayoutQueryIsGroupedWithStableSectionAndSeatOrdering() {
		VenueRecord venue = insertVenue();
		VenueSectionRecord balcony = insertSection(venue.id(), "Balcony", 2);
		VenueSectionRecord orchestra = insertSection(venue.id(), "Orchestra", 1);
		seatMapper.insertBatch(List.of(
				seat(orchestra.id(), "A", 2, "A2", false),
				seat(orchestra.id(), "A", 1, "A1", true),
				seat(balcony.id(), "B", 1, "B1", false)));

		SeatLayoutResponse layout = seatingService.getSeatLayout(venue.id());

		assertThat(layout.sections()).hasSize(2);
		assertThat(layout.sections()).extracting(SeatLayoutSectionResponse::name)
				.containsExactly("Orchestra", "Balcony");
		assertThat(layout.sections().getFirst().seats())
				.extracting(SeatResponse::seatLabel)
				.containsExactly("A1", "A2");
		assertThat(layout.sections().get(1).seats())
				.extracting(SeatResponse::seatLabel)
				.containsExactly("B1");
	}

	private VenueRecord insertVenue() {
		VenueRecord venue = VenueRecord.forInsert(
				UUID.randomUUID(),
				"Main Hall %s".formatted(UUID.randomUUID()),
				"1 Event Street",
				"Ho Chi Minh City",
				"Vietnam",
				"Asia/Ho_Chi_Minh");
		venueMapper.insert(venue);
		return venueMapper.findById(venue.id());
	}

	private VenueSectionRecord insertSection(UUID venueId, String name, int displayOrder) {
		VenueSectionRecord section = VenueSectionRecord.forInsert(UUID.randomUUID(), venueId, name, displayOrder);
		sectionMapper.insert(section);
		return sectionMapper.findById(section.id());
	}

	private static SeatRecord seat(
			UUID sectionId,
			String rowLabel,
			int seatNumber,
			String seatLabel,
			boolean accessible) {
		return SeatRecord.forInsert(UUID.randomUUID(), sectionId, rowLabel, seatNumber, seatLabel, accessible);
	}
}
