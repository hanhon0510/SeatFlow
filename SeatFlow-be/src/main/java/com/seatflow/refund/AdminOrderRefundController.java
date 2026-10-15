package com.seatflow.refund;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seatflow.auth.AuthenticationFailedException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderRefundController {

	private final RefundService refundService;

	public AdminOrderRefundController(RefundService refundService) {
		this.refundService = refundService;
	}

	@PostMapping("/{orderId}/refund")
	public RefundResponse refund(
			@PathVariable UUID orderId,
			@Valid @RequestBody(required = false) RefundRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return refundService.refundOrder(orderId, adminId(jwt), request == null ? RefundRequest.NONE : request);
	}

	private static UUID adminId(Jwt jwt) {
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
