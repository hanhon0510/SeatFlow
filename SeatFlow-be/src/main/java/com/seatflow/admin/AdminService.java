package com.seatflow.admin;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.seatflow.user.CurrentUserService;
import com.seatflow.user.UserRecord;
import com.seatflow.user.UserRole;

@Service
public class AdminService {

	private final CurrentUserService currentUserService;

	public AdminService(CurrentUserService currentUserService) {
		this.currentUserService = currentUserService;
	}

	public AdminStatusResponse getAdminStatus(Jwt jwt) {
		UserRecord user = currentUserService.getCurrentUserRecord(jwt);
		if (user.role() != UserRole.ADMIN) {
			throw new AccessDeniedException("Admin role required");
		}

		return AdminStatusResponse.from(user);
	}
}
