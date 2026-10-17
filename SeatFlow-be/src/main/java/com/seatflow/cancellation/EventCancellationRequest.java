package com.seatflow.cancellation;

import jakarta.validation.constraints.Size;

public record EventCancellationRequest(@Size(max = 200) String reason) {

	public static final EventCancellationRequest NONE = new EventCancellationRequest(null);

	/** The reason as stored: trimmed, and absent rather than blank. */
	public String normalizedReason() {
		if (reason == null || reason.isBlank()) {
			return null;
		}
		return reason.trim();
	}
}
