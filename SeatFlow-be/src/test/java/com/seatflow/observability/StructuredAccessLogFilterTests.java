package com.seatflow.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.common.CorrelationId;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(OutputCaptureExtension.class)
class StructuredAccessLogFilterTests {

	private static final Instant NOW = Instant.parse("2026-08-23T12:00:00Z");
	private static final String CORRELATION_ID = "11111111-1111-4111-8111-111111111111";
	private static final UUID USER_ID = UUID.fromString("5b3c09fd-8194-4719-82d7-035349d07f18");

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
		CorrelationId.clearMdc();
	}

	@Test
	void structuredAccessLogContainsRequiredFields(CapturedOutput output) throws Exception {
		StructuredAccessLogFilter filter = filter();
		MockHttpServletRequest request = request("GET", "/api/v1/events");
		MockHttpServletResponse response = new MockHttpServletResponse();
		SecurityContextHolder.getContext().setAuthentication(authentication());
		FilterChain chain = (servletRequest, servletResponse) ->
				((HttpServletResponse) servletResponse).setStatus(HttpServletResponse.SC_OK);

		filter.doFilter(request, response, chain);

		assertThat(output.getOut())
				.contains("\"timestamp\":\"2026-08-23T12:00:00Z\"")
				.contains("\"level\":\"INFO\"")
				.contains("\"service\":\"seatflow-backend\"")
				.contains("\"correlationId\":\"%s\"".formatted(CORRELATION_ID))
				.contains("\"userId\":\"%s\"".formatted(USER_ID))
				.contains("\"method\":\"GET\"")
				.contains("\"path\":\"/api/v1/events\"")
				.contains("\"status\":200")
				.contains("\"durationMs\":");
	}

	@Test
	void structuredAccessLogDoesNotIncludeSensitiveHeadersQueryOrBody(CapturedOutput output) throws Exception {
		StructuredAccessLogFilter filter = filter();
		MockHttpServletRequest request = request("POST", "/api/v1/orders/123/payments");
		request.setQueryString("password=hidden&token=tok_sensitive_payment");
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer super-secret-jwt");
		request.addHeader(HttpHeaders.COOKIE, "refreshToken=super-secret-refresh-token");
		request.setContent("""
				{"token":"tok_sensitive_payment","password":"Secret123!"}
				""".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		MockHttpServletResponse response = new MockHttpServletResponse();
		SecurityContextHolder.getContext().setAuthentication(authentication());
		FilterChain chain = (servletRequest, servletResponse) ->
				((HttpServletResponse) servletResponse).setStatus(HttpServletResponse.SC_CREATED);

		filter.doFilter(request, response, chain);

		assertThat(output.getOut())
				.contains("\"path\":\"/api/v1/orders/123/payments\"")
				.contains("\"status\":201")
				.doesNotContain("super-secret-jwt")
				.doesNotContain("super-secret-refresh-token")
				.doesNotContain("tok_sensitive_payment")
				.doesNotContain("Secret123!")
				.doesNotContain("Authorization")
				.doesNotContain("Cookie");
	}

	private static StructuredAccessLogFilter filter() {
		return new StructuredAccessLogFilter(
				new ObjectMapper(),
				"seatflow-backend",
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static MockHttpServletRequest request(String method, String path) {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		request.addHeader(CorrelationId.HEADER_NAME, CORRELATION_ID);
		return request;
	}

	private static JwtAuthenticationToken authentication() {
		Jwt jwt = Jwt.withTokenValue("jwt-principal-secret")
				.header("alg", "HS256")
				.subject(USER_ID.toString())
				.claim("role", "USER")
				.build();
		return new JwtAuthenticationToken(jwt);
	}
}
