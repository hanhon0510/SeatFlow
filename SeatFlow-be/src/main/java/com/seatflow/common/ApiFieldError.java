package com.seatflow.common;

public record ApiFieldError(
		String field,
		String message,
		String code) {
}
