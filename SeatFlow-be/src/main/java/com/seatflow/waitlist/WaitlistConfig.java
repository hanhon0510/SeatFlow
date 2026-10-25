package com.seatflow.waitlist;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WaitlistProperties.class)
public class WaitlistConfig {
}
