package com.seatflow.account;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seatflow.order.InvalidOrderPaginationException;
import com.seatflow.order.OrderMapper;

/** Reads a buyer's own orders, newest first, with the seats and any refund of each. */
@Service
public class OrderHistoryService {

	public static final int DEFAULT_PAGE_SIZE = 10;
	public static final int MAX_PAGE_SIZE = 50;

	private final OrderHistoryMapper historyMapper;
	private final OrderMapper orderMapper;

	OrderHistoryService(OrderHistoryMapper historyMapper, OrderMapper orderMapper) {
		this.historyMapper = historyMapper;
		this.orderMapper = orderMapper;
	}

	@Transactional(readOnly = true)
	public OrderHistoryPage list(UUID userId, int page, int size) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new InvalidOrderPaginationException();
		}
		long totalItems = orderMapper.countByUser(userId);
		List<OrderHistoryRow> rows = historyMapper.findPage(userId, size, (long) page * size);

		// One query for the seats of the whole page rather than one per order.
		Map<UUID, List<OrderHistoryItem.Seat>> seatsByOrder = new LinkedHashMap<>();
		if (!rows.isEmpty()) {
			List<UUID> orderIds = rows.stream().map(OrderHistoryRow::orderId).toList();
			for (OrderHistorySeatRow seat : historyMapper.findSeats(userId, orderIds)) {
				seatsByOrder.computeIfAbsent(seat.orderId(), id -> new ArrayList<>()).add(new OrderHistoryItem.Seat(
						seat.ticketId(), seat.ticketStatus(), seat.sectionName(), seat.rowLabel(), seat.seatLabel()));
			}
		}

		List<OrderHistoryItem> items = rows.stream()
				.map(row -> item(row, seatsByOrder.getOrDefault(row.orderId(), List.of())))
				.toList();
		int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / size);
		return new OrderHistoryPage(items, page, size, totalItems, totalPages);
	}

	private static OrderHistoryItem item(OrderHistoryRow row, List<OrderHistoryItem.Seat> seats) {
		OrderHistoryItem.Refund refund = row.refundedAt() == null
				? null
				: new OrderHistoryItem.Refund(row.refundAmount(), row.refundReason(), row.refundedAt());
		return new OrderHistoryItem(
				row.orderId(),
				row.status(),
				row.totalAmount(),
				row.discountAmount(),
				row.promoCode(),
				row.currency(),
				row.createdAt(),
				row.updatedAt(),
				new OrderHistoryItem.Event(
						row.eventId(),
						row.eventName(),
						row.eventStartTime(),
						row.venueName(),
						row.venueTimezone(),
						row.eventCancelled(),
						row.cancellationReason()),
				seats,
				refund);
	}
}
