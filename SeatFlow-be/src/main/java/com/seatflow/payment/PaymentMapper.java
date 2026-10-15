package com.seatflow.payment;

import java.time.Instant;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentMapper {

	int insertPending(
			@Param("id") UUID id,
			@Param("orderId") UUID orderId,
			@Param("userId") UUID userId,
			@Param("providerReference") String providerReference,
			@Param("now") Instant now);

	PaymentRecord findById(@Param("id") UUID id);

	/** The payment that settled the order; a database index guarantees there is at most one. */
	PaymentRecord findSucceededByOrderId(@Param("orderId") UUID orderId);

	int updateStatus(
			@Param("id") UUID id,
			@Param("expectedStatus") PaymentStatus expectedStatus,
			@Param("newStatus") PaymentStatus newStatus,
			@Param("failureReason") String failureReason,
			@Param("updatedAt") Instant updatedAt);

}
