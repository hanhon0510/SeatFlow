package com.seatflow.notification;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderNotificationMapper {

	/** Null when the order (or its buyer) no longer exists. */
	OrderEmailDetails findOrderDetails(@Param("orderId") UUID orderId);

	/** The order's seats, including tickets that have since been voided. */
	List<OrderEmailSeat> findSeats(@Param("orderId") UUID orderId);
}
