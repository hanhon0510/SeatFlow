package com.seatflow.account;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.seatflow.auth.AuthenticationFailedException;
import com.seatflow.auth.PasswordResetMapper;
import com.seatflow.auth.RefreshTokenGenerator;
import com.seatflow.notification.AccountEmailTemplates;
import com.seatflow.notification.EmailDeliveryException;
import com.seatflow.notification.EmailGateway;
import com.seatflow.user.UserMapper;
import com.seatflow.user.UserRecord;
import com.seatflow.user.UserStatus;

/**
 * Lets a signed-in user choose a new password. The current password is asked for again, because a
 * stolen session alone must not be enough to take an account over, and every other device is
 * signed out because whoever held the old password may still be using it.
 */
@Service
public class PasswordChangeService {

	private static final Logger log = LoggerFactory.getLogger(PasswordChangeService.class);

	private final UserMapper userMapper;
	private final PasswordResetMapper credentialMapper;
	private final RefreshTokenGenerator tokenGenerator;
	private final PasswordEncoder passwordEncoder;
	private final EmailGateway emailGateway;
	private final AccountEmailTemplates emailTemplates;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	public PasswordChangeService(
			UserMapper userMapper,
			PasswordResetMapper credentialMapper,
			RefreshTokenGenerator tokenGenerator,
			PasswordEncoder passwordEncoder,
			EmailGateway emailGateway,
			AccountEmailTemplates emailTemplates,
			TransactionTemplate transactionTemplate,
			Clock clock) {
		this.userMapper = userMapper;
		this.credentialMapper = credentialMapper;
		this.tokenGenerator = tokenGenerator;
		this.passwordEncoder = passwordEncoder;
		this.emailGateway = emailGateway;
		this.emailTemplates = emailTemplates;
		this.transactionTemplate = transactionTemplate;
		this.clock = clock;
	}

	/**
	 * @param currentRefreshToken the raw refresh token of the session making the request, which stays
	 *        signed in; null when the request carried none, in which case every session ends
	 */
	public void changePassword(UUID userId, ChangePasswordRequest request, String currentRefreshToken) {
		UserRecord user = userMapper.findById(userId);
		if (user == null || user.status() == UserStatus.DISABLED) {
			throw new AuthenticationFailedException();
		}
		if (!passwordEncoder.matches(request.currentPassword(), user.passwordHash())) {
			throw new InvalidCurrentPasswordException();
		}
		if (request.currentPassword().equals(request.newPassword())) {
			throw new PasswordUnchangedException();
		}

		String newHash = passwordEncoder.encode(request.newPassword());
		String keepHash = currentRefreshToken == null || currentRefreshToken.isBlank()
				? null
				: tokenGenerator.hashToken(currentRefreshToken);
		Instant now = clock.instant();
		transactionTemplate.executeWithoutResult(status -> {
			credentialMapper.updatePasswordHash(userId, newHash);
			// A reset link requested before the change must not outlive it.
			credentialMapper.invalidateOutstanding(userId, now);
			credentialMapper.revokeOtherSessions(userId, keepHash, now);
		});

		try {
			emailGateway.send(emailTemplates.passwordChangedInAccount(user.email()));
		}
		catch (EmailDeliveryException ex) {
			log.warn("Could not send the password changed email for user {}", userId, ex);
		}
	}
}
