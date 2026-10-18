package com.seatflow.reminder;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ScheduledEventRemindersTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withBean(EventReminderService.class, () -> mock(EventReminderService.class))
			.withUserConfiguration(ScheduledEventReminders.class);

	@Test
	void doesNotRunByDefault() {
		runner.run(context -> org.assertj.core.api.Assertions.assertThat(context)
				.doesNotHaveBean(ScheduledEventReminders.class));
	}

	@Test
	void runsWhenEnabled() {
		runner.withPropertyValues("seatflow.reminders.enabled=true").run(context ->
				org.assertj.core.api.Assertions.assertThat(context).hasSingleBean(ScheduledEventReminders.class));
	}

	@Test
	void aFailedPassIsSwallowedSoTheScheduleKeepsRunning() {
		EventReminderService service = mock(EventReminderService.class);
		when(service.sendDueReminders()).thenThrow(new IllegalStateException("database down"));

		assertThatCode(() -> new ScheduledEventReminders(service).run()).doesNotThrowAnyException();

		verify(service).sendDueReminders();
	}

	@Test
	void propertiesFallBackToSaneDefaults() {
		ReminderProperties properties = new ReminderProperties(null, 9999, 0, 5L);

		org.assertj.core.api.Assertions.assertThat(properties.isEnabled()).isFalse();
		org.assertj.core.api.Assertions.assertThat(properties.defaultHoursBefore()).isEqualTo(24);
		org.assertj.core.api.Assertions.assertThat(properties.batchSize()).isEqualTo(100);
		org.assertj.core.api.Assertions.assertThat(properties.fixedDelayMs()).isEqualTo(60_000L);
	}
}
