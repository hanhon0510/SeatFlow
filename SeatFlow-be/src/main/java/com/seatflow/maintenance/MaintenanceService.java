package com.seatflow.maintenance;

import java.time.Clock;
import java.time.Instant;
import java.util.function.IntSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.seatflow.auth.RefreshTokenService;
import com.seatflow.consumer.ProcessedEventMapper;
import com.seatflow.idempotency.IdempotencyMapper;
import com.seatflow.observability.BusinessMetrics;
import com.seatflow.order.OrderMapper;
import com.seatflow.outbox.OutboxMapper;
import com.seatflow.reservation.ReservationMapper;

/**
 * Closes lapsed checkouts and applies retention to the tables that accumulate on the hot path.
 *
 * <p>Every step is a bounded, self-guarded statement run outside a transaction, so a sweep can
 * be interrupted at any point and simply resumes on the next pass. Nothing here races a live
 * checkout: the payment path independently rejects any reservation whose {@code expires_at} has
 * passed, and each status transition is guarded by the status it expects to find.
 */
@Service
public class MaintenanceService {

	private static final Logger log = LoggerFactory.getLogger(MaintenanceService.class);

	private final ReservationMapper reservationMapper;
	private final OrderMapper orderMapper;
	private final IdempotencyMapper idempotencyMapper;
	private final OutboxMapper outboxMapper;
	private final ProcessedEventMapper processedEventMapper;
	private final RefreshTokenService refreshTokenService;
	private final MaintenanceProperties properties;
	private final BusinessMetrics businessMetrics;
	private final Clock clock;

	public MaintenanceService(
			ReservationMapper reservationMapper,
			OrderMapper orderMapper,
			IdempotencyMapper idempotencyMapper,
			OutboxMapper outboxMapper,
			ProcessedEventMapper processedEventMapper,
			RefreshTokenService refreshTokenService,
			MaintenanceProperties properties,
			BusinessMetrics businessMetrics,
			Clock clock) {
		this.reservationMapper = reservationMapper;
		this.orderMapper = orderMapper;
		this.idempotencyMapper = idempotencyMapper;
		this.outboxMapper = outboxMapper;
		this.processedEventMapper = processedEventMapper;
		this.refreshTokenService = refreshTokenService;
		this.properties = properties;
		this.businessMetrics = businessMetrics;
		this.clock = clock;
	}

	public MaintenanceSummary sweep() {
		Instant now = clock.instant();
		int batchSize = properties.batchSize();

		// Reservations first: closing an order keys off its reservation already being closed.
		int expiredReservations = drain("expire-reservations", () -> reservationMapper.closeLapsed(now, batchSize));
		int closedOrders = drain("close-orders", () -> orderMapper.closeForClosedReservations(now, batchSize));
		int idempotencyRecords = drain("purge-idempotency", () -> idempotencyMapper.deleteExpired(now, batchSize));
		int refreshTokens = drain("purge-refresh-tokens", () -> refreshTokenService.deleteExpiredTokens(batchSize));
		int outboxEvents = drain("purge-outbox", () -> outboxMapper.deletePublishedBefore(
				now.minus(properties.outboxRetention()),
				batchSize));
		int processedEvents = drain("purge-processed-events", () -> processedEventMapper.deleteProcessedBefore(
				now.minus(properties.processedEventRetention()),
				batchSize));

		businessMetrics.reservationExpired(expiredReservations);
		MaintenanceSummary summary = new MaintenanceSummary(
				expiredReservations,
				closedOrders,
				idempotencyRecords,
				refreshTokens,
				outboxEvents,
				processedEvents);
		if (!summary.isEmpty()) {
			log.info("Maintenance sweep completed: {}", summary);
		}
		return summary;
	}

	/**
	 * Repeats a bounded statement until it stops filling its batch. The per-run cap keeps one
	 * sweep from monopolising a connection on a large backlog; hitting it is logged rather than
	 * passed over silently, so a persistent backlog is visible instead of looking like a clean run.
	 */
	private int drain(String step, IntSupplier batch) {
		int total = 0;
		for (int pass = 0; pass < properties.maxBatchesPerRun(); pass++) {
			int affected = batch.getAsInt();
			total += affected;
			if (affected < properties.batchSize()) {
				return total;
			}
		}
		log.warn(
				"Maintenance step {} stopped at its {}-batch cap after {} rows; rows remain for the next run",
				step,
				properties.maxBatchesPerRun(),
				total);
		return total;
	}
}
