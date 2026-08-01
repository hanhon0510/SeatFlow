package com.seatflow.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seatflow.venue.VenueService;

@Service
public class EventService {

	public static final int DEFAULT_PAGE_SIZE = 20;
	public static final int MAX_PAGE_SIZE = 100;

	private final EventMapper eventMapper;
	private final VenueService venueService;

	public EventService(EventMapper eventMapper, VenueService venueService) {
		this.eventMapper = eventMapper;
		this.venueService = venueService;
	}

	@Transactional
	@PreAuthorize("hasRole('ADMIN')")
	public EventResponse createEvent(EventCreateRequest request) {
		validateTiming(request.startTime(), request.salesStartTime(), request.salesEndTime());
		venueService.requireVenueCanHostNewEvent(request.venueId());

		EventRecord event = EventRecord.forInsert(
				UUID.randomUUID(),
				request.venueId(),
				clean(request.name()),
				cleanNullable(request.description()),
				request.startTime(),
				request.salesStartTime(),
				request.salesEndTime());
		eventMapper.insert(event);
		return EventResponse.from(findExisting(event.id()));
	}

	@PreAuthorize("hasRole('ADMIN')")
	public EventPageResponse listEvents(int page, int size) {
		validatePagination(page, size);
		long totalItems = eventMapper.count();
		List<EventRecord> events = eventMapper.findPage(size, (long) page * size);
		return EventPageResponse.from(events, page, size, totalItems);
	}

	@PreAuthorize("hasRole('ADMIN')")
	public EventResponse getEvent(UUID id) {
		return EventResponse.from(findExisting(id));
	}

	@Transactional
	@PreAuthorize("hasRole('ADMIN')")
	public EventResponse updateEvent(UUID id, EventUpdateRequest request) {
		validateTiming(request.startTime(), request.salesStartTime(), request.salesEndTime());
		venueService.requireVenueCanHostNewEvent(request.venueId());

		EventRecord event = EventRecord.forUpdate(
				id,
				request.venueId(),
				clean(request.name()),
				cleanNullable(request.description()),
				request.startTime(),
				request.salesStartTime(),
				request.salesEndTime());
		int updatedRows = eventMapper.update(event);
		if (updatedRows == 1) {
			return EventResponse.from(findExisting(id));
		}

		if (eventMapper.findById(id) == null) {
			throw new EventNotFoundException();
		}
		throw new EventStateConflictException();
	}

	private EventRecord findExisting(UUID id) {
		EventRecord event = eventMapper.findById(id);
		if (event == null) {
			throw new EventNotFoundException();
		}
		return event;
	}

	private static void validateTiming(Instant startTime, Instant salesStartTime, Instant salesEndTime) {
		if (!salesStartTime.isBefore(startTime)) {
			throw new InvalidEventTimingException("Sales start must precede event start");
		}
		if (salesEndTime.isAfter(startTime)) {
			throw new InvalidEventTimingException("Sales end cannot follow event start");
		}
	}

	private static void validatePagination(int page, int size) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new InvalidEventPaginationException();
		}
	}

	private static String clean(String value) {
		return value == null ? "" : value.trim();
	}

	private static String cleanNullable(String value) {
		String cleaned = value == null ? null : value.trim();
		return cleaned == null || cleaned.isBlank() ? null : cleaned;
	}
}
