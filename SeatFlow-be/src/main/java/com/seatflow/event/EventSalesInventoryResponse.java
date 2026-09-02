package com.seatflow.event;

import java.math.BigDecimal;

/**
 * Seat inventory for one event. {@code seatsInCheckout} counts seats held by a reservation that
 * has not been paid for yet, which is why they are neither sold nor free to sell to anyone else.
 */
public record EventSalesInventoryResponse(
		long seatsTotal,
		long seatsAvailable,
		long seatsSold,
		long seatsBlocked,
		long seatsInCheckout,
		BigDecimal inventoryValue,
		BigDecimal soldValue) {
}
