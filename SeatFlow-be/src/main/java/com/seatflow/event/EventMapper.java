package com.seatflow.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EventMapper {

	void insert(EventRecord event);

	int update(EventRecord event);

	int publishDraft(@Param("id") UUID id);

	/** Moves a draft or published event to CANCELLED; returns 0 when it is in any other state. */
	int cancel(@Param("id") UUID id);

	EventRecord findById(@Param("id") UUID id);

	EventRecord findByIdForUpdate(@Param("id") UUID id);

	/**
	 * Takes a shared lock on the event row: payments hold it while they settle, so cancelling the
	 * event (which needs the exclusive lock) waits for them and every later payment sees the new status.
	 */
	EventRecord findByIdForShare(@Param("id") UUID id);

	List<EventRecord> findPage(@Param("limit") int limit, @Param("offset") long offset);

	long count();

	List<PublicEventCatalogRecord> findPublishedCatalogPage(@Param("query") PublicEventCatalogQuery query);

	long countPublishedCatalog(@Param("query") PublicEventCatalogQuery query);

	PublicEventCatalogRecord findPublishedCatalogById(@Param("id") UUID id, @Param("now") Instant now);
}
