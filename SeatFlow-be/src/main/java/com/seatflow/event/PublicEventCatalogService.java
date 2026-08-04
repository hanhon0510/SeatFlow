package com.seatflow.event;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class PublicEventCatalogService {

	public static final int DEFAULT_PAGE_SIZE = 12;
	public static final int MAX_PAGE_SIZE = 100;
	private static final String DEFAULT_SORT = "START_ASC";

	private final EventMapper eventMapper;

	public PublicEventCatalogService(EventMapper eventMapper) {
		this.eventMapper = eventMapper;
	}

	public PublicEventPageResponse listEvents(
			String search,
			UUID venueId,
			String startDate,
			String endDate,
			int page,
			int size,
			String sort) {
		validatePagination(page, size);
		Instant start = parseDate(startDate, true);
		Instant end = parseDate(endDate, false);
		if (start != null && end != null && start.isAfter(end)) {
			throw new InvalidEventCatalogQueryException();
		}

		PublicEventCatalogQuery query = new PublicEventCatalogQuery(
				cleanNullable(search),
				venueId,
				start,
				end,
				normalizeSort(sort),
				size,
				(long) page * size);
		long totalItems = eventMapper.countPublishedCatalog(query);
		return PublicEventPageResponse.from(eventMapper.findPublishedCatalogPage(query), page, size, totalItems);
	}

	public PublicEventResponse getEvent(UUID id) {
		PublicEventCatalogRecord event = eventMapper.findPublishedCatalogById(id);
		if (event == null) {
			throw new EventNotFoundException();
		}
		return PublicEventResponse.from(event);
	}

	private static void validatePagination(int page, int size) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new InvalidEventPaginationException();
		}
	}

	private static Instant parseDate(String value, boolean startOfDay) {
		String cleaned = cleanNullable(value);
		if (cleaned == null) {
			return null;
		}
		try {
			return Instant.parse(cleaned);
		} catch (DateTimeParseException ignored) {
			try {
				LocalDate date = LocalDate.parse(cleaned);
				if (startOfDay) {
					return date.atStartOfDay().toInstant(ZoneOffset.UTC);
				}
				return date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusNanos(1);
			} catch (DateTimeParseException ex) {
				throw new InvalidEventCatalogQueryException();
			}
		}
	}

	private static String normalizeSort(String value) {
		String cleaned = cleanNullable(value);
		if (cleaned == null) {
			return DEFAULT_SORT;
		}

		return switch (cleaned.toLowerCase(Locale.ROOT)) {
			case "startasc", "start_asc" -> "START_ASC";
			case "startdesc", "start_desc" -> "START_DESC";
			case "priceasc", "price_asc" -> "PRICE_ASC";
			case "pricedesc", "price_desc" -> "PRICE_DESC";
			default -> throw new InvalidEventCatalogQueryException();
		};
	}

	private static String cleanNullable(String value) {
		String cleaned = value == null ? null : value.trim();
		return cleaned == null || cleaned.isBlank() ? null : cleaned;
	}
}
