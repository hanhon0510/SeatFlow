package com.seatflow.auth;

public interface RefreshTokenGenerator {

	String generateToken();

	String hashToken(String token);
}
