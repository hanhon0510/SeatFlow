package com.seatflow.hold;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SeatHoldProperties.class)
public class SeatHoldConfig {
}
