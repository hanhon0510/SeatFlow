package com.seatflow.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.seatflow.event.EventMapper;
import com.seatflow.event.EventNotFoundException;
import com.seatflow.event.EventRecord;
import com.seatflow.event.EventStateConflictException;
import com.seatflow.event.EventStatus;
import com.seatflow.notification.EmailDeliveryException;
import com.seatflow.notification.EmailGateway;
import com.seatflow.notification.EmailMessage;
import com.seatflow.notification.OrderEmailDetails;
import com.seatflow.notification.OrderEmailSeat;
import com.seatflow.notification.OrderEmailTemplates;
import com.seatflow.notification.OrderNotificationMapper;
import com.seatflow.observability.BusinessMetrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class EventReminderServiceTests {

	private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
	private static final Instant EVENT_START = Instant.parse("2026-11-02T10:00:00Z");
	private static final UUID EVENT_ID = UUID.fromString("9a2c4f10-6a1e-4c55-b3d4-1d0f6a8e7b21");
	private static final UUID ADMIN_ID = UUID.fromString("7ca3110b-513f-4445-a52d-dc96b1c2da8f");
	private static final UUID ORDER_ID = UUID.fromString("abcdef12-3456-4789-8abc-def012345678");

	@Mock
	private EventMapper eventMapper;

	@Mock
	private EventReminderMapper reminderMapper;

	@Mock
	private OrderNotificationMapper notificationMapper;

	@Mock
	private EmailGateway emailGateway;

	@Mock
	private PlatformTransactionManager transactionManager;

	private SimpleMeterRegistry meterRegistry;

	private EventReminderService service;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		service = new EventReminderService(
				eventMapper,
				reminderMapper,
				notificationMapper,
				new OrderEmailTemplates("https://seatflow.example"),
				emailGateway,
				new BusinessMetrics(meterRegistry),
				new ReminderProperties(true, 24, 50, 60_000L),
				new TransactionTemplate(transactionManager),
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void anEventNobodyConfiguredUsesTheApplicationDefault() {
		when(eventMapper.findById(EVENT_ID)).thenReturn(event(EventStatus.PUBLISHED));
		when(reminderMapper.findSettings(EVENT_ID)).thenReturn(null);
		when(reminderMapper.countSent(EVENT_ID)).thenReturn(3L);

		EventReminderResponse response = service.getSettings(EVENT_ID);

		assertThat(response.enabled()).isTrue();
		assertThat(response.hoursBefore()).isEqualTo(24);
		assertThat(response.usingDefault()).isTrue();
		assertThat(response.remindersSent()).isEqualTo(3);
		assertThat(response.remindAt()).isEqualTo(EVENT_START.minus(Duration.ofHours(24)));
	}

	@Test
	void aDisabledEventHasNoReminderTime() {
		when(eventMapper.findById(EVENT_ID)).thenReturn(event(EventStatus.PUBLISHED));
		when(reminderMapper.findSettings(EVENT_ID)).thenReturn(settings(false, 6));

		EventReminderResponse response = service.getSettings(EVENT_ID);

		assertThat(response.enabled()).isFalse();
		assertThat(response.usingDefault()).isFalse();
		assertThat(response.remindAt()).isNull();
	}

	@Test
	void anAdminCanChooseHowManyHoursBefore() {
		when(eventMapper.findById(EVENT_ID)).thenReturn(event(EventStatus.PUBLISHED));
		when(reminderMapper.findSettings(EVENT_ID)).thenReturn(settings(true, 48));

		EventReminderResponse response = service.updateSettings(
				EVENT_ID, ADMIN_ID, new EventReminderRequest(true, 48));

		verify(reminderMapper).upsertSettings(EVENT_ID, true, 48, ADMIN_ID, NOW);
		assertThat(response.hoursBefore()).isEqualTo(48);
		assertThat(response.remindAt()).isEqualTo(EVENT_START.minus(Duration.ofHours(48)));
	}

	@Test
	void aCancelledEventCannotBeConfigured() {
		when(eventMapper.findById(EVENT_ID)).thenReturn(event(EventStatus.CANCELLED));

		assertThatThrownBy(() -> service.updateSettings(EVENT_ID, ADMIN_ID, new EventReminderRequest(true, 24)))
				.isInstanceOf(EventStateConflictException.class);

		verify(reminderMapper, never()).upsertSettings(any(), org.mockito.ArgumentMatchers.anyBoolean(),
				org.mockito.ArgumentMatchers.anyInt(), any(), any());
	}

	@Test
	void anUnknownEventIsNotFound() {
		when(eventMapper.findById(EVENT_ID)).thenReturn(null);

		assertThatThrownBy(() -> service.getSettings(EVENT_ID)).isInstanceOf(EventNotFoundException.class);
	}

	@Test
	void aDueOrderIsRemindedOnceAfterBeingClaimed() {
		stubDue();
		when(reminderMapper.claim(ORDER_ID, NOW)).thenReturn(1);

		int sent = service.sendDueReminders();

		assertThat(sent).isEqualTo(1);
		ArgumentCaptor<EmailMessage> message = ArgumentCaptor.forClass(EmailMessage.class);
		verify(emailGateway).send(message.capture());
		assertThat(message.getValue().subject()).isEqualTo("Reminder: Opening Night starts soon");
		assertThat(message.getValue().text()).contains("starts in about 24 hours");
		assertThat(meterRegistry.get("email_sent").counter().count()).isEqualTo(1.0);
	}

	@Test
	void anOrderAnotherInstanceAlreadyClaimedIsNotRemindedAgain() {
		when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		when(reminderMapper.findDueOrderIds(NOW, 24, 50)).thenReturn(List.of(ORDER_ID));
		when(reminderMapper.claim(ORDER_ID, NOW)).thenReturn(0);

		assertThat(service.sendDueReminders()).isZero();

		verify(emailGateway, never()).send(any());
	}

	@Test
	void aFailedSendIsCountedAndDoesNotStopTheRestOfThePass() {
		UUID other = UUID.randomUUID();
		when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		when(reminderMapper.findDueOrderIds(NOW, 24, 50)).thenReturn(List.of(ORDER_ID, other));
		when(reminderMapper.claim(any(), any())).thenReturn(1);
		when(notificationMapper.findOrderDetails(ORDER_ID)).thenReturn(order(ORDER_ID));
		when(notificationMapper.findOrderDetails(other)).thenReturn(order(other));
		when(notificationMapper.findSeats(any())).thenReturn(List.of());
		doThrow(new EmailDeliveryException("smtp down", new RuntimeException()))
				.doNothing()
				.when(emailGateway).send(any());

		int sent = service.sendDueReminders();

		assertThat(sent).isEqualTo(1);
		assertThat(meterRegistry.get("email_failed").counter().count()).isEqualTo(1.0);
	}

	private void stubDue() {
		when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		when(reminderMapper.findDueOrderIds(NOW, 24, 50)).thenReturn(List.of(ORDER_ID));
		when(notificationMapper.findOrderDetails(ORDER_ID)).thenReturn(order(ORDER_ID));
		when(notificationMapper.findSeats(ORDER_ID))
				.thenReturn(List.of(new OrderEmailSeat(UUID.randomUUID(), "Orchestra", "A", "A1")));
	}

	private static OrderEmailDetails order(UUID orderId) {
		return new OrderEmailDetails(
				orderId, "buyer@example.com", EVENT_ID, "Opening Night", EVENT_START, "Concert Hall",
				"1 Main Street", "Ho Chi Minh City", "Asia/Ho_Chi_Minh", new BigDecimal("200000"),
				BigDecimal.ZERO, null, "VND", false, null);
	}

	private static EventReminderSettingsRecord settings(boolean enabled, int hours) {
		return new EventReminderSettingsRecord(EVENT_ID, enabled, hours, ADMIN_ID, NOW);
	}

	private static EventRecord event(EventStatus status) {
		return new EventRecord(
				EVENT_ID, UUID.randomUUID(), "Opening Night", null, EVENT_START, NOW.minusSeconds(86_400),
				EVENT_START.minusSeconds(3600), status, NOW.minusSeconds(86_400), NOW.minusSeconds(86_400));
	}
}
