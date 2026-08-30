package com.seatflow.user;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.seatflow.auth.AuthenticationFailedException;

@Service
public class CurrentUserService {

	private final UserMapper userMapper;

	public CurrentUserService(UserMapper userMapper) {
		this.userMapper = userMapper;
	}

	public UserMeResponse getCurrentUser(Jwt jwt) {
		return UserMeResponse.from(getCurrentUserRecord(jwt));
	}

	/**
	 * The row is loaded anyway, so checking status here is free. It does not cover the booking
	 * endpoints, which authenticate from the JWT alone and never load the user - disabling an
	 * account still leaves those reachable until the access token expires. That window is bounded
	 * by the JWT TTL, and refresh already refuses a disabled user, so the session cannot be
	 * extended past it.
	 */
	public UserRecord getCurrentUserRecord(Jwt jwt) {
		UserRecord user = userMapper.findById(userId(jwt));
		if (user == null || user.status() == UserStatus.DISABLED) {
			throw new AuthenticationFailedException();
		}

		return user;
	}

	private static UUID userId(Jwt jwt) {
		if (jwt == null) {
			throw new AuthenticationFailedException();
		}

		try {
			return UUID.fromString(jwt.getSubject());
		}
		catch (IllegalArgumentException ex) {
			throw new AuthenticationFailedException();
		}
	}

}
