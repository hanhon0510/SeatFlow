package com.seatflow.user;

import java.time.Instant;
import java.util.UUID;

public record UserMeResponse(
		UUID id,
		String email,
		UserRole role,
		UserStatus status,
		Instant createdAt,
		Instant updatedAt) {

	public static UserMeResponse from(UserRecord user) {
		return new UserMeResponse(
				user.id(),
				user.email(),
				user.role(),
				user.status(),
				user.createdAt(),
				user.updatedAt());
	}

}
