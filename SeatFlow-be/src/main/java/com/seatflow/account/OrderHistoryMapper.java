package com.seatflow.account;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
interface OrderHistoryMapper {

	/** The user's orders, newest first. */
	List<OrderHistoryRow> findPage(
			@Param("userId") UUID userId,
			@Param("limit") int limit,
			@Param("offset") long offset);

	/** The seats of the given orders in one query; the user id guards against ids that are not theirs. */
	List<OrderHistorySeatRow> findSeats(
			@Param("userId") UUID userId,
			@Param("orderIds") Collection<UUID> orderIds);
}
