package com.seatflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import com.seatflow.support.JwtTestSupport;
import com.seatflow.user.UserMapper;
import com.seatflow.user.UserRecord;
import com.seatflow.user.UserRole;
import com.seatflow.user.UserStatus;

class RefreshTokenServiceTests {

	private static final Instant NOW = Instant.parse("2099-01-01T00:00:00Z");
	private static final long REFRESH_EXPIRES_IN_SECONDS = 1_209_600;

	@Test
	void refreshRotatesTokenAndReturnsNewAccessToken() {
		UserRecord user = user(UserStatus.ACTIVE);
		InMemoryRefreshTokenMapper refreshTokenMapper = new InMemoryRefreshTokenMapper();
		RefreshTokenGenerator generator = new DeterministicRefreshTokenGenerator("new-refresh-token");
		refreshTokenMapper.insert(RefreshTokenRecord.forInsert(
				UUID.randomUUID(),
				user.id(),
				generator.hashToken("old-refresh-token"),
				NOW.plusSeconds(REFRESH_EXPIRES_IN_SECONDS)));
		RefreshTokenService service = refreshTokenService(refreshTokenMapper, new SingleUserMapper(user), generator);

		AuthSession session = service.refresh("old-refresh-token");

		assertThat(session.accessToken().accessToken()).isNotBlank();
		assertThat(session.refreshToken().token()).isEqualTo("new-refresh-token");
		assertThat(session.refreshToken().expiresAt()).isEqualTo(NOW.plusSeconds(REFRESH_EXPIRES_IN_SECONDS));
		assertThat(refreshTokenMapper.findActiveByHash(generator.hashToken("old-refresh-token"), NOW)).isNull();
		assertThat(refreshTokenMapper.findActiveByHash(generator.hashToken("new-refresh-token"), NOW)).isNotNull();
	}

	@Test
	void revokedTokenReuseIsRejectedAndRevokesUserTokens() {
		UserRecord user = user(UserStatus.ACTIVE);
		InMemoryRefreshTokenMapper refreshTokenMapper = new InMemoryRefreshTokenMapper();
		RefreshTokenGenerator generator = new DeterministicRefreshTokenGenerator("unused");
		refreshTokenMapper.insert(new RefreshTokenRecord(
				UUID.randomUUID(),
				user.id(),
				generator.hashToken("reused-refresh-token"),
				NOW.plusSeconds(REFRESH_EXPIRES_IN_SECONDS),
				NOW.minusSeconds(1),
				NOW.minusSeconds(60)));
		refreshTokenMapper.insert(RefreshTokenRecord.forInsert(
				UUID.randomUUID(),
				user.id(),
				generator.hashToken("other-refresh-token"),
				NOW.plusSeconds(REFRESH_EXPIRES_IN_SECONDS)));
		RefreshTokenService service = refreshTokenService(refreshTokenMapper, new SingleUserMapper(user), generator);

		assertThatThrownBy(() -> service.refresh("reused-refresh-token"))
				.isInstanceOf(InvalidRefreshTokenException.class);
		assertThat(refreshTokenMapper.findActiveByHash(generator.hashToken("other-refresh-token"), NOW)).isNull();
	}

	@Test
	void expiredTokenIsRejected() {
		UserRecord user = user(UserStatus.ACTIVE);
		InMemoryRefreshTokenMapper refreshTokenMapper = new InMemoryRefreshTokenMapper();
		RefreshTokenGenerator generator = new DeterministicRefreshTokenGenerator("unused");
		refreshTokenMapper.insert(RefreshTokenRecord.forInsert(
				UUID.randomUUID(),
				user.id(),
				generator.hashToken("expired-refresh-token"),
				NOW.minusSeconds(1)));
		RefreshTokenService service = refreshTokenService(refreshTokenMapper, new SingleUserMapper(user), generator);

		assertThatThrownBy(() -> service.refresh("expired-refresh-token"))
				.isInstanceOf(InvalidRefreshTokenException.class);
	}

	@Test
	void logoutRevokesToken() {
		UserRecord user = user(UserStatus.ACTIVE);
		InMemoryRefreshTokenMapper refreshTokenMapper = new InMemoryRefreshTokenMapper();
		RefreshTokenGenerator generator = new DeterministicRefreshTokenGenerator("unused");
		refreshTokenMapper.insert(RefreshTokenRecord.forInsert(
				UUID.randomUUID(),
				user.id(),
				generator.hashToken("logout-refresh-token"),
				NOW.plusSeconds(REFRESH_EXPIRES_IN_SECONDS)));
		RefreshTokenService service = refreshTokenService(refreshTokenMapper, new SingleUserMapper(user), generator);

		service.logout("logout-refresh-token");

		assertThat(refreshTokenMapper.findActiveByHash(generator.hashToken("logout-refresh-token"), NOW)).isNull();
	}

