package com.seatflow.promo;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seatflow.event.EventMapper;
import com.seatflow.event.EventNotFoundException;

@Service
public class PromoCodeService {

	public static final int DEFAULT_PAGE_SIZE = 20;
	public static final int MAX_PAGE_SIZE = 100;

	private static final BigDecimal MAX_PERCENT = BigDecimal.valueOf(100);

	private final PromoCodeMapper promoCodeMapper;
	private final EventMapper eventMapper;
	private final Clock clock;

	public PromoCodeService(PromoCodeMapper promoCodeMapper, EventMapper eventMapper, Clock clock) {
		this.promoCodeMapper = promoCodeMapper;
		this.eventMapper = eventMapper;
		this.clock = clock;
	}

	@Transactional
	public PromoCodeResponse create(PromoCodeCreateRequest request) {
		Instant now = clock.instant();
		if (request.discountType() == PromoDiscountType.PERCENT
				&& request.discountValue().compareTo(MAX_PERCENT) > 0) {
			throw new InvalidPromoCodeRequestException("A percent discount cannot exceed 100");
		}
		if (request.expiresAt() != null && !request.expiresAt().isAfter(now)) {
			throw new InvalidPromoCodeRequestException("Expiry must be in the future");
		}
		if (request.eventId() != null && eventMapper.findById(request.eventId()) == null) {
			throw new EventNotFoundException();
		}

		UUID id = UUID.randomUUID();
		try {
			promoCodeMapper.insert(
					id,
					normalize(request.code()),
					request.discountType(),
					request.discountValue(),
					request.eventId(),
					request.expiresAt(),
					request.maxUses(),
					now);
		}
		catch (DuplicateKeyException ex) {
			throw new DuplicatePromoCodeException();
		}
		return PromoCodeResponse.from(promoCodeMapper.findById(id));
	}

	@Transactional(readOnly = true)
	public PromoCodePageResponse list(int page, int size) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new InvalidPromoCodePaginationException();
		}
		long totalItems = promoCodeMapper.count();
		List<PromoCodeRecord> promos = promoCodeMapper.findPage(size, (long) page * size);
		return PromoCodePageResponse.from(promos, page, size, totalItems);
	}

	@Transactional
	public PromoCodeResponse deactivate(UUID promoCodeId) {
		if (promoCodeMapper.findById(promoCodeId) == null) {
			throw new PromoCodeNotFoundException();
		}
		promoCodeMapper.deactivate(promoCodeId, clock.instant());
		return PromoCodeResponse.from(promoCodeMapper.findById(promoCodeId));
	}

	/** Read-only check so the checkout screen can show the discount before the order exists. */
	@Transactional(readOnly = true)
	public PromoCodeValidationResponse validate(PromoCodeValidationRequest request) {
		PromoCodeRecord promo = promoCodeMapper.findByCode(normalize(request.code()));
		requireRedeemable(promo, request.eventId());
		BigDecimal discount = promo.discountFor(request.subtotal());
		return new PromoCodeValidationResponse(
				promo.code(),
				promo.discountType(),
				promo.discountValue(),
				discount,
				request.subtotal().subtract(discount));
	}

	/**
	 * Checks a code for an order about to be created and keeps its row locked until the caller's
	 * transaction ends, so two checkouts racing for the last use cannot both pass.
	 */
	@Transactional
	public PromoCodeRecord lockForRedemption(String rawCode, UUID eventId) {
		String code = normalize(rawCode);
		if (promoCodeMapper.findByCodeForUpdate(code) == null) {
			throw new PromoCodeRejectedException(PromoCodeRejectedException.Reason.UNKNOWN);
		}
		// Read again now that the lock is held: a statement that waited on the lock still counts uses
		// from its old snapshot, so it would miss the order the previous holder just committed.
		PromoCodeRecord promo = promoCodeMapper.findByCode(code);
		requireRedeemable(promo, eventId);
		return promo;
	}

	private void requireRedeemable(PromoCodeRecord promo, UUID eventId) {
		if (promo == null) {
			throw new PromoCodeRejectedException(PromoCodeRejectedException.Reason.UNKNOWN);
		}
		if (!promo.active()) {
			throw new PromoCodeRejectedException(PromoCodeRejectedException.Reason.INACTIVE);
		}
		if (promo.isExpired(clock.instant())) {
			throw new PromoCodeRejectedException(PromoCodeRejectedException.Reason.EXPIRED);
		}
		if (promo.eventId() != null && !promo.eventId().equals(eventId)) {
			throw new PromoCodeRejectedException(PromoCodeRejectedException.Reason.WRONG_EVENT);
		}
		if (promo.isExhausted()) {
			throw new PromoCodeRejectedException(PromoCodeRejectedException.Reason.EXHAUSTED);
		}
	}

	static String normalize(String code) {
		return code.trim().toUpperCase(Locale.ROOT);
	}
}
