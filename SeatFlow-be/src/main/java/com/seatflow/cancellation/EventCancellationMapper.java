package com.seatflow.cancellation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
interface EventCancellationMapper {

	/** Records the cancellation; keeps the first record if the event was somehow cancelled already. */
	int insert(EventCancellationRecord cancellation);

	EventCancellationRecord findByEventId(@Param("eventId") UUID eventId);

	List<OpenHold> findOpenHolds(@Param("eventId") UUID eventId);

	int cancelPendingOrders(@Param("eventId") UUID eventId, @Param("now") Instant now);

	int cancelOpenReservations(@Param("eventId") UUID eventId, @Param("now") Instant now);

	/** Paid orders of the event that can be refunded automatically, oldest first. */
	List<UUID> findRefundableOrderIds(@Param("eventId") UUID eventId, @Param("limit") int limit);

	CancellationRefundCounts refundCounts(@Param("eventId") UUID eventId);
}
