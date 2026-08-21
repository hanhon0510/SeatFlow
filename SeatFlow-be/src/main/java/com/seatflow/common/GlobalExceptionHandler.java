package com.seatflow.common;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.ibatis.exceptions.PersistenceException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.seatflow.auth.AuthenticationFailedException;
import com.seatflow.auth.InvalidRefreshTokenException;
import com.seatflow.auth.UserAlreadyExistsException;
import com.seatflow.event.DuplicateEventSectionException;
import com.seatflow.event.EventNotFoundException;
import com.seatflow.event.EventPublicationException;
import com.seatflow.event.EventStateConflictException;
import com.seatflow.event.InvalidEventCatalogQueryException;
import com.seatflow.event.InvalidEventPaginationException;
import com.seatflow.event.InvalidEventSectionException;
import com.seatflow.event.InvalidEventSectionPriceException;
import com.seatflow.event.InvalidEventTimingException;
import com.seatflow.event.MissingEventSectionPricingException;
import com.seatflow.event.NoEventSeatsException;
import com.seatflow.health.DatabaseHealthUnavailableException;
import com.seatflow.health.RedisHealthUnavailableException;
import com.seatflow.hold.InvalidSeatHoldRequestException;
import com.seatflow.hold.SeatHoldConflictException;
import com.seatflow.hold.SeatHoldNotFoundException;
import com.seatflow.hold.SeatHoldStorageException;
import com.seatflow.idempotency.IdempotencyConflictException;
import com.seatflow.idempotency.IdempotencyStorageException;
import com.seatflow.idempotency.InvalidIdempotencyKeyException;
import com.seatflow.order.InvalidOrderPaginationException;
import com.seatflow.order.OrderConflictException;
import com.seatflow.order.OrderNotFoundException;
import com.seatflow.payment.InvalidPaymentTokenException;
import com.seatflow.payment.PaymentConflictException;
import com.seatflow.ratelimit.RateLimitExceededException;
import com.seatflow.ratelimit.RateLimitResult;
import com.seatflow.ratelimit.RateLimitStorageException;
import com.seatflow.reservation.ReservationConflictException;
import com.seatflow.reservation.ReservationNotFoundException;
import com.seatflow.seating.DuplicateSeatLabelException;
import com.seatflow.seating.InvalidSeatBatchException;
import com.seatflow.seating.SeatNotFoundException;
import com.seatflow.seating.SectionNotFoundException;
import com.seatflow.ticket.TicketIssuanceException;
import com.seatflow.ticket.TicketNotFoundException;
import com.seatflow.venue.ArchivedVenueCannotHostEventsException;
import com.seatflow.venue.InvalidVenuePaginationException;
import com.seatflow.venue.InvalidVenueTimezoneException;
import com.seatflow.venue.VenueAlreadyArchivedException;
import com.seatflow.venue.VenueNotFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Pattern POSTGRES_CONSTRAINT_PATTERN =
			Pattern.compile("constraint \"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(
			MethodArgumentNotValidException ex,
			HttpServletRequest request) {
		return ApiErrorResponseFactory.response(
				request,
				ApiErrorCode.VALIDATION_FAILED,
				ex,
				validationErrors(ex));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
			ConstraintViolationException ex,
			HttpServletRequest request) {
		List<ApiFieldError> errors = ex.getConstraintViolations().stream()
				.map(violation -> new ApiFieldError(
						violation.getPropertyPath().toString(),
						safeMessage(violation.getMessage()),
						violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()))
				.sorted(Comparator.comparing(ApiFieldError::field).thenComparing(ApiFieldError::message))
				.toList();
		return ApiErrorResponseFactory.response(request, ApiErrorCode.VALIDATION_FAILED, ex, errors);
	}

	@ExceptionHandler({
			HttpMessageNotReadableException.class,
			MissingServletRequestParameterException.class
	})
	public ResponseEntity<ApiErrorResponse> handleMalformedRequest(Exception ex, HttpServletRequest request) {
		return error(request, ApiErrorCode.VALIDATION_FAILED, ex);
	}

	@ExceptionHandler(DatabaseHealthUnavailableException.class)
	public ResponseEntity<ApiErrorResponse> handleDatabaseHealthUnavailable(
			DatabaseHealthUnavailableException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.DATABASE_HEALTH_UNAVAILABLE, ex);
	}

	@ExceptionHandler(RedisHealthUnavailableException.class)
	public ResponseEntity<ApiErrorResponse> handleRedisHealthUnavailable(
			RedisHealthUnavailableException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.REDIS_HEALTH_UNAVAILABLE, ex);
	}

	@ExceptionHandler(UserAlreadyExistsException.class)
	public ResponseEntity<ApiErrorResponse> handleUserAlreadyExists(
			UserAlreadyExistsException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.USER_ALREADY_EXISTS, ex);
	}

	@ExceptionHandler(AuthenticationFailedException.class)
	public ResponseEntity<ApiErrorResponse> handleAuthenticationFailed(
			AuthenticationFailedException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.AUTHENTICATION_FAILED, ex);
	}

	@ExceptionHandler(InvalidRefreshTokenException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidRefreshToken(
			InvalidRefreshTokenException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.INVALID_REFRESH_TOKEN, ex);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
		return error(request, ApiErrorCode.FORBIDDEN, ex);
	}

	@ExceptionHandler(InvalidVenueTimezoneException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidVenueTimezone(
			InvalidVenueTimezoneException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.INVALID_TIMEZONE, ex);
	}

	@ExceptionHandler({
			InvalidVenuePaginationException.class,
			InvalidEventPaginationException.class,
			InvalidOrderPaginationException.class
	})
	public ResponseEntity<ApiErrorResponse> handleInvalidPagination(Exception ex, HttpServletRequest request) {
		return error(request, ApiErrorCode.INVALID_PAGINATION, ex);
	}

	@ExceptionHandler(InvalidEventCatalogQueryException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidEventCatalogQuery(
			InvalidEventCatalogQueryException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.INVALID_EVENT_CATALOG_QUERY, ex);
	}

	@ExceptionHandler(InvalidEventTimingException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidEventTiming(
			InvalidEventTimingException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.INVALID_EVENT_TIMING, ex);
	}

	@ExceptionHandler(InvalidEventSectionException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidEventSection(
			InvalidEventSectionException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.INVALID_EVENT_SECTION, ex);
	}

	@ExceptionHandler(InvalidEventSectionPriceException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidEventSectionPrice(
			InvalidEventSectionPriceException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.INVALID_EVENT_SECTION_PRICE, ex);
	}

	@ExceptionHandler(DuplicateEventSectionException.class)
	public ResponseEntity<ApiErrorResponse> handleDuplicateEventSection(
			DuplicateEventSectionException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.DUPLICATE_EVENT_SECTION, ex);
	}

	@ExceptionHandler(VenueNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleVenueNotFound(VenueNotFoundException ex, HttpServletRequest request) {
		return error(request, ApiErrorCode.VENUE_NOT_FOUND, ex);
	}

	@ExceptionHandler(EventNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleEventNotFound(EventNotFoundException ex, HttpServletRequest request) {
		return error(request, ApiErrorCode.EVENT_NOT_FOUND, ex);
	}

	@ExceptionHandler(EventStateConflictException.class)
	public ResponseEntity<ApiErrorResponse> handleEventStateConflict(
			EventStateConflictException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.EVENT_STATE_CONFLICT, ex);
	}

	@ExceptionHandler(MissingEventSectionPricingException.class)
	public ResponseEntity<ApiErrorResponse> handleMissingEventSectionPricing(
			MissingEventSectionPricingException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.MISSING_EVENT_SECTION_PRICING, ex);
	}

	@ExceptionHandler(NoEventSeatsException.class)
	public ResponseEntity<ApiErrorResponse> handleNoEventSeats(NoEventSeatsException ex, HttpServletRequest request) {
		return error(request, ApiErrorCode.NO_EVENT_SEATS, ex);
	}

	@ExceptionHandler(EventPublicationException.class)
	public ResponseEntity<ApiErrorResponse> handleEventPublication(
			EventPublicationException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.EVENT_PUBLICATION_FAILED, ex);
	}

	@ExceptionHandler(SeatHoldConflictException.class)
	public ResponseEntity<ApiErrorResponse> handleSeatHoldConflict(
			SeatHoldConflictException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.SEAT_ALREADY_HELD, ex);
	}

	@ExceptionHandler(InvalidSeatHoldRequestException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidSeatHoldRequest(
			InvalidSeatHoldRequestException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.INVALID_SEAT_HOLD_REQUEST, ex);
	}

	@ExceptionHandler(SeatHoldNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleSeatHoldNotFound(
			SeatHoldNotFoundException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.SEAT_HOLD_NOT_FOUND, ex);
	}

	@ExceptionHandler(SeatHoldStorageException.class)
	public ResponseEntity<ApiErrorResponse> handleSeatHoldStorage(
			SeatHoldStorageException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.SEAT_HOLD_STORAGE_UNAVAILABLE, ex);
	}

	@ExceptionHandler(ReservationNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleReservationNotFound(
			ReservationNotFoundException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.RESERVATION_NOT_FOUND, ex);
	}

	@ExceptionHandler(ReservationConflictException.class)
	public ResponseEntity<ApiErrorResponse> handleReservationConflict(
			ReservationConflictException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.RESERVATION_CONFLICT, ex);
	}

	@ExceptionHandler(OrderNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleOrderNotFound(OrderNotFoundException ex, HttpServletRequest request) {
		return error(request, ApiErrorCode.ORDER_NOT_FOUND, ex);
	}

	@ExceptionHandler(OrderConflictException.class)
	public ResponseEntity<ApiErrorResponse> handleOrderConflict(OrderConflictException ex, HttpServletRequest request) {
		return error(request, ApiErrorCode.ORDER_CONFLICT, ex);
	}

	@ExceptionHandler(InvalidPaymentTokenException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidPaymentToken(
			InvalidPaymentTokenException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.INVALID_PAYMENT_TOKEN, ex);
	}

	@ExceptionHandler(PaymentConflictException.class)
	public ResponseEntity<ApiErrorResponse> handlePaymentConflict(
			PaymentConflictException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.PAYMENT_CONFLICT, ex);
	}

	@ExceptionHandler(TicketNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleTicketNotFound(
			TicketNotFoundException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.TICKET_NOT_FOUND, ex);
	}

	@ExceptionHandler(TicketIssuanceException.class)
	public ResponseEntity<ApiErrorResponse> handleTicketIssuance(
			TicketIssuanceException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.TICKET_ISSUANCE_FAILED, ex);
	}

	@ExceptionHandler(InvalidIdempotencyKeyException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidIdempotencyKey(
			InvalidIdempotencyKeyException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.IDEMPOTENCY_KEY_REQUIRED, ex);
	}

	@ExceptionHandler(IdempotencyConflictException.class)
	public ResponseEntity<ApiErrorResponse> handleIdempotencyConflict(
			IdempotencyConflictException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.IDEMPOTENCY_KEY_CONFLICT, ex);
	}

	@ExceptionHandler(IdempotencyStorageException.class)
	public ResponseEntity<ApiErrorResponse> handleIdempotencyStorage(
			IdempotencyStorageException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.IDEMPOTENCY_STORAGE_FAILED, ex);
	}

	@ExceptionHandler(RateLimitExceededException.class)
	public ResponseEntity<ApiErrorResponse> handleRateLimitExceeded(
			RateLimitExceededException ex,
			HttpServletRequest request) {
		RateLimitResult result = ex.result();
		long retryAfterSeconds = secondsCeiling(result.retryAfter());
		long resetAfterSeconds = secondsCeiling(result.resetAfter());
		return ApiErrorResponseFactory.response(
				request,
				ApiErrorCode.RATE_LIMIT_EXCEEDED,
				ex,
				List.of(),
				headers -> {
					headers.set("Retry-After", Long.toString(retryAfterSeconds));
					headers.set("X-RateLimit-Limit", Integer.toString(result.limit()));
					headers.set("X-RateLimit-Remaining", Integer.toString(result.remaining()));
					headers.set("X-RateLimit-Reset-After", Long.toString(resetAfterSeconds));
				});
	}

	@ExceptionHandler(RateLimitStorageException.class)
	public ResponseEntity<ApiErrorResponse> handleRateLimitStorage(
			RateLimitStorageException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.RATE_LIMIT_STORAGE_UNAVAILABLE, ex);
	}

	@ExceptionHandler(VenueAlreadyArchivedException.class)
	public ResponseEntity<ApiErrorResponse> handleVenueAlreadyArchived(
			VenueAlreadyArchivedException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.VENUE_ALREADY_ARCHIVED, ex);
	}

	@ExceptionHandler(ArchivedVenueCannotHostEventsException.class)
	public ResponseEntity<ApiErrorResponse> handleArchivedVenueCannotHostEvents(
			ArchivedVenueCannotHostEventsException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.ARCHIVED_VENUE_CANNOT_HOST_EVENTS, ex);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
			HttpRequestMethodNotSupportedException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.METHOD_NOT_ALLOWED, ex);
	}

	@ExceptionHandler(SectionNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleSectionNotFound(
			SectionNotFoundException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.SECTION_NOT_FOUND, ex);
	}

	@ExceptionHandler(SeatNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleSeatNotFound(SeatNotFoundException ex, HttpServletRequest request) {
		return error(request, ApiErrorCode.SEAT_NOT_FOUND, ex);
	}

	@ExceptionHandler(DuplicateSeatLabelException.class)
	public ResponseEntity<ApiErrorResponse> handleDuplicateSeatLabel(
			DuplicateSeatLabelException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.DUPLICATE_SEAT_LABEL, ex);
	}

	@ExceptionHandler(InvalidSeatBatchException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidSeatBatch(
			InvalidSeatBatchException ex,
			HttpServletRequest request) {
		return error(request, ApiErrorCode.INVALID_SEAT_BATCH, ex);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleDataIntegrity(
			DataIntegrityViolationException ex,
			HttpServletRequest request) {
		return error(request, constraintErrorCode(ex).orElse(ApiErrorCode.CONSTRAINT_CONFLICT), ex);
	}

	@ExceptionHandler(PersistenceException.class)
	public ResponseEntity<ApiErrorResponse> handleMyBatisPersistence(
			PersistenceException ex,
			HttpServletRequest request) {
		Optional<ApiErrorCode> constraintErrorCode = constraintErrorCode(ex);
		if (constraintErrorCode.isPresent()) {
			return error(request, constraintErrorCode.get(), ex);
		}
		return error(request, ApiErrorCode.PERSISTENCE_ERROR, ex);
	}

	@ExceptionHandler(DataAccessException.class)
	public ResponseEntity<ApiErrorResponse> handleDataAccess(DataAccessException ex, HttpServletRequest request) {
		Optional<ApiErrorCode> constraintErrorCode = constraintErrorCode(ex);
		if (constraintErrorCode.isPresent()) {
			return error(request, constraintErrorCode.get(), ex);
		}
		return error(request, ApiErrorCode.PERSISTENCE_ERROR, ex);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
		return error(request, ApiErrorCode.INTERNAL_ERROR, ex);
	}

	private ResponseEntity<ApiErrorResponse> error(
			HttpServletRequest request,
			ApiErrorCode errorCode,
			Throwable exception) {
		return ApiErrorResponseFactory.response(request, errorCode, exception);
	}

	private static List<ApiFieldError> validationErrors(MethodArgumentNotValidException ex) {
		List<ApiFieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
				.map(fieldError -> new ApiFieldError(
						fieldError.getField(),
						safeMessage(fieldError.getDefaultMessage()),
						safeMessage(fieldError.getCode())))
				.toList();
		List<ApiFieldError> globalErrors = ex.getBindingResult().getGlobalErrors().stream()
				.map(globalError -> new ApiFieldError(
						globalError.getObjectName(),
						safeMessage(globalError.getDefaultMessage()),
						safeMessage(globalError.getCode())))
				.toList();
		return java.util.stream.Stream.concat(fieldErrors.stream(), globalErrors.stream())
				.sorted(Comparator.comparing(ApiFieldError::field).thenComparing(ApiFieldError::message))
				.toList();
	}

	private static Optional<ApiErrorCode> constraintErrorCode(Throwable exception) {
		SQLException sqlException = findSqlException(exception);
		if (sqlException == null) {
			return exception instanceof DataIntegrityViolationException
					? Optional.of(ApiErrorCode.CONSTRAINT_CONFLICT)
					: Optional.empty();
		}
		String sqlState = sqlException.getSQLState();
		if (sqlState == null || !sqlState.startsWith("23")) {
			return Optional.empty();
		}

		return Optional.of(errorCodeForConstraint(constraintName(sqlException).orElse(null)));
	}

	private static SQLException findSqlException(Throwable exception) {
		for (Throwable current = exception; current != null; current = current.getCause()) {
			if (current instanceof SQLException sqlException) {
				return sqlException;
			}
		}
		return null;
	}

	private static Optional<String> constraintName(SQLException sqlException) {
		String message = sqlException.getMessage();
		if (message == null) {
			return Optional.empty();
		}
		Matcher matcher = POSTGRES_CONSTRAINT_PATTERN.matcher(message);
		if (matcher.find()) {
			return Optional.of(matcher.group(1));
		}
		return Optional.empty();
	}

	private static ApiErrorCode errorCodeForConstraint(String constraintName) {
		if (constraintName == null) {
			return ApiErrorCode.CONSTRAINT_CONFLICT;
		}
		return switch (constraintName) {
			case "users_normalized_email_uq" -> ApiErrorCode.USER_ALREADY_EXISTS;
			case "seats_section_label_uq" -> ApiErrorCode.DUPLICATE_SEAT_LABEL;
			case "event_sections_event_section_uq" -> ApiErrorCode.DUPLICATE_EVENT_SECTION;
			case "tickets_order_event_seat_uq" -> ApiErrorCode.TICKET_ALREADY_ISSUED;
			case "idempotency_records_scope_uq" -> ApiErrorCode.IDEMPOTENCY_KEY_CONFLICT;
			default -> ApiErrorCode.CONSTRAINT_CONFLICT;
		};
	}

	private static String safeMessage(String message) {
		return message == null || message.isBlank() ? "Invalid value" : message;
	}

	private static long secondsCeiling(Duration duration) {
		long millis = Math.max(duration.toMillis(), 0);
		return Math.max(1, (millis + 999) / 1000);
	}
}
