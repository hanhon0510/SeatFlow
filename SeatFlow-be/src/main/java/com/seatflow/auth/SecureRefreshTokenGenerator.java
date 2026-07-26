package com.seatflow.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

@Component
public class SecureRefreshTokenGenerator implements RefreshTokenGenerator {

	private static final int TOKEN_BYTES = 32;

	private final SecureRandom secureRandom;

	public SecureRefreshTokenGenerator(SecureRandom secureRandom) {
		this.secureRandom = secureRandom;
	}

	@Override
	public String generateToken() {
		byte[] token = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(token);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
	}

	@Override
	public String hashToken(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}
}
