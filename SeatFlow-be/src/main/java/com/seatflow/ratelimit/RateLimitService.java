package com.seatflow.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class RateLimitService {

	private static final String KEY_PREFIX = "seatflow:rate-limit:";

	private final RateLimitProperties properties;
	private final RedisRateLimiter redisRateLimiter;
	private final ClientIpResolver clientIpResolver;

	public RateLimitService(
			RateLimitProperties properties,
			RedisRateLimiter redisRateLimiter,
			ClientIpResolver clientIpResolver) {
		this.properties = properties;
		this.redisRateLimiter = redisRateLimiter;
		this.clientIpResolver = clientIpResolver;
	}

	public void checkLogin(HttpServletRequest request, String email) {
		check("login", clientIpResolver.resolve(request) + "|" + normalizeEmail(email), properties.login());
	}

	public void checkRegister(HttpServletRequest request, String email) {
		check("register", clientIpResolver.resolve(request) + "|" + normalizeEmail(email), properties.register());
	}

	public void checkHold(UUID userId) {
		check("holds", userId.toString(), properties.holds());
	}

	public void checkPayment(UUID userId, UUID orderId) {
		check("payments", userId + "|" + orderId, properties.payments());
	}

	private void check(String bucket, String scope, RateLimitProperties.Policy policy) {
		if (!properties.isEnabled()) {
			return;
		}
		RateLimitResult result = redisRateLimiter.consume(key(bucket, scope), policy.limit(), policy.window());
		if (!result.allowed()) {
			throw new RateLimitExceededException(result);
		}
	}

	private static String key(String bucket, String scope) {
		return KEY_PREFIX + bucket + ":" + sha256(scope);
	}

	private static String normalizeEmail(String email) {
		return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
	}

	private static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}
}
