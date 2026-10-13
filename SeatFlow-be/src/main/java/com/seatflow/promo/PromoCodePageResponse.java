package com.seatflow.promo;

import java.util.List;

public record PromoCodePageResponse(
		List<PromoCodeResponse> items,
		int page,
		int size,
		long totalItems,
		int totalPages) {

	public static PromoCodePageResponse from(List<PromoCodeRecord> promos, int page, int size, long totalItems) {
		int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / size);
		return new PromoCodePageResponse(
				promos.stream().map(PromoCodeResponse::from).toList(),
				page,
				size,
				totalItems,
				totalPages);
	}
}
