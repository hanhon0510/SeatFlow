package com.seatflow.observability;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.common.CorrelationId;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class StructuredAccessLogFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(StructuredAccessLogFilter.class);

	private final ObjectMapper objectMapper;
	private final String serviceName;
	private final Clock clock;

	@Autowired
	public StructuredAccessLogFilter(
			ObjectMapper objectMapper,
			@Value("${spring.application.name:seatflow-backend}") String serviceName,
			ObjectProvider<Clock> clockProvider) {
		this(objectMapper, serviceName, clockProvider.getIfAvailable(Clock::systemUTC));
	}

	StructuredAccessLogFilter(
			ObjectMapper objectMapper,
			String serviceName,
			Clock clock) {
		this.objectMapper = objectMapper;
		this.serviceName = serviceName;
		this.clock = clock;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String correlationId = CorrelationId.ensure(request);
		boolean clearMdc = !CorrelationId.isFilterManaged(request);
		long startedAt = System.nanoTime();
		Throwable failure = null;
		try {
			filterChain.doFilter(request, response);
		}
		catch (RuntimeException | ServletException | IOException | Error ex) {
			failure = ex;
			throw ex;
		}
		finally {
			logRequest(request, response, correlationId, startedAt, failure);
			if (clearMdc) {
				CorrelationId.clearMdc();
			}
		}
	}

	private void logRequest(
			HttpServletRequest request,
			HttpServletResponse response,
			String correlationId,
			long startedAt,
			Throwable failure) {
		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("timestamp", Instant.now(clock).toString());
		fields.put("level", "INFO");
		fields.put("service", serviceName);
		fields.put("correlationId", correlationId);
		fields.put("userId", userId());
		fields.put("method", request.getMethod());
		fields.put("path", request.getRequestURI());
		fields.put("status", status(response, failure));
		fields.put("durationMs", Math.max(0, (System.nanoTime() - startedAt) / 1_000_000));
		log.info("{}", serialize(fields));
	}

	private String serialize(Map<String, Object> fields) {
		try {
			return objectMapper.writeValueAsString(fields);
		}
		catch (JsonProcessingException ex) {
			return fields.toString();
		}
	}

	private static int status(HttpServletResponse response, Throwable failure) {
		if (failure != null && response.getStatus() < 400) {
			return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
		}
		return response.getStatus();
	}

	private static String userId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null) {
			return null;
		}
		Object principal = authentication.getPrincipal();
		if (principal instanceof Jwt jwt) {
			return jwt.getSubject();
		}
		if (!authentication.isAuthenticated()) {
			return null;
		}
		return authentication.getName();
	}
}
