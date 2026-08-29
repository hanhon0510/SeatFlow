package com.seatflow.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OutboxMapper {

	int insert(OutboxEventRecord event);

	List<OutboxEventRecord> claimPending(
			@Param("batchSize") int batchSize,
			@Param("now") Instant now,
			@Param("leaseUntil") Instant leaseUntil);

	long countPending();

	int deletePublishedBefore(
			@Param("threshold") Instant threshold,
			@Param("limit") int limit);

	int markPublished(
			@Param("id") UUID id,
			@Param("publishedAt") Instant publishedAt);

	int scheduleRetry(
			@Param("id") UUID id,
			@Param("nextAttemptAt") Instant nextAttemptAt);

	OutboxEventRecord findById(@Param("id") UUID id);
}
