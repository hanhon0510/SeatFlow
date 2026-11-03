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

	List<TicketRecord> findByOrderId(@Param("orderId") UUID orderId);

	/** Voids every unused ticket of the order and returns how many were voided. */
	int cancelActiveByOrderId(@Param("orderId") UUID orderId);

	TicketDetailRecord findDetailByIdAndUser(
			@Param("id") UUID id,
			@Param("userId") UUID userId);

	List<TicketDetailRecord> findDetailsByUser(@Param("userId") UUID userId);

	/** Hands an ACTIVE ticket from its holder to another user under a new code; 0 when it is no longer theirs to give. */
	int transfer(
			@Param("id") UUID id,
			@Param("fromUserId") UUID fromUserId,
			@Param("toUserId") UUID toUserId,
			@Param("ticketCode") String ticketCode);

	void insertTransfer(
			@Param("id") UUID id,
			@Param("ticketId") UUID ticketId,
			@Param("fromUserId") UUID fromUserId,
			@Param("toUserId") UUID toUserId,
			@Param("transferredAt") Instant transferredAt);

	List<TicketTransferRecord> findTransfersByEvent(@Param("eventId") UUID eventId);
}
