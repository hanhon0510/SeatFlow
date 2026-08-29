package com.seatflow.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.seatflow.security.JwtTokenService;
import com.seatflow.user.UserMapper;
import com.seatflow.user.UserRecord;
import com.seatflow.user.UserStatus;

@Service
public class RefreshTokenService {

	private final RefreshTokenMapper refreshTokenMapper;
	private final UserMapper userMapper;
	private final JwtTokenService jwtTokenService;
	private final RefreshTokenGenerator refreshTokenGenerator;
	private final RefreshTokenProperties refreshTokenProperties;
	private final Clock clock;

	public RefreshTokenService(
			RefreshTokenMapper refreshTokenMapper,
			UserMapper userMapper,
			JwtTokenService jwtTokenService,
			RefreshTokenGenerator refreshTokenGenerator,
			RefreshTokenProperties refreshTokenProperties,
			Clock clock) {
		this.refreshTokenMapper = refreshTokenMapper;
		this.userMapper = userMapper;
		this.jwtTokenService = jwtTokenService;
		this.refreshTokenGenerator = refreshTokenGenerator;
		this.refreshTokenProperties = refreshTokenProperties;
		this.clock = clock;
		if (refreshTokenProperties.expiresInSeconds() <= 0) {
			throw new IllegalStateException("Refresh token expiration must be positive");
		}
	}

	public AuthSession issueSession(UserRecord user) {
		return new AuthSession(jwtTokenService.issueAccessToken(user), issueRefreshToken(user));
	}

	@Transactional
	public AuthSession refresh(String rawToken) {
		String tokenHash = requireTokenHash(rawToken);
		Instant now = clock.instant();
		RefreshTokenRecord existing = refreshTokenMapper.findActiveByHash(tokenHash, now);
		if (existing == null) {
			handleInactiveRefreshToken(tokenHash, now);
			throw new InvalidRefreshTokenException();
		}

		UserRecord user = userMapper.findById(existing.userId());
		if (user == null || user.status() == UserStatus.DISABLED) {
			refreshTokenMapper.revoke(existing.id(), now);
			throw new InvalidRefreshTokenException();
		}

		int revokedRows = refreshTokenMapper.revoke(existing.id(), now);
		if (revokedRows != 1) {
			throw new InvalidRefreshTokenException();
		}

		return issueSession(user);
	}

	@Transactional
	public void logout(String rawToken) {
		if (!StringUtils.hasText(rawToken)) {
			return;
		}

		RefreshTokenRecord refreshToken = refreshTokenMapper.findByHash(refreshTokenGenerator.hashToken(rawToken));
		if (refreshToken != null && refreshToken.revokedAt() == null) {
			refreshTokenMapper.revoke(refreshToken.id(), clock.instant());
		}
	}

	public int deleteExpiredTokens(int limit) {
		return refreshTokenMapper.deleteExpired(clock.instant(), limit);
	}

	private IssuedRefreshToken issueRefreshToken(UserRecord user) {
		String rawToken = refreshTokenGenerator.generateToken();
		Instant expiresAt = clock.instant().plusSeconds(refreshTokenProperties.expiresInSeconds());
		refreshTokenMapper.insert(RefreshTokenRecord.forInsert(
				UUID.randomUUID(),
				user.id(),
				refreshTokenGenerator.hashToken(rawToken),
				expiresAt));
		return new IssuedRefreshToken(rawToken, expiresAt);
	}

	private void handleInactiveRefreshToken(String tokenHash, Instant revokedAt) {
		RefreshTokenRecord knownToken = refreshTokenMapper.findByHash(tokenHash);
		if (knownToken != null && knownToken.revokedAt() != null) {
			refreshTokenMapper.revokeAllUserTokens(knownToken.userId(), revokedAt);
		}
	}

	private String requireTokenHash(String rawToken) {
		if (!StringUtils.hasText(rawToken)) {
			throw new InvalidRefreshTokenException();
		}
		return refreshTokenGenerator.hashToken(rawToken);
	}
}
