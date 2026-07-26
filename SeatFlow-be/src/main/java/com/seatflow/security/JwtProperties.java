package com.seatflow.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "seatflow.jwt")
public record JwtProperties(String secret, String issuer, long expiresInSeconds) {
}
