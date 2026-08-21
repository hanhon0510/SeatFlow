package com.seatflow.common;

import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

public final class CorrelationId {

	public static final String HEADER_NAME = "X-Correlation-ID";
	public static final String ATTRIBUTE_NAME = CorrelationId.class.getName() + ".value";
	public static final String FILTER_MANAGED_ATTRIBUTE = CorrelationId.class.getName() + ".filterManaged";
	public static final String MDC_KEY = "correlationId";

	private CorrelationId() {
	}

	public static String ensure(HttpServletRequest request) {
		Object existing = request.getAttribute(ATTRIBUTE_NAME);
		if (existing instanceof String value && StringUtils.hasText(value)) {
			MDC.put(MDC_KEY, value);
			return value;
		}

		String correlationId = resolveFromHeader(request);
		request.setAttribute(ATTRIBUTE_NAME, correlationId);
		MDC.put(MDC_KEY, correlationId);
		return correlationId;
	}

	public static boolean isFilterManaged(HttpServletRequest request) {
		return Boolean.TRUE.equals(request.getAttribute(FILTER_MANAGED_ATTRIBUTE));
	}

	public static void clearMdc() {
		MDC.remove(MDC_KEY);
	}

	private static String resolveFromHeader(HttpServletRequest request) {
		String header = request.getHeader(HEADER_NAME);
		if (StringUtils.hasText(header)) {
			try {
				return UUID.fromString(header.trim()).toString();
			}
			catch (IllegalArgumentException ex) {
				return UUID.randomUUID().toString();
			}
		}
		return UUID.randomUUID().toString();
	}
}
