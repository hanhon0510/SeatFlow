package com.seatflow.common;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class ApiErrorController implements ErrorController {

	@RequestMapping("/error")
	public ResponseEntity<ApiErrorResponse> error(HttpServletRequest request) {
		ApiErrorCode errorCode = errorCode(request);
		Throwable exception = exception(request);
		return ApiErrorResponseFactory.response(request, errorCode, exception);
	}

	private static ApiErrorCode errorCode(HttpServletRequest request) {
		HttpStatus status = status(request);
		return switch (status) {
			case BAD_REQUEST -> ApiErrorCode.VALIDATION_FAILED;
			case UNAUTHORIZED -> ApiErrorCode.UNAUTHORIZED;
			case FORBIDDEN -> ApiErrorCode.FORBIDDEN;
			case NOT_FOUND -> ApiErrorCode.RESOURCE_NOT_FOUND;
			case METHOD_NOT_ALLOWED -> ApiErrorCode.METHOD_NOT_ALLOWED;
			default -> ApiErrorCode.INTERNAL_ERROR;
		};
	}

	private static HttpStatus status(HttpServletRequest request) {
		Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
		if (value instanceof Integer statusCode) {
			HttpStatus status = HttpStatus.resolve(statusCode);
			return status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
		}
		if (value instanceof String statusCode) {
			try {
				HttpStatus status = HttpStatus.resolve(Integer.parseInt(statusCode));
				return status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
			}
			catch (NumberFormatException ex) {
				return HttpStatus.INTERNAL_SERVER_ERROR;
			}
		}
		return HttpStatus.INTERNAL_SERVER_ERROR;
	}

	private static Throwable exception(HttpServletRequest request) {
		Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
		return exception instanceof Throwable throwable ? throwable : null;
	}
}
