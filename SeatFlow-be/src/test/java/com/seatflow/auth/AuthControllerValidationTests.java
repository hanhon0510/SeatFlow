package com.seatflow.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.seatflow.common.GlobalExceptionHandler;
import com.seatflow.ratelimit.RateLimitService;

class AuthControllerValidationTests {

	@Test
	void weakPasswordReturnsBadRequest() throws Exception {
		MockMvc mockMvc = mockMvc(mock(RegistrationService.class));

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerJson("user@example.com", "weak")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Invalid request"));
	}

	@Test
	void invalidEmailReturnsBadRequest() throws Exception {
		MockMvc mockMvc = mockMvc(mock(RegistrationService.class));

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerJson("not-an-email", "StrongPassword123!")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Invalid request"));
	}

	@Test
	void duplicateRegistrationReturnsConflict() throws Exception {
		RegistrationService registrationService = mock(RegistrationService.class);
		when(registrationService.register(any(RegisterRequest.class)))
				.thenThrow(new UserAlreadyExistsException(new RuntimeException("duplicate")));
		MockMvc mockMvc = mockMvc(registrationService);

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerJson("user@example.com", "StrongPassword123!")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("User already exists"));
	}

	private static MockMvc mockMvc(RegistrationService registrationService) {
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();

		return MockMvcBuilders
				.standaloneSetup(new AuthController(
						registrationService,
						mock(LoginService.class),
						mock(RefreshTokenService.class),
						mock(RefreshTokenCookieService.class),
						mock(RateLimitService.class)))
				.setControllerAdvice(new GlobalExceptionHandler())
				.setValidator(validator)
				.build();
	}

	private static String registerJson(String email, String password) {
		return """
				{
				  "email": "%s",
				  "password": "%s"
				}
				""".formatted(email, password);
	}

}
