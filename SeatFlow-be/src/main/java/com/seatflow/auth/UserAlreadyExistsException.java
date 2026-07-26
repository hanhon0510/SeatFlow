package com.seatflow.auth;

public class UserAlreadyExistsException extends RuntimeException {

	public UserAlreadyExistsException(Throwable cause) {
		super("User already exists", cause);
	}

}
