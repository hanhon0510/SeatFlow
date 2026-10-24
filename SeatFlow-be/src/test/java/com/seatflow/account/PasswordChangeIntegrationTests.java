package com.seatflow.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.auth.PasswordResetMapper;
import com.seatflow.auth.RefreshTokenGenerator;
import com.seatflow.support.JwtTestSupport;
import com.seatflow.support.PostgresTestContainerSupport;
import com.seatflow.user.UserMapper;
import com.seatflow.user.UserRecord;

import jakarta.servlet.http.Cookie;

@SpringBootTest(properties = "seatflow.jwt.secret=" + JwtTestSupport.DEFAULT_SECRET)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PasswordChangeIntegrationTests extends PostgresTestContainerSupport {

	private static final String OLD_PASSWORD = "StrongPassword123!";
	private static final String NEW_PASSWORD = "BrandNewPassw0rd!";
	private static final String COOKIE = "seatflow_refresh_token";
	private static final String URL = "/api/v1/users/me/password";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private UserMapper userMapper;

	@Autowired
	private PasswordResetMapper passwordResetMapper;

	@Autowired
	private RefreshTokenGenerator tokenGenerator;

	@Test
	void changesThePasswordAndSignsOutEveryOtherDeviceButNotThisOne() throws Exception {
		UserRecord user = insertUser();
		Session thisDevice = login(user.email(), OLD_PASSWORD);
		Session otherDevice = login(user.email(), OLD_PASSWORD);
		assertThat(activeRefreshTokens(user.id())).isEqualTo(2);

		change(thisDevice, OLD_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

		assertThat(activeRefreshTokens(user.id())).isEqualTo(1);
		assertThat(isActive(thisDevice.refreshToken())).isTrue();
		assertThat(isActive(otherDevice.refreshToken())).isFalse();
		loginRequest(user.email(), NEW_PASSWORD).andExpect(status().isOk());
		loginRequest(user.email(), OLD_PASSWORD).andExpect(status().isUnauthorized());
	}

	@Test
	void aWrongCurrentPasswordIsRefusedAndNothingChanges() throws Exception {
		UserRecord user = insertUser();
		Session session = login(user.email(), OLD_PASSWORD);

		change(session, "NotMyPassw0rd!x", NEW_PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));

		loginRequest(user.email(), OLD_PASSWORD).andExpect(status().isOk());
	}

	@Test
	void theSamePasswordIsRefused() throws Exception {
		UserRecord user = insertUser();
		Session session = login(user.email(), OLD_PASSWORD);

		change(session, OLD_PASSWORD, OLD_PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PASSWORD_UNCHANGED"));
	}

	@Test
	void aWeakNewPasswordIsRefused() throws Exception {
		UserRecord user = insertUser();
		Session session = login(user.email(), OLD_PASSWORD);

		change(session, OLD_PASSWORD, "short").andExpect(status().isBadRequest());

		loginRequest(user.email(), OLD_PASSWORD).andExpect(status().isOk());
	}

	@Test
	void aResetLinkRequestedBeforeTheChangeStopsWorking() throws Exception {
		UserRecord user = insertUser();
		Session session = login(user.email(), OLD_PASSWORD);
		String token = "pending-reset-token";
		Instant now = Instant.now();
		passwordResetMapper.insert(
				UUID.randomUUID(), user.id(), tokenGenerator.hashToken(token), now.plusSeconds(3600), now);

		change(session, OLD_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

		mockMvc.perform(post("/api/v1/auth/reset-password")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"token\":\"%s\",\"password\":\"AnotherPassw0rd!x\"}".formatted(token)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void youHaveToBeSignedIn() throws Exception {
		mockMvc.perform(post(URL)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}"
								.formatted(OLD_PASSWORD, NEW_PASSWORD)))
				.andExpect(status().isUnauthorized());
	}

	private ResultActions change(Session session, String current, String next) throws Exception {
		return mockMvc.perform(post(URL)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
				.cookie(new Cookie(COOKIE, session.refreshToken()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(current, next)));
	}

	private Session login(String email, String password) throws Exception {
		MvcResult result = loginRequest(email, password).andExpect(status().isOk()).andReturn();
		String accessToken = objectMapper.readTree(result.getResponse().getContentAsString())
				.get("accessToken").asText();
		return new Session(accessToken, result.getResponse().getCookie(COOKIE).getValue());
	}

	private ResultActions loginRequest(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
	}

	private UserRecord insertUser() {
		UUID id = UUID.randomUUID();
		userMapper.insert(UserRecord.forInsert(
				id, "change-%s@example.com".formatted(id), passwordEncoder.encode(OLD_PASSWORD)));
		return userMapper.findById(id);
	}

	private int activeRefreshTokens(UUID userId) {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL", Integer.class,
				userId);
	}

	private boolean isActive(String rawRefreshToken) {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM refresh_tokens WHERE token_hash = ? AND revoked_at IS NULL", Integer.class,
				tokenGenerator.hashToken(rawRefreshToken)) == 1;
	}

	private record Session(String accessToken, String refreshToken) {
	}
}
