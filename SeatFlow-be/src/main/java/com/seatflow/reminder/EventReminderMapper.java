package com.seatflow.reminder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EventReminderMapper {

	EventReminderSettingsRecord findSettings(@Param("eventId") UUID eventId);

	int upsertSettings(
			@Param("eventId") UUID eventId,
			@Param("enabled") boolean enabled,
			@Param("hoursBefore") int hoursBefore,
			@Param("updatedBy") UUID updatedBy,
			@Param("updatedAt") Instant updatedAt);

	/**
	 * Paid orders whose reminder window has opened and who have not been reminded. Orders placed
	 * after the window opened are left out: they were told everything when they bought.
	 */
	List<UUID> findDueOrderIds(
			@Param("now") Instant now,
			@Param("defaultHoursBefore") int defaultHoursBefore,
			@Param("limit") int limit);

	/** Returns 1 for the caller that gets to send this order's reminder, 0 if it was already claimed. */
	int claim(@Param("orderId") UUID orderId, @Param("sentAt") Instant sentAt);

	long countSent(@Param("eventId") UUID eventId);
}
