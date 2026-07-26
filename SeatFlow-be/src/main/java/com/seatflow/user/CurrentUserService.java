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
		UserRecord user = userMapper.findById(userId(jwt));
		if (user == null) {
			throw new AuthenticationFailedException();
		}

		return UserMeResponse.from(user);
	}

	private static UUID userId(Jwt jwt) {
		try {
			return UUID.fromString(jwt.getSubject());
		}
		catch (IllegalArgumentException ex) {
			throw new AuthenticationFailedException();
		}
	}

}
