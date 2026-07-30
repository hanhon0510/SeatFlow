package com.seatflow.venue;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Param;

public interface VenueMapper {

	void insert(VenueRecord venue);

	int update(VenueRecord venue);

	VenueRecord findById(@Param("id") UUID id);

	List<VenueRecord> findPage(@Param("limit") int limit, @Param("offset") long offset);

	long count();

	int archive(@Param("id") UUID id);
}
