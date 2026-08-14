package com.seatflow.ticket;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

@Component
public class SecureTicketCodeGenerator implements TicketCodeGenerator {

	private static final int CODE_BYTES = 32;

	private final SecureRandom secureRandom;

	public SecureTicketCodeGenerator(SecureRandom secureRandom) {
		this.secureRandom = secureRandom;
	}

	@Override
	public String generateCode() {
		byte[] code = new byte[CODE_BYTES];
		secureRandom.nextBytes(code);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(code);
	}
}
