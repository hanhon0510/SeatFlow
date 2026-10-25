package com.seatflow.waitlist;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.seatflow.notification.WaitlistOfferEmailDetails;

@Mapper
public interface WaitlistMapper {

	int insert(@Param("entry") WaitlistEntryRecord entry);

	/** The user's WAITING or OFFERED entry for the event, or null. */
	WaitlistEntryRecord findActive(@Param("eventId") UUID eventId, @Param("userId") UUID userId);

	long countWaiting(@Param("eventId") UUID eventId);

	/** Waiting entries that joined before the given one. */
	long countWaitingAhead(
			@Param("eventId") UUID eventId,
			@Param("createdAt") Instant createdAt,
			@Param("id") UUID id);

	List<UUID> findOfferedEventSeatIds(@Param("entryId") UUID entryId);

	/** Leaves the list from either active state. Returns the number of entries ended (0 or 1). */
	int cancelActive(@Param("eventId") UUID eventId, @Param("userId") UUID userId, @Param("now") Instant now);

	/**
	 * Marks the user's still-open offer as taken up when the seats they are holding include any
	 * of the offered ones. Returns the number of offers closed.
	 */
	int fulfillOffer(
			@Param("eventId") UUID eventId,
			@Param("userId") UUID userId,
			@Param("eventSeatIds") List<UUID> eventSeatIds,
			@Param("now") Instant now);

	/** Seats in an offer that is still open, whoever it was made to. */
	List<WaitlistOfferedSeat> findOpenOfferedSeats(@Param("eventId") UUID eventId, @Param("now") Instant now);

	List<WaitlistStatusCount> countByStatus(@Param("eventId") UUID eventId);

	// --- background pass ---

	/** Takes a lock held until the transaction ends, so two instances never offer for one event at once. */
	Integer lockEvent(@Param("eventId") UUID eventId);

	List<UUID> findEventIdsWithWaiting(@Param("now") Instant now, @Param("limit") int limit);

	/** WAITING entries in the order they joined. */
	List<WaitlistEntryRecord> findWaiting(@Param("eventId") UUID eventId, @Param("limit") int limit);

	List<WaitlistFreeSeat> findFreeSeatCandidates(
			@Param("eventId") UUID eventId,
			@Param("now") Instant now,
			@Param("limit") int limit);

	/** Returns 1 when the entry was still WAITING and is now OFFERED. */
	int markOffered(
			@Param("id") UUID id,
			@Param("offeredAt") Instant offeredAt,
			@Param("offerExpiresAt") Instant offerExpiresAt);

	int insertOfferSeats(@Param("entryId") UUID entryId, @Param("eventSeatIds") List<UUID> eventSeatIds);

	/** Offers whose time has run out, oldest first. */
	List<WaitlistEntryRecord> findLapsedOffers(@Param("now") Instant now, @Param("limit") int limit);

	/** Returns 1 when the entry was still OFFERED and is now EXPIRED. */
	int expireOffer(@Param("id") UUID id, @Param("now") Instant now);

	/** Ends entries of events that are no longer on sale. */
	int closeEnded(@Param("now") Instant now, @Param("limit") int limit);

	/** Null when the entry or its user no longer exists. */
	WaitlistOfferEmailDetails findOfferEmailDetails(@Param("entryId") UUID entryId);
}
