package com.seatflow.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.common.ApiErrorCode;
import com.seatflow.common.ApiErrorResponseFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			ObjectMapper objectMapper,
			Converter<Jwt, JwtAuthenticationToken> jwtAuthenticationConverter) throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(exception -> exception
						.authenticationEntryPoint(authenticationEntryPoint(objectMapper))
						.accessDeniedHandler(accessDeniedHandler(objectMapper)))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(
								"/api/v1/auth/login",
								"/api/v1/auth/logout",
								"/api/v1/auth/refresh",
								"/api/v1/auth/register",
								"/api/v1/health",
								"/api/v1/health/**",
								"/actuator/health",
								"/actuator/info",
								"/error",
								"/ws",
								"/ws/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/events", "/api/v1/events/**").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/events/*/holds").hasAnyRole("USER", "ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/holds/*").hasAnyRole("USER", "ADMIN")
						.requestMatchers(HttpMethod.DELETE, "/api/v1/holds/*").hasAnyRole("USER", "ADMIN")
						.requestMatchers(HttpMethod.POST, "/api/v1/reservations").hasAnyRole("USER", "ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/reservations/*").hasAnyRole("USER", "ADMIN")
						.requestMatchers(HttpMethod.POST, "/api/v1/orders").hasAnyRole("USER", "ADMIN")
						.requestMatchers(HttpMethod.POST, "/api/v1/orders/*/payments").hasAnyRole("USER", "ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/orders/*").hasAnyRole("USER", "ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/users/me/orders").hasAnyRole("USER", "ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/tickets/*").hasAnyRole("USER", "ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/users/me/tickets").hasAnyRole("USER", "ADMIN")
						.requestMatchers("/api/v1/admin", "/api/v1/admin/**").hasRole("ADMIN")
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2
						.authenticationEntryPoint(authenticationEntryPoint(objectMapper))
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public Converter<Jwt, JwtAuthenticationToken> jwtAuthenticationConverter() {
		return jwt -> new JwtAuthenticationToken(jwt, authorities(jwt), jwt.getSubject());
	}

	private static Collection<GrantedAuthority> authorities(Jwt jwt) {
		List<GrantedAuthority> authorities = new ArrayList<>();
		String role = jwt.getClaimAsString("role");
		if (StringUtils.hasText(role)) {
			authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
		}
		return authorities;
	}

	private static AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
		return new AuthenticationEntryPoint() {
			@Override
			public void commence(
					HttpServletRequest request,
					HttpServletResponse response,
					AuthenticationException authException) throws IOException, ServletException {
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
				ApiErrorResponseFactory.write(
						request,
						response,
						objectMapper,
						ApiErrorCode.UNAUTHORIZED,
						authException);
			}
		};
	}

	private static AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
		return new AccessDeniedHandler() {
			@Override
			public void handle(
					HttpServletRequest request,
					HttpServletResponse response,
					AccessDeniedException accessDeniedException) throws IOException, ServletException {
				response.setStatus(HttpServletResponse.SC_FORBIDDEN);
				response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
				ApiErrorResponseFactory.write(
						request,
						response,
						objectMapper,
						ApiErrorCode.FORBIDDEN,
						accessDeniedException);
			}
		};
	}

}
