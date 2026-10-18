package com.seatflow.reminder;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ReminderProperties.class)
public class ReminderConfig {
}