	@Test
	void deleteExpiredRecordsRemovesExpiredTokensOnly() {
		UserRecord user = user(UserStatus.ACTIVE);
		InMemoryRefreshTokenMapper refreshTokenMapper = new InMemoryRefreshTokenMapper();
		RefreshTokenGenerator generator = new DeterministicRefreshTokenGenerator("unused");
		refreshTokenMapper.insert(RefreshTokenRecord.forInsert(
				UUID.randomUUID(),
				user.id(),
				generator.hashToken("expired-refresh-token"),
				NOW.minusSeconds(1)));
		refreshTokenMapper.insert(RefreshTokenRecord.forInsert(
				UUID.randomUUID(),
				user.id(),
				generator.hashToken("active-refresh-token"),
				NOW.plusSeconds(REFRESH_EXPIRES_IN_SECONDS)));
		RefreshTokenService service = refreshTokenService(refreshTokenMapper, new SingleUserMapper(user), generator);

		assertThat(service.deleteExpiredTokens()).isEqualTo(1);
		assertThat(refreshTokenMapper.findByHash(generator.hashToken("expired-refresh-token"))).isNull();
		assertThat(refreshTokenMapper.findActiveByHash(generator.hashToken("active-refresh-token"), NOW)).isNotNull();
	}

	private static RefreshTokenService refreshTokenService(
			RefreshTokenMapper refreshTokenMapper,
			UserMapper userMapper,
			RefreshTokenGenerator refreshTokenGenerator) {
		return new RefreshTokenService(
				refreshTokenMapper,
				userMapper,
				JwtTestSupport.tokenService(Clock.fixed(NOW, ZoneOffset.UTC), 900),
				refreshTokenGenerator,
				new RefreshTokenProperties("seatflow_refresh_token", REFRESH_EXPIRES_IN_SECONDS, false, "Strict"),
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static UserRecord user(UserStatus status) {
		return new UserRecord(
				UUID.randomUUID(),
				"user@example.com",
				"password-hash",
				UserRole.USER,
				status,
				NOW,
				NOW);
	}

	private static final class DeterministicRefreshTokenGenerator implements RefreshTokenGenerator {

		private final ArrayDeque<String> tokens;

		private DeterministicRefreshTokenGenerator(String... tokens) {
			this.tokens = new ArrayDeque<>(List.of(tokens));
		}

		@Override
		public String generateToken() {
			return tokens.removeFirst();
		}

		@Override
		public String hashToken(String token) {
			return "hash:" + token;
		}
	}

	private static final class InMemoryRefreshTokenMapper implements RefreshTokenMapper {

		private final Map<String, RefreshTokenRecord> tokensByHash = new HashMap<>();

		@Override
		public void insert(RefreshTokenRecord refreshToken) {
			if (tokensByHash.containsKey(refreshToken.tokenHash())) {
				throw new DuplicateKeyException("duplicate refresh token hash");
			}
			tokensByHash.put(refreshToken.tokenHash(), refreshToken);
		}

		@Override
		public RefreshTokenRecord findActiveByHash(String tokenHash, Instant now) {
			RefreshTokenRecord refreshToken = tokensByHash.get(tokenHash);
			if (refreshToken == null || refreshToken.revokedAt() != null || !refreshToken.expiresAt().isAfter(now)) {
				return null;
			}
			return refreshToken;
		}

		@Override
		public RefreshTokenRecord findByHash(String tokenHash) {
			return tokensByHash.get(tokenHash);
		}

		@Override
		public int revoke(UUID id, Instant revokedAt) {
			for (RefreshTokenRecord refreshToken : tokensByHash.values()) {
				if (refreshToken.id().equals(id) && refreshToken.revokedAt() == null) {
					tokensByHash.put(refreshToken.tokenHash(), revoked(refreshToken, revokedAt));
					return 1;
				}
			}
			return 0;
		}

		@Override
		public int revokeAllUserTokens(UUID userId, Instant revokedAt) {
			int revoked = 0;
			for (RefreshTokenRecord refreshToken : List.copyOf(tokensByHash.values())) {
				if (refreshToken.userId().equals(userId) && refreshToken.revokedAt() == null) {
					tokensByHash.put(refreshToken.tokenHash(), revoked(refreshToken, revokedAt));
					revoked++;
				}
			}
			return revoked;
		}

		@Override
		public int deleteExpired(Instant now) {
			int before = tokensByHash.size();
			tokensByHash.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
			return before - tokensByHash.size();
		}

		private static RefreshTokenRecord revoked(RefreshTokenRecord refreshToken, Instant revokedAt) {
			return new RefreshTokenRecord(
					refreshToken.id(),
					refreshToken.userId(),
					refreshToken.tokenHash(),
					refreshToken.expiresAt(),
					revokedAt,
					refreshToken.createdAt());
		}
	}

	private static final class SingleUserMapper implements UserMapper {

		private final UserRecord user;

		private SingleUserMapper(UserRecord user) {
			this.user = user;
		}

		@Override
		public void insert(UserRecord user) {
		}

		@Override
		public UserRecord findById(UUID id) {
			return user != null && user.id().equals(id) ? user : null;
		}

		@Override
		public UserRecord findByNormalizedEmail(String normalizedEmail) {
			return user != null && user.email().equals(normalizedEmail) ? user : null;
		}

		@Override
		public int updateStatus(UUID id, UserStatus status) {
			return 0;
		}
	}
}
