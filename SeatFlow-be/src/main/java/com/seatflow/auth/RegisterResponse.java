package com.seatflow.auth;

import java.time.Instant;
import java.util.UUID;

import com.seatflow.user.UserRecord;
import com.seatflow.user.UserRole;
import com.seatflow.user.UserStatus;

public record RegisterResponse(
		UUID id,
		String email,
		UserRole role,
		UserStatus status,
		Instant createdAt) {

	public static RegisterResponse from(UserRecord user) {
		return new RegisterResponse(user.id(), user.email(), user.role(), user.status(), user.createdAt());
	}

}
