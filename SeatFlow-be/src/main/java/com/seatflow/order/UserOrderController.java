package com.seatflow.order;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.seatflow.auth.AuthenticationFailedException;

@RestController
@RequestMapping("/api/v1/users/me/orders")
public class UserOrderController {

	private final OrderService orderService;

	public UserOrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@GetMapping
	public OrderPageResponse listOrders(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "" + OrderService.DEFAULT_PAGE_SIZE) int size,
			@AuthenticationPrincipal Jwt jwt) {
		return orderService.listUserOrders(userId(jwt), page, size);
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
