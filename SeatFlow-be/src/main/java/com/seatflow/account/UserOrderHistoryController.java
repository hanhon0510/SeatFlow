package com.seatflow.account;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.seatflow.auth.AuthenticationFailedException;

@RestController
@RequestMapping("/api/v1/users/me/order-history")
public class UserOrderHistoryController {

	private final OrderHistoryService orderHistoryService;

	public UserOrderHistoryController(OrderHistoryService orderHistoryService) {
		this.orderHistoryService = orderHistoryService;
	}

	@GetMapping
	public OrderHistoryPage list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "" + OrderHistoryService.DEFAULT_PAGE_SIZE) int size,
			@AuthenticationPrincipal Jwt jwt) {
		return orderHistoryService.list(userId(jwt), page, size);
	}

	private static UUID userId(Jwt jwt) {
		if (jwt == null) {
			throw new AuthenticationFailedException();
		}
		try {
			return UUID.fromString(jwt.getSubject());
		}
		catch (IllegalArgumentException ex) {
			throw new AuthenticationFailedException();
		}
	}
}
