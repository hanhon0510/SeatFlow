package com.seatflow.payment;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.seatflow.auth.AuthenticationFailedException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/orders/{orderId}/payments")
public class PaymentController {

	private final PaymentService paymentService;

	public PaymentController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PaymentResponse createPayment(
			@PathVariable UUID orderId,
			@Valid @RequestBody PaymentCreateRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return paymentService.createPayment(orderId, userId(jwt), request);
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
