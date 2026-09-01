package com.seatflow.security;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
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
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.common.ApiErrorCode;
import com.seatflow.common.ApiErrorResponseFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({ WebOriginProperties.class, ActuatorProperties.class })
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			ObjectMapper objectMapper,
			Converter<Jwt, JwtAuthenticationToken> jwtAuthenticationConverter,
			CorsConfigurationSource corsConfigurationSource,
			ActuatorProperties actuatorProperties) throws Exception {
		return http
				.cors(cors -> cors.configurationSource(corsConfigurationSource))
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
						.requestMatchers(HttpMethod.POST, "/api/v1/events/*/holds").hasRole("USER")
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
						.requestMatchers("/actuator/prometheus").access(metricsAuthorization(actuatorProperties))
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
	public CorsConfigurationSource corsConfigurationSource(WebOriginProperties webOriginProperties) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(webOriginProperties.allowedOrigins());
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("*"));
		// The refresh token travels in an HttpOnly cookie, so the browser needs credentials
		// allowed. That is also why the origins above must be exact and never a wildcard.
		configuration.setAllowCredentials(true);
		configuration.setMaxAge(Duration.ofHours(1));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", configuration);
		return source;
	}

	/**
	 * The Prometheus scrape exposes URI templates, request volumes, latency distributions and
	 * every business counter - holds, conflicts, payments, tickets. It requires ADMIN unless a
	 * deployment explicitly declares the management surface unroutable from outside.
	 */
	private static AuthorizationManager<RequestAuthorizationContext> metricsAuthorization(
			ActuatorProperties actuatorProperties) {
		return actuatorProperties.metricsPublic()
				? (authentication, context) -> new AuthorizationDecision(true)
				: AuthorityAuthorizationManager.hasRole("ADMIN");
	}

	/**
	 * Deliberately a named class rather than a lambda. Spring MVC adds every {@code Converter}
	 * bean to its FormatterRegistry, and a lambda erases its type arguments, so registration
	 * fails with "Unable to determine source type &lt;S&gt; and target type &lt;T&gt;" and takes
	 * the whole application context down with it.
	 */
	@Bean
	public Converter<Jwt, JwtAuthenticationToken> jwtAuthenticationConverter() {
		return new JwtAuthenticationTokenConverter();
	}

	static final class JwtAuthenticationTokenConverter implements Converter<Jwt, JwtAuthenticationToken> {

		@Override
		public JwtAuthenticationToken convert(Jwt jwt) {
			return new JwtAuthenticationToken(jwt, authorities(jwt), jwt.getSubject());
		}
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
