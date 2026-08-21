package com.seatflow.common;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public final class ApiErrorResponseFactory {

	private static final Logger log = LoggerFactory.getLogger(ApiErrorResponseFactory.class);

	private ApiErrorResponseFactory() {
	}

	public static ResponseEntity<ApiErrorResponse> response(
			HttpServletRequest request,
			ApiErrorCode errorCode,
			Throwable exception) {
		return response(request, errorCode, exception, List.of());
	}

	public static ResponseEntity<ApiErrorResponse> response(
			HttpServletRequest request,
			ApiErrorCode errorCode,
			Throwable exception,
			List<ApiFieldError> errors) {
		return response(request, errorCode, exception, errors, headers -> {
		});
	}

	public static ResponseEntity<ApiErrorResponse> response(
			HttpServletRequest request,
			ApiErrorCode errorCode,
			Throwable exception,
			List<ApiFieldError> errors,
			Consumer<HttpHeaders> headerCustomizer) {
		String correlationId = CorrelationId.ensure(request);
		boolean clearMdc = !CorrelationId.isFilterManaged(request);
		try {
			ApiErrorResponse body = body(errorCode, correlationId, errors);
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
			headers.set(CorrelationId.HEADER_NAME, correlationId);
			headerCustomizer.accept(headers);
			logFailure(errorCode, correlationId, exception);
			return ResponseEntity.status(errorCode.status()).headers(headers).body(body);
		}
		finally {
			if (clearMdc) {
				CorrelationId.clearMdc();
			}
		}
	}

	public static void write(
			HttpServletRequest request,
			HttpServletResponse response,
			ObjectMapper objectMapper,
			ApiErrorCode errorCode,
			Throwable exception) throws IOException {
		String correlationId = CorrelationId.ensure(request);
		boolean clearMdc = !CorrelationId.isFilterManaged(request);
		try {
			response.setStatus(errorCode.status().value());
			response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
			response.setHeader(CorrelationId.HEADER_NAME, correlationId);
			logFailure(errorCode, correlationId, exception);
			objectMapper.writeValue(response.getOutputStream(), body(errorCode, correlationId, List.of()));
		}
		finally {
			if (clearMdc) {
				CorrelationId.clearMdc();
			}
		}
	}

	private static ApiErrorResponse body(
			ApiErrorCode errorCode,
			String correlationId,
			List<ApiFieldError> errors) {
		return new ApiErrorResponse(
				errorCode.type(),
				errorCode.title(),
				errorCode.status().value(),
				errorCode.code(),
				errorCode.detail(),
				correlationId,
				Instant.now(),
				errors);
	}

	private static void logFailure(ApiErrorCode errorCode, String correlationId, Throwable exception) {
		if (errorCode.status().is5xxServerError()) {
			log.error("API error code={} status={} correlationId={}",
					errorCode.code(),
					errorCode.status().value(),
					correlationId,
					exception);
			return;
		}

		log.warn("API error code={} status={} correlationId={}",
				errorCode.code(),
				errorCode.status().value(),
				correlationId);
	}
}
