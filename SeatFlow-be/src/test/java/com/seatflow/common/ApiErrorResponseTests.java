package com.seatflow.common;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;

import org.apache.ibatis.exceptions.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.seatflow.event.EventNotFoundException;

import jakarta.servlet.RequestDispatcher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

class ApiErrorResponseTests {

	private static final String CORRELATION_ID = "11111111-1111-1111-1111-111111111111";

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		mockMvc = MockMvcBuilders
				.standaloneSetup(new ErrorTestController())
				.setControllerAdvice(new GlobalExceptionHandler())
				.setValidator(validator)
				.build();
	}

	@Test
	void validationErrorUsesProblemSchemaWithFieldErrors() throws Exception {
		mockMvc.perform(post("/validation")
						.header(CorrelationId.HEADER_NAME, CORRELATION_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"not-email\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(header().string(CorrelationId.HEADER_NAME, CORRELATION_ID))
				.andExpect(jsonPath("$.type").value("https://seatflow.dev/errors/invalid-request"))
				.andExpect(jsonPath("$.title").value("Invalid request"))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.correlationId").value(CORRELATION_ID))
				.andExpect(jsonPath("$.timestamp").isNotEmpty())
				.andExpect(jsonPath("$.errors[*].field").value(hasItem("email")))
				.andExpect(jsonPath("$.errors[*].field").value(hasItem("name")));
	}

	@Test
	void authorizationErrorUsesProblemSchema() throws Exception {
		mockMvc.perform(get("/forbidden")
						.header(CorrelationId.HEADER_NAME, CORRELATION_ID))
				.andExpect(status().isForbidden())
				.andExpect(header().string(CorrelationId.HEADER_NAME, CORRELATION_ID))
				.andExpect(jsonPath("$.title").value("Forbidden"))
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.code").value("FORBIDDEN"))
				.andExpect(jsonPath("$.correlationId").value(CORRELATION_ID));
	}

	@Test
	void postgresConstraintViolationMapsSafely() throws Exception {
		mockMvc.perform(get("/constraint")
						.header(CorrelationId.HEADER_NAME, CORRELATION_ID))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("User already exists"))
				.andExpect(jsonPath("$.code").value("USER_ALREADY_EXISTS"))
				.andExpect(content().string(not(containsString("users_normalized_email_uq"))))
				.andExpect(content().string(not(containsString("secret@example.com"))));
	}

	@Test
	void myBatisPersistenceErrorHidesSqlDetails() throws Exception {
		mockMvc.perform(get("/persistence")
						.header(CorrelationId.HEADER_NAME, CORRELATION_ID))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.title").value("Persistence error"))
				.andExpect(jsonPath("$.code").value("PERSISTENCE_ERROR"))
				.andExpect(content().string(not(containsString("SELECT"))))
				.andExpect(content().string(not(containsString("password_hash"))));
	}

	@Test
	void notFoundErrorUsesProblemSchema() throws Exception {
		mockMvc.perform(get("/not-found")
						.header(CorrelationId.HEADER_NAME, CORRELATION_ID))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Event not found"))
				.andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
	}

	@Test
	void fallbackNotFoundErrorUsesProblemSchema() throws Exception {
		MockMvc errorMvc = MockMvcBuilders
				.standaloneSetup(new ApiErrorController())
				.build();

		errorMvc.perform(get("/error")
						.header(CorrelationId.HEADER_NAME, CORRELATION_ID)
						.requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404))
				.andExpect(status().isNotFound())
				.andExpect(header().string(CorrelationId.HEADER_NAME, CORRELATION_ID))
				.andExpect(jsonPath("$.title").value("Not found"))
				.andExpect(jsonPath("$.code").value("NOT_FOUND"))
				.andExpect(jsonPath("$.correlationId").value(CORRELATION_ID));
	}

	@Test
	void internalErrorHidesStackTraceAndExceptionMessage() throws Exception {
		mockMvc.perform(get("/internal")
						.header(CorrelationId.HEADER_NAME, CORRELATION_ID))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.title").value("Unexpected error"))
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(content().string(not(containsString("SELECT"))))
				.andExpect(content().string(not(containsString("IllegalStateException"))));
	}

	@RestController
	private static class ErrorTestController {

		@PostMapping("/validation")
		void validation(@Valid @RequestBody ValidationRequest request) {
		}

		@GetMapping("/forbidden")
		void forbidden() {
			throw new AccessDeniedException("Secret authorization details");
		}

		@GetMapping("/constraint")
		void constraint() {
			SQLException cause = new SQLException(
					"ERROR: duplicate key value violates unique constraint \"users_normalized_email_uq\" "
							+ "Detail: Key (email)=(secret@example.com) already exists.",
					"23505");
			throw new DataIntegrityViolationException("Database constraint failed", cause);
		}

		@GetMapping("/persistence")
		void persistence() {
			SQLException cause = new SQLException("syntax error at or near SELECT password_hash", "42601");
			throw new PersistenceException("Mapper failed: SELECT password_hash FROM users", cause);
		}

		@GetMapping("/not-found")
		void notFound() {
			throw new EventNotFoundException();
		}

		@GetMapping("/internal")
		void internal() {
			throw new IllegalStateException("SELECT password_hash FROM users");
		}
	}

	private record ValidationRequest(
			@NotBlank String name,
			@NotBlank @Email String email) {
	}
}
