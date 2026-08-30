package com.seatflow.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.support.serializer.SerializationUtils;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.support.KafkaTestContainerSupport;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class KafkaInfrastructureIntegrationTests extends KafkaTestContainerSupport {

	@Autowired
	private KafkaEventPublisher publisher;

	@Autowired
	private SeatFlowKafkaProperties kafkaProperties;

	@Autowired
	private ConsumerFactory<Object, Object> consumerFactory;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void kafkaTestcontainerStarts() {
		assertThat(kafka().isRunning()).isTrue();
	}

	@Test
	void producerPublishesEnvelopeWithAggregateIdKey() throws Exception {
		UUID aggregateId = UUID.randomUUID();
		EventEnvelope<Map<String, String>> envelope = EventEnvelope.create(
				"OrderPaid",
				1,
				aggregateId,
				UUID.randomUUID(),
				Map.of("orderId", aggregateId.toString()));

		SendResult<Object, Object> result = publisher.publish(
						kafkaProperties.topics().orderEvents(),
						envelope)
				.get(10, TimeUnit.SECONDS);

		assertThat(result.getProducerRecord().topic()).isEqualTo(kafkaProperties.topics().orderEvents());
		assertThat(result.getProducerRecord().key()).isEqualTo(aggregateId.toString());
		assertThat(result.getRecordMetadata().topic()).isEqualTo(kafkaProperties.topics().orderEvents());
	}

	@Test
	void consumerReadsPublishedEnvelope() throws Exception {
		UUID aggregateId = UUID.randomUUID();
		EventEnvelope<Map<String, String>> envelope = EventEnvelope.create(
				"OrderPaid",
				1,
				aggregateId,
				UUID.randomUUID(),
				Map.of("orderId", aggregateId.toString()));

		try (Consumer<Object, Object> consumer = consumer("seatflow-consumer-test-" + UUID.randomUUID())) {
			consumer.subscribe(List.of(kafkaProperties.topics().orderEvents()));
			publisher.publish(kafkaProperties.topics().orderEvents(), envelope).get(10, TimeUnit.SECONDS);

			ConsumerRecord<Object, Object> record = readRecordByKey(consumer, aggregateId.toString());

			assertThat(record.value()).isInstanceOf(EventEnvelope.class);
			EventEnvelope<?> consumed = (EventEnvelope<?>) record.value();
			assertThat(consumed.eventId()).isEqualTo(envelope.eventId());
			assertThat(consumed.eventType()).isEqualTo("OrderPaid");
			assertThat(consumed.eventVersion()).isEqualTo(1);
			assertThat(consumed.aggregateId()).isEqualTo(aggregateId);
			assertThat(consumed.payload()).isInstanceOf(Map.class);
			assertThat(((Map<?, ?>) consumed.payload()).get("orderId")).isEqualTo(aggregateId.toString());
		}
	}

	@Test
	void serializesEnvelopeWithRequiredFields() throws Exception {
		UUID eventId = UUID.randomUUID();
		UUID aggregateId = UUID.randomUUID();
		UUID correlationId = UUID.randomUUID();
		Instant occurredAt = Instant.parse("2026-08-15T01:02:03Z");
		EventEnvelope<Map<String, String>> envelope = new EventEnvelope<>(
				eventId,
				"OrderPaid",
				1,
				aggregateId,
				correlationId,
				occurredAt,
				Map.of("orderId", aggregateId.toString()));

		String json = objectMapper.writeValueAsString(envelope);
		JsonNode node = objectMapper.readTree(json);
		EventEnvelope<?> decoded = objectMapper.readValue(json, EventEnvelope.class);

		assertThat(node.get("eventId").asText()).isEqualTo(eventId.toString());
		assertThat(node.get("eventType").asText()).isEqualTo("OrderPaid");
		assertThat(node.get("eventVersion").asInt()).isEqualTo(1);
		assertThat(node.get("aggregateId").asText()).isEqualTo(aggregateId.toString());
		assertThat(node.get("correlationId").asText()).isEqualTo(correlationId.toString());
		assertThat(node.get("occurredAt").asText()).isEqualTo("2026-08-15T01:02:03Z");
		assertThat(node.get("payload").get("orderId").asText()).isEqualTo(aggregateId.toString());
		assertThat(decoded.eventType()).isEqualTo("OrderPaid");
		// Asserts the payload is convertible rather than that it is one concrete Map type.
		// spring-kafka-test drags in jackson-module-scala, which Jackson auto-registers, so an
		// untyped JSON object decodes to a Scala Map here and a java.util.LinkedHashMap in
		// production. What consumers actually rely on is convertValue, which works from either.
		assertThat(objectMapper.convertValue(decoded.payload(), new TypeReference<Map<String, String>>() { }))
				.containsEntry("orderId", aggregateId.toString());
	}

	@Test
	void invalidPayloadIsReturnedAsDeserializerError() throws Exception {
		String key = UUID.randomUUID().toString();

		try (Consumer<Object, Object> consumer = consumer("seatflow-invalid-payload-test-" + UUID.randomUUID())) {
			consumer.subscribe(List.of(kafkaProperties.topics().orderEvents()));
			sendRawValue(kafkaProperties.topics().orderEvents(), key, "{invalid-json");

			ConsumerRecord<Object, Object> record = readRecordByKey(consumer, key);
			Header deserializerError = record.headers()
					.lastHeader(SerializationUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER);

			assertThat(record.value()).isNull();
			assertThat(deserializerError).isNotNull();
		}
	}

	@Test
	void brokerUnavailableFailsPublish() {
		DefaultKafkaProducerFactory<Object, Object> producerFactory =
				new DefaultKafkaProducerFactory<>(unavailableProducerProperties());
		KafkaTemplate<Object, Object> kafkaTemplate = new KafkaTemplate<>(producerFactory);
		KafkaEventPublisher unavailablePublisher = new KafkaEventPublisher(kafkaTemplate);
		EventEnvelope<Map<String, String>> envelope = EventEnvelope.create(
				"OrderPaid",
				1,
				UUID.randomUUID(),
				UUID.randomUUID(),
				Map.of());

		try {
			assertThatThrownBy(() -> unavailablePublisher.publish(
							kafkaProperties.topics().orderEvents(),
							envelope)
					.get(5, TimeUnit.SECONDS))
					.isInstanceOf(Exception.class);
		}
		finally {
			kafkaTemplate.destroy();
			producerFactory.destroy();
		}
	}

	private Consumer<Object, Object> consumer(String groupId) {
		return consumerFactory.createConsumer(groupId, "seatflow-kafka-test");
	}

	private ConsumerRecord<Object, Object> readRecordByKey(Consumer<Object, Object> consumer, String key) {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
		while (Instant.now().isBefore(deadline)) {
			for (ConsumerRecord<Object, Object> record : consumer.poll(Duration.ofMillis(250))) {
				if (key.equals(record.key())) {
					return record;
				}
			}
		}
		throw new AssertionError("Expected Kafka record with key " + key);
	}

	private void sendRawValue(String topic, String key, String value) throws Exception {
		Map<String, Object> properties = new HashMap<>();
		properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka().getBootstrapServers());
		properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

		try (Producer<String, String> producer = new KafkaProducer<>(properties)) {
			producer.send(new ProducerRecord<>(topic, key, value)).get(10, TimeUnit.SECONDS);
		}
	}

	private static Map<String, Object> unavailableProducerProperties() {
		Map<String, Object> properties = new HashMap<>();
		properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:1");
		properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
		properties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 500);
		properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 500);
		properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 1000);
		properties.put(ProducerConfig.RETRIES_CONFIG, 0);
		return properties;
	}
}
