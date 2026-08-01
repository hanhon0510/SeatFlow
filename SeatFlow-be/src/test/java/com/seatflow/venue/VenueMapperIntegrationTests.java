package com.seatflow.venue;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.seatflow.support.PostgresTestContainerSupport;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class VenueMapperIntegrationTests extends PostgresTestContainerSupport {

	@Autowired
	private VenueMapper venueMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void deleteVenues() {
		jdbcTemplate.update("DELETE FROM events");
		jdbcTemplate.update("DELETE FROM seats");
		jdbcTemplate.update("DELETE FROM venue_sections");
		jdbcTemplate.update("DELETE FROM venues");
	}

	@Test
	void insertsAndFindsVenueWithDefaults() {
		VenueRecord venue = newVenue("Main Hall");

		venueMapper.insert(venue);

		VenueRecord found = venueMapper.findById(venue.id());
		assertThat(found).isNotNull();
		assertThat(found.id()).isEqualTo(venue.id());
		assertThat(found.name()).isEqualTo(venue.name());
		assertThat(found.address()).isEqualTo(venue.address());
		assertThat(found.city()).isEqualTo(venue.city());
		assertThat(found.country()).isEqualTo(venue.country());
		assertThat(found.timezone()).isEqualTo(venue.timezone());
		assertThat(found.status()).isEqualTo(VenueStatus.ACTIVE);
		assertThat(found.createdAt()).isNotNull();
		assertThat(found.updatedAt()).isNotNull();
	}

	@Test
	void updatesVenueAndReturnsAffectedRows() {
		VenueRecord venue = newVenue("Old Hall");
		venueMapper.insert(venue);
		VenueRecord update = VenueRecord.forUpdate(
				venue.id(),
				"Updated Hall",
				"2 Event Street",
				"Da Nang",
				"Vietnam",
				"UTC");

		int updatedRows = venueMapper.update(update);

		VenueRecord found = venueMapper.findById(venue.id());
		assertThat(updatedRows).isEqualTo(1);
		assertThat(found.name()).isEqualTo("Updated Hall");
		assertThat(found.address()).isEqualTo("2 Event Street");
		assertThat(found.city()).isEqualTo("Da Nang");
		assertThat(found.timezone()).isEqualTo("UTC");
		assertThat(found.status()).isEqualTo(VenueStatus.ACTIVE);
	}

	@Test
	void updateMissingVenueReturnsZeroRows() {
		VenueRecord update = VenueRecord.forUpdate(
				UUID.randomUUID(),
				"Missing Hall",
				"1 Event Street",
				"Ho Chi Minh City",
				"Vietnam",
				"UTC");

		assertThat(venueMapper.update(update)).isZero();
	}

	@Test
	void listsVenuesWithPaginationAndExplicitOrdering() {
		VenueRecord gamma = newVenue("Gamma Hall");
		VenueRecord alpha = newVenue("Alpha Hall");
		VenueRecord beta = newVenue("Beta Hall");
		venueMapper.insert(gamma);
		venueMapper.insert(alpha);
		venueMapper.insert(beta);

		List<VenueRecord> firstPage = venueMapper.findPage(2, 0);
		List<VenueRecord> secondPage = venueMapper.findPage(2, 2);

		assertThat(venueMapper.count()).isEqualTo(3);
		assertThat(firstPage).extracting(VenueRecord::name).containsExactly("Alpha Hall", "Beta Hall");
		assertThat(secondPage).extracting(VenueRecord::name).containsExactly("Gamma Hall");
	}

	@Test
	void archivesUsingConditionalUpdate() {
		VenueRecord venue = newVenue("Archive Hall");
		venueMapper.insert(venue);

		int archivedRows = venueMapper.archive(venue.id());
		int archivedAgainRows = venueMapper.archive(venue.id());

		assertThat(archivedRows).isEqualTo(1);
		assertThat(archivedAgainRows).isZero();
		assertThat(venueMapper.findById(venue.id()).status()).isEqualTo(VenueStatus.ARCHIVED);
	}

	private static VenueRecord newVenue(String name) {
		return VenueRecord.forInsert(
				UUID.randomUUID(),
				name,
				"1 Event Street",
				"Ho Chi Minh City",
				"Vietnam",
				"Asia/Ho_Chi_Minh");
	}
}
