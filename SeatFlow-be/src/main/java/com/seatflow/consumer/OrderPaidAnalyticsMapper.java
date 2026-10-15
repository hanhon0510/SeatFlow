package com.seatflow.consumer;

import java.time.Instant;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderPaidAnalyticsMapper {

	int insert(OrderPaidAnalyticsRecord record);

	/** Flags the order refunded and returns how many analytics rows were touched. */
	int markRefunded(@Param("orderId") UUID orderId, @Param("refundedAt") Instant refundedAt);
}
