package com.seatflow.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

	private final CurrentUserService currentUserService;

	public UserController(CurrentUserService currentUserService) {
		this.currentUserService = currentUserService;
	}

	@GetMapping("/me")
	public UserMeResponse me(@AuthenticationPrincipal Jwt jwt) {
		return currentUserService.getCurrentUser(jwt);
	}

}
