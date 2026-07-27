package com.seatflow.support;

import java.util.UUID;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

@ActiveProfiles("test")
public abstract class PostgresTestContainerSupport {

	private static final String TEST_DATABASE_PASSWORD = UUID.randomUUID().toString();

	@Container
	private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
			DockerImageName.parse("postgres:16-alpine"))
			.withDatabaseName("seatflow_test")
			.withUsername("seatflow")
			.withPassword(TEST_DATABASE_PASSWORD);

	@DynamicPropertySource
	protected static void registerPostgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("server.port", () -> "8080");
		registry.add("seatflow.jwt.secret", () -> JwtTestSupport.DEFAULT_SECRET);
		registry.add("seatflow.jwt.issuer", () -> JwtTestSupport.ISSUER);
		registry.add("seatflow.jwt.expires-in-seconds", () -> "900");
		registry.add("seatflow.refresh-token.cookie-name", () -> "seatflow_refresh_token");
		registry.add("seatflow.refresh-token.expires-in-seconds", () -> "1209600");
		registry.add("seatflow.refresh-token.cookie-secure", () -> "false");
		registry.add("seatflow.refresh-token.same-site", () -> "Strict");
	}

	protected static PostgreSQLContainer<?> postgres() {
		return POSTGRES;
	}

}
