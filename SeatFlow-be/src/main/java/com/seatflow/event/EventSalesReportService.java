package com.seatflow.event;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
				heatmap(eventId),
				eventSalesMapper.findDailySales(eventId, now.minus(TREND_WINDOW)),
				now);
	}

	/**
	 * Folds the flat section/row aggregates into one entry per section. The query already
	 * orders by section then row, so a linked map preserves that order without re-sorting.
	 */
	private List<EventSalesHeatmapSectionResponse> heatmap(UUID eventId) {
		Map<UUID, List<EventSalesHeatmapRecord>> rowsBySection = eventSalesMapper.findHeatmapRows(eventId).stream()
				.collect(Collectors.groupingBy(
						EventSalesHeatmapRecord::sectionId,
						LinkedHashMap::new,
						Collectors.toList()));

		return rowsBySection.values().stream()
				.map(EventSalesReportService::section)
				.toList();
	}

	private static EventSalesHeatmapSectionResponse section(List<EventSalesHeatmapRecord> rows) {
		return new EventSalesHeatmapSectionResponse(
				rows.getFirst().sectionId(),
				rows.getFirst().sectionName(),
				rows.stream()
						.map(row -> new EventSalesHeatmapRowResponse(
								row.rowLabel(),
								row.seatsTotal(),
								row.seatsAvailable(),
								row.seatsSold(),
								row.seatsBlocked()))
						.toList());
	}
}
