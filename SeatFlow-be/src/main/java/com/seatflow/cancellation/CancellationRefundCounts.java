package com.seatflow.cancellation;

/**
 * Where the refunds of a cancelled event stand. {@code needsAttention} counts paid orders that
 * cannot be refunded automatically because some of their tickets were already used at the door.
 */
public record CancellationRefundCounts(long refunded, long remaining, long needsAttention) {
}
