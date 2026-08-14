package com.seatflow.ticket;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TicketMapper {

	int insert(
			@Param("id") UUID id,
			@Param("orderId") UUID orderId,
			@Param("eventSeatId") UUID eventSeatId,
			@Param("ticketCode") String ticketCode,
			@Param("issuedAt") Instant issuedAt,
			@Param("createdAt") Instant createdAt);

	TicketRecord findByOrderAndEventSeat(
			@Param("orderId") UUID orderId,
			@Param("eventSeatId") UUID eventSeatId);

	TicketDetailRecord findDetailByIdAndUser(
			@Param("id") UUID id,
			@Param("userId") UUID userId);

	List<TicketDetailRecord> findDetailsByUser(@Param("userId") UUID userId);
}
