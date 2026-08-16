package com.seatflow.consumer;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderPaidAnalyticsMapper {

	int insert(OrderPaidAnalyticsRecord record);
}
