package com.seatflow.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.seatflow.auth.AuthenticationFailedException;
import com.seatflow.auth.InvalidRefreshTokenException;
import com.seatflow.auth.UserAlreadyExistsException;
import com.seatflow.event.DuplicateEventSectionException;
import com.seatflow.event.EventNotFoundException;
import com.seatflow.event.EventPublicationException;
import com.seatflow.event.EventStateConflictException;
import com.seatflow.event.InvalidEventSectionException;
import com.seatflow.event.InvalidEventSectionPriceException;
import com.seatflow.event.InvalidEventPaginationException;
import com.seatflow.event.InvalidEventTimingException;
import com.seatflow.event.MissingEventSectionPricingException;
import com.seatflow.event.NoEventSeatsException;
import com.seatflow.health.DatabaseHealthUnavailableException;
import com.seatflow.seating.DuplicateSeatLabelException;
import com.seatflow.seating.InvalidSeatBatchException;
import com.seatflow.seating.SeatNotFoundException;
import com.seatflow.seating.SectionNotFoundException;
import com.seatflow.venue.ArchivedVenueCannotHostEventsException;
import com.seatflow.venue.InvalidVenuePaginationException;
import com.seatflow.venue.InvalidVenueTimezoneException;
import com.seatflow.venue.VenueAlreadyArchivedException;
import com.seatflow.venue.VenueNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
		return error("Invalid request", HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(DatabaseHealthUnavailableException.class)
	public ResponseEntity<ApiResponse<Void>> handleDatabaseHealthUnavailable(DatabaseHealthUnavailableException ex) {
		return error("Database health check failed", HttpStatus.SERVICE_UNAVAILABLE);
	}

	@ExceptionHandler(UserAlreadyExistsException.class)
	public ResponseEntity<ApiResponse<Void>> handleUserAlreadyExists(UserAlreadyExistsException ex) {
		return error("User already exists", HttpStatus.CONFLICT);
	}

	@ExceptionHandler(AuthenticationFailedException.class)
	public ResponseEntity<ApiResponse<Void>> handleAuthenticationFailed(AuthenticationFailedException ex) {
		return error("Invalid email or password", HttpStatus.UNAUTHORIZED);
	}

	@ExceptionHandler(InvalidRefreshTokenException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
		return error("Invalid refresh token", HttpStatus.UNAUTHORIZED);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
		return error("Forbidden", HttpStatus.FORBIDDEN);
	}

	@ExceptionHandler(InvalidVenueTimezoneException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidVenueTimezone(InvalidVenueTimezoneException ex) {
		return error("Invalid timezone", HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(InvalidVenuePaginationException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidVenuePagination(InvalidVenuePaginationException ex) {
		return error("Invalid pagination", HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(InvalidEventPaginationException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidEventPagination(InvalidEventPaginationException ex) {
		return error("Invalid pagination", HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(InvalidEventTimingException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidEventTiming(InvalidEventTimingException ex) {
		return error("Invalid event timing", HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(InvalidEventSectionException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidEventSection(InvalidEventSectionException ex) {
		return error("Invalid event section", HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(InvalidEventSectionPriceException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidEventSectionPrice(InvalidEventSectionPriceException ex) {
		return error("Invalid event section price", HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(DuplicateEventSectionException.class)
	public ResponseEntity<ApiResponse<Void>> handleDuplicateEventSection(DuplicateEventSectionException ex) {
		return error("Duplicate event section", HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(VenueNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleVenueNotFound(VenueNotFoundException ex) {
		return error("Venue not found", HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(EventNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleEventNotFound(EventNotFoundException ex) {
		return error("Event not found", HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(EventStateConflictException.class)
	public ResponseEntity<ApiResponse<Void>> handleEventStateConflict(EventStateConflictException ex) {
		return error("Event state conflict", HttpStatus.CONFLICT);
	}

	@ExceptionHandler(MissingEventSectionPricingException.class)
	public ResponseEntity<ApiResponse<Void>> handleMissingEventSectionPricing(
			MissingEventSectionPricingException ex) {
		return error("Event section pricing is incomplete", HttpStatus.CONFLICT);
	}

	@ExceptionHandler(NoEventSeatsException.class)
	public ResponseEntity<ApiResponse<Void>> handleNoEventSeats(NoEventSeatsException ex) {
		return error("Event has no seats", HttpStatus.CONFLICT);
	}

	@ExceptionHandler(EventPublicationException.class)
	public ResponseEntity<ApiResponse<Void>> handleEventPublication(EventPublicationException ex) {
		return error("Event publication failed", HttpStatus.CONFLICT);
	}

	@ExceptionHandler(VenueAlreadyArchivedException.class)
	public ResponseEntity<ApiResponse<Void>> handleVenueAlreadyArchived(VenueAlreadyArchivedException ex) {
		return error("Venue is already archived", HttpStatus.CONFLICT);
	}

	@ExceptionHandler(ArchivedVenueCannotHostEventsException.class)
	public ResponseEntity<ApiResponse<Void>> handleArchivedVenueCannotHostEvents(
			ArchivedVenueCannotHostEventsException ex) {
		return error("Archived venue cannot host new events", HttpStatus.CONFLICT);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
		return error("Method not supported", HttpStatus.METHOD_NOT_ALLOWED);
	}

	@ExceptionHandler(SectionNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleSectionNotFound(SectionNotFoundException ex) {
		return error("Section not found", HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(SeatNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleSeatNotFound(SeatNotFoundException ex) {
		return error("Seat not found", HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(DuplicateSeatLabelException.class)
	public ResponseEntity<ApiResponse<Void>> handleDuplicateSeatLabel(DuplicateSeatLabelException ex) {
		return error("Duplicate seat label", HttpStatus.CONFLICT);
	}

	@ExceptionHandler(InvalidSeatBatchException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidSeatBatch(InvalidSeatBatchException ex) {
		return error("Invalid seat batch", HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
		return error("Unexpected error", HttpStatus.INTERNAL_SERVER_ERROR);
	}

	private ResponseEntity<ApiResponse<Void>> error(String message, HttpStatus status) {
		return ResponseEntity.status(status).body(ApiResponse.error(message));
	}
}
