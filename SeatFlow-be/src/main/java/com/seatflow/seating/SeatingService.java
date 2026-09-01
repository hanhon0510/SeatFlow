package com.seatflow.seating;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seatflow.venue.VenueMapper;
import com.seatflow.venue.VenueNotFoundException;

@Service
public class SeatingService {

	private final VenueMapper venueMapper;
	private final VenueSectionMapper sectionMapper;
	private final SeatMapper seatMapper;

	public SeatingService(
			VenueMapper venueMapper,
			VenueSectionMapper sectionMapper,
			SeatMapper seatMapper) {
		this.venueMapper = venueMapper;
		this.sectionMapper = sectionMapper;
		this.seatMapper = seatMapper;
	}

	@Transactional
	@PreAuthorize("hasRole('ADMIN')")
	public SectionResponse createSection(UUID venueId, SectionCreateRequest request) {
		requireVenueExists(venueId);
		VenueSectionRecord section = VenueSectionRecord.forInsert(
				UUID.randomUUID(),
				venueId,
				clean(request.name()),
				request.displayOrder());
		sectionMapper.insert(section);
		return SectionResponse.from(findExistingSection(section.id()));
	}

	@Transactional
	@PreAuthorize("hasRole('ADMIN')")
	public SectionResponse updateSection(UUID sectionId, SectionUpdateRequest request) {
		VenueSectionRecord section = VenueSectionRecord.forUpdate(
				sectionId,
				clean(request.name()),
				request.displayOrder());
		int updatedRows = sectionMapper.update(section);
		if (updatedRows != 1) {
			throw new SectionNotFoundException();
		}
		return SectionResponse.from(findExistingSection(sectionId));
	}

	@Transactional
	@PreAuthorize("hasRole('ADMIN')")
	public void deleteSection(UUID sectionId) {
		int deletedRows = sectionMapper.delete(sectionId);
		if (deletedRows != 1) {
			throw new SectionNotFoundException();
		}
	}

	@Transactional
	@PreAuthorize("hasRole('ADMIN')")
	public SeatResponse createSeat(UUID sectionId, SeatCreateRequest request) {
		requireSectionExists(sectionId);
		SeatRecord seat = seatForInsert(sectionId, request);
		try {
			seatMapper.insert(seat);
		}
		catch (DuplicateKeyException ex) {
			throw new DuplicateSeatLabelException(ex);
		}
		return SeatResponse.from(findExistingSeat(seat.id()));
	}

	@Transactional
	@PreAuthorize("hasRole('ADMIN')")
	public List<SeatResponse> createSeatsBulk(UUID sectionId, BulkSeatCreateRequest request) {
		requireSectionExists(sectionId);
		if (request.seats().isEmpty()) {
			throw new InvalidSeatBatchException();
		}

		List<SeatRecord> seats = request.seats().stream()
				.map(seat -> seatForInsert(sectionId, seat))
				.toList();
		try {
			seatMapper.insertBatch(seats);
		}
		catch (DuplicateKeyException ex) {
			throw new DuplicateSeatLabelException(ex);
		}

		return seats.stream()
				.map(seat -> SeatResponse.from(findExistingSeat(seat.id())))
				.toList();
	}

	@Transactional
	@PreAuthorize("hasRole('ADMIN')")
	public SeatResponse updateSeatAccessibility(UUID seatId, SeatUpdateRequest request) {
		int updatedRows = seatMapper.updateAccessible(seatId, request.accessible());
		if (updatedRows != 1) {
			throw new SeatNotFoundException();
		}
		return SeatResponse.from(findExistingSeat(seatId));
	}

	@PreAuthorize("hasRole('ADMIN')")
	public SeatLayoutResponse getSeatLayout(UUID venueId) {
		requireVenueExists(venueId);
		List<SeatLayoutRow> rows = seatMapper.findSeatLayoutByVenueId(venueId);
		Map<UUID, MutableSectionLayout> sections = new LinkedHashMap<>();
		for (SeatLayoutRow row : rows) {
			MutableSectionLayout section = sections.computeIfAbsent(
					row.sectionId(),
					ignored -> new MutableSectionLayout(row));
			if (row.seatId() != null) {
				section.seats().add(SeatResponse.fromLayoutRow(row));
			}
		}

		return new SeatLayoutResponse(
				venueId,
				sections.values().stream()
						.map(MutableSectionLayout::toResponse)
						.toList());
	}

	private void requireVenueExists(UUID venueId) {
		if (venueMapper.findById(venueId) == null) {
			throw new VenueNotFoundException();
		}
	}

	private void requireSectionExists(UUID sectionId) {
		if (sectionMapper.findById(sectionId) == null) {
			throw new SectionNotFoundException();
		}
	}

	private VenueSectionRecord findExistingSection(UUID sectionId) {
		VenueSectionRecord section = sectionMapper.findById(sectionId);
		if (section == null) {
			throw new SectionNotFoundException();
		}
		return section;
	}

	private SeatRecord findExistingSeat(UUID seatId) {
		SeatRecord seat = seatMapper.findById(seatId);
		if (seat == null) {
			throw new SeatNotFoundException();
		}
		return seat;
	}

	private static SeatRecord seatForInsert(UUID sectionId, SeatCreateRequest request) {
		return SeatRecord.forInsert(
				UUID.randomUUID(),
				sectionId,
				clean(request.rowLabel()),
				request.seatNumber(),
				clean(request.seatLabel()),
				request.accessible());
	}

	private static String clean(String value) {
		return value == null ? "" : value.trim();
	}

	private record MutableSectionLayout(
			UUID id,
			String name,
			int displayOrder,
			java.time.Instant createdAt,
			List<SeatResponse> seats) {

		private MutableSectionLayout(SeatLayoutRow row) {
			this(
					row.sectionId(),
					row.sectionName(),
					row.displayOrder(),
					row.sectionCreatedAt(),
					new ArrayList<>());
		}

		private SeatLayoutSectionResponse toResponse() {
			return new SeatLayoutSectionResponse(id, name, displayOrder, createdAt, List.copyOf(seats));
		}
	}
}
