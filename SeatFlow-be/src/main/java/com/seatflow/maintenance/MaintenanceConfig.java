package com.seatflow.maintenance;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MaintenanceProperties.class)
public class MaintenanceConfig {
}
