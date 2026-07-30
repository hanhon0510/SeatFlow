package com.seatflow.seating;

import java.util.List;
import java.util.UUID;

public record SeatLayoutResponse(UUID venueId, List<SeatLayoutSectionResponse> sections) {
}
