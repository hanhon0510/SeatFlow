package com.seatflow.auth;

import java.time.Instant;

public record IssuedRefreshToken(String token, Instant expiresAt) {
}
