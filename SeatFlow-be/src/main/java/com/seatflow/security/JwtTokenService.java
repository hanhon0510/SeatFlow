package com.seatflow.security;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.seatflow.auth.LoginResponse;
import com.seatflow.user.UserRecord;

@Service
public class JwtTokenService {

	private static final String TOKEN_TYPE = "Bearer";

	private final JwtEncoder jwtEncoder;
	private final JwtProperties jwtProperties;
	private final Clock clock;

	public JwtTokenService(JwtEncoder jwtEncoder, JwtProperties jwtProperties, Clock clock) {
		this.jwtEncoder = jwtEncoder;
		this.jwtProperties = jwtProperties;
		this.clock = clock;
		if (jwtProperties.expiresInSeconds() <= 0) {
			throw new IllegalStateException("JWT expiration must be positive");
		}
	}

	public LoginResponse issueAccessToken(UserRecord user) {
		Instant issuedAt = clock.instant();
		Instant expiresAt = issuedAt.plusSeconds(jwtProperties.expiresInSeconds());
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(jwtProperties.issuer())
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.subject(user.id().toString())
				.claim("role", user.role().name())
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new LoginResponse(token, TOKEN_TYPE, expiresAt);
	}

}
