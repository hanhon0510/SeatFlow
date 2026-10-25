package com.seatflow.waitlist;

/**
 * What one background pass did.
 *
 * @param offered waiting entries that were offered seats and emailed
 * @param expired offers whose time ran out
 * @param closed entries ended because the sale closed or the event is no longer on sale
 */
public record WaitlistPassSummary(int offered, int expired, int closed) {
}
