package com.seatflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import com.seatflow.auth.LoginResponse;
import com.seatflow.support.JwtTestSupport;
import com.seatflow.user.UserRecord;
import com.seatflow.user.UserRole;
import com.seatflow.user.UserStatus;

class JwtTokenServiceTests {

	private static final Instant NOW = Instant.parse("2099-01-01T00:00:00Z");

	@Test
	void validJwtIncludesUserIdAndRole() {
		JwtProperties jwtProperties = JwtTestSupport.properties(900);
		SecretKey secretKey = JwtTestSupport.secretKey(jwtProperties.secret());
		JwtTokenService jwtTokenService = new JwtTokenService(
				JwtTestSupport.encoder(secretKey),
				jwtProperties,
				Clock.fixed(NOW, ZoneOffset.UTC));
		UserRecord user = user(UserRole.ADMIN);

		LoginResponse response = jwtTokenService.issueAccessToken(user);

		Jwt jwt = JwtTestSupport.decoder(secretKey, jwtProperties).decode(response.accessToken());
		assertThat(jwt.getSubject()).isEqualTo(user.id().toString());
		assertThat(jwt.getClaimAsString("iss")).isEqualTo(JwtTestSupport.ISSUER);
		assertThat(jwt.getClaimAsString("role")).isEqualTo(UserRole.ADMIN.name());
		assertThat(response.expiresAt()).isEqualTo(NOW.plusSeconds(900));
	}

	@Test
	void expiredJwtIsRejected() {
		JwtProperties jwtProperties = JwtTestSupport.properties(1);
		SecretKey secretKey = JwtTestSupport.secretKey(jwtProperties.secret());
		JwtTokenService jwtTokenService = new JwtTokenService(
				JwtTestSupport.encoder(secretKey),
				jwtProperties,
				Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC));
		String token = jwtTokenService.issueAccessToken(user(UserRole.USER)).accessToken();

		assertThatThrownBy(() -> JwtTestSupport.decoder(secretKey, jwtProperties).decode(token))
				.isInstanceOf(JwtException.class);
	}

	@Test
	void invalidSignatureIsRejected() {
		JwtProperties jwtProperties = JwtTestSupport.properties(900);
		SecretKey signingKey = JwtTestSupport.secretKey(jwtProperties.secret());
		SecretKey verificationKey = JwtTestSupport.secretKey(JwtTestSupport.OTHER_SECRET);
		JwtTokenService jwtTokenService = new JwtTokenService(
				JwtTestSupport.encoder(signingKey),
				jwtProperties,
				Clock.fixed(NOW, ZoneOffset.UTC));
		String token = jwtTokenService.issueAccessToken(user(UserRole.USER)).accessToken();

		assertThatThrownBy(() -> JwtTestSupport.decoder(verificationKey, jwtProperties).decode(token))
				.isInstanceOf(JwtException.class);
	}

	private static UserRecord user(UserRole role) {
		return new UserRecord(
				UUID.randomUUID(),
				"user@example.com",
				"password-hash",
				role,
				UserStatus.ACTIVE,
				NOW,
				NOW);
	}

}
