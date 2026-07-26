package com.seatflow.auth;

import java.time.Instant;
import java.util.UUID;

public record RefreshTokenRecord(
		UUID id,
		UUID userId,
		String tokenHash,
		Instant expiresAt,
		Instant revokedAt,
		Instant createdAt) {

	public static RefreshTokenRecord forInsert(UUID id, UUID userId, String tokenHash, Instant expiresAt) {
		return new RefreshTokenRecord(id, userId, tokenHash, expiresAt, null, null);
	}
}
