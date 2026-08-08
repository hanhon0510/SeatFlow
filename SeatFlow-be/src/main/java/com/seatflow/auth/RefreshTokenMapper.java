package com.seatflow.auth;

import java.time.Instant;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RefreshTokenMapper {

	void insert(RefreshTokenRecord refreshToken);

	RefreshTokenRecord findActiveByHash(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

	RefreshTokenRecord findByHash(@Param("tokenHash") String tokenHash);

	int revoke(@Param("id") UUID id, @Param("revokedAt") Instant revokedAt);

	int revokeAllUserTokens(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);

	int deleteExpired(@Param("now") Instant now);
}
