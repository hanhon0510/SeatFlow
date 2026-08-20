package com.seatflow.seatupdates;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class SeatStateBroadcaster implements SeatStateNotifier {

	private static final Logger log = LoggerFactory.getLogger(SeatStateBroadcaster.class);

	private final SimpMessagingTemplate messagingTemplate;
	private final Clock clock;

	public SeatStateBroadcaster(SimpMessagingTemplate messagingTemplate, Clock clock) {
		this.messagingTemplate = messagingTemplate;
		this.clock = clock;
	}

	@Override
	public void seatsHeld(UUID eventId, List<UUID> eventSeatIds) {
		broadcast(SeatStateChangeType.SEATS_HELD, eventId, eventSeatIds);
	}

	@Override
	public void seatsReleased(UUID eventId, List<UUID> eventSeatIds) {
		broadcast(SeatStateChangeType.SEATS_RELEASED, eventId, eventSeatIds);
	}

	@Override
	public void seatsSold(UUID eventId, List<UUID> eventSeatIds) {
		broadcast(SeatStateChangeType.SEATS_SOLD, eventId, eventSeatIds);
	}

	static String destination(UUID eventId) {
		return "/topic/events/%s/seats".formatted(eventId);
	}

	private void broadcast(SeatStateChangeType type, UUID eventId, List<UUID> eventSeatIds) {
		SeatStateUpdateMessage message =
				new SeatStateUpdateMessage(type, eventId, eventSeatIds, clock.instant());
		try {
			messagingTemplate.convertAndSend(destination(eventId), message);
		}
		catch (RuntimeException ex) {
			log.warn("Failed to broadcast {} for event {}", type, eventId, ex);
		}
	}
}
