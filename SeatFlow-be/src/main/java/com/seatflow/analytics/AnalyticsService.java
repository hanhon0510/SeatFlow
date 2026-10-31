package com.seatflow.analytics;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Sales, refunds, checkout conversion and waitlist results for a range of UTC days, across all
 * events or for one. Days are the same UTC days the per-event sales report uses.
 */
@Service
public class AnalyticsService {

	static final int DEFAULT_DAYS = 30;
	static final int MAX_DAYS = 366;
	static final int TOP_EVENT_LIMIT = 10;

	private static final String CSV_HEADER = "date,event_id,event,currency,orders,seats,gross,refunded,net\n";

	private final AnalyticsMapper analyticsMapper;
	private final Clock clock;

	public AnalyticsService(AnalyticsMapper analyticsMapper, Clock clock) {
		this.analyticsMapper = analyticsMapper;
		this.clock = clock;
	}

	@PreAuthorize("hasRole('ADMIN')")
	public AnalyticsReport getReport(LocalDate from, LocalDate to, UUID eventId) {
		ReportRange range = resolve(from, to);
		List<AnalyticsSalesRow> rows = analyticsMapper.findSalesRows(range.start(), range.end(), eventId);

		return new AnalyticsReport(
				range.from(),
				range.to(),
				eventId,
				totals(rows),
				daily(rows),
				topEvents(rows),
				analyticsMapper.findCheckout(range.start(), range.end(), eventId),
				analyticsMapper.findWaitlist(range.start(), range.end(), eventId),
				clock.instant());
	}

	/** One line per day, event and currency: what an admin pastes into a spreadsheet. */
	@PreAuthorize("hasRole('ADMIN')")
	public String exportCsv(LocalDate from, LocalDate to, UUID eventId) {
		ReportRange range = resolve(from, to);
		StringBuilder csv = new StringBuilder(CSV_HEADER);
		for (AnalyticsSalesRow row : analyticsMapper.findSalesRows(range.start(), range.end(), eventId)) {
			csv.append(row.date()).append(',')
					.append(row.eventId()).append(',')
					.append(csvText(row.eventName())).append(',')
					.append(csvText(row.currency())).append(',')
					.append(row.orders()).append(',')
					.append(row.seats()).append(',')
					.append(row.gross().toPlainString()).append(',')
					.append(row.refunded().toPlainString()).append(',')
					.append(row.gross().subtract(row.refunded()).toPlainString()).append('\n');
		}
		return csv.toString();
	}

	public String exportFileName(LocalDate from, LocalDate to) {
		ReportRange range = resolve(from, to);
		return "seatflow-sales-" + range.from() + "-to-" + range.to() + ".csv";
	}

	private ReportRange resolve(LocalDate from, LocalDate to) {
		LocalDate last = to != null ? to : LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
		LocalDate first = from != null ? from : last.minusDays(DEFAULT_DAYS - 1L);
		if (first.isAfter(last)) {
			throw new InvalidAnalyticsRangeException("The start date must not be after the end date.");
		}
		if (ChronoUnit.DAYS.between(first, last) + 1 > MAX_DAYS) {
			throw new InvalidAnalyticsRangeException("The range can cover at most " + MAX_DAYS + " days.");
		}
		return new ReportRange(
				first,
				last,
				first.atStartOfDay().toInstant(ZoneOffset.UTC),
				last.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
	}

	private static List<AnalyticsCurrencyTotal> totals(List<AnalyticsSalesRow> rows) {
		Map<String, long[]> counts = new LinkedHashMap<>();
		Map<String, BigDecimal[]> amounts = new LinkedHashMap<>();
		for (AnalyticsSalesRow row : rows) {
			long[] count = counts.computeIfAbsent(row.currency(), key -> new long[3]);
			count[0] += row.orders();
			count[1] += row.seats();
			count[2] += row.refundedOrders();
			BigDecimal[] amount = amounts.computeIfAbsent(row.currency(), key -> zeroes());
			amount[0] = amount[0].add(row.gross());
			amount[1] = amount[1].add(row.refunded());
		}
		return counts.keySet().stream()
				.sorted()
				.map(currency -> new AnalyticsCurrencyTotal(
						currency,
						counts.get(currency)[0],
						counts.get(currency)[1],
						amounts.get(currency)[0],
						amounts.get(currency)[1],
						counts.get(currency)[2]))
				.toList();
	}

	private static List<AnalyticsDailyPoint> daily(List<AnalyticsSalesRow> rows) {
		Map<DayCurrency, AnalyticsDailyPoint> points = new LinkedHashMap<>();
		for (AnalyticsSalesRow row : rows) {
			points.merge(
					new DayCurrency(row.date(), row.currency()),
					new AnalyticsDailyPoint(row.date(), row.currency(), row.orders(), row.seats(), row.gross(),
							row.refunded()),
					(left, right) -> new AnalyticsDailyPoint(
							left.date(),
							left.currency(),
							left.orders() + right.orders(),
							left.seats() + right.seats(),
							left.gross().add(right.gross()),
							left.refunded().add(right.refunded())));
		}
		return new ArrayList<>(points.values()).stream()
				.sorted(Comparator.comparing(AnalyticsDailyPoint::date).thenComparing(AnalyticsDailyPoint::currency))
				.toList();
	}

	private static List<AnalyticsEventTotal> topEvents(List<AnalyticsSalesRow> rows) {
		Map<EventCurrency, AnalyticsEventTotal> events = new LinkedHashMap<>();
		for (AnalyticsSalesRow row : rows) {
			events.merge(
					new EventCurrency(row.eventId(), row.currency()),
					new AnalyticsEventTotal(row.eventId(), row.eventName(), row.currency(), row.orders(), row.seats(),
							row.gross(), row.refunded()),
					(left, right) -> new AnalyticsEventTotal(
							left.eventId(),
							left.eventName(),
							left.currency(),
							left.orders() + right.orders(),
							left.seats() + right.seats(),
							left.gross().add(right.gross()),
							left.refunded().add(right.refunded())));
		}
		return events.values().stream()
				.sorted(Comparator.comparing(AnalyticsEventTotal::net).reversed()
						.thenComparing(AnalyticsEventTotal::eventName))
				.limit(TOP_EVENT_LIMIT)
				.toList();
	}

	private static BigDecimal[] zeroes() {
		return new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO };
	}

	/**
	 * A spreadsheet runs a cell that starts with one of these as a formula, and an event name is
	 * typed by an admin, so it is prefixed to stay plain text.
	 */
	private static String csvText(String value) {
		String text = value == null ? "" : value;
		if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
			text = "'" + text;
		}
		if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
			text = "\"" + text.replace("\"", "\"\"") + "\"";
		}
		return text;
	}

	// Distinct names: MyBatis registers every class in this package under its simple name.
	private record DayCurrency(LocalDate date, String currency) {
	}

	private record EventCurrency(UUID eventId, String currency) {
	}

	private record ReportRange(LocalDate from, LocalDate to, Instant start, Instant end) {
	}
}
