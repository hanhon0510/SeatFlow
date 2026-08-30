package com.seatflow.auth;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.seatflow.user.UserMapper;
import com.seatflow.user.UserRecord;
import com.seatflow.user.UserStatus;

@Service
public class LoginService {

	/** A real BCrypt hash of a value nobody can supply, used only to burn the same time. */
	private static final String DUMMY_PASSWORD_HASH =
			"$2a$10$7EqJtq98hPqEX7fNZaFWoOa8Rr.7hR5kK4hVJ1u9m8Z1Q0jK1Zz3O";

	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;
	private final RefreshTokenService refreshTokenService;

	public LoginService(UserMapper userMapper, PasswordEncoder passwordEncoder, RefreshTokenService refreshTokenService) {
		this.userMapper = userMapper;
		this.passwordEncoder = passwordEncoder;
		this.refreshTokenService = refreshTokenService;
	}

	public AuthSession login(LoginRequest request) {
		UserRecord user = userMapper.findByNormalizedEmail(normalizeEmail(request.email()));
		// Hash against a placeholder when the account does not exist, so an unknown address costs
		// the same ~100ms as a known one. Skipping the hash returned in microseconds and turned
		// login into a reliable oracle for which addresses are registered.
		String passwordHash = user == null ? DUMMY_PASSWORD_HASH : user.passwordHash();
		boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);
		if (user == null || !passwordMatches) {
			throw new AuthenticationFailedException();
		}
		if (user.status() == UserStatus.DISABLED) {
			throw new AuthenticationFailedException();
		}

		return refreshTokenService.issueSession(user);
	}

	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

}
