package com.seatflow.admin;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminDashboardMapper {

	AdminDashboardVenues findVenueSummary();

	AdminDashboardEvents findEventSummary(
			@Param("now") Instant now,
			@Param("startingSoonBefore") Instant startingSoonBefore);

	long countOrdersByStatus(@Param("status") String status);

	long countTicketsByStatus(@Param("status") String status);

	List<AdminDashboardRevenue> findRevenueByCurrency();

	List<AdminDashboardUpcomingEvent> findUpcomingEvents(
			@Param("now") Instant now,
			@Param("limit") int limit);
}
