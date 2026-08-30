package com.seatflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.seatflow.support.PostgresTestContainerSupport;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class DatabaseSchemaIntegrationTests extends PostgresTestContainerSupport {

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

	@Test
	void everyFlywayMigrationResourceWasAppliedSuccessfully() throws Exception {
		List<String> migrationScripts = Arrays.stream(new PathMatchingResourcePatternResolver()
						.getResources("classpath:db/migration/V*.sql"))
				.map(Resource::getFilename)
				.sorted()
				.toList();

		List<String> appliedScripts = jdbcTemplate.queryForList("""
				SELECT script
				FROM flyway_schema_history
				WHERE success = TRUE
				  AND type = 'SQL'
				ORDER BY installed_rank
				""", String.class);

		assertThat(appliedScripts).containsExactlyInAnyOrderElementsOf(migrationScripts);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE success = FALSE",
				Integer.class)).isZero();
	}

	@Test
	void importantIndexesAndConstraintsExistInPostgreSqlCatalog() {
		assertThat(existingConstraints())
				.contains(
						"tickets_order_event_seat_uq",
						"tickets_ticket_code_uq",
						"outbox_events_status_check",
						"outbox_events_event_version_check",
						"processed_events_consumer_event_uq",
						"order_paid_analytics_event_uq",
						"idempotency_records_scope_uq");
		assertThat(existingIndexes())
				.contains(
						"users_normalized_email_uq",
						"orders_active_reservation_uq",
						"outbox_events_pending_idx");
	}

	@Test
	void directSqlEnforcesCaseInsensitiveUserEmailUniqueness() {
		insertUser(UUID.randomUUID(), "SchemaCase@Test.com");

		assertThatThrownBy(() -> insertUser(UUID.randomUUID(), "schemacase@test.com"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("users_normalized_email_uq");
	}

	@Test
	void directSqlEnforcesTicketUniquenessAndStatusChecks() {
		TicketFixture fixture = insertTicketFixture();
		UUID firstTicketId = UUID.randomUUID();
		insertTicket(firstTicketId, fixture.orderId(), fixture.eventSeatId(), "schema-ticket-code-00000000000000000001");

		assertThatThrownBy(() -> insertTicket(
				UUID.randomUUID(),
				fixture.orderId(),
				fixture.eventSeatId(),
				"schema-ticket-code-00000000000000000002"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("tickets_order_event_seat_uq");

		assertThatThrownBy(() -> jdbcTemplate.update("""
				INSERT INTO tickets (id, order_id, event_seat_id, ticket_code, status, issued_at, created_at)
				VALUES (?, ?, ?, ?, 'VOID', ?, ?)
				""",
				UUID.randomUUID(),
				fixture.orderId(),
				fixture.secondEventSeatId(),
				"schema-ticket-code-00000000000000000003",
				OffsetDateTime.now(ZoneOffset.UTC),
				OffsetDateTime.now(ZoneOffset.UTC)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("tickets_status_check");
	}

	@Test
	void directSqlEnforcesOutboxAndProcessedEventConstraints() {
		UUID eventId = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO processed_events (id, consumer_name, event_id, processed_at)
				VALUES (?, 'schema-consumer', ?, ?)
				""", UUID.randomUUID(), eventId, OffsetDateTime.now(ZoneOffset.UTC));

		assertThatThrownBy(() -> jdbcTemplate.update("""
				INSERT INTO processed_events (id, consumer_name, event_id, processed_at)
				VALUES (?, 'schema-consumer', ?, ?)
				""", UUID.randomUUID(), eventId, OffsetDateTime.now(ZoneOffset.UTC)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("processed_events_consumer_event_uq");

		assertThatThrownBy(() -> jdbcTemplate.update("""
				INSERT INTO outbox_events (
				    id, aggregate_type, aggregate_id, event_type, event_version, payload,
				    correlation_id, status, attempt_count, created_at, published_at, next_attempt_at
				)
				VALUES (?, 'Order', ?, 'OrderPaid', 0, '{}'::jsonb, ?, 'PENDING', 0, ?, NULL, ?)
				""",
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				OffsetDateTime.now(ZoneOffset.UTC),
				OffsetDateTime.now(ZoneOffset.UTC)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("outbox_events_event_version_check");
	}

	private List<String> existingConstraints() {
		return jdbcTemplate.queryForList("""
				SELECT conname
				FROM pg_constraint
				WHERE connamespace = 'public'::regnamespace
				""", String.class);
	}

	private List<String> existingIndexes() {
		return jdbcTemplate.queryForList("""
				SELECT indexname
				FROM pg_indexes
				WHERE schemaname = 'public'
				""", String.class);
	}

	private TicketFixture insertTicketFixture() {
		UUID userId = UUID.randomUUID();
		UUID venueId = UUID.randomUUID();
		UUID sectionId = UUID.randomUUID();
		UUID firstSeatId = UUID.randomUUID();
		UUID secondSeatId = UUID.randomUUID();
		UUID eventId = UUID.randomUUID();
		UUID firstEventSeatId = UUID.randomUUID();
		UUID secondEventSeatId = UUID.randomUUID();
		UUID reservationId = UUID.randomUUID();
		UUID orderId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

		insertUser(userId, "schema-ticket-%s@example.com".formatted(userId));
		jdbcTemplate.update("""
				INSERT INTO venues (id, name, address, city, country, timezone)
				VALUES (?, 'Schema Hall', '1 Schema Street', 'Ho Chi Minh City', 'Vietnam', 'Asia/Ho_Chi_Minh')
				""", venueId);
		jdbcTemplate.update("""
				INSERT INTO venue_sections (id, venue_id, name, display_order)
				VALUES (?, ?, 'Orchestra', 1)
				""", sectionId, venueId);
		jdbcTemplate.update("""
				INSERT INTO seats (id, section_id, row_label, seat_number, seat_label)
				VALUES (?, ?, 'A', 1, 'A1'), (?, ?, 'A', 2, 'A2')
				""", firstSeatId, sectionId, secondSeatId, sectionId);
		jdbcTemplate.update("""
				INSERT INTO events (
				    id, venue_id, name, description, start_time, sales_start_time, sales_end_time, status
				)
				VALUES (?, ?, 'Schema Event', 'Schema integration test', ?, ?, ?, 'PUBLISHED')
				""", eventId, venueId, now.plusSeconds(86_400), now.minusSeconds(3_600), now.plusSeconds(3_600));
		jdbcTemplate.update("""
				INSERT INTO event_seats (id, event_id, seat_id, price)
				VALUES (?, ?, ?, 125000.00), (?, ?, ?, 125000.00)
				""", firstEventSeatId, eventId, firstSeatId, secondEventSeatId, eventId, secondSeatId);
		jdbcTemplate.update("""
				INSERT INTO reservations (
				    id, user_id, event_id, hold_id, status, expires_at, total_amount, created_at, updated_at
				)
				VALUES (?, ?, ?, ?, 'PENDING_PAYMENT', ?, 250000.00, ?, ?)
				""", reservationId, userId, eventId, UUID.randomUUID(), now.plusSeconds(600), now, now);
		jdbcTemplate.update("""
				INSERT INTO orders (
				    id, reservation_id, user_id, status, total_amount, currency, created_at, updated_at
				)
				VALUES (?, ?, ?, 'PAID', 250000.00, 'VND', ?, ?)
				""", orderId, reservationId, userId, now, now);

		return new TicketFixture(orderId, firstEventSeatId, secondEventSeatId);
	}

	private void insertTicket(UUID ticketId, UUID orderId, UUID eventSeatId, String ticketCode) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update("""
				INSERT INTO tickets (id, order_id, event_seat_id, ticket_code, status, issued_at, created_at)
				VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
				""", ticketId, orderId, eventSeatId, ticketCode, now, now);
	}

	private void insertUser(UUID userId, String email) {
		jdbcTemplate.update("""
				INSERT INTO users (id, email, password_hash)
				VALUES (?, ?, '{bcrypt}hash')
				""", userId, email);
	}

	private void cleanDatabase() {
		jdbcTemplate.update("DELETE FROM order_paid_analytics");
		jdbcTemplate.update("DELETE FROM processed_events");
		jdbcTemplate.update("DELETE FROM idempotency_records");
		jdbcTemplate.update("DELETE FROM tickets");
		jdbcTemplate.update("DELETE FROM outbox_events");
		jdbcTemplate.update("DELETE FROM payments");
		jdbcTemplate.update("DELETE FROM orders");
		jdbcTemplate.update("DELETE FROM reservation_items");
		jdbcTemplate.update("DELETE FROM reservations");
		jdbcTemplate.update("DELETE FROM refresh_tokens");
		jdbcTemplate.update("DELETE FROM event_seats");
		jdbcTemplate.update("DELETE FROM event_sections");
		jdbcTemplate.update("DELETE FROM events");
		jdbcTemplate.update("DELETE FROM seats");
		jdbcTemplate.update("DELETE FROM venue_sections");
		jdbcTemplate.update("DELETE FROM venues");
		jdbcTemplate.update("DELETE FROM users");
	}

	private record TicketFixture(UUID orderId, UUID eventSeatId, UUID secondEventSeatId) {
	}
}
