package com.seatflow.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import com.seatflow.user.CurrentUserService;
import com.seatflow.user.UserMapper;
import com.seatflow.user.UserRecord;
import com.seatflow.user.UserRole;
import com.seatflow.user.UserStatus;

class AdminServiceTests {

	private static final Instant NOW = Instant.parse("2026-07-29T00:00:00Z");

	@Test
	void adminUserReceivesAdminStatus() {
		UserRecord admin = user(UserRole.ADMIN);
		UserMapper userMapper = mock(UserMapper.class);
		when(userMapper.findById(admin.id())).thenReturn(admin);
		AdminService adminService = new AdminService(new CurrentUserService(userMapper));

		AdminStatusResponse response = adminService.getAdminStatus(jwt(admin));

		assertThat(response.id()).isEqualTo(admin.id());
		assertThat(response.email()).isEqualTo(admin.email());
		assertThat(response.role()).isEqualTo(UserRole.ADMIN);
	}

	@Test
	void normalUserIsRejectedByServiceAuthorization() {
		UserRecord user = user(UserRole.USER);
		UserMapper userMapper = mock(UserMapper.class);
		when(userMapper.findById(user.id())).thenReturn(user);
		AdminService adminService = new AdminService(new CurrentUserService(userMapper));

		assertThatThrownBy(() -> adminService.getAdminStatus(jwt(user)))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessage("Admin role required");
	}

	private static Jwt jwt(UserRecord user) {
		return Jwt.withTokenValue("token")
				.header("alg", "none")
				.subject(user.id().toString())
				.claim("role", user.role().name())
				.issuedAt(NOW)
				.expiresAt(NOW.plusSeconds(900))
				.build();
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
