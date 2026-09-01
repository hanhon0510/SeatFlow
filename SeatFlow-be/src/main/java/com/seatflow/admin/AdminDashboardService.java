package com.seatflow.admin;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.seatflow.order.OrderStatus;
import com.seatflow.ticket.TicketStatus;

@Service
public class AdminDashboardService {

	/** An event starting inside this window is the one an admin still has time to act on. */
	private static final Duration STARTING_SOON = Duration.ofDays(7);
	private static final int UPCOMING_EVENT_LIMIT = 5;

	private final AdminDashboardMapper adminDashboardMapper;
	private final Clock clock;

	public AdminDashboardService(AdminDashboardMapper adminDashboardMapper, Clock clock) {
		this.adminDashboardMapper = adminDashboardMapper;
		this.clock = clock;
	}

	@PreAuthorize("hasRole('ADMIN')")
	public AdminDashboardResponse getDashboard() {
		Instant now = clock.instant();

		return new AdminDashboardResponse(
				adminDashboardMapper.findVenueSummary(),
				adminDashboardMapper.findEventSummary(now, now.plus(STARTING_SOON)),
				sales(),
				adminDashboardMapper.findUpcomingEvents(now, UPCOMING_EVENT_LIMIT),
				now);
	}

	private AdminDashboardSales sales() {
		return new AdminDashboardSales(
				adminDashboardMapper.countOrdersByStatus(OrderStatus.PAID.name()),
				adminDashboardMapper.countOrdersByStatus(OrderStatus.PENDING.name()),
				adminDashboardMapper.countTicketsByStatus(TicketStatus.ACTIVE.name()),
				adminDashboardMapper.countTicketsByStatus(TicketStatus.USED.name()),
				adminDashboardMapper.findRevenueByCurrency());
	}
}
