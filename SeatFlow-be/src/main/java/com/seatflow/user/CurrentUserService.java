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

	public UserRecord getCurrentUserRecord(Jwt jwt) {
		UserRecord user = userMapper.findById(userId(jwt));
		if (user == null) {
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
