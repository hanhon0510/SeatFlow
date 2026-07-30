package com.seatflow.admin;

import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.seatflow.user.UserMapper;
import com.seatflow.user.UserRecord;
import com.seatflow.user.UserRole;

@Component
@Profile("local")
public class LocalAdminSeeder implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(LocalAdminSeeder.class);
	private static final int MIN_PASSWORD_LENGTH = 12;

	private final LocalAdminProperties properties;
	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;

	public LocalAdminSeeder(
			LocalAdminProperties properties,
			UserMapper userMapper,
			PasswordEncoder passwordEncoder) {
		this.properties = properties;
		this.userMapper = userMapper;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public void run(String... args) {
		if (!properties.enabled()) {
			return;
		}

		String email = normalizeEmail(properties.email());
		String password = properties.password();
		if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
			throw new IllegalStateException("Local admin seeding requires email and password");
		}
		if (password.length() < MIN_PASSWORD_LENGTH) {
			throw new IllegalStateException("Local admin password must be at least 12 characters");
		}

		UserRecord existing = userMapper.findByNormalizedEmail(email);
		if (existing != null) {
			if (existing.role() == UserRole.ADMIN) {
				log.info("Local admin user already exists: {}", existing.email());
				return;
			}

			throw new IllegalStateException("Local admin email already belongs to a non-admin user");
		}

		UserRecord admin = UserRecord.forInsert(
				UUID.randomUUID(),
				email,
				passwordEncoder.encode(password),
				UserRole.ADMIN);
		userMapper.insertWithRole(admin);
		log.info("Created local admin user: {}", email);
	}

	private static String normalizeEmail(String email) {
		return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
	}
}
