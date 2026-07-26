package com.seatflow.support;

import java.time.Clock;

import javax.crypto.SecretKey;

import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import com.seatflow.security.JwtConfig;
import com.seatflow.security.JwtProperties;
import com.seatflow.security.JwtTokenService;

public final class JwtTestSupport {

	public static final String DEFAULT_SECRET = "01234567890123456789012345678901";
	public static final String OTHER_SECRET = "abcdefghijklmnopqrstuvwxyz123456";
	public static final String ISSUER = "seatflow-test";

	private JwtTestSupport() {
	}

	public static JwtProperties properties(long expiresInSeconds) {
		return new JwtProperties(DEFAULT_SECRET, ISSUER, expiresInSeconds);
	}

	public static SecretKey secretKey(String secret) {
		return new JwtConfig().jwtSecretKey(new JwtProperties(secret, ISSUER, 900));
	}

	public static JwtEncoder encoder(SecretKey secretKey) {
		return new JwtConfig().jwtEncoder(secretKey);
	}

	public static JwtDecoder decoder(SecretKey secretKey, JwtProperties jwtProperties) {
		return new JwtConfig().jwtDecoder(secretKey, jwtProperties);
	}

	public static JwtTokenService tokenService(Clock clock, long expiresInSeconds) {
		JwtProperties jwtProperties = properties(expiresInSeconds);
		SecretKey secretKey = secretKey(jwtProperties.secret());
		return new JwtTokenService(encoder(secretKey), jwtProperties, clock);
	}

}
