package com.seatflow.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.seatflow.auth.RefreshTokenService;
import com.seatflow.consumer.ProcessedEventMapper;
import com.seatflow.idempotency.IdempotencyMapper;
import com.seatflow.observability.BusinessMetrics;
import com.seatflow.order.OrderMapper;
import com.seatflow.outbox.OutboxMapper;
import com.seatflow.reservation.ReservationMapper;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MaintenanceServiceTests {

	private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
	private static final int BATCH_SIZE = 10;

	@Mock
	private ReservationMapper reservationMapper;

	@Mock
	private OrderMapper orderMapper;

	@Mock
	private IdempotencyMapper idempotencyMapper;

	@Mock
	private OutboxMapper outboxMapper;

	@Mock
	private ProcessedEventMapper processedEventMapper;

	@Mock
	private RefreshTokenService refreshTokenService;

	private SimpleMeterRegistry meterRegistry;
	private MaintenanceService service;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		service = newService(new MaintenanceProperties(true, BATCH_SIZE, 3, Duration.ofDays(7), Duration.ofDays(2)));
	}

	@Test
	void sweepClosesReservationsBeforeOrdersSoOrdersSeeTheClosedState() {
		service.sweep();

		InOrder inOrder = inOrder(reservationMapper, orderMapper);
		inOrder.verify(reservationMapper).closeLapsed(NOW, BATCH_SIZE);
		inOrder.verify(orderMapper).closeForClosedReservations(NOW, BATCH_SIZE);
	}

	@Test
	void sweepAppliesTheConfiguredRetentionWindowToEachPurgedTable() {
		service.sweep();

		verify(idempotencyMapper).deleteExpired(NOW, BATCH_SIZE);
		verify(refreshTokenService).deleteExpiredTokens(BATCH_SIZE);
		verify(outboxMapper).deletePublishedBefore(NOW.minus(Duration.ofDays(7)), BATCH_SIZE);
		verify(processedEventMapper).deleteProcessedBefore(NOW.minus(Duration.ofDays(2)), BATCH_SIZE);
	}

	@Test
	void aStepThatKeepsFillingItsBatchIsRepeatedUntilItDrains() {
		when(reservationMapper.closeLapsed(eq(NOW), anyInt())).thenReturn(BATCH_SIZE, BATCH_SIZE, 4);

		MaintenanceSummary summary = service.sweep();

		verify(reservationMapper, times(3)).closeLapsed(NOW, BATCH_SIZE);
		assertThat(summary.expiredReservations()).isEqualTo(BATCH_SIZE + BATCH_SIZE + 4);
		assertThat(meterRegistry.get("reservation_expired").counter().count()).isEqualTo(24);
	}

	@Test
	void aBacklogLargerThanOneRunStopsAtTheCapInsteadOfMonopolisingAConnection() {
		when(reservationMapper.closeLapsed(eq(NOW), anyInt())).thenReturn(BATCH_SIZE);

		MaintenanceSummary summary = service.sweep();

		// Three batches is the configured cap; the rest is left for the next run.
		verify(reservationMapper, times(3)).closeLapsed(NOW, BATCH_SIZE);
		assertThat(summary.expiredReservations()).isEqualTo(3 * BATCH_SIZE);
	}

	@Test
	void anIdleSweepReportsNothing() {
		assertThat(service.sweep().isEmpty()).isTrue();
	}

	private MaintenanceService newService(MaintenanceProperties properties) {
		return new MaintenanceService(
				reservationMapper,
				orderMapper,
				idempotencyMapper,
				outboxMapper,
				processedEventMapper,
				refreshTokenService,
				properties,
				new BusinessMetrics(meterRegistry),
				Clock.fixed(NOW, ZoneOffset.UTC));
	}
}
