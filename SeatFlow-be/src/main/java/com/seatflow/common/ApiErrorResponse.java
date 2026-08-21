package com.seatflow.common;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
		String type,
		String title,
		int status,
		String code,
		String detail,
		String correlationId,
		Instant timestamp,
		List<ApiFieldError> errors) {

	public ApiErrorResponse {
		errors = errors == null ? List.of() : List.copyOf(errors);
	}
}
