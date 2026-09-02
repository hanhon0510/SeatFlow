package com.seatflow.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EventSalesMapper {

	EventSalesEventResponse findEvent(@Param("eventId") UUID eventId, @Param("now") Instant now);

	EventSalesInventoryResponse findInventory(@Param("eventId") UUID eventId, @Param("now") Instant now);

	List<EventSalesRevenueResponse> findRevenueByCurrency(@Param("eventId") UUID eventId);

	EventSalesOrderCounts findOrderCounts(@Param("eventId") UUID eventId);

	List<EventSalesRecentOrderResponse> findRecentOrders(
			@Param("eventId") UUID eventId,
			@Param("limit") int limit);

	EventSalesTicketsResponse findTicketCounts(@Param("eventId") UUID eventId);

	List<EventSalesSectionResponse> findSections(@Param("eventId") UUID eventId);

	/** The latest order behind each seat of the event, for the admin seat map. */
	List<AdminSeatOrderResponse> findSeatOrders(@Param("eventId") UUID eventId);

	List<EventSalesDailyPointResponse> findDailySales(
			@Param("eventId") UUID eventId,
			@Param("from") Instant from);
}
