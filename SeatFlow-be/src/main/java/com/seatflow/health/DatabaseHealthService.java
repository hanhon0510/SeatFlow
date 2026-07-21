package com.seatflow.health;

import org.springframework.stereotype.Service;

@Service
public class DatabaseHealthService {

	private static final String UP = "UP";

	private final DatabaseHealthMapper databaseHealthMapper;

	public DatabaseHealthService(DatabaseHealthMapper databaseHealthMapper) {
		this.databaseHealthMapper = databaseHealthMapper;
	}

	public DatabaseHealthResponse checkDatabase() {
		String status;
		try {
			status = databaseHealthMapper.findSystemHealthStatus();
		}
		catch (RuntimeException ex) {
			throw new DatabaseHealthUnavailableException(ex);
		}

		if (!UP.equals(status)) {
			throw new DatabaseHealthUnavailableException();
		}

		return new DatabaseHealthResponse(UP, "PostgreSQL");
	}

	public record DatabaseHealthResponse(String status, String database) {
	}

}

