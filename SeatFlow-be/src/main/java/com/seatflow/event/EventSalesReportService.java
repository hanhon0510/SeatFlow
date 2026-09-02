package com.seatflow.event;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class EventSalesReportService {

	/** Far enough back to show whether demand is still moving, short enough to stay readable. */
	private static final Duration TREND_WINDOW = Duration.ofDays(30);
	private static final int RECENT_ORDER_LIMIT = 10;

	private final EventSalesMapper eventSalesMapper;
	private final Clock clock;

	public EventSalesReportService(EventSalesMapper eventSalesMapper, Clock clock) {
		this.eventSalesMapper = eventSalesMapper;
		this.clock = clock;
	}

	@PreAuthorize("hasRole('ADMIN')")
	public EventSalesReportResponse getSalesReport(UUID eventId) {
		Instant now = clock.instant();
		EventSalesEventResponse event = eventSalesMapper.findEvent(eventId, now);
		if (event == null) {
			throw new EventNotFoundException();
		}

		return new EventSalesReportResponse(
				event,
				eventSalesMapper.findInventory(eventId, now),
				eventSalesMapper.findRevenueByCurrency(eventId),
				new EventSalesOrdersResponse(
						eventSalesMapper.findOrderCounts(eventId),
						eventSalesMapper.findRecentOrders(eventId, RECENT_ORDER_LIMIT)),
				eventSalesMapper.findTicketCounts(eventId),
				eventSalesMapper.findSections(eventId),
				eventSalesMapper.findDailySales(eventId, now.minus(TREND_WINDOW)),
				now);
	}
}
