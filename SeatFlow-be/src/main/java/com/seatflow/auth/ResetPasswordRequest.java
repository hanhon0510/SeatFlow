package com.seatflow.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The new password must meet the same rules as at registration. */
public record ResetPasswordRequest(
		@NotBlank
		@Size(max = 200)
		String token,

		@NotBlank
		@Size(min = 12, max = 128)
		@Pattern(regexp = ".*[a-z].*")
		@Pattern(regexp = ".*[A-Z].*")
		@Pattern(regexp = ".*\\d.*")
		@Pattern(regexp = ".*[^A-Za-z0-9].*")
		@Pattern(regexp = "^\\S+$")
		String password) {
}
