package com.seatflow.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.seatflow.notification.AccountEmailTemplates;
import com.seatflow.notification.EmailDeliveryException;
import com.seatflow.notification.EmailGateway;
import com.seatflow.user.UserMapper;
import com.seatflow.user.UserRecord;
import com.seatflow.user.UserStatus;

/**
 * Lets someone who cannot sign in choose a new password through a link sent to their email.
 *
 * <p>Only a hash of each link's token is stored, so a leaked database cannot be used to take over
 * accounts. A link works once, expires, and is replaced by any newer one; using it also signs the
 * account out everywhere, because whoever knew the old password may still be signed in.
 */
@Service
public class PasswordResetService {

	private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

	private final UserMapper userMapper;
	private final PasswordResetMapper passwordResetMapper;
	private final RefreshTokenMapper refreshTokenMapper;
	private final RefreshTokenGenerator tokenGenerator;
	private final PasswordEncoder passwordEncoder;
	private final PasswordResetProperties properties;
	private final EmailGateway emailGateway;
	private final AccountEmailTemplates emailTemplates;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	public PasswordResetService(
			UserMapper userMapper,
			PasswordResetMapper passwordResetMapper,
			RefreshTokenMapper refreshTokenMapper,
			RefreshTokenGenerator tokenGenerator,
			PasswordEncoder passwordEncoder,
			PasswordResetProperties properties,
			EmailGateway emailGateway,
			AccountEmailTemplates emailTemplates,
			TransactionTemplate transactionTemplate,
			Clock clock) {
		this.userMapper = userMapper;
		this.passwordResetMapper = passwordResetMapper;
		this.refreshTokenMapper = refreshTokenMapper;
		this.tokenGenerator = tokenGenerator;
		this.passwordEncoder = passwordEncoder;
		this.properties = properties;
		this.emailGateway = emailGateway;
		this.emailTemplates = emailTemplates;
		this.transactionTemplate = transactionTemplate;
		this.clock = clock;
	}

	/**
	 * Always completes quietly, whether or not the address belongs to an account, so the endpoint
	 * cannot be used to find out who is registered.
	 */
	public void requestReset(String email) {
		UserRecord user = userMapper.findByNormalizedEmail(email.trim().toLowerCase(Locale.ROOT));
		if (user == null || user.status() == UserStatus.DISABLED) {
			log.info("Password reset requested for an address with no active account");
			return;
		}

		String token = tokenGenerator.generateToken();
		Instant now = clock.instant();
		transactionTemplate.executeWithoutResult(status -> {
			passwordResetMapper.invalidateOutstanding(user.id(), now);
			passwordResetMapper.insert(
					UUID.randomUUID(), user.id(), tokenGenerator.hashToken(token), now.plus(properties.ttl()), now);
		});

		// After the commit, so the link in the email always points at a token that exists. A failure
		// here is logged rather than reported: telling the caller would reveal the account exists.
		try {
			emailGateway.send(emailTemplates.passwordReset(user.email(), token, properties.ttl()));
		}
		catch (EmailDeliveryException ex) {
			log.warn("Could not send the password reset email for user {}", user.id(), ex);
		}
	}

	public void resetPassword(String token, String newPassword) {
		String tokenHash = tokenGenerator.hashToken(token.trim());
		String newHash = passwordEncoder.encode(newPassword);
		Instant now = clock.instant();

		UserRecord user = transactionTemplate.execute(status -> {
			UUID userId = passwordResetMapper.consume(tokenHash, now);
			UserRecord owner = userId == null ? null : userMapper.findById(userId);
			if (owner == null || owner.status() == UserStatus.DISABLED) {
				// Rolling back also un-uses the link, which is harmless: it is refused again next time.
				throw new InvalidPasswordResetTokenException();
			}
			passwordResetMapper.updatePasswordHash(owner.id(), newHash);
			passwordResetMapper.invalidateOutstanding(owner.id(), now);
			refreshTokenMapper.revokeAllUserTokens(owner.id(), now);
			return owner;
		});

		try {
			emailGateway.send(emailTemplates.passwordChanged(user.email()));
		}
		catch (EmailDeliveryException ex) {
			log.warn("Could not send the password changed email for user {}", user.id(), ex);
		}
	}
}
