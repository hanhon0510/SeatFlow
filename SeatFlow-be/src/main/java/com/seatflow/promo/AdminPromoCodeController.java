package com.seatflow.promo;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/promo-codes")
public class AdminPromoCodeController {

	private final PromoCodeGenerator promoCodeGenerator;

	public AdminPromoCodeController(PromoCodeGenerator promoCodeGenerator) {
		this.promoCodeGenerator = promoCodeGenerator;
	}

	@PostMapping("/generate")
	public PromoCodeGenerationResponse generate(@Valid @RequestBody PromoCodeGenerationRequest request) {
		return new PromoCodeGenerationResponse(promoCodeGenerator.generate(request.length(), request.characterSet()));
	}
}
