package com.seatflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.support.JwtTestSupport;
import com.seatflow.support.PostgresTestContainerSupport;
import com.seatflow.user.UserMapper;
import com.seatflow.user.UserRecord;
import com.seatflow.user.UserStatus;

@SpringBootTest(properties = "seatflow.jwt.secret=" + JwtTestSupport.DEFAULT_SECRET)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AuthLoginIntegrationTests extends PostgresTestContainerSupport {

	private static final String PASSWORD = "StrongPassword123!";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private UserMapper userMapper;

	@Test
	void successfulLoginReturnsJwtAndAllowsCurrentUserAccess() throws Exception {
		UserRecord user = insertUser(uniqueEmail("login"), PASSWORD);

		MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginJson(user.email().toUpperCase(Locale.ROOT), PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresAt").isNotEmpty())
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andExpect(content().string(not(containsString(PASSWORD))))
				.andReturn();

		String token = readJson(loginResult).get("accessToken").asText();
		assertThat(token).isNotBlank();

		mockMvc.perform(get("/api/v1/users/me")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(user.id().toString()))
				.andExpect(jsonPath("$.email").value(user.email()))
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void wrongPasswordReturnsUnauthorized() throws Exception {
		UserRecord user = insertUser(uniqueEmail("wrong-password"), PASSWORD);

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginJson(user.email(), "WrongPassword123!")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.correlationId").isNotEmpty())
				.andExpect(jsonPath("$.title").value("Invalid email or password"))
				.andExpect(content().string(not(containsString("WrongPassword123!"))));
	}

	@Test
	void unknownEmailReturnsUnauthorized() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginJson(uniqueEmail("unknown"), PASSWORD)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.correlationId").isNotEmpty())
				.andExpect(jsonPath("$.title").value("Invalid email or password"));
	}

	@Test
	void disabledUserCannotLogIn() throws Exception {
		UserRecord user = insertUser(uniqueEmail("disabled"), PASSWORD);
		userMapper.updateStatus(user.id(), UserStatus.DISABLED);

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginJson(user.email(), PASSWORD)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.title").value("Invalid email or password"));
	}

	private UserRecord insertUser(String email, String rawPassword) {
		UUID id = UUID.randomUUID();
		String normalizedEmail = email.toLowerCase(Locale.ROOT);
		userMapper.insert(UserRecord.forInsert(id, normalizedEmail, passwordEncoder.encode(rawPassword)));
		return userMapper.findById(id);
	}

	private JsonNode readJson(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private static String uniqueEmail(String label) {
		return "%s-%s@example.com".formatted(label, UUID.randomUUID());
	}

	private static String loginJson(String email, String password) {
		return """
				{
				  "email": "%s",
				  "password": "%s"
				}
				""".formatted(email, password);
	}

}
