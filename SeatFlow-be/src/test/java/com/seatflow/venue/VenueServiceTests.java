package com.seatflow.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class VenueServiceTests {

	private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");

	@Test
	void createRejectsInvalidTimezone() {
		InMemoryVenueMapper venueMapper = new InMemoryVenueMapper();
		VenueService venueService = new VenueService(venueMapper);

		assertThatThrownBy(() -> venueService.createVenue(createRequest("Mars/Phobos")))
				.isInstanceOf(InvalidVenueTimezoneException.class)
				.hasMessage("Invalid venue timezone: Mars/Phobos");
		assertThat(venueMapper.venues).isEmpty();
	}

	@Test
	void listRejectsPageSizeAboveMaximum() {
		VenueService venueService = new VenueService(new InMemoryVenueMapper());

		assertThatThrownBy(() -> venueService.listVenues(0, VenueService.MAX_PAGE_SIZE + 1))
				.isInstanceOf(InvalidVenuePaginationException.class)
				.hasMessage("Invalid venue pagination");
	}

	@Test
	void updateValidatesAffectedRows() {
		VenueService venueService = new VenueService(new InMemoryVenueMapper());

		assertThatThrownBy(() -> venueService.updateVenue(UUID.randomUUID(), updateRequest("UTC")))
				.isInstanceOf(VenueNotFoundException.class)
				.hasMessage("Venue not found");
	}

	@Test
	void archivedVenueCannotHostNewEvents() {
		InMemoryVenueMapper venueMapper = new InMemoryVenueMapper();
		VenueRecord venue = venue("Archived Hall", VenueStatus.ARCHIVED);
		venueMapper.insert(venue);
		VenueService venueService = new VenueService(venueMapper);

		assertThatThrownBy(() -> venueService.requireVenueCanHostNewEvent(venue.id()))
				.isInstanceOf(ArchivedVenueCannotHostEventsException.class)
				.hasMessage("Archived venue cannot host new events");
	}

	@Test
	void activeVenueCanHostNewEvents() {
		InMemoryVenueMapper venueMapper = new InMemoryVenueMapper();
		VenueRecord venue = venue("Active Hall", VenueStatus.ACTIVE);
		venueMapper.insert(venue);
		VenueService venueService = new VenueService(venueMapper);

		assertThat(venueService.requireVenueCanHostNewEvent(venue.id()).id()).isEqualTo(venue.id());
	}

	private static VenueCreateRequest createRequest(String timezone) {
		return new VenueCreateRequest(
				"Main Hall",
				"1 Event Street",
				"Ho Chi Minh City",
				"Vietnam",
				timezone);
	}

	private static VenueUpdateRequest updateRequest(String timezone) {
		return new VenueUpdateRequest(
				"Updated Hall",
				"2 Event Street",
				"Ho Chi Minh City",
				"Vietnam",
				timezone);
	}

	private static VenueRecord venue(String name, VenueStatus status) {
		return new VenueRecord(
				UUID.randomUUID(),
				name,
				"1 Event Street",
				"Ho Chi Minh City",
				"Vietnam",
				"Asia/Ho_Chi_Minh",
				status,
				NOW,
				NOW);
	}

	private static final class InMemoryVenueMapper implements VenueMapper {

		private final List<VenueRecord> venues = new ArrayList<>();

		@Override
		public void insert(VenueRecord venue) {
			venues.add(withDefaults(venue));
		}

		@Override
		public int update(VenueRecord venue) {
			for (int index = 0; index < venues.size(); index++) {
				VenueRecord existing = venues.get(index);
				if (existing.id().equals(venue.id())) {
					venues.set(index, new VenueRecord(
							existing.id(),
							venue.name(),
							venue.address(),
							venue.city(),
							venue.country(),
							venue.timezone(),
							existing.status(),
							existing.createdAt(),
							NOW.plusSeconds(1)));
					return 1;
				}
			}
			return 0;
		}

		@Override
		public VenueRecord findById(UUID id) {
			return venues.stream()
					.filter(venue -> venue.id().equals(id))
					.findFirst()
					.orElse(null);
		}

		@Override
		public List<VenueRecord> findPage(int limit, long offset) {
			return venues.stream()
					.sorted(Comparator.comparing(VenueRecord::name).thenComparing(VenueRecord::id))
					.skip(offset)
					.limit(limit)
					.toList();
		}

		@Override
		public long count() {
			return venues.size();
		}

		@Override
		public int archive(UUID id) {
			for (int index = 0; index < venues.size(); index++) {
				VenueRecord existing = venues.get(index);
				if (existing.id().equals(id) && existing.status() != VenueStatus.ARCHIVED) {
					venues.set(index, new VenueRecord(
							existing.id(),
							existing.name(),
							existing.address(),
							existing.city(),
							existing.country(),
							existing.timezone(),
							VenueStatus.ARCHIVED,
							existing.createdAt(),
							NOW.plusSeconds(1)));
					return 1;
				}
			}
			return 0;
		}

		private static VenueRecord withDefaults(VenueRecord venue) {
			return new VenueRecord(
					venue.id(),
					venue.name(),
					venue.address(),
					venue.city(),
					venue.country(),
					venue.timezone(),
					venue.status() == null ? VenueStatus.ACTIVE : venue.status(),
					venue.createdAt() == null ? NOW : venue.createdAt(),
					venue.updatedAt() == null ? NOW : venue.updatedAt());
		}
	}
}
