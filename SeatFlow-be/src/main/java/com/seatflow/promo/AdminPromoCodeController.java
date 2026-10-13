package com.seatflow.promo;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/promo-codes")
public class AdminPromoCodeController {

	private final PromoCodeGenerator promoCodeGenerator;
	private final PromoCodeService promoCodeService;

	public AdminPromoCodeController(PromoCodeGenerator promoCodeGenerator, PromoCodeService promoCodeService) {
		this.promoCodeGenerator = promoCodeGenerator;
		this.promoCodeService = promoCodeService;
	}

	@PostMapping("/generate")
	public PromoCodeGenerationResponse generate(@Valid @RequestBody PromoCodeGenerationRequest request) {
		return new PromoCodeGenerationResponse(promoCodeGenerator.generate(request.length(), request.characterSet()));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PromoCodeResponse create(@Valid @RequestBody PromoCodeCreateRequest request) {
		return promoCodeService.create(request);
	}

	@GetMapping
	public PromoCodePageResponse list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "" + PromoCodeService.DEFAULT_PAGE_SIZE) int size) {
		return promoCodeService.list(page, size);
	}

	@PostMapping("/{promoCodeId}/deactivate")
	public PromoCodeResponse deactivate(@PathVariable UUID promoCodeId) {
		return promoCodeService.deactivate(promoCodeId);
	}
}
