package com.seatflow.event;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record EventSectionReplaceRequest(
		@NotNull List<@NotNull @Valid EventSectionItemRequest> sections) {
}
