package com.seatflow.reservation;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReservationItemMapper {

	int batchInsert(@Param("items") List<ReservationItemRecord> items);

	List<ReservationItemRecord> findByReservationId(@Param("reservationId") UUID reservationId);
}
