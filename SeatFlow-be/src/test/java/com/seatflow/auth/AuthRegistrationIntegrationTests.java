package com.seatflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.seatflow.support.PostgresTestContainerSupport;
import com.seatflow.user.UserMapper;
import com.seatflow.user.UserRecord;
import com.seatflow.user.UserRole;
import com.seatflow.user.UserStatus;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AuthRegistrationIntegrationTests extends PostgresTestContainerSupport {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private UserMapper userMapper;

	@Test
	void successfulRegistrationCreatesUser() throws Exception {
		String email = uniqueEmail("Register");
		String password = "StrongPassword123!";

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerJson(email, password)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.email").value(email.toLowerCase(Locale.ROOT)))
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andExpect(content().string(not(containsString(password))));

		UserRecord stored = userMapper.findByNormalizedEmail(email.toLowerCase(Locale.ROOT));
		assertThat(stored).isNotNull();
		assertThat(stored.email()).isEqualTo(email.toLowerCase(Locale.ROOT));
		assertThat(stored.passwordHash()).isNotEqualTo(password);
		assertThat(passwordEncoder.matches(password, stored.passwordHash())).isTrue();
		assertThat(stored.role()).isEqualTo(UserRole.USER);
		assertThat(stored.status()).isEqualTo(UserStatus.ACTIVE);
	}

	@Test
	void duplicateRegistrationReturnsConflict() throws Exception {
		String email = uniqueEmail("duplicate");

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerJson(email, "StrongPassword123!")))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerJson(email, "AnotherStrong123!")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("User already exists"));
	}

	@Test
	void mixedCaseDuplicateEmailReturnsConflict() throws Exception {
		String token = UUID.randomUUID().toString();
		String firstEmail = "Case-%s@Example.com".formatted(token);
		String secondEmail = "case-%s@example.com".formatted(token);

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerJson(firstEmail, "StrongPassword123!")))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerJson(secondEmail, "AnotherStrong123!")))
				.andExpect(status().isConflict());
	}

	@Test
	void weakPasswordReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerJson(uniqueEmail("weak"), "weak")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false));
	}

	@Test
	void invalidEmailReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerJson("not-an-email", "StrongPassword123!")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false));
	}

	@Test
	void sqlConstraintViolationMapsToConflict() throws Exception {
		String email = uniqueEmail("constraint");
		userMapper.insert(UserRecord.forInsert(UUID.randomUUID(), email.toLowerCase(Locale.ROOT), "{bcrypt}existing"));

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerJson(email, "StrongPassword123!")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("User already exists"));
	}

	private static String uniqueEmail(String label) {
		return "%s-%s@Example.com".formatted(label, UUID.randomUUID());
	}

	private static String registerJson(String email, String password) {
		return """
				{
				  "email": "%s",
				  "password": "%s"
				}
				""".formatted(email, password);
	}

}
