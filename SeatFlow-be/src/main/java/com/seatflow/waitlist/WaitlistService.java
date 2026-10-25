package com.seatflow.waitlist;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.seatflow.event.EventMapper;
import com.seatflow.event.EventNotFoundException;
import com.seatflow.event.EventRecord;
import com.seatflow.event.EventStatus;
import com.seatflow.hold.SeatHoldProperties;
import com.seatflow.hold.SeatHoldStorageException;
import com.seatflow.hold.SeatHoldStore;
import com.seatflow.notification.EmailDeliveryException;
import com.seatflow.notification.EmailGateway;
import com.seatflow.notification.WaitlistEmailTemplates;
import com.seatflow.notification.WaitlistOfferEmailDetails;
import com.seatflow.observability.BusinessMetrics;
import com.seatflow.seatupdates.SeatStateNotifier;

/**
 * A queue of buyers for events that have no seats left.
 *
 * <p>Seats come back by themselves: a hold lapses in Redis, an order is refunded. Nothing tells
 * this service when that happens, so a background pass looks for free seats instead. When it finds
 * enough for the first buyer in line it picks the seats, sets them aside for that buyer for a
 * short window (nobody else can hold them) and emails them. The buyer then holds those seats like
 * any others; that closes the offer. If they do not, the offer lapses and the seats go to the
 * next buyer.
 *
 * <p>A buyer who wants more seats than are free keeps their place and is passed over until enough
 * seats come back, so one large group cannot hold up everyone behind it.
 */
@Service
public class WaitlistService {

	private static final Logger log = LoggerFactory.getLogger(WaitlistService.class);

	/** Bound on how many batches one pass closes for each reason. */
	private static final int CLEANUP_ROUNDS = 20;

