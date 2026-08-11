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

@ExtendWith(MockitoExtension.class)
class SeatHoldReleaseListenerTests {

	@Mock
	private SeatHoldStore seatHoldStore;

	@InjectMocks
	private SeatHoldReleaseListener listener;

	@Test
	void releasesHoldAfterCommitEvent() {
		SeatHoldReleaseRequested event = releaseEvent();

		listener.release(event);

		verify(seatHoldStore).releaseHold(event.hold());
	}

	@Test
	void redisFailureDoesNotEscapeTheAfterCommitListener() {
		SeatHoldReleaseRequested event = releaseEvent();
		doThrow(new IllegalStateException("Redis unavailable"))
				.when(seatHoldStore)
				.releaseHold(event.hold());

		assertThatCode(() -> listener.release(event)).doesNotThrowAnyException();
		verify(seatHoldStore).releaseHold(event.hold());
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
