package com.seatflow.payment;

import com.seatflow.hold.SeatHoldRecord;

public record SeatHoldReleaseRequested(SeatHoldRecord hold) {
}
