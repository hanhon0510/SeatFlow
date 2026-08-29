package com.seatflow.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.seatflow.support.PostgresTestContainerSupport;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class OutboxMapperIntegrationTests extends PostgresTestContainerSupport {

	private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

	@Autowired
	private OutboxMapper outboxMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		cleanDatabase();
	}

	@AfterEach
	void tearDown() {
		cleanDatabase();
	}

	/**
	 * The claim has to hide rows from other pollers without an enclosing transaction — that is
	 * the whole point of the lease. If this ever needed a transaction again, the publisher would
	 * be back to holding one open across Kafka round trips.
	 */
	@Test
	void claimPendingLeasesRowsWithoutHoldingATransactionOpen() {
		OutboxEventRecord first = event(NOW.minusSeconds(2));
		OutboxEventRecord second = event(NOW.minusSeconds(1));
		assertThat(outboxMapper.insert(first)).isEqualTo(1);
		assertThat(outboxMapper.insert(second)).isEqualTo(1);

		Instant leaseUntil = NOW.plusSeconds(60);
		List<OutboxEventRecord> firstClaim = outboxMapper.claimPending(1, NOW, leaseUntil);

		assertThat(firstClaim).hasSize(1);
		assertThat(firstClaim.getFirst().id()).isEqualTo(first.id());
		assertThat(firstClaim.getFirst().nextAttemptAt()).isEqualTo(leaseUntil);

		List<OutboxEventRecord> secondClaim = outboxMapper.claimPending(5, NOW, leaseUntil);

		assertThat(secondClaim).hasSize(1);
		assertThat(secondClaim.getFirst().id()).isEqualTo(second.id());
		assertThat(outboxMapper.claimPending(5, NOW, leaseUntil)).isEmpty();
	}

	@Test
	void claimPendingReclaimsRowsOnceTheLeaseLapses() {
		OutboxEventRecord pending = event(NOW.minusSeconds(2));
		assertThat(outboxMapper.insert(pending)).isEqualTo(1);

		Instant leaseUntil = NOW.plusSeconds(60);
		assertThat(outboxMapper.claimPending(5, NOW, leaseUntil)).hasSize(1);
		assertThat(outboxMapper.claimPending(5, NOW.plusSeconds(30), leaseUntil)).isEmpty();

		// A publisher that dies mid-pass must not strand its batch.
		assertThat(outboxMapper.claimPending(5, NOW.plusSeconds(61), NOW.plusSeconds(121))).hasSize(1);
	}

	@Test
	void concurrentClaimsNeverHandTheSameRowToTwoPublishers() throws Exception {
		OutboxEventRecord first = event(NOW.minusSeconds(2));
		OutboxEventRecord second = event(NOW.minusSeconds(1));
		assertThat(outboxMapper.insert(first)).isEqualTo(1);
		assertThat(outboxMapper.insert(second)).isEqualTo(1);

		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Callable<List<OutboxEventRecord>> claim = () -> {
				assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
				return outboxMapper.claimPending(2, NOW, NOW.plusSeconds(60));
			};
			Future<List<OutboxEventRecord>> left = executor.submit(claim);
			Future<List<OutboxEventRecord>> right = executor.submit(claim);
			start.countDown();

			List<UUID> claimed = Stream
					.concat(left.get(10, TimeUnit.SECONDS).stream(), right.get(10, TimeUnit.SECONDS).stream())
					.map(OutboxEventRecord::id)
					.toList();

			assertThat(claimed).doesNotHaveDuplicates();
			assertThat(claimed).containsExactlyInAnyOrder(first.id(), second.id());
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void deletePublishedBeforeRemovesOnlySettledRowsPastTheThreshold() {
		OutboxEventRecord stillPending = event(NOW.minusSeconds(600));
		assertThat(outboxMapper.insert(stillPending)).isEqualTo(1);
		OutboxEventRecord published = event(NOW.minusSeconds(600));
		assertThat(outboxMapper.insert(published)).isEqualTo(1);
		assertThat(outboxMapper.markPublished(published.id(), NOW.minusSeconds(300))).isEqualTo(1);

		assertThat(outboxMapper.deletePublishedBefore(NOW.minusSeconds(400), 100)).isZero();
		assertThat(outboxMapper.deletePublishedBefore(NOW.minusSeconds(200), 100)).isEqualTo(1);

		assertThat(outboxMapper.findById(published.id())).isNull();
		assertThat(outboxMapper.findById(stillPending.id())).isNotNull();
	}

	private static OutboxEventRecord event(Instant createdAt) {
		return new OutboxEventRecord(
				UUID.randomUUID(),
				"Order",
				UUID.randomUUID(),
				"OrderPaid",
				1,
				"{}",
				UUID.randomUUID(),
				OutboxEventStatus.PENDING,
				0,
				createdAt,
				null,
				NOW.minusSeconds(1));
	}

	private void cleanDatabase() {
		jdbcTemplate.update("DELETE FROM outbox_events");
	}
}
