package com.seatflow.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.seatflow.support.PostgresTestContainerSupport;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class DatabaseHealthIntegrationTests extends PostgresTestContainerSupport {

	@Autowired
	private DatabaseHealthMapper databaseHealthMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MockMvc mockMvc;

	@Test
	void postgreSqlTestcontainerStarts() {
		assertThat(postgres().isRunning()).isTrue();
	}

	@Test
	void flywayMigrationApplies() {
		Integer migrationCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM flyway_schema_history
				WHERE version = '1'
					AND script = 'V1__create_system_health_table.sql'
					AND success = TRUE
				""", Integer.class);

		Integer rowCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM system_health
				WHERE id = 1
					AND status = 'UP'
				""", Integer.class);

		assertThat(migrationCount).isEqualTo(1);
		assertThat(rowCount).isEqualTo(1);
	}

	@Test
	void mapperQuerySucceeds() {
		assertThat(databaseHealthMapper.findSystemHealthStatus()).isEqualTo("UP");
	}

	@Test
	void databaseHealthEndpointSucceeds() throws Exception {
		mockMvc.perform(get("/api/v1/health/database"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.database").value("PostgreSQL"));
	}

}
