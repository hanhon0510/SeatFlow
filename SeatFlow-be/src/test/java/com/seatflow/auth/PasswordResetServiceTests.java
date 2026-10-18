package com.seatflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.seatflow.notification.AccountEmailTemplates;
import com.seatflow.notification.EmailDeliveryException;
import com.seatflow.notification.EmailGateway;
import com.seatflow.notification.EmailMessage;
import com.seatflow.user.UserMapper;
import com.seatflow.user.UserRecord;
import com.seatflow.user.UserRole;
import com.seatflow.user.UserStatus;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTests {

	private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
	private static final UUID USER_ID = UUID.fromString("0a8b70f9-cbe7-4a57-bd34-b4a2f133779e");

	@Mock
	private UserMapper userMapper;

	@Mock
	private PasswordResetMapper passwordResetMapper;

	@Mock
	private RefreshTokenMapper refreshTokenMapper;

	@Mock
	private RefreshTokenGenerator tokenGenerator;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private EmailGateway emailGateway;

	@Mock
	private PlatformTransactionManager transactionManager;

	private PasswordResetService service;

	@BeforeEach
	void setUp() {
		service = new PasswordResetService(
				userMapper,
				passwordResetMapper,
				refreshTokenMapper,
				tokenGenerator,
				passwordEncoder,
				new PasswordResetProperties(Duration.ofMinutes(30)),
				emailGateway,
				new AccountEmailTemplates("https://seatflow.example"),
				new TransactionTemplate(transactionManager),
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void anUnknownAddressGetsNoTokenAndNoEmail() {
		when(userMapper.findByNormalizedEmail("ghost@example.com")).thenReturn(null);

		service.requestReset("  Ghost@Example.com ");

		verify(passwordResetMapper, never()).insert(any(), any(), anyString(), any(), any());
		verify(emailGateway, never()).send(any());
	}

	@Test
	void aDisabledAccountGetsNoTokenAndNoEmail() {
		when(userMapper.findByNormalizedEmail("off@example.com")).thenReturn(user(UserStatus.DISABLED));

		service.requestReset("off@example.com");

		verify(passwordResetMapper, never()).insert(any(), any(), anyString(), any(), any());
		verify(emailGateway, never()).send(any());
	}

	@Test
	void aKnownAddressStoresOnlyTheHashAndEmailsTheRawToken() {
		stubTransaction();
		when(userMapper.findByNormalizedEmail("buyer@example.com")).thenReturn(user(UserStatus.ACTIVE));
		when(tokenGenerator.generateToken()).thenReturn("raw-token-value");
		when(tokenGenerator.hashToken("raw-token-value")).thenReturn("hashed-value");

		service.requestReset("Buyer@Example.com");

		verify(passwordResetMapper).invalidateOutstanding(USER_ID, NOW);
		verify(passwordResetMapper).insert(
				any(UUID.class), eq(USER_ID), eq("hashed-value"), eq(NOW.plus(Duration.ofMinutes(30))), eq(NOW));
		ArgumentCaptor<EmailMessage> message = ArgumentCaptor.forClass(EmailMessage.class);
		verify(emailGateway).send(message.capture());
		assertThat(message.getValue().to()).isEqualTo("buyer@example.com");
		assertThat(message.getValue().text())
				.contains("https://seatflow.example/reset-password?token=raw-token-value")
				.contains("30 minutes")
				.doesNotContain("hashed-value");
	}

	@Test
	void aFailedEmailDoesNotRevealWhetherTheAccountExists() {
		stubTransaction();
		when(userMapper.findByNormalizedEmail("buyer@example.com")).thenReturn(user(UserStatus.ACTIVE));
		when(tokenGenerator.generateToken()).thenReturn("raw-token-value");
		when(tokenGenerator.hashToken(anyString())).thenReturn("hashed-value");
		doThrow(new EmailDeliveryException("smtp down", new RuntimeException())).when(emailGateway).send(any());

		service.requestReset("buyer@example.com");

		verify(passwordResetMapper).insert(any(), any(), anyString(), any(), any());
	}

	@Test
	void anUnknownOrUsedLinkIsRefusedAndChangesNothing() {
		stubTransaction();
		when(tokenGenerator.hashToken("stale")).thenReturn("stale-hash");
		when(passwordEncoder.encode("NewPassw0rd!x")).thenReturn("encoded");
		when(passwordResetMapper.consume("stale-hash", NOW)).thenReturn(null);

		assertThatThrownBy(() -> service.resetPassword("stale", "NewPassw0rd!x"))
				.isInstanceOf(InvalidPasswordResetTokenException.class);

		verify(passwordResetMapper, never()).updatePasswordHash(any(), anyString());
		verify(refreshTokenMapper, never()).revokeAllUserTokens(any(), any());
		verify(emailGateway, never()).send(any());
	}

	@Test
	void aValidLinkChangesThePasswordSignsEveryoneOutAndTellsTheOwner() {
		stubTransaction();
		when(tokenGenerator.hashToken("good")).thenReturn("good-hash");
		when(passwordEncoder.encode("NewPassw0rd!x")).thenReturn("encoded");
		when(passwordResetMapper.consume("good-hash", NOW)).thenReturn(USER_ID);
		when(userMapper.findById(USER_ID)).thenReturn(user(UserStatus.ACTIVE));

		service.resetPassword("  good ", "NewPassw0rd!x");

		verify(passwordResetMapper).updatePasswordHash(USER_ID, "encoded");
		verify(passwordResetMapper).invalidateOutstanding(USER_ID, NOW);
		verify(refreshTokenMapper).revokeAllUserTokens(USER_ID, NOW);
		ArgumentCaptor<EmailMessage> message = ArgumentCaptor.forClass(EmailMessage.class);
		verify(emailGateway).send(message.capture());
		assertThat(message.getValue().subject()).isEqualTo("Your SeatFlow password was changed");
	}

	@Test
	void aDisabledAccountCannotResetItsPassword() {
		stubTransaction();
		when(tokenGenerator.hashToken("good")).thenReturn("good-hash");
		when(passwordEncoder.encode(anyString())).thenReturn("encoded");
		when(passwordResetMapper.consume("good-hash", NOW)).thenReturn(USER_ID);
		when(userMapper.findById(USER_ID)).thenReturn(user(UserStatus.DISABLED));

		assertThatThrownBy(() -> service.resetPassword("good", "NewPassw0rd!x"))
				.isInstanceOf(InvalidPasswordResetTokenException.class);

		verify(passwordResetMapper, never()).updatePasswordHash(any(), anyString());
	}

	@Test
	void aFailedConfirmationEmailDoesNotUndoTheReset() {
		stubTransaction();
		when(tokenGenerator.hashToken("good")).thenReturn("good-hash");
		when(passwordEncoder.encode(anyString())).thenReturn("encoded");
		when(passwordResetMapper.consume("good-hash", NOW)).thenReturn(USER_ID);
		when(userMapper.findById(USER_ID)).thenReturn(user(UserStatus.ACTIVE));
		doThrow(new EmailDeliveryException("smtp down", new RuntimeException())).when(emailGateway).send(any());

		service.resetPassword("good", "NewPassw0rd!x");

		verify(passwordResetMapper).updatePasswordHash(USER_ID, "encoded");
	}

	private void stubTransaction() {
		when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
	}

	private static UserRecord user(UserStatus status) {
		return new UserRecord(
				USER_ID, "buyer@example.com", "old-hash", UserRole.USER, status, NOW.minusSeconds(3600),
				NOW.minusSeconds(3600));
	}
}
