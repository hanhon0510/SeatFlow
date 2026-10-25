package com.seatflow.waitlist;

public record WaitlistStatusCount(WaitlistStatus status, long entries, long seats) {
}
