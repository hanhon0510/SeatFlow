package com.seatflow.idempotency;

import java.time.Instant;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IdempotencyMapper {

	int deleteExpiredByScope(
			@Param("userId") UUID userId,
			@Param("operation") IdempotencyOperation operation,
			@Param("idempotencyKey") String idempotencyKey,
			@Param("now") Instant now);

	int deleteExpired(
			@Param("now") Instant now,
			@Param("limit") int limit);

	int insert(IdempotencyRecord record);

	IdempotencyRecord findByScope(
			@Param("userId") UUID userId,
			@Param("operation") IdempotencyOperation operation,
			@Param("idempotencyKey") String idempotencyKey);

	int complete(
			@Param("id") UUID id,
			@Param("responseStatus") int responseStatus,
			@Param("responseBody") String responseBody);
}
