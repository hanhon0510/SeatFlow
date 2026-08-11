package com.seatflow.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.idempotency.IdempotencyConflictException;
import com.seatflow.idempotency.IdempotencyMapper;
import com.seatflow.idempotency.IdempotencyOperation;
import com.seatflow.idempotency.IdempotencyProperties;
import com.seatflow.idempotency.IdempotencyRecord;
import com.seatflow.idempotency.IdempotencyStorageException;
import com.seatflow.idempotency.InvalidIdempotencyKeyException;

@Service
public class PaymentIdempotencyService {

	static final int MAX_KEY_LENGTH = 255;

	private static final IdempotencyOperation OPERATION = IdempotencyOperation.CREATE_PAYMENT;

	private final IdempotencyMapper idempotencyMapper;
	private final PaymentService paymentService;
	private final IdempotencyProperties properties;
	private final ObjectMapper objectMapper;
	private final Clock clock;

	public PaymentIdempotencyService(
			IdempotencyMapper idempotencyMapper,
			PaymentService paymentService,
			IdempotencyProperties properties,
			ObjectMapper objectMapper,
			Clock clock) {
		this.idempotencyMapper = idempotencyMapper;
		this.paymentService = paymentService;
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	@Transactional
	public IdempotentPaymentResult createPayment(
			UUID orderId,
			UUID userId,
			String idempotencyKey,
			PaymentCreateRequest request) {
		validateKey(idempotencyKey);
		String requestHash = hashRequest(orderId, request);
		Instant now = clock.instant();
		idempotencyMapper.deleteExpiredByScope(userId, OPERATION, idempotencyKey, now);

		IdempotencyRecord claim = new IdempotencyRecord(
				UUID.randomUUID(),
				userId,
				OPERATION,
				idempotencyKey,
				requestHash,
				null,
				null,
				now,
				now.plus(properties.ttl()));
		if (idempotencyMapper.insert(claim) == 0) {
			return replay(userId, idempotencyKey, requestHash);
		}

		PaymentResponse response = paymentService.createPayment(orderId, userId, request);
		int responseStatus = HttpStatus.CREATED.value();
		String responseBody = serialize(response);
		if (idempotencyMapper.complete(claim.id(), responseStatus, responseBody) != 1) {
			throw new IdempotencyStorageException();
		}
		return new IdempotentPaymentResult(responseStatus, response);
	}

	private IdempotentPaymentResult replay(UUID userId, String idempotencyKey, String requestHash) {
		IdempotencyRecord existing = idempotencyMapper.findByScope(userId, OPERATION, idempotencyKey);
		if (existing == null || existing.responseStatus() == null || existing.responseBody() == null) {
			throw new IdempotencyStorageException();
		}
		if (!existing.requestHash().equals(requestHash)) {
			throw new IdempotencyConflictException();
		}
		return new IdempotentPaymentResult(existing.responseStatus(), deserialize(existing.responseBody()));
	}

	private String serialize(PaymentResponse response) {
		try {
			return objectMapper.writeValueAsString(response);
		}
		catch (JsonProcessingException ex) {
			throw new IdempotencyStorageException(ex);
		}
	}

	private PaymentResponse deserialize(String responseBody) {
		try {
			return objectMapper.readValue(responseBody, PaymentResponse.class);
		}
		catch (JsonProcessingException ex) {
			throw new IdempotencyStorageException(ex);
		}
	}

	private static void validateKey(String idempotencyKey) {
		if (idempotencyKey == null
				|| idempotencyKey.isBlank()
				|| idempotencyKey.length() > MAX_KEY_LENGTH) {
			throw new InvalidIdempotencyKeyException();
		}
	}

	private static String hashRequest(UUID orderId, PaymentCreateRequest request) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.update(orderId.toString().getBytes(StandardCharsets.UTF_8));
			digest.update((byte) 0);
			String token = request.token() == null ? "" : request.token();
			return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is unavailable", ex);
		}
	}
}
