package com.seatflow.promo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PromoCodeMapper {

	int insert(
			@Param("id") UUID id,
			@Param("code") String code,
			@Param("discountType") PromoDiscountType discountType,
			@Param("discountValue") BigDecimal discountValue,
			@Param("eventId") UUID eventId,
			@Param("expiresAt") Instant expiresAt,
			@Param("maxUses") Integer maxUses,
			@Param("now") Instant now);

	PromoCodeRecord findById(@Param("id") UUID id);

	PromoCodeRecord findByCode(@Param("code") String code);

	/**
	 * Locks the promo row so concurrent redemptions of one code queue up behind each other and each
	 * sees the previous one's order when counting uses.
	 */
	PromoCodeRecord findByCodeForUpdate(@Param("code") String code);

	long count();

	List<PromoCodeRecord> findPage(@Param("limit") int limit, @Param("offset") long offset);

	int deactivate(@Param("id") UUID id, @Param("now") Instant now);
}
