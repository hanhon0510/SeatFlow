package com.seatflow.health;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DatabaseHealthServiceTests {

	@Test
	void databaseFailuresProduceSafeErrors() {
		DatabaseHealthMapper mapper = () -> {
			throw new RuntimeException("internal connection detail");
		};
		DatabaseHealthService service = new DatabaseHealthService(mapper);

		assertThatThrownBy(service::checkDatabase)
				.isInstanceOf(DatabaseHealthUnavailableException.class)
				.hasMessage("Database health check failed")
				.hasMessageNotContaining("internal connection detail");
	}

}
