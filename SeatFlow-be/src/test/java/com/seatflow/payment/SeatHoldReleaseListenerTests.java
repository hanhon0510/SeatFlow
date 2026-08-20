package com.seatflow.payment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.seatflow.hold.SeatHoldRecord;
import com.seatflow.hold.SeatHoldStore;
import com.seatflow.seatupdates.SeatStateNotifier;

@ExtendWith(MockitoExtension.class)
class SeatHoldReleaseListenerTests {

	@Mock
	private SeatHoldStore seatHoldStore;

	@Mock
	private SeatStateNotifier seatStateNotifier;

	@InjectMocks
	private SeatHoldReleaseListener listener;

	@Test
	void releasesHoldAfterCommitEvent() {
		SeatHoldReleaseRequested event = releaseEvent();

		listener.release(event);

		verify(seatHoldStore).releaseHold(event.hold());
		verify(seatStateNotifier).seatsSold(event.hold().eventId(), event.hold().eventSeatIds());
	}

	@Test
	void redisFailureDoesNotEscapeTheAfterCommitListener() {
		SeatHoldReleaseRequested event = releaseEvent();
		doThrow(new IllegalStateException("Redis unavailable"))
				.when(seatHoldStore)
				.releaseHold(event.hold());

		assertThatCode(() -> listener.release(event)).doesNotThrowAnyException();
		verify(seatHoldStore).releaseHold(event.hold());
		verify(seatStateNotifier).seatsSold(event.hold().eventId(), event.hold().eventSeatIds());
	}

	@Test
	void websocketFailureDoesNotEscapeTheAfterCommitListener() {
		SeatHoldReleaseRequested event = releaseEvent();
		doThrow(new IllegalStateException("WebSocket unavailable"))
				.when(seatStateNotifier)
				.seatsSold(event.hold().eventId(), event.hold().eventSeatIds());

		assertThatCode(() -> listener.release(event)).doesNotThrowAnyException();
		verify(seatHoldStore).releaseHold(event.hold());
		verify(seatStateNotifier).seatsSold(event.hold().eventId(), event.hold().eventSeatIds());
	}

	private static SeatHoldReleaseRequested releaseEvent() {
		return new SeatHoldReleaseRequested(new SeatHoldRecord(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				Instant.now().plusSeconds(60)));
	}
}
