package com.seatflow.auth;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.seatflow.security.JwtTokenService;
import com.seatflow.user.UserMapper;
import com.seatflow.user.UserRecord;
import com.seatflow.user.UserStatus;

@Service
public class LoginService {

	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenService jwtTokenService;

	public LoginService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService) {
		this.userMapper = userMapper;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenService = jwtTokenService;
	}

	public LoginResponse login(LoginRequest request) {
		UserRecord user = userMapper.findByNormalizedEmail(normalizeEmail(request.email()));
		if (user == null || !passwordEncoder.matches(request.password(), user.passwordHash())) {
			throw new AuthenticationFailedException();
		}
		if (user.status() == UserStatus.DISABLED) {
			throw new AuthenticationFailedException();
		}

		return jwtTokenService.issueAccessToken(user);
	}

	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

}