	private final WaitlistMapper waitlistMapper;
	private final EventMapper eventMapper;
	private final SeatHoldStore seatHoldStore;
	private final SeatHoldProperties holdProperties;
	private final WaitlistProperties properties;
	private final SeatStateNotifier seatStateNotifier;
	private final WaitlistEmailTemplates templates;
	private final EmailGateway emailGateway;
	private final BusinessMetrics businessMetrics;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	public WaitlistService(
			WaitlistMapper waitlistMapper,
			EventMapper eventMapper,
			SeatHoldStore seatHoldStore,
			SeatHoldProperties holdProperties,
			WaitlistProperties properties,
			SeatStateNotifier seatStateNotifier,
			WaitlistEmailTemplates templates,
			EmailGateway emailGateway,
			BusinessMetrics businessMetrics,
			TransactionTemplate transactionTemplate,
			Clock clock) {
		this.waitlistMapper = waitlistMapper;
		this.eventMapper = eventMapper;
		this.seatHoldStore = seatHoldStore;
		this.holdProperties = holdProperties;
		this.properties = properties;
		this.seatStateNotifier = seatStateNotifier;
		this.templates = templates;
		this.emailGateway = emailGateway;
		this.businessMetrics = businessMetrics;
		this.transactionTemplate = transactionTemplate;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public WaitlistStatusResponse getStatus(UUID eventId, UUID userId) {
		EventRecord event = requireEvent(eventId);
		Instant now = clock.instant();
		WaitlistEntryRecord entry = waitlistMapper.findActive(eventId, userId);
		if (entry == null) {
			boolean joinable = onSale(event, now) && freeSeats(eventId, now).isEmpty();
			return new WaitlistStatusResponse(
					eventId, WaitlistState.NONE, null, null, null, waitlistMapper.countWaiting(eventId),
					null, List.of(), joinable);
		}
		return status(entry);
	}

	@Transactional
	public WaitlistStatusResponse join(UUID eventId, UUID userId, WaitlistJoinRequest request) {
		int seatCount = request.seatCount();
		if (seatCount < 1 || seatCount > holdProperties.maxSeats()) {
			throw new InvalidWaitlistRequestException();
		}
		EventRecord event = requireEvent(eventId);
		Instant now = clock.instant();
		if (!onSale(event, now)) {
			throw new WaitlistUnavailableException();
		}
		if (waitlistMapper.findActive(eventId, userId) != null) {
			throw new WaitlistAlreadyJoinedException();
		}
		// Joining makes sense only when the buyer cannot just book what they want.
		if (freeSeats(eventId, now).size() >= seatCount) {
			throw new WaitlistUnavailableException();
		}
		WaitlistEntryRecord entry = WaitlistEntryRecord.waiting(UUID.randomUUID(), eventId, userId, seatCount, now);
		try {
			waitlistMapper.insert(entry);
		}
		catch (DuplicateKeyException ex) {
			// Two requests from the same buyer raced; the unique index let only one in.
			throw new WaitlistAlreadyJoinedException();
		}
		businessMetrics.waitlistJoined();
		return status(entry);
	}

	/** Leaving is idempotent: leaving a list you are not on does nothing. */
	@Transactional
	public void leave(UUID eventId, UUID userId) {
		Instant now = clock.instant();
		WaitlistEntryRecord entry = waitlistMapper.findActive(eventId, userId);
		if (entry == null) {
			return;
		}
		List<UUID> offeredSeatIds = entry.status() == WaitlistStatus.OFFERED
				? waitlistMapper.findOfferedEventSeatIds(entry.id())
				: List.of();
		if (waitlistMapper.cancelActive(eventId, userId, now) == 1) {
			notifyReleased(eventId, offeredSeatIds);
		}
	}

	@Transactional(readOnly = true)
	public WaitlistSummaryResponse getSummary(UUID eventId) {
		requireEvent(eventId);
		Map<WaitlistStatus, WaitlistStatusCount> counts = new EnumMap<>(WaitlistStatus.class);
		for (WaitlistStatusCount count : waitlistMapper.countByStatus(eventId)) {
			counts.put(count.status(), count);
		}
		return new WaitlistSummaryResponse(
				eventId,
				entries(counts, WaitlistStatus.WAITING),
				seats(counts, WaitlistStatus.WAITING),
				entries(counts, WaitlistStatus.OFFERED),
				entries(counts, WaitlistStatus.FULFILLED),
				entries(counts, WaitlistStatus.EXPIRED),
				entries(counts, WaitlistStatus.CANCELLED));
	}

	/** One pass of the background job. */
	public WaitlistPassSummary processWaitlists() {
		Instant now = clock.instant();
		int expired = expireLapsedOffers(now);
		int closed = drain(() -> waitlistMapper.closeEnded(now, properties.batchSize()));
		int offered = 0;
		for (UUID eventId : waitlistMapper.findEventIdsWithWaiting(now, properties.batchSize())) {
			try {
				offered += offerForEvent(eventId, now);
			}
			catch (RuntimeException ex) {
				// One event with a problem (say, Redis down) must not starve the others.
				log.warn("Could not process the waitlist of event {}; it will be retried", eventId, ex);
			}
		}
		return new WaitlistPassSummary(offered, expired, closed);
	}

	private int expireLapsedOffers(Instant now) {
		int expired = 0;
		for (int round = 0; round < CLEANUP_ROUNDS; round++) {
			List<WaitlistEntryRecord> lapsed = waitlistMapper.findLapsedOffers(now, properties.batchSize());
			for (WaitlistEntryRecord entry : lapsed) {
				List<UUID> seatIds = waitlistMapper.findOfferedEventSeatIds(entry.id());
				if (waitlistMapper.expireOffer(entry.id(), now) == 1) {
					expired++;
					notifyReleased(entry.eventId(), seatIds);
				}
			}
			if (lapsed.size() < properties.batchSize()) {
				break;
			}
		}
		return expired;
	}

	private int offerForEvent(UUID eventId, Instant now) {
		Set<UUID> skipped = new HashSet<>();
		int offered = 0;
		for (int i = 0; i < properties.batchSize(); i++) {
			OfferOutcome outcome;
			try {
				outcome = transactionTemplate.execute(status -> offerNext(eventId, now, skipped));
			}
			catch (OfferFailedException ex) {
				// The entry stays WAITING and is tried again on the next pass.
				log.warn("Could not offer waitlist seats to entry {}; it will be retried", ex.entryId, ex.getCause());
				skipped.add(ex.entryId);
				continue;
			}
			if (outcome == null || outcome.entryId() == null) {
				break;
			}
			offered++;
			businessMetrics.waitlistOffered();
			notifyHeld(eventId, outcome.eventSeatIds());
		}
		return offered;
	}

	/**
	 * Offers the free seats to the first buyer in line they are enough for. The event lock is held
	 * for the whole transaction, so two instances running the job cannot hand the same seats to
	 * two buyers.
	 */
	private OfferOutcome offerNext(UUID eventId, Instant now, Set<UUID> skipped) {
		waitlistMapper.lockEvent(eventId);
		List<WaitlistFreeSeat> free = freeSeats(eventId, now);
		if (free.isEmpty()) {
			return OfferOutcome.NOTHING;
		}
		for (WaitlistEntryRecord entry : waitlistMapper.findWaiting(eventId, properties.batchSize())) {
			if (skipped.contains(entry.id()) || free.size() < entry.seatCount()) {
				continue;
			}
			List<UUID> seatIds = pick(free, entry.seatCount()).stream().map(WaitlistFreeSeat::eventSeatId).toList();
			Instant expiresAt = now.plus(properties.offerWindow());
			if (waitlistMapper.markOffered(entry.id(), now, expiresAt) != 1) {
				continue;
			}
			waitlistMapper.insertOfferSeats(entry.id(), seatIds);
			send(entry.id());
			return new OfferOutcome(entry.id(), seatIds);
		}
		return OfferOutcome.NOTHING;
	}

	/** Sent inside the transaction: a failed send undoes the offer instead of leaving it unannounced. */
	private void send(UUID entryId) {
		WaitlistOfferEmailDetails details = waitlistMapper.findOfferEmailDetails(entryId);
		if (details == null) {
			throw new OfferFailedException(entryId, new IllegalStateException("Waitlist entry has no buyer"));
		}
		try {
			emailGateway.send(templates.seatsAvailable(details, properties.offerWindow()));
		}
		catch (EmailDeliveryException ex) {
			businessMetrics.emailFailed();
			throw new OfferFailedException(entryId, ex);
		}
		businessMetrics.emailSent();
		log.info("Offered waitlist seats entryId={} eventId={}", entryId, details.eventId());
	}

	/**
	 * Free means on sale, not set aside for an offer, and not held by anyone right now. Looks at
	 * no more than the scan limit, so past that the answer is simply "plenty".
	 */
	private List<WaitlistFreeSeat> freeSeats(UUID eventId, Instant now) {
		List<WaitlistFreeSeat> candidates = waitlistMapper.findFreeSeatCandidates(eventId, now, properties.scanLimit());
		if (candidates.isEmpty()) {
			return candidates;
		}
		Map<UUID, UUID> held = findHolds(eventId, candidates.stream().map(WaitlistFreeSeat::eventSeatId).toList());
		return candidates.stream().filter(seat -> !held.containsKey(seat.eventSeatId())).toList();
	}

	private Map<UUID, UUID> findHolds(UUID eventId, List<UUID> eventSeatIds) {
		try {
			Map<UUID, UUID> holds = seatHoldStore.findActiveSeatHoldOwners(eventId, eventSeatIds);
			return holds == null ? Map.of() : holds;
		}
		catch (RuntimeException ex) {
			throw new SeatHoldStorageException(ex);
		}
	}

	/** Takes the first run of neighbouring seats in one row that is long enough, else the first seats there are. */
	static List<WaitlistFreeSeat> pick(List<WaitlistFreeSeat> free, int count) {
		int runStart = 0;
		for (int i = 1; i <= free.size(); i++) {
			boolean continues = i < free.size() && nextTo(free.get(i - 1), free.get(i));
			if (!continues) {
				if (i - runStart >= count) {
					return new ArrayList<>(free.subList(runStart, runStart + count));
				}
				runStart = i;
			}
		}
		return new ArrayList<>(free.subList(0, count));
	}

	private static boolean nextTo(WaitlistFreeSeat previous, WaitlistFreeSeat next) {
		return previous.sectionId().equals(next.sectionId())
				&& previous.rowLabel().equals(next.rowLabel())
				&& next.seatNumber() == previous.seatNumber() + 1;
	}

	private WaitlistStatusResponse status(WaitlistEntryRecord entry) {
		long queueLength = waitlistMapper.countWaiting(entry.eventId());
		if (entry.status() == WaitlistStatus.OFFERED) {
			return new WaitlistStatusResponse(
					entry.eventId(), WaitlistState.OFFERED, entry.id(), entry.seatCount(), null, queueLength,
					entry.offerExpiresAt(), waitlistMapper.findOfferedEventSeatIds(entry.id()), false);
		}
		int position = (int) waitlistMapper.countWaitingAhead(entry.eventId(), entry.createdAt(), entry.id()) + 1;
		return new WaitlistStatusResponse(
				entry.eventId(), WaitlistState.WAITING, entry.id(), entry.seatCount(), position, queueLength,
				null, List.of(), false);
	}

	private static boolean onSale(EventRecord event, Instant now) {
		return event.status() == EventStatus.PUBLISHED
				&& !now.isBefore(event.salesStartTime())
				&& now.isBefore(event.salesEndTime());
	}

	private EventRecord requireEvent(UUID eventId) {
		EventRecord event = eventMapper.findById(eventId);
		if (event == null) {
			throw new EventNotFoundException();
		}
		return event;
	}

	private int drain(IntSupplier step) {
		int total = 0;
		for (int round = 0; round < CLEANUP_ROUNDS; round++) {
			int changed = step.getAsInt();
			total += changed;
			if (changed < properties.batchSize()) {
				break;
			}
		}
		return total;
	}

	private void notifyHeld(UUID eventId, List<UUID> eventSeatIds) {
		try {
			seatStateNotifier.seatsHeld(eventId, eventSeatIds);
		}
		catch (RuntimeException ex) {
			log.warn("Failed to broadcast waitlist seats for event {}", eventId, ex);
		}
	}

	private void notifyReleased(UUID eventId, List<UUID> eventSeatIds) {
		if (eventSeatIds.isEmpty()) {
			return;
		}
		try {
			seatStateNotifier.seatsReleased(eventId, eventSeatIds);
		}
		catch (RuntimeException ex) {
			log.warn("Failed to broadcast released waitlist seats for event {}", eventId, ex);
		}
	}

	private static long entries(Map<WaitlistStatus, WaitlistStatusCount> counts, WaitlistStatus status) {
		WaitlistStatusCount count = counts.get(status);
		return count == null ? 0 : count.entries();
	}

	private static long seats(Map<WaitlistStatus, WaitlistStatusCount> counts, WaitlistStatus status) {
		WaitlistStatusCount count = counts.get(status);
		return count == null ? 0 : count.seats();
	}

	private record OfferOutcome(UUID entryId, List<UUID> eventSeatIds) {

		static final OfferOutcome NOTHING = new OfferOutcome(null, List.of());
	}

	private static final class OfferFailedException extends RuntimeException {

		private final UUID entryId;

		private OfferFailedException(UUID entryId, Throwable cause) {
			super(cause);
			this.entryId = entryId;
		}
	}
}
