package com.seatflow.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "seatflow.local-admin")
public record LocalAdminProperties(boolean enabled, String email, String password) {
}
