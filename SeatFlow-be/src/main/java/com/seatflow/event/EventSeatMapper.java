package com.seatflow.event;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Param;

public interface EventSeatMapper {

	int insertForDraftEvent(@Param("eventId") UUID eventId);

	long countByEventId(@Param("eventId") UUID eventId);

	long countSourceSeatsForEvent(@Param("eventId") UUID eventId);

	long countMissingPricedSeatsForEvent(@Param("eventId") UUID eventId);

	List<EventSeatRecord> findByEventId(@Param("eventId") UUID eventId);

	List<EventSeatLayoutRow> findPublishedLayoutByEventId(@Param("eventId") UUID eventId);

	EventSeatHoldCandidate findHoldCandidate(
			@Param("eventId") UUID eventId,
			@Param("eventSeatId") UUID eventSeatId);
}
