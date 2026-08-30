package com.seatflow.seatupdates;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.seatflow.security.WebOriginProperties;

@Configuration
@EnableWebSocketMessageBroker
public class SeatUpdateWebSocketConfig implements WebSocketMessageBrokerConfigurer {

	public static final String STOMP_ENDPOINT = "/ws";

	private final WebOriginProperties webOriginProperties;

	public SeatUpdateWebSocketConfig(WebOriginProperties webOriginProperties) {
		this.webOriginProperties = webOriginProperties;
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/topic");
		registry.setApplicationDestinationPrefixes("/app");
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint(STOMP_ENDPOINT)
				.setAllowedOrigins(webOriginProperties.allowedOrigins().toArray(String[]::new));
	}
}
