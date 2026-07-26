package com.seatflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;

import com.seatflow.support.JwtTestSupport;
import com.seatflow.user.UserMapper;
import com.seatflow.user.UserRecord;
import com.seatflow.user.UserRole;
import com.seatflow.user.UserStatus;

class LoginServiceTests {

	private static final Instant NOW = Instant.parse("2099-01-01T00:00:00Z");
	private static final String PASSWORD = "StrongPassword123!";

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	@Test
	void successfulLoginReturnsJwt() {
		UserRecord user = user("user@example.com", PASSWORD, UserStatus.ACTIVE);
		LoginService loginService = loginService(user);

		AuthSession session = loginService.login(new LoginRequest(" User@Example.COM ", PASSWORD));
		LoginResponse response = session.accessToken();

		assertThat(response.accessToken()).isNotBlank();
		assertThat(response.tokenType()).isEqualTo("Bearer");
		assertThat(response.expiresAt()).isEqualTo(NOW.plusSeconds(900));
		assertThat(session.refreshToken().token()).isEqualTo("refresh-token");

		Jwt jwt = JwtTestSupport.decoder(
				JwtTestSupport.secretKey(JwtTestSupport.DEFAULT_SECRET),
				JwtTestSupport.properties(900))
				.decode(response.accessToken());
		assertThat(jwt.getSubject()).isEqualTo(user.id().toString());
		assertThat(jwt.getClaimAsString("role")).isEqualTo(UserRole.USER.name());
	}

	@Test
	void wrongPasswordFailsWithGenericAuthenticationError() {
		UserRecord user = user("user@example.com", PASSWORD, UserStatus.ACTIVE);
		LoginService loginService = loginService(user);

		assertThatThrownBy(() -> loginService.login(new LoginRequest("user@example.com", "WrongPassword123!")))
				.isInstanceOf(AuthenticationFailedException.class)
				.hasMessage("Invalid email or password")
				.hasMessageNotContaining("WrongPassword123!");
	}

	@Test
	void unknownEmailFailsWithGenericAuthenticationError() {
		LoginService loginService = loginService(null);

		assertThatThrownBy(() -> loginService.login(new LoginRequest("missing@example.com", PASSWORD)))
				.isInstanceOf(AuthenticationFailedException.class)
				.hasMessage("Invalid email or password");
	}

	@Test
	void disabledUserCannotLogIn() {
		UserRecord user = user("disabled@example.com", PASSWORD, UserStatus.DISABLED);
		LoginService loginService = loginService(user);

		assertThatThrownBy(() -> loginService.login(new LoginRequest("disabled@example.com", PASSWORD)))
				.isInstanceOf(AuthenticationFailedException.class)
				.hasMessage("Invalid email or password");
	}

	private LoginService loginService(UserRecord user) {
		RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
		if (user != null) {
			LoginResponse accessToken = JwtTestSupport.tokenService(Clock.fixed(NOW, ZoneOffset.UTC), 900)
					.issueAccessToken(user);
			when(refreshTokenService.issueSession(user))
					.thenReturn(new AuthSession(
							accessToken,
							new IssuedRefreshToken("refresh-token", NOW.plusSeconds(1_209_600))));
		}
		return new LoginService(
				new SingleUserMapper(user),
				passwordEncoder,
				refreshTokenService);
	}

	private UserRecord user(String email, String rawPassword, UserStatus status) {
		return new UserRecord(
				UUID.randomUUID(),
				email,
				passwordEncoder.encode(rawPassword),
				UserRole.USER,
				status,
				NOW,
				NOW);
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
