package com.seatflow.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SeatFlowMailPropertiesTests {

	@Test
	void blankValuesFallBackToUsableDefaults() {
		SeatFlowMailProperties properties = new SeatFlowMailProperties(false, " ", null);

		assertThat(properties.from()).isEqualTo("SeatFlow <no-reply@seatflow.local>");
		assertThat(properties.frontendBaseUrl()).isEqualTo("http://localhost:5173");
		assertThat(properties.enabled()).isFalse();
	}

	@Test
	void aTrailingSlashIsDroppedSoLinksDoNotGetADoubleSlash() {
		SeatFlowMailProperties properties = new SeatFlowMailProperties(true, "a@b.test", "https://seatflow.example/");

		assertThat(properties.frontendBaseUrl()).isEqualTo("https://seatflow.example");
	}
}
