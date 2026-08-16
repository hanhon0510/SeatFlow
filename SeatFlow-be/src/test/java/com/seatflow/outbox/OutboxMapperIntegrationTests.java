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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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

	@Autowired
	private PlatformTransactionManager transactionManager;

	@BeforeEach
	void setUp() {
		cleanDatabase();
	}

	@AfterEach
	void tearDown() {
		cleanDatabase();
	}

	@Test
	void lockPendingSkipsRowsAlreadyLockedByAnotherPublisher() throws Exception {
		OutboxEventRecord first = event(NOW.minusSeconds(2));
		OutboxEventRecord second = event(NOW.minusSeconds(1));
		assertThat(outboxMapper.insert(first)).isEqualTo(1);
		assertThat(outboxMapper.insert(second)).isEqualTo(1);

		CountDownLatch firstPublisherLocked = new CountDownLatch(1);
		CountDownLatch releaseFirstPublisher = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<List<OutboxEventRecord>> firstLock = executor.submit(() -> inTransaction(() -> {
				List<OutboxEventRecord> locked = outboxMapper.lockPending(1);
				firstPublisherLocked.countDown();
				assertThat(releaseFirstPublisher.await(5, TimeUnit.SECONDS)).isTrue();
				return locked;
			}));
			assertThat(firstPublisherLocked.await(5, TimeUnit.SECONDS)).isTrue();

			Future<List<OutboxEventRecord>> secondLock = executor.submit(() -> inTransaction(
					() -> outboxMapper.lockPending(2)));

			List<OutboxEventRecord> secondPublisherRows = secondLock.get(5, TimeUnit.SECONDS);
			releaseFirstPublisher.countDown();
			List<OutboxEventRecord> firstPublisherRows = firstLock.get(5, TimeUnit.SECONDS);

			assertThat(firstPublisherRows).hasSize(1);
			assertThat(secondPublisherRows).hasSize(1);
			assertThat(secondPublisherRows.getFirst().id()).isNotEqualTo(firstPublisherRows.getFirst().id());
			assertThat(List.of(firstPublisherRows.getFirst().id(), secondPublisherRows.getFirst().id()))
					.containsExactlyInAnyOrder(first.id(), second.id());
		}
		finally {
			releaseFirstPublisher.countDown();
			executor.shutdownNow();
		}
	}

	private <T> T inTransaction(Callable<T> action) {
		return new TransactionTemplate(transactionManager).execute(status -> {
			try {
				return action.call();
			}
			catch (RuntimeException ex) {
				throw ex;
			}
			catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		});
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
