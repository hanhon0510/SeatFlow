package com.seatflow.promo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.seatflow.event.EventMapper;
import com.seatflow.promo.PromoCodeRejectedException.Reason;

@ExtendWith(MockitoExtension.class)
class PromoCodeServiceTests {

	private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
	private static final UUID EVENT_ID = UUID.fromString("9a2c4f10-6a1e-4c55-b3d4-1d0f6a8e7b21");

	@Mock
	private PromoCodeMapper promoCodeMapper;

	@Mock
	private EventMapper eventMapper;

	private PromoCodeService service;

	@BeforeEach
	void setUp() {
		service = new PromoCodeService(promoCodeMapper, eventMapper, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void percentDiscountRoundsToWholeUnitsAndFixedDiscountNeverExceedsSubtotal() {
		assertThat(PromoDiscountType.PERCENT.discountFor(new BigDecimal("10"), new BigDecimal("150005")))
				.isEqualByComparingTo("15001");
		assertThat(PromoDiscountType.PERCENT.discountFor(new BigDecimal("100"), new BigDecimal("500")))
				.isEqualByComparingTo("500");
		assertThat(PromoDiscountType.FIXED.discountFor(new BigDecimal("50000"), new BigDecimal("20000")))
				.isEqualByComparingTo("20000");
		assertThat(PromoDiscountType.FIXED.discountFor(new BigDecimal("50000"), new BigDecimal("120000")))
				.isEqualByComparingTo("50000");
	}

	@Test
	void validCodeIsNormalizedAndAccepted() {
		PromoCodeRecord valid = promo(true, null, null, 0, null);
		when(promoCodeMapper.findByCodeForUpdate("SAVE10")).thenReturn(valid);
		when(promoCodeMapper.findByCode("SAVE10")).thenReturn(valid);

		assertThat(service.lockForRedemption("  save10 ", EVENT_ID).code()).isEqualTo("SAVE10");
	}

	@Test
	void unknownInactiveExpiredExhaustedAndWrongEventCodesAreRejectedWithTheirReason() {
		when(promoCodeMapper.findByCodeForUpdate("NONE")).thenReturn(null);
		lenient().when(promoCodeMapper.findByCode("NONE")).thenReturn(null);
		assertRejected("NONE", Reason.UNKNOWN);

		when(promoCodeMapper.findByCodeForUpdate("OFFF")).thenReturn(promo(false, null, null, 0, null));
		lenient().when(promoCodeMapper.findByCode("OFFF")).thenReturn(promo(false, null, null, 0, null));
		assertRejected("OFFF", Reason.INACTIVE);

		when(promoCodeMapper.findByCodeForUpdate("OLDD")).thenReturn(promo(true, NOW, null, 0, null));
		lenient().when(promoCodeMapper.findByCode("OLDD")).thenReturn(promo(true, NOW, null, 0, null));
		assertRejected("OLDD", Reason.EXPIRED);

		when(promoCodeMapper.findByCodeForUpdate("FULL")).thenReturn(promo(true, null, null, 5, 5));
		lenient().when(promoCodeMapper.findByCode("FULL")).thenReturn(promo(true, null, null, 5, 5));
		assertRejected("FULL", Reason.EXHAUSTED);

		when(promoCodeMapper.findByCodeForUpdate("ELSE")).thenReturn(promo(true, null, UUID.randomUUID(), 0, null));
		lenient().when(promoCodeMapper.findByCode("ELSE")).thenReturn(promo(true, null, UUID.randomUUID(), 0, null));
		assertRejected("ELSE", Reason.WRONG_EVENT);
	}

	@Test
	void codeScopedToTheEventIsAccepted() {
		PromoCodeRecord scoped = promo(true, null, EVENT_ID, 1, 2);
		when(promoCodeMapper.findByCodeForUpdate("ONLY")).thenReturn(scoped);
		when(promoCodeMapper.findByCode("ONLY")).thenReturn(scoped);
		lenient().when(promoCodeMapper.findByCode("ONLY")).thenReturn(promo(true, null, EVENT_ID, 1, 2));

		assertThat(service.lockForRedemption("ONLY", EVENT_ID)).isNotNull();
	}

	private void assertRejected(String code, Reason reason) {
		assertThatThrownBy(() -> service.lockForRedemption(code, EVENT_ID))
				.isInstanceOfSatisfying(PromoCodeRejectedException.class, ex -> assertThat(ex.reason()).isEqualTo(reason));
	}

	private static PromoCodeRecord promo(boolean active, Instant expiresAt, UUID eventId, long used, Integer maxUses) {
		return new PromoCodeRecord(
				UUID.randomUUID(), "SAVE10", PromoDiscountType.PERCENT, BigDecimal.TEN,
				eventId, expiresAt, maxUses, active, used, NOW, NOW);
	}
}
