package com.seatflow.account;

/** The new password is the same as the current one, so nothing would change. */
public class PasswordUnchangedException extends RuntimeException {

	public PasswordUnchangedException() {
		super("The new password must be different from the current one");
	}
}
