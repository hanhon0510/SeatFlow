package com.seatflow.admin;

import java.util.UUID;

import com.seatflow.user.UserRecord;
import com.seatflow.user.UserRole;

public record AdminStatusResponse(UUID id, String email, UserRole role) {

	public static AdminStatusResponse from(UserRecord user) {
		return new AdminStatusResponse(user.id(), user.email(), user.role());
	}
}
