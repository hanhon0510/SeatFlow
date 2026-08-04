package com.seatflow.event;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Param;

public interface EventMapper {

	void insert(EventRecord event);

	int update(EventRecord event);

	int publishDraft(@Param("id") UUID id);

	EventRecord findById(@Param("id") UUID id);

	EventRecord findByIdForUpdate(@Param("id") UUID id);

	List<EventRecord> findPage(@Param("limit") int limit, @Param("offset") long offset);

	long count();

	List<PublicEventCatalogRecord> findPublishedCatalogPage(@Param("query") PublicEventCatalogQuery query);

	long countPublishedCatalog(@Param("query") PublicEventCatalogQuery query);

	PublicEventCatalogRecord findPublishedCatalogById(@Param("id") UUID id);
}
