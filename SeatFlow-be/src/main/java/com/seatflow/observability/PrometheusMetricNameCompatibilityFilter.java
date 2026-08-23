package com.seatflow.observability;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class PrometheusMetricNameCompatibilityFilter extends OncePerRequestFilter {

	private static final String PROMETHEUS_PATH = "/actuator/prometheus";
	private static final Map<String, String> RENAMES = Map.of(
			"seat_hold_total", "seat_hold_created_total",
			"reservation_total", "reservation_created_total");

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !PROMETHEUS_PATH.equals(request.getRequestURI());
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
		filterChain.doFilter(request, wrappedResponse);

		byte[] body = wrappedResponse.getContentAsByteArray();
		if (body.length == 0 || response.getStatus() >= 400) {
			wrappedResponse.copyBodyToResponse();
			return;
		}

		Charset charset = charset(response.getCharacterEncoding());
		byte[] rewrittenBody = rewrite(new String(body, charset)).getBytes(charset);
		response.setContentLength(rewrittenBody.length);
		response.getOutputStream().write(rewrittenBody);
	}

	String rewrite(String scrape) {
		String rewritten = scrape;
		for (Map.Entry<String, String> rename : RENAMES.entrySet()) {
			rewritten = rewritten.replace(rename.getKey(), rename.getValue());
		}
		return rewritten;
	}

	private static Charset charset(String characterEncoding) {
		if (characterEncoding == null || characterEncoding.isBlank()) {
			return StandardCharsets.UTF_8;
		}
		return Charset.forName(characterEncoding);
	}
}
