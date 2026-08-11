package com.seatflow.payment;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seatflow.auth.AuthenticationFailedException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/orders/{orderId}/payments")
public class PaymentController {

	private final PaymentIdempotencyService paymentIdempotencyService;

	public PaymentController(PaymentIdempotencyService paymentIdempotencyService) {
		this.paymentIdempotencyService = paymentIdempotencyService;
	}

	@PostMapping
	public ResponseEntity<PaymentResponse> createPayment(
			@PathVariable UUID orderId,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@Valid @RequestBody PaymentCreateRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		IdempotentPaymentResult result = paymentIdempotencyService.createPayment(
				orderId,
				userId(jwt),
				idempotencyKey,
				request);
		return ResponseEntity.status(result.responseStatus()).body(result.responseBody());
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
