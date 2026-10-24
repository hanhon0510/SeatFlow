package com.seatflow.notification;

import java.time.Duration;

/** The wording of emails about a user's account rather than an order. */
public class AccountEmailTemplates {

	private static final String GREETING = "Hello,";
	private static final String SIGN_OFF = "The SeatFlow team";

	private final String frontendBaseUrl;

	public AccountEmailTemplates(String frontendBaseUrl) {
		this.frontendBaseUrl = frontendBaseUrl;
	}

	public EmailMessage passwordReset(String to, String token, Duration validFor) {
		String link = frontendBaseUrl + "/reset-password?token=" + token;
		EmailBody body = new EmailBody()
				.paragraph("We received a request to reset the password of your SeatFlow account.")
				.link("Choose a new password", link)
				.paragraph("This link works once and expires in " + describe(validFor) + ". If you did not ask "
						+ "for a reset you can ignore this email: your password stays as it is.");
		return new EmailMessage(
				to, "Reset your SeatFlow password", body.text(GREETING, SIGN_OFF), body.html(GREETING, SIGN_OFF));
	}

	public EmailMessage passwordChanged(String to) {
		EmailBody body = new EmailBody()
				.paragraph("The password of your SeatFlow account was just changed, and you were signed out "
						+ "everywhere.")
				.paragraph("If this was you, there is nothing more to do. If it was not, reset your password "
						+ "again straight away and contact us.")
				.link("Reset your password", frontendBaseUrl + "/forgot-password");
		return new EmailMessage(
				to, "Your SeatFlow password was changed", body.text(GREETING, SIGN_OFF), body.html(GREETING, SIGN_OFF));
	}

	/** For a change made from inside the account, where the current device stays signed in. */
	public EmailMessage passwordChangedInAccount(String to) {
		EmailBody body = new EmailBody()
				.paragraph("The password of your SeatFlow account was just changed from your account settings, "
						+ "and your other devices were signed out.")
				.paragraph("If this was you, there is nothing more to do. If it was not, reset your password "
						+ "straight away and contact us.")
				.link("Reset your password", frontendBaseUrl + "/forgot-password");
		return new EmailMessage(
				to, "Your SeatFlow password was changed", body.text(GREETING, SIGN_OFF), body.html(GREETING, SIGN_OFF));
	}

	private static String describe(Duration duration) {
		long minutes = duration.toMinutes();
		if (minutes >= 60 && minutes % 60 == 0) {
			long hours = minutes / 60;
			return hours == 1 ? "1 hour" : hours + " hours";
		}
		return Math.max(1, minutes) + (minutes == 1 ? " minute" : " minutes");
	}
}
