package com.seatflow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.seatflow.support.PostgresTestContainerSupport;
import com.seatflow.user.UserMapper;
import com.seatflow.user.UserRecord;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class RefreshTokenMapperIntegrationTests extends PostgresTestContainerSupport {

	@Autowired
	private RefreshTokenMapper refreshTokenMapper;

	@Autowired
	private UserMapper userMapper;

	@Test
	void insertsAndFindsActiveTokenByHash() {
		UserRecord user = insertUser("active");
		RefreshTokenRecord refreshToken = RefreshTokenRecord.forInsert(
				UUID.randomUUID(),
				user.id(),
				"hash-active-%s".formatted(UUID.randomUUID()),
				Instant.now().plusSeconds(3600));

		refreshTokenMapper.insert(refreshToken);

		RefreshTokenRecord found = refreshTokenMapper.findActiveByHash(refreshToken.tokenHash(), Instant.now());
		assertThat(found).isNotNull();
		assertThat(found.id()).isEqualTo(refreshToken.id());
		assertThat(found.userId()).isEqualTo(user.id());
		assertThat(found.tokenHash()).isEqualTo(refreshToken.tokenHash());
		assertThat(found.revokedAt()).isNull();
		assertThat(found.createdAt()).isNotNull();
	}

	@Test
	void revokeTokenMakesItInactive() {
		UserRecord user = insertUser("revoke");
		RefreshTokenRecord refreshToken = insertRefreshToken(user, "revoke", Instant.now().plusSeconds(3600));
		Instant revokedAt = Instant.now();

		int updatedRows = refreshTokenMapper.revoke(refreshToken.id(), revokedAt);

		assertThat(updatedRows).isEqualTo(1);
		assertThat(refreshTokenMapper.findActiveByHash(refreshToken.tokenHash(), Instant.now())).isNull();
		assertThat(refreshTokenMapper.findByHash(refreshToken.tokenHash()).revokedAt()).isNotNull();
	}

	@Test
	void revokeAllUserTokensOnlyRevokesThatUsersTokens() {
		UserRecord user = insertUser("revoke-all");
		UserRecord otherUser = insertUser("revoke-all-other");
		RefreshTokenRecord first = insertRefreshToken(user, "first", Instant.now().plusSeconds(3600));
		RefreshTokenRecord second = insertRefreshToken(user, "second", Instant.now().plusSeconds(3600));
		RefreshTokenRecord other = insertRefreshToken(otherUser, "other", Instant.now().plusSeconds(3600));

		int updatedRows = refreshTokenMapper.revokeAllUserTokens(user.id(), Instant.now());

		assertThat(updatedRows).isEqualTo(2);
		assertThat(refreshTokenMapper.findActiveByHash(first.tokenHash(), Instant.now())).isNull();
		assertThat(refreshTokenMapper.findActiveByHash(second.tokenHash(), Instant.now())).isNull();
		assertThat(refreshTokenMapper.findActiveByHash(other.tokenHash(), Instant.now())).isNotNull();
	}

	@Test
	void deleteExpiredRecordsRemovesOnlyExpiredTokens() {
		UserRecord user = insertUser("delete-expired");
		RefreshTokenRecord expired = insertRefreshToken(user, "expired", Instant.now().minusSeconds(1));
		RefreshTokenRecord active = insertRefreshToken(user, "active", Instant.now().plusSeconds(3600));

		int deletedRows = refreshTokenMapper.deleteExpired(Instant.now(), 100);

		assertThat(deletedRows).isEqualTo(1);
		assertThat(refreshTokenMapper.findByHash(expired.tokenHash())).isNull();
		assertThat(refreshTokenMapper.findByHash(active.tokenHash())).isNotNull();
	}

	private RefreshTokenRecord insertRefreshToken(UserRecord user, String label, Instant expiresAt) {
		RefreshTokenRecord refreshToken = RefreshTokenRecord.forInsert(
				UUID.randomUUID(),
				user.id(),
				"hash-%s-%s".formatted(label, UUID.randomUUID()),
				expiresAt);
		refreshTokenMapper.insert(refreshToken);
		return refreshToken;
	}

	private UserRecord insertUser(String label) {
		UserRecord user = UserRecord.forInsert(
				UUID.randomUUID(),
				"%s-%s@example.com".formatted(label, UUID.randomUUID()),
				"{bcrypt}%s".formatted(UUID.randomUUID()));
		userMapper.insert(user);
		return userMapper.findById(user.id());
	}
}
