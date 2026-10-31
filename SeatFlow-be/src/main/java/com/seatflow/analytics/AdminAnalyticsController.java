package com.seatflow.analytics;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dates are UTC days, inclusive at both ends; leave them out for the last 30 days. */
@RestController
@RequestMapping("/api/v1/admin/analytics")
public class AdminAnalyticsController {

	private static final MediaType CSV = new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8);

	private final AnalyticsService analyticsService;

	public AdminAnalyticsController(AnalyticsService analyticsService) {
		this.analyticsService = analyticsService;
	}

	@GetMapping
	public AnalyticsReport report(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) UUID eventId) {
		return analyticsService.getReport(from, to, eventId);
	}

	@GetMapping("/export")
	public ResponseEntity<String> export(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) UUID eventId) {
		String csv = analyticsService.exportCsv(from, to, eventId);
		return ResponseEntity.ok()
				.contentType(CSV)
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
						.filename(analyticsService.exportFileName(from, to))
						.build()
						.toString())
				.body(csv);
	}
}
