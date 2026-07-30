package com.seatflow.seating;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Param;

public interface VenueSectionMapper {

	void insert(VenueSectionRecord section);

	int update(VenueSectionRecord section);

	int delete(@Param("id") UUID id);

	VenueSectionRecord findById(@Param("id") UUID id);

	List<VenueSectionRecord> findByVenueId(@Param("venueId") UUID venueId);
}
