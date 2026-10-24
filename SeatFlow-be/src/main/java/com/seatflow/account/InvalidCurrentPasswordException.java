package com.seatflow.account;

/** The password typed as "current" is not the account's password. */
public class InvalidCurrentPasswordException extends RuntimeException {

	public InvalidCurrentPasswordException() {
		super("Current password is incorrect");
	}
}
