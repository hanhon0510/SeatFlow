package com.seatflow.auth;

import java.time.Instant;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PasswordResetMapper {

	void insert(
			@Param("id") UUID id,
			@Param("userId") UUID userId,
			@Param("tokenHash") String tokenHash,
			@Param("expiresAt") Instant expiresAt,
			@Param("createdAt") Instant createdAt);

	/** Retires every link the user has not used yet, so only the newest one ever works. */
	int invalidateOutstanding(@Param("userId") UUID userId, @Param("now") Instant now);

	/**
	 * Marks a live link used and returns its user in one statement, so two requests with the same
	 * link cannot both succeed. Null when the link is unknown, expired or already used.
	 */
	UUID consume(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

	int updatePasswordHash(@Param("userId") UUID userId, @Param("passwordHash") String passwordHash);

	/**
	 * Signs the user out everywhere except the session identified by {@code keepTokenHash}. A null
	 * hash keeps nothing. Used when the user changes their password while signed in.
	 */
	int revokeOtherSessions(
			@Param("userId") UUID userId,
			@Param("keepTokenHash") String keepTokenHash,
			@Param("now") Instant now);

	int deleteExpired(@Param("before") Instant before, @Param("limit") int limit);
}
