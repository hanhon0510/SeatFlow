package com.seatflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.seatflow.support.JwtTestSupport;
import com.seatflow.support.PostgresTestContainerSupport;
import com.seatflow.user.UserMapper;
import com.seatflow.user.UserRecord;

@SpringBootTest(properties = "seatflow.jwt.secret=" + JwtTestSupport.DEFAULT_SECRET)
@Testcontainers(disabledWithoutDocker = true)
class RefreshTokenTransactionIntegrationTests extends PostgresTestContainerSupport {

	private static final String PASSWORD = "StrongPassword123!";
	private static final String RAW_TOKEN = "rollback-refresh-token";
	private static final String TOKEN_HASH = "rollback-refresh-token-hash";

	@Autowired
	private LoginService loginService;

	@Autowired
	private RefreshTokenService refreshTokenService;

	@Autowired
	private RefreshTokenMapper refreshTokenMapper;

	@Autowired
	private UserMapper userMapper;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@MockitoBean
	private RefreshTokenGenerator refreshTokenGenerator;

	@Test
	void rotationRollsBackRevocationWhenNewTokenInsertFails() {
		when(refreshTokenGenerator.generateToken()).thenReturn(RAW_TOKEN, RAW_TOKEN);
		when(refreshTokenGenerator.hashToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
		UserRecord user = insertUser();

		AuthSession loginSession = loginService.login(new LoginRequest(user.email(), PASSWORD));
		assertThat(loginSession.refreshToken().token()).isEqualTo(RAW_TOKEN);
		assertThat(refreshTokenMapper.findActiveByHash(TOKEN_HASH, Instant.now())).isNotNull();

		assertThatThrownBy(() -> refreshTokenService.refresh(RAW_TOKEN))
				.isInstanceOf(RuntimeException.class);

		assertThat(refreshTokenMapper.findActiveByHash(TOKEN_HASH, Instant.now())).isNotNull();
	}

	private UserRecord insertUser() {
		UserRecord user = UserRecord.forInsert(
				UUID.randomUUID(),
				"rollback-%s@example.com".formatted(UUID.randomUUID()),
				passwordEncoder.encode(PASSWORD));
		userMapper.insert(user);
		return userMapper.findById(user.id());
	}
}
