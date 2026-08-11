package com.seatflow.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.seatflow.idempotency.IdempotencyConflictException;
import com.seatflow.idempotency.IdempotencyMapper;
import com.seatflow.idempotency.IdempotencyOperation;
import com.seatflow.idempotency.IdempotencyProperties;
import com.seatflow.idempotency.IdempotencyRecord;
import com.seatflow.idempotency.InvalidIdempotencyKeyException;

@ExtendWith(MockitoExtension.class)
class PaymentIdempotencyServiceTests {

	private static final Instant NOW = Instant.parse("2026-08-11T12:00:00Z");
	private static final Duration TTL = Duration.ofHours(24);
	private static final UUID USER_ID = UUID.fromString("0abcc6a7-eb30-46aa-be8b-677811650fe4");
	private static final UUID ORDER_ID = UUID.fromString("2ae13b6b-df9b-4e08-99ae-f6b13f1997b0");
	private static final UUID PAYMENT_ID = UUID.fromString("fd9e706d-a502-41ae-9012-a20e24625333");
	private static final String KEY = "payment-attempt-1";
	private static final PaymentCreateRequest REQUEST = new PaymentCreateRequest("tok_success");

	@Mock
	private IdempotencyMapper idempotencyMapper;

	@Mock
	private PaymentService paymentService;

	private ObjectMapper objectMapper;
	private PaymentIdempotencyService service;

	@BeforeEach
	void setUp() {
		objectMapper = JsonMapper.builder().findAndAddModules().build();
		service = new PaymentIdempotencyService(
				idempotencyMapper,
				paymentService,
				new IdempotencyProperties(TTL),
				objectMapper,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void newRequestExecutesPaymentAndStoresSanitizedResponse() {
		when(idempotencyMapper.insert(any())).thenReturn(1);
		when(paymentService.createPayment(ORDER_ID, USER_ID, REQUEST)).thenReturn(paymentResponse());
		when(idempotencyMapper.complete(any(), eq(201), any())).thenReturn(1);

		IdempotentPaymentResult result = service.createPayment(ORDER_ID, USER_ID, KEY, REQUEST);

		assertThat(result.responseStatus()).isEqualTo(201);
		assertThat(result.responseBody()).isEqualTo(paymentResponse());
		ArgumentCaptor<IdempotencyRecord> recordCaptor = ArgumentCaptor.forClass(IdempotencyRecord.class);
		verify(idempotencyMapper).insert(recordCaptor.capture());
		IdempotencyRecord record = recordCaptor.getValue();
		assertThat(record.operation()).isEqualTo(IdempotencyOperation.CREATE_PAYMENT);
		assertThat(record.requestHash()).hasSize(64).doesNotContain(REQUEST.token());
		assertThat(record.createdAt()).isEqualTo(NOW);
		assertThat(record.expiresAt()).isEqualTo(NOW.plus(TTL));

		ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
		verify(idempotencyMapper).complete(eq(record.id()), eq(201), bodyCaptor.capture());
		assertThat(bodyCaptor.getValue())
				.contains(PAYMENT_ID.toString())
				.doesNotContain(REQUEST.token());
	}

	@Test
	void duplicateRequestReplaysStoredResponseWithoutExecutingPayment() throws Exception {
		AtomicReference<IdempotencyRecord> attemptedClaim = new AtomicReference<>();
		when(idempotencyMapper.insert(any())).thenAnswer(invocation -> {
			attemptedClaim.set(invocation.getArgument(0));
			return 0;
		});
		when(idempotencyMapper.findByScope(USER_ID, IdempotencyOperation.CREATE_PAYMENT, KEY))
				.thenAnswer(invocation -> completedRecord(
						attemptedClaim.get().requestHash(),
						objectMapper.writeValueAsString(paymentResponse())));

		IdempotentPaymentResult result = service.createPayment(ORDER_ID, USER_ID, KEY, REQUEST);

		assertThat(result.responseStatus()).isEqualTo(201);
		assertThat(result.responseBody()).isEqualTo(paymentResponse());
		verify(paymentService, never()).createPayment(any(), any(), any());
		verify(idempotencyMapper, never()).complete(any(), any(Integer.class), any());
	}

	@Test
	void differentRequestWithSameKeyIsRejected() {
		when(idempotencyMapper.insert(any())).thenReturn(0);
		when(idempotencyMapper.findByScope(USER_ID, IdempotencyOperation.CREATE_PAYMENT, KEY))
				.thenReturn(completedRecord("0".repeat(64), "{}"));

		assertThatThrownBy(() -> service.createPayment(ORDER_ID, USER_ID, KEY, REQUEST))
				.isInstanceOf(IdempotencyConflictException.class);

		verify(paymentService, never()).createPayment(any(), any(), any());
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "\t"})
	void missingOrBlankKeyIsRejected(String key) {
		assertThatThrownBy(() -> service.createPayment(ORDER_ID, USER_ID, key, REQUEST))
				.isInstanceOf(InvalidIdempotencyKeyException.class);

		verify(idempotencyMapper, never()).insert(any());
	}

	@Test
	void oversizedKeyIsRejected() {
		String key = "k".repeat(PaymentIdempotencyService.MAX_KEY_LENGTH + 1);

		assertThatThrownBy(() -> service.createPayment(ORDER_ID, USER_ID, key, REQUEST))
				.isInstanceOf(InvalidIdempotencyKeyException.class);
	}

	private static IdempotencyRecord completedRecord(String requestHash, String responseBody) {
		return new IdempotencyRecord(
				UUID.randomUUID(),
				USER_ID,
				IdempotencyOperation.CREATE_PAYMENT,
				KEY,
				requestHash,
				201,
				responseBody,
				NOW,
				NOW.plus(TTL));
	}

	private static PaymentResponse paymentResponse() {
		return new PaymentResponse(
				PAYMENT_ID,
				ORDER_ID,
				PaymentStatus.SUCCEEDED,
				new BigDecimal("210000.75"),
				"sim_" + PAYMENT_ID,
				null,
				NOW,
				NOW);
	}
}
