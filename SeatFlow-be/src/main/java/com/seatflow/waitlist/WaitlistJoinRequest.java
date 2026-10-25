package com.seatflow.waitlist;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** The upper bound is the seat-hold limit, which the service checks against its configuration. */
public record WaitlistJoinRequest(@NotNull @Min(1) Integer seatCount) {
}
