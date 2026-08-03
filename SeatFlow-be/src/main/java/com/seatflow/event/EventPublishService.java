package com.seatflow.event;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventPublishService {

	private final EventMapper eventMapper;
	private final EventSeatMapper eventSeatMapper;

	public EventPublishService(EventMapper eventMapper, EventSeatMapper eventSeatMapper) {
		this.eventMapper = eventMapper;
		this.eventSeatMapper = eventSeatMapper;
	}

	@Transactional
	@PreAuthorize("hasRole('ADMIN')")
	public EventPublishResponse publishEvent(UUID eventId) {
		EventRecord event = eventMapper.findByIdForUpdate(eventId);
		if (event == null) {
			throw new EventNotFoundException();
		}
		if (event.status() == EventStatus.PUBLISHED) {
			return publishedResponse(eventId);
		}
		if (event.status() != EventStatus.DRAFT) {
			throw new EventStateConflictException();
		}

		long sourceSeatCount = eventSeatMapper.countSourceSeatsForEvent(eventId);
		if (sourceSeatCount == 0) {
			throw new NoEventSeatsException();
		}
		if (eventSeatMapper.countMissingPricedSeatsForEvent(eventId) > 0) {
			throw new MissingEventSectionPricingException();
		}

		int insertedRows = eventSeatMapper.insertForDraftEvent(eventId);
		if (insertedRows != sourceSeatCount) {
			throw new EventPublicationException();
		}
		if (eventMapper.publishDraft(eventId) != 1) {
			throw new EventStateConflictException();
		}
		return publishedResponse(eventId);
	}

	private EventPublishResponse publishedResponse(UUID eventId) {
		return new EventPublishResponse(eventId, EventStatus.PUBLISHED, eventSeatMapper.countByEventId(eventId));
	}
}
