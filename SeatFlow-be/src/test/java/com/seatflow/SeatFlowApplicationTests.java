package com.seatflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.seatflow.support.PostgresTestContainerSupport;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class SeatFlowApplicationTests extends PostgresTestContainerSupport {

	@Test
	void contextLoads() {
	}

}
