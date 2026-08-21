package com.seatflow.common;

import org.springframework.http.HttpStatus;

public enum ApiErrorCode {
	VALIDATION_FAILED("invalid-request", "Invalid request", HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
			"Request validation failed."),
	UNAUTHORIZED("unauthorized", "Unauthorized", HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
			"Authentication is required."),
	AUTHENTICATION_FAILED("authentication-failed", "Invalid email or password", HttpStatus.UNAUTHORIZED,
			"AUTHENTICATION_FAILED", "Invalid email or password."),
	INVALID_REFRESH_TOKEN("invalid-refresh-token", "Invalid refresh token", HttpStatus.UNAUTHORIZED,
			"INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired."),
	FORBIDDEN("forbidden", "Forbidden", HttpStatus.FORBIDDEN, "FORBIDDEN",
			"You do not have permission to access this resource."),
	RESOURCE_NOT_FOUND("not-found", "Not found", HttpStatus.NOT_FOUND, "NOT_FOUND",
			"Resource was not found."),
	METHOD_NOT_ALLOWED("method-not-allowed", "Method not supported", HttpStatus.METHOD_NOT_ALLOWED,
			"METHOD_NOT_ALLOWED", "The HTTP method is not supported for this endpoint."),
	USER_ALREADY_EXISTS("user-already-exists", "User already exists", HttpStatus.CONFLICT,
			"USER_ALREADY_EXISTS", "An account with that email already exists."),
	INVALID_TIMEZONE("invalid-timezone", "Invalid timezone", HttpStatus.BAD_REQUEST, "INVALID_TIMEZONE",
			"The supplied timezone is not supported."),
	INVALID_PAGINATION("invalid-pagination", "Invalid pagination", HttpStatus.BAD_REQUEST, "INVALID_PAGINATION",
			"Pagination parameters are invalid."),
	INVALID_EVENT_CATALOG_QUERY("invalid-event-catalog-query", "Invalid event catalog query",
			HttpStatus.BAD_REQUEST, "INVALID_EVENT_CATALOG_QUERY", "Event catalog query parameters are invalid."),
	INVALID_EVENT_TIMING("invalid-event-timing", "Invalid event timing", HttpStatus.BAD_REQUEST,
			"INVALID_EVENT_TIMING", "Event timing is invalid."),
	INVALID_EVENT_SECTION("invalid-event-section", "Invalid event section", HttpStatus.BAD_REQUEST,
			"INVALID_EVENT_SECTION", "Event section configuration is invalid."),
	INVALID_EVENT_SECTION_PRICE("invalid-event-section-price", "Invalid event section price",
			HttpStatus.BAD_REQUEST, "INVALID_EVENT_SECTION_PRICE", "Event section price is invalid."),
	DUPLICATE_EVENT_SECTION("duplicate-event-section", "Duplicate event section", HttpStatus.CONFLICT,
			"DUPLICATE_EVENT_SECTION", "The event already includes this venue section."),
	VENUE_NOT_FOUND("venue-not-found", "Venue not found", HttpStatus.NOT_FOUND, "VENUE_NOT_FOUND",
			"Venue was not found."),
	EVENT_NOT_FOUND("event-not-found", "Event not found", HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND",
			"Event was not found."),
	EVENT_STATE_CONFLICT("event-state-conflict", "Event state conflict", HttpStatus.CONFLICT,
			"EVENT_STATE_CONFLICT", "The event state does not allow this operation."),
	MISSING_EVENT_SECTION_PRICING("event-section-pricing-incomplete", "Event section pricing is incomplete",
			HttpStatus.CONFLICT, "EVENT_SECTION_PRICING_INCOMPLETE",
			"Every event section must have pricing before this operation."),
	NO_EVENT_SEATS("event-has-no-seats", "Event has no seats", HttpStatus.CONFLICT, "EVENT_HAS_NO_SEATS",
			"The event has no seats configured."),
	EVENT_PUBLICATION_FAILED("event-publication-failed", "Event publication failed", HttpStatus.CONFLICT,
			"EVENT_PUBLICATION_FAILED", "The event could not be published."),
	SEAT_ALREADY_HELD("seat-unavailable", "Seat hold conflict", HttpStatus.CONFLICT, "SEAT_ALREADY_HELD",
			"One or more seats are unavailable."),
	INVALID_SEAT_HOLD_REQUEST("invalid-seat-hold-request", "Invalid seat hold request", HttpStatus.BAD_REQUEST,
			"INVALID_SEAT_HOLD_REQUEST", "Seat hold request is invalid."),
	SEAT_HOLD_NOT_FOUND("seat-hold-not-found", "Seat hold not found", HttpStatus.NOT_FOUND,
			"SEAT_HOLD_NOT_FOUND", "Seat hold was not found."),
	SEAT_HOLD_STORAGE_UNAVAILABLE("seat-hold-storage-unavailable", "Seat hold storage unavailable",
			HttpStatus.SERVICE_UNAVAILABLE, "SEAT_HOLD_STORAGE_UNAVAILABLE", "Seat hold storage is unavailable."),
	RESERVATION_NOT_FOUND("reservation-not-found", "Reservation not found", HttpStatus.NOT_FOUND,
			"RESERVATION_NOT_FOUND", "Reservation was not found."),
	RESERVATION_CONFLICT("reservation-conflict", "Reservation conflict", HttpStatus.CONFLICT,
			"RESERVATION_CONFLICT", "The reservation cannot be completed in its current state."),
	ORDER_NOT_FOUND("order-not-found", "Order not found", HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND",
			"Order was not found."),
	ORDER_CONFLICT("order-conflict", "Order conflict", HttpStatus.CONFLICT, "ORDER_CONFLICT",
			"The order cannot be completed in its current state."),
	INVALID_PAYMENT_TOKEN("invalid-payment-token", "Invalid payment token", HttpStatus.BAD_REQUEST,
			"INVALID_PAYMENT_TOKEN", "Payment token is invalid."),
	PAYMENT_CONFLICT("payment-conflict", "Payment conflict", HttpStatus.CONFLICT, "PAYMENT_CONFLICT",
			"The payment cannot be completed in its current state."),
	TICKET_NOT_FOUND("ticket-not-found", "Ticket not found", HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND",
			"Ticket was not found."),
	TICKET_ISSUANCE_FAILED("ticket-issuance-failed", "Ticket issuance failed", HttpStatus.CONFLICT,
			"TICKET_ISSUANCE_FAILED", "Tickets could not be issued for the order."),
	TICKET_ALREADY_ISSUED("ticket-already-issued", "Ticket issuance failed", HttpStatus.CONFLICT,
			"TICKET_ALREADY_ISSUED", "A ticket already exists for this order seat."),
	IDEMPOTENCY_KEY_REQUIRED("idempotency-key-required", "Idempotency-Key header is required",
			HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required."),
	IDEMPOTENCY_KEY_CONFLICT("idempotency-key-conflict", "Idempotency key conflict", HttpStatus.CONFLICT,
			"IDEMPOTENCY_KEY_CONFLICT", "The idempotency key was already used with a different request."),
	IDEMPOTENCY_STORAGE_FAILED("idempotency-storage-failed", "Idempotency storage failed",
			HttpStatus.INTERNAL_SERVER_ERROR, "IDEMPOTENCY_STORAGE_FAILED", "Idempotency storage failed."),
	RATE_LIMIT_EXCEEDED("rate-limit-exceeded", "Rate limit exceeded", HttpStatus.TOO_MANY_REQUESTS,
			"RATE_LIMIT_EXCEEDED", "Too many requests were made in this rate limit window."),
	RATE_LIMIT_STORAGE_UNAVAILABLE("rate-limit-storage-unavailable", "Rate limit storage unavailable",
			HttpStatus.SERVICE_UNAVAILABLE, "RATE_LIMIT_STORAGE_UNAVAILABLE", "Rate limit storage is unavailable."),
	VENUE_ALREADY_ARCHIVED("venue-already-archived", "Venue is already archived", HttpStatus.CONFLICT,
			"VENUE_ALREADY_ARCHIVED", "Venue is already archived."),
	ARCHIVED_VENUE_CANNOT_HOST_EVENTS("archived-venue-cannot-host-events",
			"Archived venue cannot host new events", HttpStatus.CONFLICT, "ARCHIVED_VENUE_CANNOT_HOST_EVENTS",
			"Archived venue cannot host new events."),
	SECTION_NOT_FOUND("section-not-found", "Section not found", HttpStatus.NOT_FOUND, "SECTION_NOT_FOUND",
			"Section was not found."),
	SEAT_NOT_FOUND("seat-not-found", "Seat not found", HttpStatus.NOT_FOUND, "SEAT_NOT_FOUND",
			"Seat was not found."),
	DUPLICATE_SEAT_LABEL("duplicate-seat-label", "Duplicate seat label", HttpStatus.CONFLICT,
			"DUPLICATE_SEAT_LABEL", "A seat with that label already exists in the section."),
	INVALID_SEAT_BATCH("invalid-seat-batch", "Invalid seat batch", HttpStatus.BAD_REQUEST,
			"INVALID_SEAT_BATCH", "Seat batch request is invalid."),
	CONSTRAINT_CONFLICT("constraint-conflict", "Constraint conflict", HttpStatus.CONFLICT, "CONSTRAINT_CONFLICT",
			"The request conflicts with stored data."),
	PERSISTENCE_ERROR("persistence-error", "Persistence error", HttpStatus.INTERNAL_SERVER_ERROR,
			"PERSISTENCE_ERROR", "The request could not be completed."),
	DATABASE_HEALTH_UNAVAILABLE("database-health-unavailable", "Database health check failed",
			HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_HEALTH_UNAVAILABLE", "Database health check failed."),
	REDIS_HEALTH_UNAVAILABLE("redis-health-unavailable", "Redis health check failed",
			HttpStatus.SERVICE_UNAVAILABLE, "REDIS_HEALTH_UNAVAILABLE", "Redis health check failed."),
	INTERNAL_ERROR("internal-error", "Unexpected error", HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
			"An unexpected error occurred.");

	private static final String TYPE_PREFIX = "https://seatflow.dev/errors/";

	private final String slug;
	private final String title;
	private final HttpStatus status;
	private final String code;
	private final String detail;

	ApiErrorCode(String slug, String title, HttpStatus status, String code, String detail) {
		this.slug = slug;
		this.title = title;
		this.status = status;
		this.code = code;
		this.detail = detail;
	}

	public String type() {
		return TYPE_PREFIX + slug;
	}

	public String title() {
		return title;
	}

	public HttpStatus status() {
		return status;
	}

	public String code() {
		return code;
	}

	public String detail() {
		return detail;
	}
}
