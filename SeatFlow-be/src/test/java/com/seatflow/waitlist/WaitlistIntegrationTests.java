package com.seatflow.waitlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.seatflow.event.EventMapper;
import com.seatflow.event.EventRecord;
import com.seatflow.event.EventSeatMapper;
import com.seatflow.event.EventSectionMapper;
import com.seatflow.event.EventSectionRecord;
import com.seatflow.hold.SeatHoldProperties;
import com.seatflow.hold.SeatHoldRecord;
import com.seatflow.hold.SeatHoldStore;
import com.seatflow.notification.EmailDeliveryException;
import com.seatflow.notification.EmailGateway;
import com.seatflow.notification.EmailMessage;
import com.seatflow.notification.WaitlistEmailTemplates;
import com.seatflow.observability.BusinessMetrics;
import com.seatflow.security.JwtTokenService;
import com.seatflow.seating.SeatMapper;
import com.seatflow.seating.SeatRecord;
import com.seatflow.seating.VenueSectionMapper;
import com.seatflow.seating.VenueSectionRecord;
import com.seatflow.seatupdates.SeatStateNotifier;
import com.seatflow.support.PostgresTestContainerSupport;
import com.seatflow.user.UserMapper;
import com.seatflow.user.UserRecord;
import com.seatflow.user.UserRole;
import com.seatflow.venue.VenueMapper;
import com.seatflow.venue.VenueRecord;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class WaitlistIntegrationTests extends PostgresTestContainerSupport {

	private static final Duration OFFER_WINDOW = Duration.ofMinutes(10);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JwtTokenService jwtTokenService;

	@Autowired
	private UserMapper userMapper;

	@Autowired
	private VenueMapper venueMapper;

	@Autowired
	private VenueSectionMapper sectionMapper;

	@Autowired
	private SeatMapper seatMapper;

	@Autowired
	private EventMapper eventMapper;

	@Autowired
	private EventSectionMapper eventSectionMapper;

	@Autowired
	private EventSeatMapper eventSeatMapper;

	@Autowired
	private WaitlistMapper waitlistMapper;

	@Autowired
	private WaitlistEmailTemplates templates;

	@Autowired
	private SeatHoldProperties holdProperties;

	@Autowired
	private SeatStateNotifier seatStateNotifier;

	@Autowired
	private BusinessMetrics businessMetrics;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@MockitoBean
	private SeatHoldStore seatHoldStore;

	// Built by hand with a mock gateway so the messages can be read and made to fail.
	private final EmailGateway gateway = mock(EmailGateway.class);

	private WaitlistService service;

	private UserRecord admin;

	private final Map<UUID, UUID> holds = new HashMap<>();

	@BeforeEach
	void setUp() {
		cleanDatabase();
		reset(gateway, seatHoldStore);
		holds.clear();
		when(seatHoldStore.findActiveSeatHoldOwners(any(), anyList())).thenAnswer(invocation -> {
			List<UUID> asked = invocation.getArgument(1);
			Map<UUID, UUID> found = new HashMap<>();
			asked.forEach(id -> {
				if (holds.containsKey(id)) {
					found.put(id, holds.get(id));
				}
			});
			return found;
		});
		when(seatHoldStore.createHold(any(SeatHoldRecord.class), any(Duration.class))).thenReturn(true);
		admin = insertUser("admin@example.com", UserRole.ADMIN);
		service = new WaitlistService(
				waitlistMapper,
				eventMapper,
				seatHoldStore,
				holdProperties,
				new WaitlistProperties(true, OFFER_WINDOW, 50, 500, 15_000L),
				seatStateNotifier,
				templates,
				gateway,
				businessMetrics,
				transactionTemplate,
				Clock.systemUTC());
	}

	@AfterEach
	void tearDown() {
		cleanDatabase();
	}

	@Test
	void aBuyerCanJoinOnlyWhenTheyCannotSimplyBook() throws Exception {
		Venue venue = venueWithSeats(3);
		UserRecord buyer = insertUser("buyer@example.com", UserRole.USER);

		// Seats are free, so there is nothing to wait for.
		join(buyer, venue.event(), 1).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("WAITLIST_UNAVAILABLE"));
		waitlistOf(buyer, venue.event()).andExpect(status().isOk())
				.andExpect(jsonPath("$.state").value("NONE"))
				.andExpect(jsonPath("$.joinable").value(false));

		holdEverySeat(venue);
		waitlistOf(buyer, venue.event()).andExpect(jsonPath("$.joinable").value(true));
		join(buyer, venue.event(), 2).andExpect(status().isCreated())
				.andExpect(jsonPath("$.state").value("WAITING"))
				.andExpect(jsonPath("$.position").value(1))
				.andExpect(jsonPath("$.seatCount").value(2));
		join(buyer, venue.event(), 2).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("WAITLIST_ALREADY_JOINED"));
	}

	@Test
	void aBuyerWhoWantsMoreSeatsThanAreFreeMayStillJoin() throws Exception {
		Venue venue = venueWithSeats(3);
		holdSeats(venue, 0, 1);
		UserRecord buyer = insertUser("group@example.com", UserRole.USER);

		join(buyer, venue.event(), 3).andExpect(status().isCreated());
	}

	@Test
	void rejectsABadSeatCountAndStrangersAndUnknownEvents() throws Exception {
		Venue venue = venueWithSeats(2);
		holdEverySeat(venue);
		UserRecord buyer = insertUser("buyer@example.com", UserRole.USER);

		join(buyer, venue.event(), 0).andExpect(status().isBadRequest());
		join(buyer, venue.event(), holdProperties.maxSeats() + 1).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_WAITLIST_REQUEST"));
		mockMvc.perform(get("/api/v1/events/{id}/waitlist", venue.event().id()))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/events/{id}/waitlist", venue.event().id())
						.header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/events/{id}/waitlist", UUID.randomUUID())
						.header(HttpHeaders.AUTHORIZATION, bearer(buyer)))
				.andExpect(status().isNotFound());
	}

	@Test
	void cannotJoinAnEventThatIsNotOnSale() throws Exception {
		Venue venue = venueWithSeats(1);
		holdEverySeat(venue);
		jdbcTemplate.update("UPDATE events SET sales_end_time = NOW() - INTERVAL '1 minute' WHERE id = ?",
				venue.event().id());
		UserRecord buyer = insertUser("late@example.com", UserRole.USER);

		join(buyer, venue.event(), 1).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("WAITLIST_UNAVAILABLE"));
	}

	@Test
	void freedSeatsAreOfferedInTheOrderPeopleJoined() throws Exception {
		Venue venue = venueWithSeats(3);
		holdEverySeat(venue);
		UserRecord first = insertUser("first@example.com", UserRole.USER);
		UserRecord second = insertUser("second@example.com", UserRole.USER);
		join(first, venue.event(), 1).andExpect(status().isCreated());
		join(second, venue.event(), 2).andExpect(jsonPath("$.position").value(2));

		// Nothing is free yet.
		assertThat(service.processWaitlists().offered()).isZero();

		holds.clear();
		WaitlistPassSummary summary = service.processWaitlists();

		assertThat(summary.offered()).isEqualTo(2);
		ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
		verify(gateway, times(2)).send(captor.capture());
		assertThat(captor.getAllValues()).extracting(EmailMessage::to)
				.containsExactly("first@example.com", "second@example.com");
		waitlistOf(first, venue.event()).andExpect(jsonPath("$.state").value("OFFERED"))
				.andExpect(jsonPath("$.eventSeatIds.length()").value(1))
				.andExpect(jsonPath("$.offerExpiresAt").isNotEmpty());
		waitlistOf(second, venue.event()).andExpect(jsonPath("$.state").value("OFFERED"))
				.andExpect(jsonPath("$.eventSeatIds.length()").value(2));
		// Nobody is offered the same seat twice.
		assertThat(count("SELECT COUNT(DISTINCT event_seat_id) FROM waitlist_offer_seats")).isEqualTo(3);
	}

	@Test
	void aLargeGroupIsPassedOverUntilEnoughSeatsComeBack() throws Exception {
		Venue venue = venueWithSeats(3);
		holdEverySeat(venue);
		UserRecord group = insertUser("group@example.com", UserRole.USER);
		UserRecord single = insertUser("single@example.com", UserRole.USER);
		join(group, venue.event(), 3).andExpect(status().isCreated());
		join(single, venue.event(), 1).andExpect(status().isCreated());

		holds.remove(venue.seatIds().get(0));
		assertThat(service.processWaitlists().offered()).isEqualTo(1);

		waitlistOf(group, venue.event()).andExpect(jsonPath("$.state").value("WAITING"))
				.andExpect(jsonPath("$.position").value(1));
		waitlistOf(single, venue.event()).andExpect(jsonPath("$.state").value("OFFERED"));
	}

	@Test
	void anOfferedGroupGetsNeighbouringSeats() throws Exception {
		Venue venue = venueWithSeats(5);
		holdEverySeat(venue);
		UserRecord group = insertUser("pair@example.com", UserRole.USER);
		join(group, venue.event(), 2).andExpect(status().isCreated());
		// Seats 1, 3 and 4 come back: only 3 and 4 sit together.
		holds.remove(venue.seatIds().get(0));
		holds.remove(venue.seatIds().get(2));
		holds.remove(venue.seatIds().get(3));

		service.processWaitlists();

		WaitlistEntryRecord entry = waitlistMapper.findActive(venue.event().id(), group.id());
		assertThat(waitlistMapper.findOfferedEventSeatIds(entry.id()))
				.containsExactlyInAnyOrder(venue.seatIds().get(2), venue.seatIds().get(3));
	}

	@Test
	void offeredSeatsAreKeptForTheBuyerAndLookHeldToEveryoneElse() throws Exception {
		Venue venue = venueWithSeats(2);
		holdEverySeat(venue);
		UserRecord winner = insertUser("winner@example.com", UserRole.USER);
		UserRecord other = insertUser("other@example.com", UserRole.USER);
		join(winner, venue.event(), 1).andExpect(status().isCreated());
		holds.clear();
		service.processWaitlists();
		UUID offeredSeat = waitlistMapper.findOfferedEventSeatIds(
				waitlistMapper.findActive(venue.event().id(), winner.id()).id()).getFirst();
		UUID otherSeat = venue.seatIds().stream().filter(id -> !id.equals(offeredSeat)).findFirst().orElseThrow();

		// Someone else cannot take the offered seat, but the other one is theirs for the asking.
		hold(other, venue.event(), offeredSeat).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SEAT_ALREADY_HELD"));
		hold(other, venue.event(), otherSeat).andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/events/{id}/seats", venue.event().id())
						.header(HttpHeaders.AUTHORIZATION, bearer(other)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sections[0].rows[0].seats[?(@.eventSeatId == '%s')].status"
						.formatted(offeredSeat)).value("HELD"));
		mockMvc.perform(get("/api/v1/events/{id}/seats", venue.event().id())
						.header(HttpHeaders.AUTHORIZATION, bearer(winner)))
				.andExpect(jsonPath("$.sections[0].rows[0].seats[?(@.eventSeatId == '%s')].status"
						.formatted(offeredSeat)).value("AVAILABLE"));

		// The winner holds it, which closes the offer.
		hold(winner, venue.event(), offeredSeat).andExpect(status().isCreated());
		waitlistOf(winner, venue.event()).andExpect(jsonPath("$.state").value("NONE"));
		assertThat(count("SELECT COUNT(*) FROM waitlist_entries WHERE status = 'FULFILLED'")).isEqualTo(1);
	}

	@Test
	void anUnclaimedOfferLapsesAndPassesToTheNextBuyer() throws Exception {
		Venue venue = venueWithSeats(1);
		holdEverySeat(venue);
		UserRecord slow = insertUser("slow@example.com", UserRole.USER);
		UserRecord next = insertUser("next@example.com", UserRole.USER);
		join(slow, venue.event(), 1).andExpect(status().isCreated());
		join(next, venue.event(), 1).andExpect(status().isCreated());
		holds.clear();
		service.processWaitlists();
		waitlistOf(slow, venue.event()).andExpect(jsonPath("$.state").value("OFFERED"));
		waitlistOf(next, venue.event()).andExpect(jsonPath("$.state").value("WAITING"));

		jdbcTemplate.update("UPDATE waitlist_entries SET offer_expires_at = NOW() - INTERVAL '1 second' "
				+ "WHERE status = 'OFFERED'");
		WaitlistPassSummary summary = service.processWaitlists();

		assertThat(summary.expired()).isEqualTo(1);
		assertThat(summary.offered()).isEqualTo(1);
		waitlistOf(slow, venue.event()).andExpect(jsonPath("$.state").value("NONE"));
		waitlistOf(next, venue.event()).andExpect(jsonPath("$.state").value("OFFERED"));
		verify(gateway, times(2)).send(any());
	}

	@Test
	void aFailedEmailLeavesTheBuyerWaitingAndDoesNotBlockTheOthers() throws Exception {
		Venue venue = venueWithSeats(2);
		holdEverySeat(venue);
		UserRecord unreachable = insertUser("unreachable@example.com", UserRole.USER);
		UserRecord reachable = insertUser("reachable@example.com", UserRole.USER);
		join(unreachable, venue.event(), 1).andExpect(status().isCreated());
		join(reachable, venue.event(), 1).andExpect(status().isCreated());
		holds.clear();
		doThrow(new EmailDeliveryException("smtp down", new RuntimeException()))
				.when(gateway).send(org.mockito.ArgumentMatchers.argThat(m -> m.to().startsWith("unreachable")));

		WaitlistPassSummary summary = service.processWaitlists();

		assertThat(summary.offered()).isEqualTo(1);
		waitlistOf(unreachable, venue.event()).andExpect(jsonPath("$.state").value("WAITING"));
		waitlistOf(reachable, venue.event()).andExpect(jsonPath("$.state").value("OFFERED"));
		assertThat(count("SELECT COUNT(*) FROM waitlist_offer_seats")).isEqualTo(1);

		// Once the mail works again the first buyer is offered the next seat that comes back.
		reset(gateway);
		assertThat(service.processWaitlists().offered()).isEqualTo(1);
		waitlistOf(unreachable, venue.event()).andExpect(jsonPath("$.state").value("OFFERED"));
	}

	@Test
	void leavingFreesTheSeatsOfAnOpenOffer() throws Exception {
		Venue venue = venueWithSeats(1);
		holdEverySeat(venue);
		UserRecord buyer = insertUser("leaver@example.com", UserRole.USER);
		join(buyer, venue.event(), 1).andExpect(status().isCreated());
		holds.clear();
		service.processWaitlists();
		UserRecord other = insertUser("other@example.com", UserRole.USER);

		hold(other, venue.event(), venue.seatIds().getFirst()).andExpect(status().isConflict());
		mockMvc.perform(delete("/api/v1/events/{id}/waitlist", venue.event().id())
						.header(HttpHeaders.AUTHORIZATION, bearer(buyer)))
				.andExpect(status().isNoContent());
		// Leaving again is harmless.
		mockMvc.perform(delete("/api/v1/events/{id}/waitlist", venue.event().id())
						.header(HttpHeaders.AUTHORIZATION, bearer(buyer)))
				.andExpect(status().isNoContent());

		hold(other, venue.event(), venue.seatIds().getFirst()).andExpect(status().isCreated());
		waitlistOf(buyer, venue.event()).andExpect(jsonPath("$.state").value("NONE"));
	}

	@Test
	void entriesEndWhenTheSaleCloses() throws Exception {
		Venue venue = venueWithSeats(1);
		holdEverySeat(venue);
		UserRecord buyer = insertUser("waiting@example.com", UserRole.USER);
		join(buyer, venue.event(), 1).andExpect(status().isCreated());
		jdbcTemplate.update("UPDATE events SET sales_end_time = NOW() - INTERVAL '1 minute' WHERE id = ?",
				venue.event().id());

		WaitlistPassSummary summary = service.processWaitlists();

		assertThat(summary.closed()).isEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM waitlist_entries WHERE status = 'EXPIRED'")).isEqualTo(1);
	}

	@Test
	void adminsSeeHowTheQueueIsDoing() throws Exception {
		Venue venue = venueWithSeats(2);
		holdEverySeat(venue);
		UserRecord one = insertUser("one@example.com", UserRole.USER);
		UserRecord two = insertUser("two@example.com", UserRole.USER);
		join(one, venue.event(), 1).andExpect(status().isCreated());
		join(two, venue.event(), 3).andExpect(status().isCreated());
		mockMvc.perform(delete("/api/v1/events/{id}/waitlist", venue.event().id())
						.header(HttpHeaders.AUTHORIZATION, bearer(one)))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/admin/events/{id}/waitlist", venue.event().id())
						.header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.waiting").value(1))
				.andExpect(jsonPath("$.waitingSeats").value(3))
				.andExpect(jsonPath("$.cancelled").value(1))
				.andExpect(jsonPath("$.offered").value(0));
		mockMvc.perform(get("/api/v1/admin/events/{id}/waitlist", venue.event().id())
						.header(HttpHeaders.AUTHORIZATION, bearer(one)))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/admin/events/{id}/waitlist", UUID.randomUUID())
						.header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isNotFound());
	}

	@Test
	void announcesOfferedSeatsToOpenSeatMaps() {
		SeatStateNotifier notifier = mock(SeatStateNotifier.class);
		Venue venue = venueWithSeats(1);
		holdEverySeat(venue);
		UserRecord buyer = insertUser("live@example.com", UserRole.USER);
		service = new WaitlistService(
				waitlistMapper, eventMapper, seatHoldStore, holdProperties,
				new WaitlistProperties(true, OFFER_WINDOW, 50, 500, 15_000L),
				notifier, templates, gateway, businessMetrics, transactionTemplate, Clock.systemUTC());
		service.join(venue.event().id(), buyer.id(), new WaitlistJoinRequest(1));
		holds.clear();

		service.processWaitlists();

		verify(notifier).seatsHeld(eq(venue.event().id()), eq(venue.seatIds()));
	}

	// --- helpers ---

	private org.springframework.test.web.servlet.ResultActions join(UserRecord user, EventRecord event, int seats)
			throws Exception {
		return mockMvc.perform(post("/api/v1/events/{id}/waitlist", event.id())
				.header(HttpHeaders.AUTHORIZATION, bearer(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"seatCount\":%d}".formatted(seats)));
	}

	private org.springframework.test.web.servlet.ResultActions waitlistOf(UserRecord user, EventRecord event)
			throws Exception {
		return mockMvc.perform(get("/api/v1/events/{id}/waitlist", event.id())
				.header(HttpHeaders.AUTHORIZATION, bearer(user)));
	}

	private org.springframework.test.web.servlet.ResultActions hold(UserRecord user, EventRecord event, UUID seatId)
			throws Exception {
		return mockMvc.perform(post("/api/v1/events/{id}/holds", event.id())
				.header(HttpHeaders.AUTHORIZATION, bearer(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"eventSeatId\":\"%s\"}".formatted(seatId)));
	}

	private void holdEverySeat(Venue venue) {
		venue.seatIds().forEach(id -> holds.put(id, UUID.randomUUID()));
	}

	private void holdSeats(Venue venue, int... indexes) {
		for (int index : indexes) {
			holds.put(venue.seatIds().get(index), UUID.randomUUID());
		}
	}

	/** A published event on sale now, whose single row A has {@code seats} seats numbered from 1. */
	private Venue venueWithSeats(int seats) {
		VenueRecord venue = VenueRecord.forInsert(
				UUID.randomUUID(), "Waitlist Hall", "1 Waitlist Street", "Ho Chi Minh City", "Vietnam",
				"Asia/Ho_Chi_Minh");
		venueMapper.insert(venue);
		VenueSectionRecord section = VenueSectionRecord.forInsert(UUID.randomUUID(), venue.id(), "Orchestra", 1);
		sectionMapper.insert(section);
		for (int number = 1; number <= seats; number++) {
			seatMapper.insert(SeatRecord.forInsert(UUID.randomUUID(), section.id(), "A", number, "A" + number, false));
		}
		Instant now = Instant.now();
		EventRecord event = EventRecord.forInsert(
				UUID.randomUUID(), venue.id(), "Waitlist Event %s".formatted(UUID.randomUUID()),
				"Waitlist integration test", now.plus(Duration.ofDays(2)), now.minus(Duration.ofHours(1)),
				now.plus(Duration.ofHours(2)));
		eventMapper.insert(event);
		eventSectionMapper.insertForDraftEvent(EventSectionRecord.forInsert(
				UUID.randomUUID(), event.id(), section.id(), new BigDecimal("125000.00"), true));
		eventSeatMapper.insertForDraftEvent(event.id());
		jdbcTemplate.update(
				"UPDATE events SET status = 'PUBLISHED', start_time = ?, sales_start_time = ?, sales_end_time = ? "
						+ "WHERE id = ?",
				Timestamp.from(now.plus(Duration.ofDays(5))),
				Timestamp.from(now.minus(Duration.ofDays(1))),
				Timestamp.from(now.plus(Duration.ofDays(4))),
				event.id());
		List<UUID> seatIds = jdbcTemplate.queryForList(
				"SELECT es.id FROM event_seats es JOIN seats s ON s.id = es.seat_id "
						+ "WHERE es.event_id = ? ORDER BY s.seat_number",
				UUID.class, event.id());
		return new Venue(eventMapper.findById(event.id()), seatIds);
	}

	private UserRecord insertUser(String email, UserRole role) {
		UserRecord user = UserRecord.forInsert(UUID.randomUUID(), email, "{bcrypt}hash", role);
		userMapper.insertWithRole(user);
		return userMapper.findById(user.id());
	}

	private String bearer(UserRecord user) {
		return "Bearer " + jwtTokenService.issueAccessToken(user).accessToken();
	}

	private int count(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Integer.class, args);
	}

	private void cleanDatabase() {
		jdbcTemplate.update("DELETE FROM waitlist_entries");
		jdbcTemplate.update("DELETE FROM refresh_tokens");
		jdbcTemplate.update("DELETE FROM event_seats");
		jdbcTemplate.update("DELETE FROM event_sections");
		jdbcTemplate.update("DELETE FROM events");
		jdbcTemplate.update("DELETE FROM seats");
		jdbcTemplate.update("DELETE FROM venue_sections");
		jdbcTemplate.update("DELETE FROM venues");
		jdbcTemplate.update("DELETE FROM users");
	}

	private record Venue(EventRecord event, List<UUID> seatIds) {
	}
}
