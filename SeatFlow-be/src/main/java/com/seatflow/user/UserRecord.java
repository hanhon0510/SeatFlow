package com.seatflow.user;

import java.time.Instant;
import java.util.UUID;

public record UserRecord(
		UUID id,
		String email,
		String passwordHash,
		UserRole role,
		UserStatus status,
		Instant createdAt,
		Instant updatedAt) {

	public static UserRecord forInsert(UUID id, String email, String passwordHash) {
		return new UserRecord(id, email, passwordHash, null, null, null, null);
	}

	public User toUser() {
		return new User(id, email, passwordHash, role, status, createdAt, updatedAt);
	}

}

