package com.seatflow.order;

import java.math.BigDecimal;
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
			@Param("promoCodeId") UUID promoCodeId,
			@Param("promoCode") String promoCode,
			@Param("discountAmount") BigDecimal discountAmount,
			@Param("now") Instant now);

	OrderRecord findActiveByReservationAndUser(
			@Param("reservationId") UUID reservationId,
			@Param("userId") UUID userId);

	OrderRecord findByIdAndUser(
			@Param("id") UUID id,
			@Param("userId") UUID userId);

	/** Locks the order row for the rest of the transaction, whoever owns it. */
	OrderRecord findByIdForUpdate(@Param("id") UUID id);

	/** Compare-and-set on status for staff actions, which act on any user's order. */
	int transition(
			@Param("id") UUID id,
			@Param("expectedStatus") OrderStatus expectedStatus,
			@Param("newStatus") OrderStatus newStatus,
			@Param("updatedAt") Instant updatedAt);

	long countByUser(@Param("userId") UUID userId);

	List<OrderRecord> findPageByUser(
			@Param("userId") UUID userId,
			@Param("limit") int limit,
			@Param("offset") long offset);

	int closeForClosedReservations(
			@Param("now") Instant now,
			@Param("limit") int limit);

	int updateStatus(
			@Param("id") UUID id,
			@Param("userId") UUID userId,
			@Param("expectedStatus") OrderStatus expectedStatus,
			@Param("newStatus") OrderStatus newStatus,
			@Param("updatedAt") Instant updatedAt);
}
