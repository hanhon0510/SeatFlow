package com.seatflow.analytics;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Every query takes the half-open range [from, to) and an optional event to narrow it to. */
@Mapper
public interface AnalyticsMapper {

	List<AnalyticsSalesRow> findSalesRows(
			@Param("from") Instant from,
			@Param("to") Instant to,
			@Param("eventId") UUID eventId);

	AnalyticsCheckout findCheckout(
			@Param("from") Instant from,
			@Param("to") Instant to,
			@Param("eventId") UUID eventId);

	AnalyticsWaitlist findWaitlist(
			@Param("from") Instant from,
			@Param("to") Instant to,
			@Param("eventId") UUID eventId);
}
