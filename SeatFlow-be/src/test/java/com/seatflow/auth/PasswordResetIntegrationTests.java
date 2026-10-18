package com.seatflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.seatflow.notification.AccountEmailTemplates;
import com.seatflow.notification.EmailGateway;
import com.seatflow.notification.EmailMessage;
import com.seatflow.support.JwtTestSupport;
import com.seatflow.support.PostgresTestContainerSupport;
import com.seatflow.user.UserMapper;
import com.seatflow.user.UserRecord;

@SpringBootTest(properties = "seatflow.jwt.secret=" + JwtTestSupport.DEFAULT_SECRET)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PasswordResetIntegrationTests extends PostgresTestContainerSupport {

	private static final String OLD_PASSWORD = "StrongPassword123!";
	private static final String NEW_PASSWORD = "BrandNewPassw0rd!";
	private static final Pattern TOKEN_IN_LINK = Pattern.compile("reset-password\\?token=(\\S+)");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private UserMapper userMapper;

	@Autowired
	private PasswordResetMapper passwordResetMapper;

	@Autowired
	private RefreshTokenMapper refreshTokenMapper;

	@Autowired
	private RefreshTokenGenerator tokenGenerator;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private PasswordResetProperties properties;

	// Built by hand with a mock gateway so the emailed token can be read back, while everything
	// else (mappers, hashing, transactions) is the real thing. The HTTP calls use the real service.
	private final EmailGateway gateway = mock(EmailGateway.class);

	private PasswordResetService service;

	@BeforeEach
	void setUp() {
		service = new PasswordResetService(
				userMapper,
				passwordResetMapper,
				refreshTokenMapper,
				tokenGenerator,
				passwordEncoder,
				properties,
				gateway,
				new AccountEmailTemplates("https://seatflow.example"),
				transactionTemplate,
				Clock.systemUTC());
	}

	@Test
	void asksForALinkAndChoosesANewPassword() throws Exception {
		UserRecord user = insertUser();
		String token = requestToken(user.email());
		assertThat(jdbcTemplate.queryForObject(
				"SELECT token_hash FROM password_reset_tokens WHERE user_id = ?", String.class, user.id()))
				.isNotEqualTo(token)
				.isEqualTo(tokenGenerator.hashToken(token));

		reset(token, NEW_PASSWORD).andExpect(status().isNoContent());

		login(user.email(), NEW_PASSWORD).andExpect(status().isOk());
		login(user.email(), OLD_PASSWORD).andExpect(status().isUnauthorized());
	}

	@Test
	void aLinkWorksOnlyOnce() throws Exception {
		UserRecord user = insertUser();
		String token = requestToken(user.email());

		reset(token, NEW_PASSWORD).andExpect(status().isNoContent());
		reset(token, "AnotherPassw0rd!x")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PASSWORD_RESET_TOKEN"));

		login(user.email(), NEW_PASSWORD).andExpect(status().isOk());
	}

	@Test
	void anExpiredLinkIsRefused() throws Exception {
		UserRecord user = insertUser();
		String token = requestToken(user.email());
		jdbcTemplate.update(
				"UPDATE password_reset_tokens SET created_at = NOW() - INTERVAL '2 hours', "
						+ "expires_at = NOW() - INTERVAL '1 hour' WHERE user_id = ?",
				user.id());

		reset(token, NEW_PASSWORD).andExpect(status().isBadRequest());

		login(user.email(), OLD_PASSWORD).andExpect(status().isOk());
	}

	@Test
	void aNewRequestRetiresTheEarlierLink() throws Exception {
		UserRecord user = insertUser();
		String first = requestToken(user.email());
		String second = requestToken(user.email());

		reset(first, NEW_PASSWORD).andExpect(status().isBadRequest());
		reset(second, NEW_PASSWORD).andExpect(status().isNoContent());
	}

	@Test
	void resettingSignsTheAccountOutEverywhere() throws Exception {
		UserRecord user = insertUser();
		login(user.email(), OLD_PASSWORD).andExpect(status().isOk());
		assertThat(activeRefreshTokens(user.id())).isEqualTo(1);

		reset(requestToken(user.email()), NEW_PASSWORD).andExpect(status().isNoContent());

		assertThat(activeRefreshTokens(user.id())).isZero();
	}

	@Test
	void aWeakPasswordIsRejectedWithoutUsingUpTheLink() throws Exception {
		UserRecord user = insertUser();
		String token = requestToken(user.email());

		reset(token, "short").andExpect(status().isBadRequest());

		reset(token, NEW_PASSWORD).andExpect(status().isNoContent());
	}

	@Test
	void theConfirmationEmailGoesToTheOwnerAfterAReset() throws Exception {
		UserRecord user = insertUser();
		String token = requestToken(user.email());

		service.resetPassword(token, NEW_PASSWORD);

		ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
		verify(gateway).send(captor.capture());
		assertThat(captor.getValue().subject()).isEqualTo("Your SeatFlow password was changed");
		assertThat(captor.getValue().to()).isEqualTo(user.email());
	}

	@Test
	void askingForALinkLooksTheSameWhetherOrNotTheAccountExists() throws Exception {
		UserRecord user = insertUser();

		mockMvc.perform(post("/api/v1/auth/forgot-password")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"%s\"}".formatted(user.email())))
				.andExpect(status().isAccepted())
				.andExpect(content().string(""));
		mockMvc.perform(post("/api/v1/auth/forgot-password")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"nobody-%s@example.com\"}".formatted(UUID.randomUUID())))
				.andExpect(status().isAccepted())
				.andExpect(content().string(""));
	}

	@Test
	void aMalformedAddressIsRejected() throws Exception {
		mockMvc.perform(post("/api/v1/auth/forgot-password")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"not-an-email\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void anUnknownLinkIsRefused() throws Exception {
		reset("not-a-real-token", NEW_PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PASSWORD_RESET_TOKEN"));
	}

	@Test
	void expiredLinksAreSweptAway() {
		UserRecord user = insertUser();
		requestToken(user.email());
		jdbcTemplate.update(
				"UPDATE password_reset_tokens SET created_at = NOW() - INTERVAL '3 days', "
						+ "expires_at = NOW() - INTERVAL '2 days' WHERE user_id = ?",
				user.id());

		int deleted = passwordResetMapper.deleteExpired(
				java.time.Instant.now().minus(Duration.ofDays(1)), 10);

		assertThat(deleted).isEqualTo(1);
	}

	private String requestToken(String email) {
		org.mockito.Mockito.clearInvocations(gateway);
		service.requestReset(email);
		ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
		verify(gateway).send(captor.capture());
		Matcher matcher = TOKEN_IN_LINK.matcher(captor.getValue().text());
		assertThat(matcher.find()).isTrue();
		org.mockito.Mockito.reset(gateway);
		return matcher.group(1);
	}

	private org.springframework.test.web.servlet.ResultActions reset(String token, String password)
			throws Exception {
		return mockMvc.perform(post("/api/v1/auth/reset-password")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"%s\",\"password\":\"%s\"}".formatted(token, password)));
	}

	private org.springframework.test.web.servlet.ResultActions login(String email, String password)
			throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
	}

	private UserRecord insertUser() {
		UUID id = UUID.randomUUID();
		userMapper.insert(UserRecord.forInsert(
				id, "reset-%s@example.com".formatted(id), passwordEncoder.encode(OLD_PASSWORD)));
		return userMapper.findById(id);
	}

	private int activeRefreshTokens(UUID userId) {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL", Integer.class,
				userId);
	}

}
