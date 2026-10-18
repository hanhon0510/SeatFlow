package com.seatflow.reminder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

/**
 * Lets admins choose how long before the show buyers are reminded, and sends those reminders.
 *
 * <p>Each order is reminded at most once. The reminder is claimed in {@code order_reminders} in the
 * same transaction as the send: a failed send rolls the claim back so the next pass tries again,
 * and two application instances running the job cannot both send the same reminder.
 */
@Service
public class EventReminderService {

	private static final Logger log = LoggerFactory.getLogger(EventReminderService.class);

	private final EventMapper eventMapper;
	private final EventReminderMapper reminderMapper;
	private final OrderNotificationMapper notificationMapper;
	private final OrderEmailTemplates templates;
	private final EmailGateway emailGateway;
	private final BusinessMetrics businessMetrics;
	private final ReminderProperties properties;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	public EventReminderService(
			EventMapper eventMapper,
			EventReminderMapper reminderMapper,
			OrderNotificationMapper notificationMapper,
			OrderEmailTemplates templates,
			EmailGateway emailGateway,
			BusinessMetrics businessMetrics,
			ReminderProperties properties,
			TransactionTemplate transactionTemplate,
			Clock clock) {
		this.eventMapper = eventMapper;
		this.reminderMapper = reminderMapper;
		this.notificationMapper = notificationMapper;
		this.templates = templates;
		this.emailGateway = emailGateway;
		this.businessMetrics = businessMetrics;
		this.properties = properties;
		this.transactionTemplate = transactionTemplate;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public EventReminderResponse getSettings(UUID eventId) {
		return response(requireEvent(eventId));
	}

	@Transactional
	public EventReminderResponse updateSettings(UUID eventId, UUID adminId, EventReminderRequest request) {
		EventRecord event = requireEvent(eventId);
		// Reminders only make sense for an event that is still going to happen.
		if (event.status() != EventStatus.DRAFT && event.status() != EventStatus.PUBLISHED) {
			throw new EventStateConflictException();
		}
		reminderMapper.upsertSettings(eventId, request.enabled(), request.hoursBefore(), adminId, clock.instant());
		return response(event);
	}

	/**
	 * One pass of the background job.
	 *
	 * @return how many reminders were sent
	 */
	public int sendDueReminders() {
		Instant now = clock.instant();
		List<UUID> dueOrderIds = reminderMapper.findDueOrderIds(
				now, properties.defaultHoursBefore(), properties.batchSize());
		int sent = 0;
		for (UUID orderId : dueOrderIds) {
			try {
				if (Boolean.TRUE.equals(transactionTemplate.execute(status -> remind(orderId, now)))) {
					sent++;
				}
			}
			catch (RuntimeException ex) {
				// Left unclaimed, so it is found again on the next pass.
				log.warn("Could not send the reminder for order {}; it will be retried", orderId, ex);
			}
		}
		return sent;
	}

	private boolean remind(UUID orderId, Instant now) {
		if (reminderMapper.claim(orderId, now) != 1) {
			return false;
		}
		OrderEmailDetails order = notificationMapper.findOrderDetails(orderId);
		if (order == null) {
			return false;
		}
		List<OrderEmailSeat> seats = notificationMapper.findSeats(orderId);
		Duration untilStart = Duration.between(now, order.eventStartTime());
		EmailMessage message = templates.eventReminder(order, seats, untilStart);
		try {
			emailGateway.send(message);
		}
		catch (EmailDeliveryException ex) {
			businessMetrics.emailFailed();
			throw ex;
		}
		businessMetrics.emailSent();
		log.info("Sent event reminder orderId={} eventId={}", orderId, order.eventId());
		return true;
	}

	private EventRecord requireEvent(UUID eventId) {
		EventRecord event = eventMapper.findById(eventId);
		if (event == null) {
			throw new EventNotFoundException();
		}
		return event;
	}

	private EventReminderResponse response(EventRecord event) {
		EventReminderSettingsRecord settings = reminderMapper.findSettings(event.id());
		boolean enabled = settings == null || settings.enabled();
		int hours = settings == null ? properties.defaultHoursBefore() : settings.hoursBefore();
		Instant remindAt = enabled ? event.startTime().minus(Duration.ofHours(hours)) : null;
		return new EventReminderResponse(
				event.id(), enabled, hours, settings == null, reminderMapper.countSent(event.id()), remindAt);
	}
}
