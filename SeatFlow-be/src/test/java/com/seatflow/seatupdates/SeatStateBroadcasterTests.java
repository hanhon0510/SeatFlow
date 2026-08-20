package com.seatflow.seatupdates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class SeatStateBroadcasterTests {

	private static final UUID EVENT_ID = UUID.fromString("5d2b80f6-4a0e-4f91-9676-e2c2d8b59e42");
	private static final UUID EVENT_SEAT_ID = UUID.fromString("0ab96cb6-0b8b-4db4-855e-1bd12f3fc0e5");
	private static final UUID EVENT_SEAT_ID_2 = UUID.fromString("252d8a6e-5dab-4ab2-bcb1-c4baeb017fd2");
	private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");

	@Mock
	private SimpMessagingTemplate messagingTemplate;

	@Test
	void destinationIsScopedByEventForSubscriptions() {
		assertThat(SeatStateBroadcaster.destination(EVENT_ID))
				.isEqualTo("/topic/events/5d2b80f6-4a0e-4f91-9676-e2c2d8b59e42/seats");
	}

	@Test
	void broadcastsAllSeatStateTypesToEventTopic() {
		SeatStateBroadcaster broadcaster = broadcaster();
		List<UUID> eventSeatIds = List.of(EVENT_SEAT_ID, EVENT_SEAT_ID_2);

		broadcaster.seatsHeld(EVENT_ID, eventSeatIds);
		broadcaster.seatsReleased(EVENT_ID, eventSeatIds);
		broadcaster.seatsSold(EVENT_ID, eventSeatIds);

		ArgumentCaptor<SeatStateUpdateMessage> messageCaptor =
				ArgumentCaptor.forClass(SeatStateUpdateMessage.class);
		verify(messagingTemplate, times(3))
				.convertAndSend(eq(SeatStateBroadcaster.destination(EVENT_ID)), messageCaptor.capture());
		assertThat(messageCaptor.getAllValues())
				.extracting(SeatStateUpdateMessage::type)
				.containsExactly(
						SeatStateChangeType.SEATS_HELD,
						SeatStateChangeType.SEATS_RELEASED,
						SeatStateChangeType.SEATS_SOLD);
		assertThat(messageCaptor.getAllValues())
				.allSatisfy(message -> {
					assertThat(message.eventId()).isEqualTo(EVENT_ID);
					assertThat(message.eventSeatIds()).containsExactly(EVENT_SEAT_ID, EVENT_SEAT_ID_2);
					assertThat(message.occurredAt()).isEqualTo(NOW);
				});
	}

	@Test
	void messageDoesNotExposePrivateIdentifiers() {
		List<String> fields = Arrays.stream(SeatStateUpdateMessage.class.getRecordComponents())
				.map(RecordComponent::getName)
				.toList();

		assertThat(fields).containsExactly("type", "eventId", "eventSeatIds", "occurredAt");
	}

	@Test
	void brokerFailureDoesNotEscapeBroadcaster() {
		SeatStateBroadcaster broadcaster = broadcaster();
		doThrow(new IllegalStateException("broker unavailable"))
				.when(messagingTemplate)
				.convertAndSend(anyString(), any(SeatStateUpdateMessage.class));

		assertThatCode(() -> broadcaster.seatsHeld(EVENT_ID, List.of(EVENT_SEAT_ID)))
				.doesNotThrowAnyException();
	}

	private SeatStateBroadcaster broadcaster() {
		return new SeatStateBroadcaster(messagingTemplate, Clock.fixed(NOW, ZoneOffset.UTC));
	}
}
