package com.seatflow.event;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class EventSeatLayoutService {

	private final EventSeatMapper eventSeatMapper;

	public EventSeatLayoutService(EventSeatMapper eventSeatMapper) {
		this.eventSeatMapper = eventSeatMapper;
	}

	public EventSeatLayoutResponse getSeatLayout(UUID eventId) {
		List<EventSeatLayoutRow> rows = eventSeatMapper.findPublishedLayoutByEventId(eventId);
		if (rows.isEmpty()) {
			throw new EventNotFoundException();
		}

		Map<UUID, MutableSectionLayout> sections = new LinkedHashMap<>();
		for (EventSeatLayoutRow row : rows) {
			MutableSectionLayout section = sections.computeIfAbsent(
					row.sectionId(),
					ignored -> new MutableSectionLayout(row));
			section.addSeat(row);
		}

		return new EventSeatLayoutResponse(
				eventId,
				sections.values().stream()
						.map(MutableSectionLayout::toResponse)
						.toList());
	}

	private record MutableSectionLayout(
			UUID id,
			String name,
			int displayOrder,
			Map<String, MutableSeatRow> rows) {

		private MutableSectionLayout(EventSeatLayoutRow row) {
			this(row.sectionId(), row.sectionName(), row.sectionDisplayOrder(), new LinkedHashMap<>());
		}

		private void addSeat(EventSeatLayoutRow row) {
			rows.computeIfAbsent(row.rowLabel(), MutableSeatRow::new)
					.seats()
					.add(EventSeatLayoutSeatResponse.from(row));
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
