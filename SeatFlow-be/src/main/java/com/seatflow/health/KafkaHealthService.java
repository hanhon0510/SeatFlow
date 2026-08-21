package com.seatflow.health;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.seatflow.kafka.SeatFlowKafkaProperties;

@Service
public class KafkaHealthService {

	private static final String UP = "UP";
	private static final String DISABLED = "DISABLED";

	private final SeatFlowKafkaProperties kafkaProperties;
	private final Optional<KafkaHealthClient> kafkaHealthClient;

	public KafkaHealthService(
			SeatFlowKafkaProperties kafkaProperties,
			Optional<KafkaHealthClient> kafkaHealthClient) {
		this.kafkaProperties = kafkaProperties;
		this.kafkaHealthClient = kafkaHealthClient;
	}

	public KafkaHealthResponse checkKafka() {
		if (!kafkaProperties.enabled()) {
			return new KafkaHealthResponse(DISABLED, "Kafka");
		}

		KafkaHealthClient client = kafkaHealthClient.orElseThrow(KafkaHealthUnavailableException::new);
		try {
			client.check(kafkaProperties.health().timeout());
		}
		catch (KafkaHealthUnavailableException ex) {
			throw ex;
		}
		catch (RuntimeException ex) {
			throw new KafkaHealthUnavailableException(ex);
		}
		return new KafkaHealthResponse(UP, "Kafka");
	}

	public record KafkaHealthResponse(String status, String broker) {
	}
}
