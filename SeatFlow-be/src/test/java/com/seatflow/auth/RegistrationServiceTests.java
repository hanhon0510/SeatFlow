package com.seatflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.seatflow.user.UserMapper;
import com.seatflow.user.UserRecord;
import com.seatflow.user.UserRole;
import com.seatflow.user.UserStatus;

class RegistrationServiceTests {

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	@Test
	void registerNormalizesEmailAndHashesPassword() {
		CapturingUserMapper userMapper = new CapturingUserMapper();
		RegistrationService registrationService = new RegistrationService(userMapper, passwordEncoder);

		RegisterResponse response = registrationService.register(
				new RegisterRequest("New.User@Example.COM", "StrongPassword123!"));

		assertThat(userMapper.inserted.email()).isEqualTo("new.user@example.com");
		assertThat(userMapper.inserted.passwordHash()).isNotEqualTo("StrongPassword123!");
		assertThat(passwordEncoder.matches("StrongPassword123!", userMapper.inserted.passwordHash())).isTrue();
		assertThat(response.email()).isEqualTo("new.user@example.com");
		assertThat(response.role()).isEqualTo(UserRole.USER);
		assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
	}

	@Test
	void duplicateSqlConstraintMapsToConflictException() {
		UserMapper userMapper = new DuplicateEmailUserMapper();
		RegistrationService registrationService = new RegistrationService(userMapper, passwordEncoder);

		assertThatThrownBy(() -> registrationService.register(
				new RegisterRequest("exists@example.com", "StrongPassword123!")))
				.isInstanceOf(UserAlreadyExistsException.class)
				.hasMessage("User already exists")
				.hasMessageNotContaining("StrongPassword123!");
	}

	private static final class CapturingUserMapper implements UserMapper {

		private UserRecord inserted;

		@Override
		public void insert(UserRecord user) {
			this.inserted = user;
		}

		@Override
		public UserRecord findById(UUID id) {
			return new UserRecord(
					inserted.id(),
					inserted.email(),
					inserted.passwordHash(),
					UserRole.USER,
					UserStatus.ACTIVE,
					Instant.now(),
					Instant.now());
		}

		@Override
		public UserRecord findByNormalizedEmail(String normalizedEmail) {
			return null;
		}

		@Override
		public int updateStatus(UUID id, UserStatus status) {
			return 0;
		}

	}

	private static final class DuplicateEmailUserMapper implements UserMapper {

		@Override
		public void insert(UserRecord user) {
			throw new DuplicateKeyException("duplicate normalized email");
		}

		@Override
		public UserRecord findById(UUID id) {
			return null;
		}

		@Override
		public UserRecord findByNormalizedEmail(String normalizedEmail) {
			return null;
		}

		@Override
		public int updateStatus(UUID id, UserStatus status) {
			return 0;
		}

	}

}
