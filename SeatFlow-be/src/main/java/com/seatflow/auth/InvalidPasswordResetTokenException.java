package com.seatflow.auth;

/** The reset link is unknown, expired or already used. The three are deliberately indistinguishable. */
public class InvalidPasswordResetTokenException extends RuntimeException {

	public InvalidPasswordResetTokenException() {
		super("Password reset token is invalid or expired");
	}
}
