package com.seatflow.checkin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CheckInMapper {

	/** Finds the scanned ticket by its code; {@code ticketId} narrows it further when the QR carried one. */
	CheckInTicket findByCode(@Param("code") String code, @Param("ticketId") UUID ticketId);

	/** Admits the holder: only an ACTIVE ticket can become USED, which is what stops a second entry. */
	int markUsed(
			@Param("ticketId") UUID ticketId,
			@Param("checkedInBy") UUID checkedInBy,
			@Param("usedAt") Instant usedAt);

	CheckInCounts countsForEvent(@Param("eventId") UUID eventId);

	List<CheckInEntry> findRecentForEvent(@Param("eventId") UUID eventId, @Param("limit") int limit);
}
