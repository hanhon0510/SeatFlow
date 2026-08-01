package com.seatflow.event;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventSectionPricingService {

	private final EventMapper eventMapper;
	private final EventSectionMapper eventSectionMapper;

	public EventSectionPricingService(EventMapper eventMapper, EventSectionMapper eventSectionMapper) {
		this.eventMapper = eventMapper;
		this.eventSectionMapper = eventSectionMapper;
	}

	@Transactional
	@PreAuthorize("hasRole('ADMIN')")
	public EventSectionConfigurationResponse replaceEventSections(
			UUID eventId,
			EventSectionReplaceRequest request) {
		EventRecord event = eventMapper.findByIdForUpdate(eventId);
		if (event == null) {
			throw new EventNotFoundException();
		}
		if (event.status() != EventStatus.DRAFT) {
			throw new EventStateConflictException();
		}

		List<EventSectionRecord> sections = sectionsForReplacement(eventId, request);
		eventSectionMapper.deleteByEventId(eventId);
		for (EventSectionRecord section : sections) {
			int insertedRows = eventSectionMapper.insertForDraftEvent(section);
			if (insertedRows != 1) {
				throw new InvalidEventSectionException();
			}
		}

		return currentConfiguration(eventId);
	}

	@PreAuthorize("hasRole('ADMIN')")
	public EventSectionConfigurationResponse getEventSections(UUID eventId) {
		if (eventMapper.findById(eventId) == null) {
			throw new EventNotFoundException();
		}
		return currentConfiguration(eventId);
	}

	private EventSectionConfigurationResponse currentConfiguration(UUID eventId) {
		return EventSectionConfigurationResponse.from(eventId, eventSectionMapper.findByEventId(eventId));
	}

	private static List<EventSectionRecord> sectionsForReplacement(
			UUID eventId,
			EventSectionReplaceRequest request) {
		if (request.sections() == null) {
			throw new InvalidEventSectionException();
		}

		Set<UUID> seenSectionIds = new HashSet<>();
		List<EventSectionRecord> sections = new ArrayList<>();
		for (EventSectionItemRequest item : request.sections()) {
			if (item == null || item.venueSectionId() == null || item.salesEnabled() == null) {
				throw new InvalidEventSectionException();
			}
			validatePrice(item.price());
			if (!seenSectionIds.add(item.venueSectionId())) {
				throw new DuplicateEventSectionException();
			}
			sections.add(EventSectionRecord.forInsert(
					UUID.randomUUID(),
					eventId,
					item.venueSectionId(),
					item.price(),
					item.salesEnabled()));
		}
		return sections;
	}

	private static void validatePrice(BigDecimal price) {
		if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
			throw new InvalidEventSectionPriceException();
		}
	}
}
