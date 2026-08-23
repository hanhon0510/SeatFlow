package com.seatflow.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.seatflow.event.EventSeatStatus;
import com.seatflow.hold.SeatHoldNotFoundException;
import com.seatflow.hold.SeatHoldResponse;
import com.seatflow.hold.SeatHoldService;
import com.seatflow.observability.BusinessMetrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTests {

	private static final Instant NOW = Instant.parse("2026-08-09T10:00:00Z");
	private static final Instant EXPIRES_AT = NOW.plusSeconds(300);
	private static final UUID USER_ID = UUID.fromString("93d8e036-c1f7-47a3-ab6e-1f74e6dfe673");
	private static final UUID OTHER_USER_ID = UUID.fromString("01f15873-3624-4e23-9a2e-28fc393e6540");
	private static final UUID EVENT_ID = UUID.fromString("fb34247c-6350-4673-92ce-aea2c58096ce");
	private static final UUID HOLD_ID = UUID.fromString("5172f79e-3586-48d6-ae4a-8059f1433e69");
	private static final UUID FIRST_EVENT_SEAT_ID = UUID.fromString("2ca709d5-d889-458d-8f72-b0ca7886ff4a");
	private static final UUID SECOND_EVENT_SEAT_ID = UUID.fromString("94243a96-fae2-4d3f-b98a-c3b3fc4b3181");

	@Mock
	private ReservationMapper reservationMapper;

	@Mock
	private ReservationItemMapper reservationItemMapper;

	@Mock
	private SeatHoldService seatHoldService;

	private SimpleMeterRegistry meterRegistry;

	private ReservationService reservationService;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		reservationService = new ReservationService(
				reservationMapper,
				reservationItemMapper,
				seatHoldService,
				Clock.fixed(NOW, ZoneOffset.UTC),
				new BusinessMetrics(meterRegistry));
	}

	@Test
	void validHoldCreatesPendingReservationUsingStoredPrices() {
		SeatHoldResponse hold = activeHold();
		when(seatHoldService.getHold(HOLD_ID, USER_ID)).thenReturn(hold);
		when(reservationMapper.findSeatPricesForEvent(EVENT_ID, hold.eventSeatIds()))
				.thenReturn(List.of(
						seatPrice(SECOND_EVENT_SEAT_ID, "85000.25"),
						seatPrice(FIRST_EVENT_SEAT_ID, "125000.50")));
		when(reservationMapper.insert(any(ReservationRecord.class))).thenReturn(1);
		when(reservationItemMapper.batchInsert(anyList())).thenAnswer(invocation -> invocation.<List<?>>getArgument(0).size());

		ReservationResponse response = reservationService.createReservation(
				USER_ID,
				new ReservationCreateRequest(HOLD_ID));

		assertThat(response.userId()).isEqualTo(USER_ID);
		assertThat(response.eventId()).isEqualTo(EVENT_ID);
		assertThat(response.holdId()).isEqualTo(HOLD_ID);
		assertThat(response.status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
		assertThat(response.expiresAt()).isEqualTo(EXPIRES_AT);
		assertThat(response.totalAmount()).isEqualByComparingTo("210000.75");
		assertThat(response.items()).extracting(ReservationItemResponse::eventSeatId)
				.containsExactly(FIRST_EVENT_SEAT_ID, SECOND_EVENT_SEAT_ID);
		assertThat(response.items()).extracting(ReservationItemResponse::price)
				.containsExactly(new BigDecimal("125000.50"), new BigDecimal("85000.25"));
		assertThat(response.createdAt()).isEqualTo(NOW);
		assertThat(response.updatedAt()).isEqualTo(NOW);
		assertThat(meterRegistry.get("reservation_created").counter().count()).isEqualTo(1);
	}

	@Test
	void repeatedRequestReturnsExistingReservationWithoutRevalidatingHold() {
		ReservationRecord existing = existingReservation(USER_ID);
		ReservationItemRecord item = new ReservationItemRecord(
				UUID.randomUUID(),
				existing.id(),
				FIRST_EVENT_SEAT_ID,
				new BigDecimal("125000.50"),
				NOW);
		when(reservationMapper.findByHoldId(HOLD_ID)).thenReturn(existing);
		when(reservationItemMapper.findByReservationId(existing.id())).thenReturn(List.of(item));

		ReservationResponse response = reservationService.createReservation(
				USER_ID,
				new ReservationCreateRequest(HOLD_ID));

		assertThat(response.id()).isEqualTo(existing.id());
		verify(seatHoldService, never()).getHold(any(), any());
		verify(reservationMapper, never()).insert(any());
		verify(reservationItemMapper, never()).batchInsert(anyList());
	}

	@Test
	void existingReservationIsNotExposedToAnotherUser() {
		when(reservationMapper.findByHoldId(HOLD_ID)).thenReturn(existingReservation(USER_ID));

		assertThatThrownBy(() -> reservationService.createReservation(
				OTHER_USER_ID,
				new ReservationCreateRequest(HOLD_ID)))
				.isInstanceOf(AccessDeniedException.class);

		verify(reservationItemMapper, never()).findByReservationId(any());
		verify(seatHoldService, never()).getHold(any(), any());
	}

	@Test
	void foreignHoldIsRejectedBeforeDatabaseWrites() {
		when(seatHoldService.getHold(HOLD_ID, OTHER_USER_ID))
				.thenThrow(new AccessDeniedException("Hold belongs to another user"));

		assertThatThrownBy(() -> reservationService.createReservation(
				OTHER_USER_ID,
				new ReservationCreateRequest(HOLD_ID)))
				.isInstanceOf(AccessDeniedException.class);

		verify(reservationMapper, never()).insert(any());
		verify(reservationItemMapper, never()).batchInsert(anyList());
	}

	@Test
	void expiredHoldIsRejectedBeforePricesAreLoaded() {
		SeatHoldResponse expiredHold = new SeatHoldResponse(
				HOLD_ID,
				EVENT_ID,
				FIRST_EVENT_SEAT_ID,
				List.of(FIRST_EVENT_SEAT_ID),
				USER_ID,
				NOW);
		when(seatHoldService.getHold(HOLD_ID, USER_ID)).thenReturn(expiredHold);

		assertThatThrownBy(() -> reservationService.createReservation(
				USER_ID,
				new ReservationCreateRequest(HOLD_ID)))
				.isInstanceOf(SeatHoldNotFoundException.class);

		verify(reservationMapper, never()).findSeatPricesForEvent(any(), anyList());
		verify(reservationMapper, never()).insert(any());
	}

	@Test
	void missingOrUnavailableSeatPriceRejectsReservation() {
		SeatHoldResponse hold = activeHold();
		when(seatHoldService.getHold(HOLD_ID, USER_ID)).thenReturn(hold);
		when(reservationMapper.findSeatPricesForEvent(EVENT_ID, hold.eventSeatIds()))
				.thenReturn(List.of(seatPrice(FIRST_EVENT_SEAT_ID, "125000.50")));

		assertThatThrownBy(() -> reservationService.createReservation(
				USER_ID,
				new ReservationCreateRequest(HOLD_ID)))
				.isInstanceOf(ReservationConflictException.class);

		verify(reservationMapper, never()).insert(any());
	}

	private static SeatHoldResponse activeHold() {
		return new SeatHoldResponse(
				HOLD_ID,
				EVENT_ID,
				FIRST_EVENT_SEAT_ID,
				List.of(FIRST_EVENT_SEAT_ID, SECOND_EVENT_SEAT_ID),
				USER_ID,
				EXPIRES_AT);
	}

	private static ReservationSeatPrice seatPrice(UUID eventSeatId, String price) {
		return new ReservationSeatPrice(
				eventSeatId,
				EVENT_ID,
				new BigDecimal(price),
				EventSeatStatus.AVAILABLE);
	}

	private static ReservationRecord existingReservation(UUID userId) {
		return ReservationRecord.pending(
				UUID.randomUUID(),
				userId,
				EVENT_ID,
				HOLD_ID,
				EXPIRES_AT,
				new BigDecimal("125000.50"),
				NOW);
	}
}
