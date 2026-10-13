package com.seatflow.promo;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/promo-codes")
public class PromoCodeController {

	private final PromoCodeService promoCodeService;

	public PromoCodeController(PromoCodeService promoCodeService) {
		this.promoCodeService = promoCodeService;
	}

	@PostMapping("/validate")
	public PromoCodeValidationResponse validate(@Valid @RequestBody PromoCodeValidationRequest request) {
		return promoCodeService.validate(request);
	}
}
