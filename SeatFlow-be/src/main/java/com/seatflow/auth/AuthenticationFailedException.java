package com.seatflow.auth;

public class AuthenticationFailedException extends RuntimeException {

	public AuthenticationFailedException() {
		super("Invalid email or password");
	}

}
