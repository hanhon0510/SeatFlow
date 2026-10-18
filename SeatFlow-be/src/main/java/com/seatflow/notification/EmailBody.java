package com.seatflow.notification;

import java.util.List;

import org.springframework.web.util.HtmlUtils;

/**
 * Builds the plain-text and HTML versions of one email from the same calls, so the two cannot
 * drift apart. Everything passed in is treated as untrusted text and escaped for HTML.
 */
final class EmailBody {

	private final StringBuilder text = new StringBuilder();
	private final StringBuilder html = new StringBuilder();

	EmailBody paragraph(String content) {
		text.append(content).append("\n\n");
		html.append("<p style=\"margin:0 0 16px\">").append(escape(content)).append("</p>");
		return this;
	}

	EmailBody heading(String content) {
		text.append(content).append('\n').append("-".repeat(content.length())).append("\n\n");
		html.append("<h2 style=\"margin:24px 0 8px;font-size:16px\">").append(escape(content)).append("</h2>");
		return this;
	}

	EmailBody facts(List<String[]> rows) {
		for (String[] row : rows) {
			text.append(row[0]).append(": ").append(row[1]).append('\n');
		}
		text.append('\n');
		html.append("<table role=\"presentation\" style=\"margin:0 0 16px;border-collapse:collapse\">");
		for (String[] row : rows) {
			html.append("<tr><td style=\"padding:2px 16px 2px 0;color:#555\">").append(escape(row[0]))
					.append("</td><td style=\"padding:2px 0\"><strong>").append(escape(row[1]))
					.append("</strong></td></tr>");
		}
		html.append("</table>");
		return this;
	}

	EmailBody bullets(List<String> items) {
		for (String item : items) {
			text.append("  - ").append(item).append('\n');
		}
		text.append('\n');
		html.append("<ul style=\"margin:0 0 16px;padding-left:20px\">");
		for (String item : items) {
			html.append("<li>").append(escape(item)).append("</li>");
		}
		html.append("</ul>");
		return this;
	}

	EmailBody link(String label, String url) {
		text.append(label).append(": ").append(url).append("\n\n");
		html.append("<p style=\"margin:0 0 16px\"><a href=\"").append(escape(url))
				.append("\" style=\"display:inline-block;padding:10px 18px;background:#1677ff;color:#fff;")
				.append("text-decoration:none;border-radius:6px\">").append(escape(label)).append("</a></p>");
		return this;
	}

	String text(String greeting, String signOff) {
		return greeting + "\n\n" + text + signOff + "\n";
	}

	String html(String greeting, String signOff) {
		return "<!doctype html><html><body style=\"margin:0;padding:24px;background:#f5f5f5\">"
				+ "<div style=\"max-width:560px;margin:0 auto;padding:24px;background:#fff;border-radius:8px;"
				+ "font-family:Arial,Helvetica,sans-serif;font-size:14px;line-height:1.5;color:#222\">"
				+ "<p style=\"margin:0 0 16px\">" + escape(greeting) + "</p>"
				+ html
				+ "<p style=\"margin:24px 0 0;color:#555\">" + escape(signOff) + "</p>"
				+ "</div></body></html>";
	}

	private static String escape(String value) {
		return HtmlUtils.htmlEscape(value, "UTF-8");
	}
}
