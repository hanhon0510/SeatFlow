package com.seatflow.promo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PromoCodeGeneratorTests {

	private final PromoCodeGenerator generator = new PromoCodeGenerator();

	@Test
	void generatesTheRequestedNumberOfLetters() {
		String code = generator.generate(12, PromoCodeCharacterSet.LETTERS);

		assertThat(code)
				.hasSize(12)
				.matches("[ABCDEFGHJKMNPQRSTUVWXYZ]+");
	}

	@Test
	void generatesTheRequestedNumberOfNumbers() {
		String code = generator.generate(12, PromoCodeCharacterSet.NUMBERS);

		assertThat(code)
				.hasSize(12)
				.matches("[23456789]+");
	}

	@Test
	void generatesAnAlphanumericCodeWithBothCharacterTypes() {
		String code = generator.generate(12, PromoCodeCharacterSet.ALPHANUMERIC);

		assertThat(code)
				.hasSize(12)
				.matches("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]+")
				.containsPattern("[ABCDEFGHJKMNPQRSTUVWXYZ]")
				.containsPattern("[23456789]");
	}
}
