package com.seatflow.waitlist;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class WaitlistSeatPickingTests {

	private static final UUID ORCHESTRA = UUID.randomUUID();
	private static final UUID BALCONY = UUID.randomUUID();

	@Test
	void keepsAGroupTogetherInOneRowWhenARunIsLongEnough() {
		// A1 and A2 are free, A3 is taken, A4 to A6 are free: three seats fit only in the second run.
		List<WaitlistFreeSeat> free = List.of(
				seat(ORCHESTRA, "A", 1), seat(ORCHESTRA, "A", 2),
				seat(ORCHESTRA, "A", 4), seat(ORCHESTRA, "A", 5), seat(ORCHESTRA, "A", 6));

		List<WaitlistFreeSeat> picked = WaitlistService.pick(free, 3);

		assertThat(picked).extracting(WaitlistFreeSeat::seatNumber).containsExactly(4, 5, 6);
	}

	@Test
	void takesTheFirstRunThatFits() {
		List<WaitlistFreeSeat> free = List.of(
				seat(ORCHESTRA, "A", 1), seat(ORCHESTRA, "A", 2),
				seat(ORCHESTRA, "B", 1), seat(ORCHESTRA, "B", 2), seat(ORCHESTRA, "B", 3));

		List<WaitlistFreeSeat> picked = WaitlistService.pick(free, 2);

		assertThat(picked).extracting(WaitlistFreeSeat::rowLabel).containsOnly("A");
	}

	@Test
	void doesNotJoinSeatsAcrossRowsOrSections() {
		List<WaitlistFreeSeat> free = List.of(
				seat(ORCHESTRA, "A", 5), seat(ORCHESTRA, "B", 6),
				seat(BALCONY, "B", 7));

		List<WaitlistFreeSeat> picked = WaitlistService.pick(free, 2);

		// No two seats sit together, so the group gets the first seats there are.
		assertThat(picked).extracting(WaitlistFreeSeat::seatNumber).containsExactly(5, 6);
	}

	@Test
	void aSingleSeatIsTheFirstFreeOne() {
		List<WaitlistFreeSeat> free = List.of(seat(ORCHESTRA, "A", 3), seat(ORCHESTRA, "A", 9));

		assertThat(WaitlistService.pick(free, 1)).extracting(WaitlistFreeSeat::seatNumber).containsExactly(3);
	}

	private static WaitlistFreeSeat seat(UUID section, String row, int number) {
		return new WaitlistFreeSeat(UUID.randomUUID(), section, row, number);
	}
}
