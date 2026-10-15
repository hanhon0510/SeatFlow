package com.seatflow.refund;

import jakarta.validation.constraints.Size;

public record RefundRequest(@Size(max = 255) String reason) {

	public static final RefundRequest NONE = new RefundRequest(null);

	/** The reason as stored: trimmed, and absent rather than blank. */
	public String normalizedReason() {
		if (reason == null || reason.isBlank()) {
			return null;
		}
		return reason.trim();
	}
}
