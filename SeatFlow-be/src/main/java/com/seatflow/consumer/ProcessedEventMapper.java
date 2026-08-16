package com.seatflow.consumer;

import java.time.Instant;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProcessedEventMapper {

	int insert(ProcessedEventRecord record);

	ProcessedEventRecord findByConsumerAndEvent(
			@Param("consumerName") String consumerName,
			@Param("eventId") UUID eventId);
}
