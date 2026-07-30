package com.seatflow.venue;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VenueService {

	public static final int DEFAULT_PAGE_SIZE = 20;
	public static final int MAX_PAGE_SIZE = 100;

	private final VenueMapper venueMapper;

	public VenueService(VenueMapper venueMapper) {
		this.venueMapper = venueMapper;
	}

	@Transactional
	@PreAuthorize("hasRole('ADMIN')")
	public VenueResponse createVenue(VenueCreateRequest request) {
		String timezone = validatedTimezone(request.timezone());
		VenueRecord venue = VenueRecord.forInsert(
				UUID.randomUUID(),
				clean(request.name()),
				clean(request.address()),
				clean(request.city()),
				clean(request.country()),
				timezone);

		venueMapper.insert(venue);
		return VenueResponse.from(findExisting(venue.id()));
	}

	@PreAuthorize("hasRole('ADMIN')")
	public VenuePageResponse listVenues(int page, int size) {
		validatePagination(page, size);
		long totalItems = venueMapper.count();
		List<VenueRecord> venues = venueMapper.findPage(size, (long) page * size);
		return VenuePageResponse.from(venues, page, size, totalItems);
	}

	@PreAuthorize("hasRole('ADMIN')")
	public VenueResponse getVenue(UUID id) {
		return VenueResponse.from(findExisting(id));
	}

	@Transactional
	@PreAuthorize("hasRole('ADMIN')")
	public VenueResponse updateVenue(UUID id, VenueUpdateRequest request) {
		String timezone = validatedTimezone(request.timezone());
		VenueRecord venue = VenueRecord.forUpdate(
				id,
				clean(request.name()),
				clean(request.address()),
				clean(request.city()),
				clean(request.country()),
				timezone);
		int updatedRows = venueMapper.update(venue);
		if (updatedRows != 1) {
			throw new VenueNotFoundException();
		}

		return VenueResponse.from(findExisting(id));
	}

	@Transactional
	@PreAuthorize("hasRole('ADMIN')")
	public VenueResponse archiveVenue(UUID id) {
		int updatedRows = venueMapper.archive(id);
		if (updatedRows == 1) {
			return VenueResponse.from(findExisting(id));
		}

		VenueRecord existing = venueMapper.findById(id);
		if (existing == null) {
			throw new VenueNotFoundException();
		}
		throw new VenueAlreadyArchivedException();
	}

	public VenueRecord requireVenueCanHostNewEvent(UUID id) {
		VenueRecord venue = findExisting(id);
		if (!venue.status().canHostNewEvents()) {
			throw new ArchivedVenueCannotHostEventsException();
		}
		return venue;
	}

	private VenueRecord findExisting(UUID id) {
		VenueRecord venue = venueMapper.findById(id);
		if (venue == null) {
			throw new VenueNotFoundException();
		}
		return venue;
	}

	private static void validatePagination(int page, int size) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new InvalidVenuePaginationException();
		}
	}

	private static String validatedTimezone(String timezone) {
		String cleaned = clean(timezone);
		try {
			ZoneId.of(cleaned);
			return cleaned;
		}
		catch (DateTimeException ex) {
			throw new InvalidVenueTimezoneException(cleaned);
		}
	}

	private static String clean(String value) {
		return value == null ? "" : value.trim();
	}
}
