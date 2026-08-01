package com.seatflow.event;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Param;

public interface EventMapper {

	void insert(EventRecord event);

	int update(EventRecord event);

	EventRecord findById(@Param("id") UUID id);

	List<EventRecord> findPage(@Param("limit") int limit, @Param("offset") long offset);

	long count();
}
