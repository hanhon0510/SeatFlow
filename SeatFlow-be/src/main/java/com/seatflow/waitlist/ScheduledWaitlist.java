package com.seatflow.waitlist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs waitlist passes in the background while {@code seatflow.waitlist.enabled} is true. */
@Component
@ConditionalOnProperty(prefix = "seatflow.waitlist", name = "enabled", havingValue = "true")
public class ScheduledWaitlist {

	private static final Logger log = LoggerFactory.getLogger(ScheduledWaitlist.class);

	private final WaitlistService waitlistService;

	public ScheduledWaitlist(WaitlistService waitlistService) {
		this.waitlistService = waitlistService;
	}

	@Scheduled(fixedDelayString = "${seatflow.waitlist.fixed-delay-ms:15000}")
	public void run() {
		try {
			WaitlistPassSummary summary = waitlistService.processWaitlists();
			if (summary.offered() > 0 || summary.expired() > 0 || summary.closed() > 0) {
				log.info("Waitlist pass offered={} expired={} closed={}",
						summary.offered(), summary.expired(), summary.closed());
			}
		}
		catch (RuntimeException ex) {
			log.warn("Waitlist pass failed; it will run again", ex);
		}
	}
}
