package com.seatflow.auth;

import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seatflow.user.UserMapper;
import com.seatflow.user.UserRecord;

@Service
public class RegistrationService {

	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;

	public RegistrationService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
		this.userMapper = userMapper;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public RegisterResponse register(RegisterRequest request) {
		String normalizedEmail = normalizeEmail(request.email());
		String passwordHash = passwordEncoder.encode(request.password());
		UserRecord user = UserRecord.forInsert(UUID.randomUUID(), normalizedEmail, passwordHash);

		try {
			userMapper.insert(user);
		}
		catch (DuplicateKeyException ex) {
			throw new UserAlreadyExistsException(ex);
		}

		return RegisterResponse.from(userMapper.findById(user.id()));
	}

	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

}
