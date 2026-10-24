package com.seatflow.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The new password must meet the same rules as at registration. */
public record ChangePasswordRequest(
		@NotBlank
		@Size(max = 128)
		String currentPassword,

		@NotBlank
		@Size(min = 12, max = 128)
		@Pattern(regexp = ".*[a-z].*")
		@Pattern(regexp = ".*[A-Z].*")
		@Pattern(regexp = ".*\\d.*")
		@Pattern(regexp = ".*[^A-Za-z0-9].*")
		@Pattern(regexp = "^\\S+$")
		String newPassword) {
}
