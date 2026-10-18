package com.seatflow.notification;

/** One email, ready to send: a plain-text body for every client and an HTML body for those that render it. */
public record EmailMessage(String to, String subject, String text, String html) {
}
