package com.seatflow.auth;

public record AuthSession(LoginResponse accessToken, IssuedRefreshToken refreshToken) {
}
