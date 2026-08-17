package com.seatflow.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer.HeaderNames.HeadersToAdd;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableConfigurationProperties(SeatFlowKafkaProperties.class)
public class SeatFlowKafkaConfig {

	@Bean
	@ConditionalOnProperty(prefix = "seatflow.kafka", name = "enabled", havingValue = "true")
	public NewTopic orderEventsTopic(SeatFlowKafkaProperties properties) {
		return topic(properties.topics().orderEvents());
	}

	@Bean
	@ConditionalOnProperty(prefix = "seatflow.kafka", name = "enabled", havingValue = "true")
	public NewTopic notificationEventsTopic(SeatFlowKafkaProperties properties) {
		return topic(properties.topics().notificationEvents());
	}

	@Bean
	@ConditionalOnProperty(prefix = "seatflow.kafka", name = "enabled", havingValue = "true")
	public NewTopic deadLetterTopic(SeatFlowKafkaProperties properties) {
		return topic(properties.topics().deadLetter());
	}

	@Bean
	@ConditionalOnProperty(prefix = "seatflow.kafka", name = "enabled", havingValue = "true")
	public CommonErrorHandler seatFlowKafkaErrorHandler(
			KafkaTemplate<Object, Object> kafkaTemplate,
			SeatFlowKafkaProperties properties) {
		DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
				kafkaTemplate,
				(record, exception) -> {
					int partition = record.partition() < 0 ? 0 : record.partition();
					return new TopicPartition(properties.topics().deadLetter(), partition);
				});
		recoverer.setHeadersFunction(KafkaDeadLetterMetadata::headers);
		recoverer.excludeHeader(HeadersToAdd.EXCEPTION, HeadersToAdd.EX_CAUSE, HeadersToAdd.EX_MSG,
				HeadersToAdd.EX_STACKTRACE);
		DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, retryBackOff(properties.retry()));
		errorHandler.addNotRetryableExceptions(DeserializationException.class, IllegalArgumentException.class);
		return errorHandler;
	}

	@Bean(name = "kafkaListenerContainerFactory")
	@ConditionalOnProperty(prefix = "seatflow.kafka", name = "enabled", havingValue = "true")
	public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
			ConsumerFactory<Object, Object> consumerFactory,
			CommonErrorHandler seatFlowKafkaErrorHandler) {
		ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
				new ConcurrentKafkaListenerContainerFactory<>();
		factory.setConsumerFactory(consumerFactory);
		factory.setCommonErrorHandler(seatFlowKafkaErrorHandler);
		factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
		return factory;
	}

	private static NewTopic topic(String name) {
		return TopicBuilder.name(name)
				.partitions(1)
				.replicas(1)
				.build();
	}

	static FixedBackOff retryBackOff(SeatFlowKafkaProperties.Retry retry) {
		return new FixedBackOff(retry.backoff().toMillis(), Math.max(0, retry.maxAttempts() - 1L));
	}
}
