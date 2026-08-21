package com.seatflow.health;

import java.time.Duration;

public interface KafkaHealthClient {

	void check(Duration timeout);
}
