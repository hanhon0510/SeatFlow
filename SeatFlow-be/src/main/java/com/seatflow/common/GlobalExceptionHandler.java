package com.seatflow.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.seatflow.health.DatabaseHealthUnavailableException;

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

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
		return error("Unexpected error", HttpStatus.INTERNAL_SERVER_ERROR);
	}

	private ResponseEntity<ApiResponse<Void>> error(String message, HttpStatus status) {
		return ResponseEntity.status(status).body(ApiResponse.error(message));
	}
}
