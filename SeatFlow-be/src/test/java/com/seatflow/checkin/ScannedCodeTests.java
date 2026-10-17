package com.seatflow.checkin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ScannedCodeTests {

	private static final String CODE = "ticket_code_abcdefghijklmnopqrstuvwxyz123456";
	private static final UUID TICKET_ID = UUID.fromString("f2287e38-c265-4841-b20b-57d2d7ba1b9d");

	@Test
	void qrPayloadYieldsTheTicketIdAndTheCode() {
		ScannedCode scanned = ScannedCode.parse("seatflow:ticket:%s:%s".formatted(TICKET_ID, CODE)).orElseThrow();

		assertThat(scanned.ticketId()).isEqualTo(TICKET_ID);
		assertThat(scanned.code()).isEqualTo(CODE);
	}

	@Test
	void bareCodeTypedByHandIsAcceptedWithoutATicketId() {
		ScannedCode scanned = ScannedCode.parse(CODE).orElseThrow();

		assertThat(scanned.ticketId()).isNull();
		assertThat(scanned.code()).isEqualTo(CODE);
	}

	@Test
	void surroundingWhitespaceFromAScannerIsIgnored() {
		assertThat(ScannedCode.parse("  " + CODE + "\r\n").orElseThrow().code()).isEqualTo(CODE);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {
			"   ",
			"short",
			"seatflow:ticket:not-a-uuid:ticket_code_abcdefghijklmnopqrstuvwxyz123456",
			"seatflow:ticket:f2287e38-c265-4841-b20b-57d2d7ba1b9d",
			"seatflow:ticket:f2287e38-c265-4841-b20b-57d2d7ba1b9d:ticket_code_abcdefghijklmnopqrstuvwxyz123456:extra",
			"seatflow:ticket:f2287e38-c265-4841-b20b-57d2d7ba1b9d:short",
			"ticket code with spaces abcdefghijklmnopqrstuvwxyz",
			"https://example.com/ticket_code_abcdefghijklmnopqrstuvwxyz123456",
	})
	void anythingThatCannotBeATicketIsRejected(String raw) {
		assertThat(ScannedCode.parse(raw)).isEmpty();
	}
}
