package com.seatflow.event;

import java.util.List;
import java.util.UUID;

public record EventSectionConfigurationResponse(
		UUID eventId,
		List<EventSectionResponse> sections) {

	public static EventSectionConfigurationResponse from(UUID eventId, List<EventSectionRecord> sections) {
		return new EventSectionConfigurationResponse(
				eventId,
				sections.stream()
						.map(EventSectionResponse::from)
						.toList());
	}
}
