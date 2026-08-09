package com.seatflow.event;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.seatflow.hold.SeatHoldStore;

@Service
public class EventSeatLayoutService {

	private final EventSeatMapper eventSeatMapper;
	private final SeatHoldStore seatHoldStore;

	public EventSeatLayoutService(EventSeatMapper eventSeatMapper, SeatHoldStore seatHoldStore) {
		this.eventSeatMapper = eventSeatMapper;
		this.seatHoldStore = seatHoldStore;
	}

	public EventSeatLayoutResponse getSeatLayout(UUID eventId) {
		return getSeatLayout(eventId, null);
	}

	public EventSeatLayoutResponse getSeatLayout(UUID eventId, UUID currentUserId) {
		List<EventSeatLayoutRow> rows = eventSeatMapper.findPublishedLayoutByEventId(eventId);
		if (rows.isEmpty()) {
			throw new EventNotFoundException();
		}

		Map<UUID, UUID> holdOwnersByEventSeatId = findHoldOwners(eventId, rows);
		Map<UUID, MutableSectionLayout> sections = new LinkedHashMap<>();
		for (EventSeatLayoutRow row : rows) {
			MutableSectionLayout section = sections.computeIfAbsent(
					row.sectionId(),
					ignored -> new MutableSectionLayout(row));
			section.addSeat(row, status(row, holdOwnersByEventSeatId, currentUserId));
		}

		return new EventSeatLayoutResponse(
				eventId,
				sections.values().stream()
						.map(MutableSectionLayout::toResponse)
						.toList());
	}

	private Map<UUID, UUID> findHoldOwners(UUID eventId, List<EventSeatLayoutRow> rows) {
		try {
			Map<UUID, UUID> holdOwners = seatHoldStore.findActiveSeatHoldOwners(
					eventId,
					rows.stream()
							.map(EventSeatLayoutRow::eventSeatId)
							.toList());
			return holdOwners == null ? Map.of() : holdOwners;
		}
		catch (RuntimeException ex) {
			return Map.of();
		}
	}

	private static EventSeatLayoutStatus status(
			EventSeatLayoutRow row,
			Map<UUID, UUID> holdOwnersByEventSeatId,
			UUID currentUserId) {
		if (row.permanentStatus() == EventSeatStatus.SOLD) {
			return EventSeatLayoutStatus.SOLD;
		}
		if (row.permanentStatus() == EventSeatStatus.BLOCKED) {
			return EventSeatLayoutStatus.BLOCKED;
		}

		UUID holdOwnerId = holdOwnersByEventSeatId.get(row.eventSeatId());
		if (holdOwnerId == null) {
			return EventSeatLayoutStatus.AVAILABLE;
		}
		if (currentUserId != null && currentUserId.equals(holdOwnerId)) {
			return EventSeatLayoutStatus.HELD_BY_YOU;
		}
		return EventSeatLayoutStatus.HELD;
	}

	private record MutableSectionLayout(
			UUID id,
			String name,
			int displayOrder,
			Map<String, MutableSeatRow> rows) {

		private MutableSectionLayout(EventSeatLayoutRow row) {
			this(row.sectionId(), row.sectionName(), row.sectionDisplayOrder(), new LinkedHashMap<>());
		}

		private void addSeat(EventSeatLayoutRow row, EventSeatLayoutStatus status) {
			rows.computeIfAbsent(row.rowLabel(), MutableSeatRow::new)
					.seats()
					.add(EventSeatLayoutSeatResponse.from(row, status));
		}

		private EventSeatLayoutSectionResponse toResponse() {
			return new EventSeatLayoutSectionResponse(
					id,
					name,
					displayOrder,
					rows.values().stream()
							.map(MutableSeatRow::toResponse)
							.toList());
		}
	}

	private record MutableSeatRow(String rowLabel, List<EventSeatLayoutSeatResponse> seats) {

		private MutableSeatRow(String rowLabel) {
			this(rowLabel, new ArrayList<>());
		}

		private EventSeatLayoutRowResponse toResponse() {
			return new EventSeatLayoutRowResponse(rowLabel, List.copyOf(seats));
		}
	}
}
