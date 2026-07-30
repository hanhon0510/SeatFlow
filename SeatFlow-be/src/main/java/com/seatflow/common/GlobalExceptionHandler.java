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
import com.seatflow.health.DatabaseHealthUnavailableException;
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

	@ExceptionHandler(VenueNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleVenueNotFound(VenueNotFoundException ex) {
		return error("Venue not found", HttpStatus.NOT_FOUND);
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

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
		return error("Unexpected error", HttpStatus.INTERNAL_SERVER_ERROR);
	}

	private ResponseEntity<ApiResponse<Void>> error(String message, HttpStatus status) {
		return ResponseEntity.status(status).body(ApiResponse.error(message));
	}
}
