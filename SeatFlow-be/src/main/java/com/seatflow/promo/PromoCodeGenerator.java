package com.seatflow.promo;

import java.security.SecureRandom;

import org.springframework.stereotype.Service;

@Service
public class PromoCodeGenerator {

	public static final int MIN_LENGTH = 4;
	public static final int MAX_LENGTH = 32;

	private static final String LETTERS = "ABCDEFGHJKMNPQRSTUVWXYZ";
	private static final String NUMBERS = "23456789";
	private static final String ALPHANUMERIC = LETTERS + NUMBERS;

	private final SecureRandom random;

	public PromoCodeGenerator() {
		this(new SecureRandom());
	}

	PromoCodeGenerator(SecureRandom random) {
		this.random = random;
	}

	public String generate(int length, PromoCodeCharacterSet characterSet) {
		if (length < MIN_LENGTH || length > MAX_LENGTH) {
			throw new IllegalArgumentException("Promo code length is outside the supported range");
		}

		char[] code = new char[length];
		if (characterSet == PromoCodeCharacterSet.ALPHANUMERIC) {
			code[0] = randomCharacter(LETTERS);
			code[1] = randomCharacter(NUMBERS);
			for (int index = 2; index < code.length; index++) {
				code[index] = randomCharacter(ALPHANUMERIC);
			}
			shuffle(code);
			return new String(code);
		}

		String alphabet = characterSet == PromoCodeCharacterSet.LETTERS ? LETTERS : NUMBERS;
		for (int index = 0; index < code.length; index++) {
			code[index] = randomCharacter(alphabet);
		}
		return new String(code);
	}

	private char randomCharacter(String alphabet) {
		return alphabet.charAt(random.nextInt(alphabet.length()));
	}

	private void shuffle(char[] code) {
		for (int index = code.length - 1; index > 0; index--) {
			int swapIndex = random.nextInt(index + 1);
			char current = code[index];
			code[index] = code[swapIndex];
			code[swapIndex] = current;
		}
	}
}
