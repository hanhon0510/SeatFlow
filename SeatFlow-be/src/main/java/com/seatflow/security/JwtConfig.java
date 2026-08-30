package com.seatflow.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.util.StringUtils;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

	private static final int MIN_HS256_SECRET_BYTES = 32;

	/**
	 * A blank secret used to fall back to a freshly generated key. That looks harmless but is
	 * silent and security-critical: every instance would sign with a different key, so tokens
	 * issued by one are rejected by the next, and every restart invalidates all sessions. Fail
	 * to start instead - a missing signing key is a deployment error, not something to paper over.
	 */
	@Bean
	public SecretKey jwtSecretKey(JwtProperties jwtProperties) {
		if (!StringUtils.hasText(jwtProperties.secret())) {
			throw new IllegalStateException("SEATFLOW_JWT_SECRET must be set");
		}
		byte[] secretBytes = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
		if (secretBytes.length < MIN_HS256_SECRET_BYTES) {
			throw new IllegalStateException("JWT secret must be at least 32 bytes for HS256");
		}
		return new SecretKeySpec(secretBytes, "HmacSHA256");
	}

	@Bean
	public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
	}

	@Bean
	public JwtDecoder jwtDecoder(SecretKey jwtSecretKey, JwtProperties jwtProperties) {
		NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder
				.withSecretKey(jwtSecretKey)
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithIssuer(jwtProperties.issuer());
		jwtDecoder.setJwtValidator(validator);
		return jwtDecoder;
	}

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

}
