package com.seatflow.reservation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReservationMapper {

	int insert(ReservationRecord reservation);

	ReservationRecord findByHoldId(@Param("holdId") UUID holdId);

	ReservationRecord findByIdAndUser(
			@Param("id") UUID id,
			@Param("userId") UUID userId);

	List<ReservationSeatPrice> findSeatPricesForEvent(
			@Param("eventId") UUID eventId,
			@Param("eventSeatIds") List<UUID> eventSeatIds);

	int updateStatus(
			@Param("id") UUID id,
			@Param("expectedStatus") ReservationStatus expectedStatus,
			@Param("newStatus") ReservationStatus newStatus,
			@Param("updatedAt") Instant updatedAt);

	int closeLapsed(
			@Param("now") Instant now,
			@Param("limit") int limit);
}
