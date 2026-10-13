package com.seatflow.order;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seatflow.promo.PromoCodeRecord;
import com.seatflow.promo.PromoCodeService;
import com.seatflow.reservation.ReservationItemMapper;
import com.seatflow.reservation.ReservationItemRecord;
import com.seatflow.reservation.ReservationMapper;
import com.seatflow.reservation.ReservationNotFoundException;
import com.seatflow.reservation.ReservationRecord;
import com.seatflow.reservation.ReservationStatus;

@Service
public class OrderService {

	public static final int DEFAULT_PAGE_SIZE = 20;
	public static final int MAX_PAGE_SIZE = 100;

	private static final String CURRENCY = "VND";

	private final OrderMapper orderMapper;
	private final ReservationMapper reservationMapper;
	private final ReservationItemMapper reservationItemMapper;
	private final PromoCodeService promoCodeService;
	private final Clock clock;

	public OrderService(
			OrderMapper orderMapper,
			ReservationMapper reservationMapper,
			ReservationItemMapper reservationItemMapper,
			PromoCodeService promoCodeService,
			Clock clock) {
		this.orderMapper = orderMapper;
		this.reservationMapper = reservationMapper;
		this.reservationItemMapper = reservationItemMapper;
		this.promoCodeService = promoCodeService;
		this.clock = clock;
	}

	@Transactional
	public OrderResponse createOrder(UUID userId, OrderCreateRequest request) {
		OrderRecord existing = orderMapper.findActiveByReservationAndUser(request.reservationId(), userId);
		if (existing != null) {
			return responseForOwner(existing, userId);
		}

		ReservationRecord reservation = reservationMapper.findByIdAndUser(request.reservationId(), userId);
		if (reservation == null) {
			throw new ReservationNotFoundException();
		}

		Instant now = clock.instant();
		if (reservation.status() != ReservationStatus.PENDING_PAYMENT
				|| !reservation.expiresAt().isAfter(now)) {
			throw new OrderConflictException();
		}

		// An order that already exists is returned above untouched, so retrying a checkout never
		// redeems the code twice. Only a brand-new order claims a use.
		PromoCodeRecord promo = null;
		BigDecimal discount = BigDecimal.ZERO;
		if (request.hasPromoCode()) {
			promo = promoCodeService.lockForRedemption(request.promoCode(), reservation.eventId());
			// Priced from the line items, like the order total itself (see OrderMapper.insertPending).
			BigDecimal subtotal = reservationItemMapper.findByReservationId(reservation.id()).stream()
					.map(ReservationItemRecord::price)
					.reduce(BigDecimal.ZERO, BigDecimal::add);
			discount = promo.discountFor(subtotal);
		}

		int insertedRows = orderMapper.insertPending(
				UUID.randomUUID(),
				reservation.id(),
				userId,
				CURRENCY,
				promo == null ? null : promo.id(),
				promo == null ? null : promo.code(),
				discount,
				now);
		OrderRecord order = orderMapper.findActiveByReservationAndUser(reservation.id(), userId);
		if (order == null || (insertedRows != 0 && insertedRows != 1)) {
			throw new OrderConflictException();
		}
		return responseForOwner(order, userId);
	}

	@Transactional(readOnly = true)
	public OrderResponse getOrder(UUID orderId, UUID userId) {
		OrderRecord order = orderMapper.findByIdAndUser(orderId, userId);
		if (order == null) {
			throw new OrderNotFoundException();
		}
		return responseForOwner(order, userId);
	}

	@Transactional(readOnly = true)
	public OrderPageResponse listUserOrders(UUID userId, int page, int size) {
		validatePagination(page, size);
		long totalItems = orderMapper.countByUser(userId);
		List<OrderRecord> orders = orderMapper.findPageByUser(userId, size, (long) page * size);
		orders.forEach(order -> validateOwner(order.userId(), userId));
		return OrderPageResponse.from(orders, page, size, totalItems);
	}

	private static OrderResponse responseForOwner(OrderRecord order, UUID userId) {
		validateOwner(order.userId(), userId);
		return OrderResponse.from(order);
	}

	private static void validatePagination(int page, int size) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new InvalidOrderPaginationException();
		}
	}

	private static void validateOwner(UUID ownerId, UUID userId) {
		if (!ownerId.equals(userId)) {
			throw new AccessDeniedException("Order belongs to another user");
		}
	}
}
