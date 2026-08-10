package com.seatflow.order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderMapper {

	int insertPending(
			@Param("id") UUID id,
			@Param("reservationId") UUID reservationId,
			@Param("userId") UUID userId,
			@Param("currency") String currency,
			@Param("now") Instant now);

	OrderRecord findActiveByReservationAndUser(
			@Param("reservationId") UUID reservationId,
			@Param("userId") UUID userId);

	OrderRecord findByIdAndUser(
			@Param("id") UUID id,
			@Param("userId") UUID userId);

	long countByUser(@Param("userId") UUID userId);

	List<OrderRecord> findPageByUser(
			@Param("userId") UUID userId,
			@Param("limit") int limit,
			@Param("offset") long offset);
}
