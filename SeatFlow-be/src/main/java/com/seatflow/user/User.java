package com.seatflow.user;

import java.time.Instant;
import java.util.UUID;

public record User(
		UUID id,
		String email,
		String passwordHash,
		UserRole role,
		UserStatus status,
		Instant createdAt,
		Instant updatedAt) {
}

