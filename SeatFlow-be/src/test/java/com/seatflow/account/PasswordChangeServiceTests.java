package com.seatflow.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
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

import com.seatflow.auth.AuthenticationFailedException;
import com.seatflow.auth.PasswordResetMapper;
import com.seatflow.auth.RefreshTokenGenerator;
import com.seatflow.notification.AccountEmailTemplates;
import com.seatflow.notification.EmailDeliveryException;
import com.seatflow.notification.EmailGateway;
import com.seatflow.notification.EmailMessage;
import com.seatflow.user.UserMapper;
import com.seatflow.user.UserRecord;
import com.seatflow.user.UserRole;
import com.seatflow.user.UserStatus;

@ExtendWith(MockitoExtension.class)
class PasswordChangeServiceTests {

	private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
	private static final UUID USER_ID = UUID.fromString("0a8b70f9-cbe7-4a57-bd34-b4a2f133779e");
	private static final ChangePasswordRequest REQUEST = new ChangePasswordRequest("OldPassw0rd!xy", "NewPassw0rd!xy");

	@Mock
	private UserMapper userMapper;

	@Mock
	private PasswordResetMapper credentialMapper;

	@Mock
	private RefreshTokenGenerator tokenGenerator;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private EmailGateway emailGateway;

	@Mock
	private PlatformTransactionManager transactionManager;

	private PasswordChangeService service;

	@BeforeEach
	void setUp() {
		service = new PasswordChangeService(
				userMapper,
				credentialMapper,
				tokenGenerator,
				passwordEncoder,
				emailGateway,
				new AccountEmailTemplates("https://seatflow.example"),
				new TransactionTemplate(transactionManager),
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void changesThePasswordAndKeepsOnlyTheCurrentSession() {
		stubUser(UserStatus.ACTIVE);
		when(passwordEncoder.matches("OldPassw0rd!xy", "old-hash")).thenReturn(true);
		when(passwordEncoder.encode("NewPassw0rd!xy")).thenReturn("new-hash");
		when(tokenGenerator.hashToken("raw-cookie")).thenReturn("cookie-hash");
		when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

		service.changePassword(USER_ID, REQUEST, "raw-cookie");

		verify(credentialMapper).updatePasswordHash(USER_ID, "new-hash");
		verify(credentialMapper).invalidateOutstanding(USER_ID, NOW);
		verify(credentialMapper).revokeOtherSessions(USER_ID, "cookie-hash", NOW);
		ArgumentCaptor<EmailMessage> message = ArgumentCaptor.forClass(EmailMessage.class);
		verify(emailGateway).send(message.capture());
		assertThat(message.getValue().to()).isEqualTo("buyer@example.com");
		assertThat(message.getValue().text()).contains("other devices were signed out");
	}

	@Test
	void withoutASessionCookieEverySessionEnds() {
		stubUser(UserStatus.ACTIVE);
		when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
		when(passwordEncoder.encode(anyString())).thenReturn("new-hash");
		when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

		service.changePassword(USER_ID, REQUEST, null);

		verify(credentialMapper).revokeOtherSessions(USER_ID, null, NOW);
		verify(tokenGenerator, never()).hashToken(any());
	}

	@Test
	void aWrongCurrentPasswordChangesNothing() {
		stubUser(UserStatus.ACTIVE);
		when(passwordEncoder.matches("OldPassw0rd!xy", "old-hash")).thenReturn(false);

		assertThatThrownBy(() -> service.changePassword(USER_ID, REQUEST, "raw-cookie"))
				.isInstanceOf(InvalidCurrentPasswordException.class);

		verify(credentialMapper, never()).updatePasswordHash(any(), anyString());
		verify(credentialMapper, never()).revokeOtherSessions(any(), any(), any());
		verify(emailGateway, never()).send(any());
	}

	@Test
	void theSamePasswordIsRefused() {
		stubUser(UserStatus.ACTIVE);
		when(passwordEncoder.matches("SamePassw0rd!xy", "old-hash")).thenReturn(true);

		assertThatThrownBy(() -> service.changePassword(
				USER_ID, new ChangePasswordRequest("SamePassw0rd!xy", "SamePassw0rd!xy"), null))
				.isInstanceOf(PasswordUnchangedException.class);

		verify(credentialMapper, never()).updatePasswordHash(any(), anyString());
	}

	@Test
	void aDisabledOrMissingAccountCannotChangeAPassword() {
		when(userMapper.findById(USER_ID)).thenReturn(null);

		assertThatThrownBy(() -> service.changePassword(USER_ID, REQUEST, null))
				.isInstanceOf(AuthenticationFailedException.class);

		verify(passwordEncoder, never()).matches(any(), any());
	}

	@Test
	void aFailedNoticeEmailDoesNotUndoTheChange() {
		stubUser(UserStatus.ACTIVE);
		when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
		when(passwordEncoder.encode(anyString())).thenReturn("new-hash");
		when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		doThrow(new EmailDeliveryException("smtp down", new RuntimeException())).when(emailGateway).send(any());

		service.changePassword(USER_ID, REQUEST, null);

		verify(credentialMapper).updatePasswordHash(USER_ID, "new-hash");
		verify(credentialMapper).revokeOtherSessions(eqUser(), isNull(), any());
	}

	private static UUID eqUser() {
		return org.mockito.ArgumentMatchers.eq(USER_ID);
	}

	private void stubUser(UserStatus status) {
		when(userMapper.findById(USER_ID)).thenReturn(new UserRecord(
				USER_ID, "buyer@example.com", "old-hash", UserRole.USER, status, NOW.minusSeconds(3600),
				NOW.minusSeconds(3600)));
	}
}
