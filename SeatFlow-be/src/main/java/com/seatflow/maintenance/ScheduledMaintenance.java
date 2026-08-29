package com.seatflow.maintenance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "seatflow.maintenance", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledMaintenance {

	private static final Logger log = LoggerFactory.getLogger(ScheduledMaintenance.class);

	private final MaintenanceService maintenanceService;

	public ScheduledMaintenance(MaintenanceService maintenanceService) {
		this.maintenanceService = maintenanceService;
	}

	@Scheduled(fixedDelayString = "${seatflow.maintenance.fixed-delay-ms:60000}")
	public void sweep() {
		try {
			maintenanceService.sweep();
		}
		catch (RuntimeException ex) {
			log.warn("Maintenance sweep failed; rows remain eligible for the next run", ex);
		}
	}
}
