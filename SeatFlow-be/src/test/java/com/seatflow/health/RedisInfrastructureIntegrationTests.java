package com.seatflow.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.seatflow.hold.SeatHoldRedisKeys;
import com.seatflow.support.RedisTestContainerSupport;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RedisInfrastructureIntegrationTests extends RedisTestContainerSupport {

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private MockMvc mockMvc;

	@Test
	void redisConnectionRespondsToPing() {
		String pong = redisTemplate.getConnectionFactory().getConnection().ping();

		assertThat(pong).isEqualToIgnoringCase("PONG");
	}

	@Test
	void redisSetAndGetWorks() {
		String key = SeatHoldRedisKeys.data(UUID.randomUUID());

		redisTemplate.opsForValue().set(key, "hold-data");

		assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("hold-data");
	}

	@Test
	void redisKeysExpireAfterTtl() throws Exception {
		String key = SeatHoldRedisKeys.seat(UUID.randomUUID(), UUID.randomUUID());

		redisTemplate.opsForValue().set(key, "held", Duration.ofMillis(300));

		assertThat(redisTemplate.getExpire(key, TimeUnit.MILLISECONDS)).isPositive();
		for (int attempt = 0; attempt < 20 && redisTemplate.opsForValue().get(key) != null; attempt++) {
			Thread.sleep(100);
		}
		assertThat(redisTemplate.opsForValue().get(key)).isNull();
	}

	@Test
	void redisHealthEndpointReturnsUp() throws Exception {
		mockMvc.perform(get("/api/v1/health/redis"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.cache").value("Redis"));
	}
}
