package com.seatflow.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.event.EventSeatRecord;
import com.seatflow.order.OrderRecord;
import com.seatflow.payment.PaymentRecord;
import com.seatflow.refund.RefundRecord;

@Service
public class OutboxService {

	private static final String ORDER_AGGREGATE = "Order";
	private static final String ORDER_PAID_EVENT = "OrderPaid";
	private static final int ORDER_PAID_VERSION = 1;
	private static final String ORDER_REFUNDED_EVENT = "OrderRefunded";
	private static final int ORDER_REFUNDED_VERSION = 1;

	private final OutboxMapper outboxMapper;
	private final ObjectMapper objectMapper;

	public OutboxService(OutboxMapper outboxMapper, ObjectMapper objectMapper) {
		this.outboxMapper = outboxMapper;
		this.objectMapper = objectMapper;
	}

	public OutboxEventRecord recordOrderPaid(
			OrderRecord order,
			PaymentRecord payment,
			List<EventSeatRecord> purchasedSeats,
			Instant paidAt) {
		OutboxEventRecord event = new OutboxEventRecord(
				UUID.randomUUID(),
				ORDER_AGGREGATE,
				order.id(),
				ORDER_PAID_EVENT,
				ORDER_PAID_VERSION,
				serialize(new OrderPaidPayload(
						order.id(),
						order.reservationId(),
						order.userId(),
						payment.id(),
						order.totalAmount(),
						order.currency(),
						purchasedSeats.stream().map(EventSeatRecord::id).toList(),
						paidAt)),
				payment.id(),
				OutboxEventStatus.PENDING,
				0,
				paidAt,
				null,
				paidAt);
		if (outboxMapper.insert(event) != 1) {
			throw new OutboxStorageException();
		}
		return event;
	}

	public OutboxEventRecord recordOrderRefunded(
			OrderRecord order,
			RefundRecord refund,
			List<UUID> eventSeatIds) {
		OutboxEventRecord event = new OutboxEventRecord(
				UUID.randomUUID(),
				ORDER_AGGREGATE,
				order.id(),
				ORDER_REFUNDED_EVENT,
				ORDER_REFUNDED_VERSION,
				serialize(new OrderRefundedPayload(
						order.id(),
						refund.id(),
						refund.paymentId(),
						order.userId(),
						refund.amount(),
						refund.currency(),
						eventSeatIds,
						refund.createdAt())),
				refund.id(),
				OutboxEventStatus.PENDING,
				0,
				refund.createdAt(),
				null,
				refund.createdAt());
		if (outboxMapper.insert(event) != 1) {
			throw new OutboxStorageException();
		}
		return event;
	}

	private String serialize(Object payload) {
		try {
			return objectMapper.writeValueAsString(payload);
		}
		catch (JsonProcessingException ex) {
			throw new OutboxStorageException(ex);
		}
	}
}
