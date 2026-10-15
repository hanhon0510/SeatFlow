package com.seatflow.refund;

import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RefundMapper {

	int insert(RefundRecord refund);

	RefundRecord findByOrderId(@Param("orderId") UUID orderId);
}
