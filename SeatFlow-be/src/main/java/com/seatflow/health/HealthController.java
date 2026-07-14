package com.seatflow.health;

import java.time.Instant;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

	@GetMapping
	public ResponseEntity<HealthResponse> health() {
		return ResponseEntity.ok(new HealthResponse("SeatFlow", "UP", Instant.now()));
	}

	public record HealthResponse(String application, String status, Instant timestamp) {
	}

}
