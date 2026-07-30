package com.seatflow.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.seatflow.user.UserMapper;
import com.seatflow.user.UserRecord;
import com.seatflow.user.UserRole;
import com.seatflow.user.UserStatus;

class LocalAdminSeederTests {

	private static final Instant NOW = Instant.parse("2026-07-29T00:00:00Z");
	private static final String STRONG_PASSWORD = "StrongPassword123!";

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	@Test
	void disabledSeederDoesNothing() {
		UserMapper userMapper = mock(UserMapper.class);
		LocalAdminSeeder seeder = seeder(false, "admin@example.com", STRONG_PASSWORD, userMapper);

		seeder.run();

		verifyNoInteractions(userMapper);
	}

	@Test
	void createsMissingLocalAdmin() {
		UserMapper userMapper = mock(UserMapper.class);
		LocalAdminSeeder seeder = seeder(true, " Admin@Example.COM ", STRONG_PASSWORD, userMapper);

		seeder.run();

		ArgumentCaptor<UserRecord> insertedUser = ArgumentCaptor.forClass(UserRecord.class);
		verify(userMapper).findByNormalizedEmail("admin@example.com");
		verify(userMapper).insertWithRole(insertedUser.capture());
		assertThat(insertedUser.getValue().email()).isEqualTo("admin@example.com");
		assertThat(insertedUser.getValue().role()).isEqualTo(UserRole.ADMIN);
		assertThat(passwordEncoder.matches(STRONG_PASSWORD, insertedUser.getValue().passwordHash())).isTrue();
	}

	@Test
	void existingAdminIsLeftUnchanged() {
		UserMapper userMapper = mock(UserMapper.class);
		UserRecord admin = user(UserRole.ADMIN);
		when(userMapper.findByNormalizedEmail(admin.email())).thenReturn(admin);
		LocalAdminSeeder seeder = seeder(true, admin.email(), STRONG_PASSWORD, userMapper);

		seeder.run();

		verify(userMapper, never()).insertWithRole(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void existingNonAdminUserIsNotPromotedAutomatically() {
		UserMapper userMapper = mock(UserMapper.class);
		UserRecord user = user(UserRole.USER);
		when(userMapper.findByNormalizedEmail(user.email())).thenReturn(user);
		LocalAdminSeeder seeder = seeder(true, user.email(), STRONG_PASSWORD, userMapper);

		assertThatThrownBy(seeder::run)
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Local admin email already belongs to a non-admin user");
		verify(userMapper, never()).insertWithRole(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void shortPasswordIsRejected() {
		UserMapper userMapper = mock(UserMapper.class);
		LocalAdminSeeder seeder = seeder(true, "admin@example.com", "short", userMapper);

		assertThatThrownBy(seeder::run)
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Local admin password must be at least 12 characters");
	}

	private LocalAdminSeeder seeder(boolean enabled, String email, String password, UserMapper userMapper) {
		return new LocalAdminSeeder(
				new LocalAdminProperties(enabled, email, password),
				userMapper,
				passwordEncoder);
	}

	private static UserRecord user(UserRole role) {
		return new UserRecord(
				UUID.randomUUID(),
				"%s@example.com".formatted(role.name().toLowerCase()),
				"password-hash",
				role,
				UserStatus.ACTIVE,
				NOW,
				NOW);
	}
}
