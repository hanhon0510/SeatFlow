package com.seatflow.event;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EventSeatMapper {

	int insertForDraftEvent(@Param("eventId") UUID eventId);

	long countByEventId(@Param("eventId") UUID eventId);

	long countSourceSeatsForEvent(@Param("eventId") UUID eventId);

	long countMissingPricedSeatsForEvent(@Param("eventId") UUID eventId);

	List<EventSeatRecord> findByEventId(@Param("eventId") UUID eventId);

	List<EventSeatRecord> lockByIds(@Param("eventSeatIds") List<UUID> eventSeatIds);

	int markSold(@Param("id") UUID id);

	List<EventSeatLayoutRow> findPublishedLayoutByEventId(
			@Param("eventId") UUID eventId,
			@Param("sectionId") UUID sectionId);

	EventSeatHoldCandidate findHoldCandidate(
			@Param("eventId") UUID eventId,
			@Param("eventSeatId") UUID eventSeatId);

	List<EventSeatHoldCandidate> findHoldCandidates(
			@Param("eventId") UUID eventId,
			@Param("eventSeatIds") List<UUID> eventSeatIds);
}
