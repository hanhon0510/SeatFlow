package com.seatflow.health;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "seatflow.kafka", name = "enabled", havingValue = "true")
public class AdminClientKafkaHealthClient implements KafkaHealthClient {

	private final ObjectProvider<KafkaAdmin> kafkaAdminProvider;

	public AdminClientKafkaHealthClient(ObjectProvider<KafkaAdmin> kafkaAdminProvider) {
		this.kafkaAdminProvider = kafkaAdminProvider;
	}

	@Override
	public void check(Duration timeout) {
		KafkaAdmin kafkaAdmin = kafkaAdminProvider.getIfAvailable();
		if (kafkaAdmin == null) {
			throw new KafkaHealthUnavailableException();
		}

		try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
			adminClient.describeCluster().nodes().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new KafkaHealthUnavailableException(ex);
		}
		catch (Exception ex) {
			throw new KafkaHealthUnavailableException(ex);
		}
	}
}
