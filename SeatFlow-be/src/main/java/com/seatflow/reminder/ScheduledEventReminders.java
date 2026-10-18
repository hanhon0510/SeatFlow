package com.seatflow.reminder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs reminder passes in the background while {@code seatflow.reminders.enabled} is true. */
@Component
@ConditionalOnProperty(prefix = "seatflow.reminders", name = "enabled", havingValue = "true")
public class ScheduledEventReminders {

	private static final Logger log = LoggerFactory.getLogger(ScheduledEventReminders.class);

	private final EventReminderService reminderService;

	public ScheduledEventReminders(EventReminderService reminderService) {
		this.reminderService = reminderService;
	}

	@Scheduled(fixedDelayString = "${seatflow.reminders.fixed-delay-ms:60000}")
	public void run() {
		try {
			int sent = reminderService.sendDueReminders();
			if (sent > 0) {
				log.info("Sent {} event reminders", sent);
			}
		}
		catch (RuntimeException ex) {
			log.warn("Event reminder pass failed; it will run again", ex);
		}
	}
}
