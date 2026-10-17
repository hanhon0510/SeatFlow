package com.seatflow.checkin;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * What a scan decodes to. The QR on a ticket is {@code seatflow:ticket:<ticketId>:<code>}, but
 * staff also type the bare code in when a camera cannot read the screen, so both are accepted.
 */
record ScannedCode(String code, UUID ticketId) {

	private static final String QR_PREFIX = "seatflow:ticket:";
	private static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{32,80}");

	/** Empty when the input cannot possibly be one of our tickets. */
	static Optional<ScannedCode> parse(String raw) {
		if (raw == null) {
			return Optional.empty();
		}
		String value = raw.trim();
		UUID ticketId = null;
		if (value.startsWith(QR_PREFIX)) {
			String[] parts = value.substring(QR_PREFIX.length()).split(":", -1);
			if (parts.length != 2) {
				return Optional.empty();
			}
			try {
				ticketId = UUID.fromString(parts[0]);
			}
			catch (IllegalArgumentException ex) {
				return Optional.empty();
			}
			value = parts[1];
		}
		if (!CODE_PATTERN.matcher(value).matches()) {
			return Optional.empty();
		}
		return Optional.of(new ScannedCode(value, ticketId));
	}
}
