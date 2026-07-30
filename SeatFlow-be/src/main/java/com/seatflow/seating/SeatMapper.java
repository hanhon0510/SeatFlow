package com.seatflow.seating;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Param;

public interface SeatMapper {

	void insert(SeatRecord seat);

	void insertBatch(@Param("seats") List<SeatRecord> seats);

	SeatRecord findById(@Param("id") UUID id);

	List<SeatRecord> findBySectionId(@Param("sectionId") UUID sectionId);

	long countBySectionId(@Param("sectionId") UUID sectionId);

	List<SeatLayoutRow> findSeatLayoutByVenueId(@Param("venueId") UUID venueId);
}
