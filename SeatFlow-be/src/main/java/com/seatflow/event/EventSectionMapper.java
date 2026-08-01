package com.seatflow.event;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Param;

public interface EventSectionMapper {

	int insertForDraftEvent(EventSectionRecord section);

	int deleteByEventId(@Param("eventId") UUID eventId);

	List<EventSectionRecord> findByEventId(@Param("eventId") UUID eventId);
}
