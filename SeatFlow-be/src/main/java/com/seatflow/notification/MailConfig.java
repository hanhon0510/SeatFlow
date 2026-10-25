package com.seatflow.notification;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SeatFlowMailProperties.class)
public class MailConfig {

	@Bean
	public OrderEmailTemplates orderEmailTemplates(SeatFlowMailProperties properties) {
		return new OrderEmailTemplates(properties.frontendBaseUrl());
	}

	@Bean
	public AccountEmailTemplates accountEmailTemplates(SeatFlowMailProperties properties) {
		return new AccountEmailTemplates(properties.frontendBaseUrl());
	}

	@Bean
	public WaitlistEmailTemplates waitlistEmailTemplates(SeatFlowMailProperties properties) {
		return new WaitlistEmailTemplates(properties.frontendBaseUrl());
	}
}
