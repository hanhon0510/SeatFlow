package com.seatflow.maintenance;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "seatflow.maintenance")
public record MaintenanceProperties(
		Boolean enabled,
		Integer batchSize,
		Integer maxBatchesPerRun,
		Duration outboxRetention,
		Duration processedEventRetention) {

	public MaintenanceProperties {
		enabled = enabled == null || enabled;
		batchSize = positive(batchSize) ? batchSize : 500;
		maxBatchesPerRun = positive(maxBatchesPerRun) ? maxBatchesPerRun : 20;
		outboxRetention = positive(outboxRetention) ? outboxRetention : Duration.ofDays(7);
		processedEventRetention = positive(processedEventRetention)
				? processedEventRetention
				: Duration.ofDays(7);
	}

	public boolean isEnabled() {
		return Boolean.TRUE.equals(enabled);
	}

	private static boolean positive(Integer value) {
		return value != null && value > 0;
	}

	private static boolean positive(Duration value) {
		return value != null && !value.isNegative() && !value.isZero();
	}
}
