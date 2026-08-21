package com.seatflow.common;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String correlationId = CorrelationId.ensure(request);
		request.setAttribute(CorrelationId.FILTER_MANAGED_ATTRIBUTE, Boolean.TRUE);
		response.setHeader(CorrelationId.HEADER_NAME, correlationId);
		try {
			filterChain.doFilter(request, response);
		}
		finally {
			CorrelationId.clearMdc();
		}
	}
}
