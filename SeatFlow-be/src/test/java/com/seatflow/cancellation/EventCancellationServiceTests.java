package com.seatflow.cancellation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.seatflow.event.EventMapper;
import com.seatflow.event.EventNotFoundException;
import com.seatflow.event.EventRecord;
import com.seatflow.event.EventStateConflictException;
import com.seatflow.event.EventStatus;
import com.seatflow.observability.BusinessMetrics;
import com.seatflow.order.OrderCancelled;
import com.seatflow.order.OrderNotFoundException;
import com.seatflow.refund.RefundRequest;
import com.seatflow.refund.RefundService;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class EventCancellationServiceTests {

	private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
	private static final UUID EVENT_ID = UUID.fromString("9a2c4f10-6a1e-4c55-b3d4-1d0f6a8e7b21");
	private static final UUID ADMIN_ID = UUID.fromString("7ca3110b-513f-4445-a52d-dc96b1c2da8f");

	@Mock
	private EventMapper eventMapper;

	@Mock
	private EventCancellationMapper cancellationMapper;

	@Mock
	private RefundService refundService;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	@Mock
	private PlatformTransactionManager transactionManager;

	private SimpleMeterRegistry meterRegistry;

	private EventCancellationService service;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		service = new EventCancellationService(
				eventMapper,
				cancellationMapper,
				refundService,
				eventPublisher,
				new BusinessMetrics(meterRegistry),
				new TransactionTemplate(transactionManager),
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void cancellingAPublishedEventClosesItFreesOpenHoldsAndRecordsWhy() {
		UUID holdId = UUID.randomUUID();
		UUID buyerId = UUID.randomUUID();
		stubTransaction();
		when(eventMapper.findByIdForUpdate(EVENT_ID)).thenReturn(event(EventStatus.PUBLISHED, NOW.plusSeconds(3600)));
		when(eventMapper.cancel(EVENT_ID)).thenReturn(1);
		stubStoredCancellation("Artist ill");
		when(cancellationMapper.findOpenHolds(EVENT_ID)).thenReturn(List.of(new OpenHold(holdId, buyerId)));
		when(cancellationMapper.findRefundableOrderIds(EVENT_ID, EventCancellationService.REFUND_BATCH_SIZE))
				.thenReturn(List.of());
		when(cancellationMapper.refundCounts(EVENT_ID)).thenReturn(new CancellationRefundCounts(0, 0, 0));

		EventCancellationResponse response = service.cancelEvent(
				EVENT_ID, ADMIN_ID, new EventCancellationRequest("  Artist ill  "));

		assertThat(response.status()).isEqualTo(EventStatus.CANCELLED);
		assertThat(response.reason()).isEqualTo("Artist ill");
		ArgumentCaptor<EventCancellationRecord> recorded = ArgumentCaptor.forClass(EventCancellationRecord.class);
		verify(cancellationMapper).insert(recorded.capture());
		assertThat(recorded.getValue().reason()).isEqualTo("Artist ill");
		assertThat(recorded.getValue().cancelledBy()).isEqualTo(ADMIN_ID);
		verify(cancellationMapper).cancelPendingOrders(EVENT_ID, NOW);
		verify(cancellationMapper).cancelOpenReservations(EVENT_ID, NOW);
		verify(eventPublisher).publishEvent(new OrderCancelled(holdId, buyerId));
		assertThat(meterRegistry.get("event_cancelled").counter().count()).isEqualTo(1.0);
	}

	@Test
	void everyRefundCarriesTheCancellationReason() {
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		stubAlreadyCancelled("Venue flooded");
		when(cancellationMapper.findRefundableOrderIds(EVENT_ID, EventCancellationService.REFUND_BATCH_SIZE))
				.thenReturn(List.of(first, second));
		when(cancellationMapper.refundCounts(EVENT_ID)).thenReturn(new CancellationRefundCounts(2, 0, 0));

		EventCancellationResponse response = service.cancelEvent(EVENT_ID, ADMIN_ID, EventCancellationRequest.NONE);

		RefundRequest expected = new RefundRequest("Event cancelled: Venue flooded");
		verify(refundService).refundOrder(first, ADMIN_ID, expected);
		verify(refundService).refundOrder(second, ADMIN_ID, expected);
		assertThat(response.refundedOrders()).isEqualTo(2);
	}

	@Test
	void aMissingReasonStillGivesTheRefundAReadableOne() {
		UUID orderId = UUID.randomUUID();
		stubAlreadyCancelled(null);
		when(cancellationMapper.findRefundableOrderIds(EVENT_ID, EventCancellationService.REFUND_BATCH_SIZE))
				.thenReturn(List.of(orderId));
		when(cancellationMapper.refundCounts(EVENT_ID)).thenReturn(new CancellationRefundCounts(1, 0, 0));

		service.cancelEvent(EVENT_ID, ADMIN_ID, EventCancellationRequest.NONE);

		verify(refundService).refundOrder(orderId, ADMIN_ID, new RefundRequest("Event cancelled"));
	}

	@Test
	void anOrderThatFailsToRefundDoesNotStopTheOthersAndStaysCounted() {
		UUID broken = UUID.randomUUID();
		UUID healthy = UUID.randomUUID();
		stubAlreadyCancelled(null);
		when(cancellationMapper.findRefundableOrderIds(EVENT_ID, EventCancellationService.REFUND_BATCH_SIZE))
				.thenReturn(List.of(broken, healthy));
		doThrow(new OrderNotFoundException()).when(refundService).refundOrder(eq(broken), any(), any());
		when(cancellationMapper.refundCounts(EVENT_ID)).thenReturn(new CancellationRefundCounts(1, 1, 0));

		EventCancellationResponse response = service.cancelEvent(EVENT_ID, ADMIN_ID, EventCancellationRequest.NONE);

		verify(refundService).refundOrder(eq(healthy), any(), any());
		assertThat(response.remainingRefunds()).isEqualTo(1);
	}

	@Test
	void repeatingACancellationOnlyResumesTheRefunds() {
		stubAlreadyCancelled("Venue flooded");
		when(cancellationMapper.findRefundableOrderIds(EVENT_ID, EventCancellationService.REFUND_BATCH_SIZE))
				.thenReturn(List.of());
		when(cancellationMapper.refundCounts(EVENT_ID)).thenReturn(new CancellationRefundCounts(5, 0, 0));

		service.cancelEvent(EVENT_ID, ADMIN_ID, new EventCancellationRequest("Something else"));

		verify(eventMapper, never()).cancel(any());
		verify(cancellationMapper, never()).cancelPendingOrders(any(), any());
		verify(cancellationMapper, never()).cancelOpenReservations(any(), any());
		verify(eventPublisher, never()).publishEvent(any(Object.class));
		assertThat(meterRegistry.find("event_cancelled").counter().count()).isZero();
	}

	@Test
	void anEventThatHasStartedCannotBeCancelled() {
		stubTransaction();
		when(eventMapper.findByIdForUpdate(EVENT_ID)).thenReturn(event(EventStatus.PUBLISHED, NOW));

		assertThatThrownBy(() -> service.cancelEvent(EVENT_ID, ADMIN_ID, EventCancellationRequest.NONE))
				.isInstanceOf(EventStateConflictException.class);

		verify(eventMapper, never()).cancel(any());
		verify(refundService, never()).refundOrder(any(), any(), any());
	}

	@Test
	void aCompletedEventCannotBeCancelled() {
		stubTransaction();
		when(eventMapper.findByIdForUpdate(EVENT_ID)).thenReturn(event(EventStatus.COMPLETED, NOW.plusSeconds(60)));

		assertThatThrownBy(() -> service.cancelEvent(EVENT_ID, ADMIN_ID, EventCancellationRequest.NONE))
				.isInstanceOf(EventStateConflictException.class);
	}

	@Test
	void anUnknownEventIsNotFound() {
		stubTransaction();
		when(eventMapper.findByIdForUpdate(EVENT_ID)).thenReturn(null);

		assertThatThrownBy(() -> service.cancelEvent(EVENT_ID, ADMIN_ID, EventCancellationRequest.NONE))
				.isInstanceOf(EventNotFoundException.class);
	}

	@Test
	void aBlankReasonIsStoredAsAbsent() {
		assertThat(new EventCancellationRequest("   ").normalizedReason()).isNull();
		assertThat(new EventCancellationRequest(null).normalizedReason()).isNull();
		assertThat(new EventCancellationRequest(" weather ").normalizedReason()).isEqualTo("weather");
	}

	private void stubTransaction() {
		when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
	}

	private void stubAlreadyCancelled(String reason) {
		stubTransaction();
		when(eventMapper.findByIdForUpdate(EVENT_ID)).thenReturn(event(EventStatus.CANCELLED, NOW.plusSeconds(3600)));
		stubStoredCancellation(reason);
	}

	private void stubStoredCancellation(String reason) {
		when(cancellationMapper.findByEventId(EVENT_ID))
				.thenReturn(new EventCancellationRecord(EVENT_ID, reason, ADMIN_ID, NOW.minusSeconds(30)));
	}

	private static EventRecord event(EventStatus status, Instant startTime) {
		return new EventRecord(
				EVENT_ID,
				UUID.randomUUID(),
				"Concert",
				null,
				startTime,
				NOW.minusSeconds(86_400),
				startTime.minusSeconds(60),
				status,
				NOW.minusSeconds(86_400),
				NOW.minusSeconds(86_400));
	}
}
