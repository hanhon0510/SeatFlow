import http from 'k6/http';
import exec from 'k6/execution';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const PROFILE = __ENV.PROFILE || 'smoke';
const USER_COUNT = Number(__ENV.USER_COUNT || (PROFILE === 'portfolio' ? 80 : 16));
const USER_PASSWORD = __ENV.USER_PASSWORD || 'Sf048-load!1';
const RESULTS_PREFIX = __ENV.RESULTS_PREFIX || 'load-tests/results/sf048';

const holdConflicts = new Counter('seatflow_hold_conflicts');
const successfulPurchases = new Counter('seatflow_successful_purchases');
const duplicateBookingCount = new Counter('seatflow_duplicate_booking_count');
const paymentDuplicateCount = new Counter('seatflow_payment_duplicate_count');
const flowErrors = new Counter('seatflow_flow_errors');
const unexpectedErrorRate = new Rate('seatflow_unexpected_error_rate');

const profiles = {
  smoke: {
    event_browsing: {
      executor: 'constant-arrival-rate',
      exec: 'eventBrowsing',
      rate: 2,
      timeUnit: '1s',
      duration: '20s',
      preAllocatedVUs: 4,
    },
    seat_map_loading: {
      executor: 'constant-arrival-rate',
      exec: 'seatMapLoading',
      rate: 2,
      timeUnit: '1s',
      duration: '20s',
      preAllocatedVUs: 4,
    },
    hot_seat_competition: {
      executor: 'constant-arrival-rate',
      exec: 'hotSeatCompetition',
      rate: 4,
      timeUnit: '1s',
      duration: '20s',
      preAllocatedVUs: 12,
    },
    popular_event_seat_selection: {
      executor: 'constant-arrival-rate',
      exec: 'popularEventSeatSelection',
      rate: 2,
      timeUnit: '1s',
      duration: '20s',
      preAllocatedVUs: 6,
    },
    payment_retries: {
      executor: 'constant-arrival-rate',
      exec: 'paymentRetries',
      rate: 1,
      timeUnit: '1s',
      duration: '20s',
      preAllocatedVUs: 6,
      maxVUs: 12,
    },
  },
  portfolio: {
    event_browsing: {
      executor: 'constant-arrival-rate',
      exec: 'eventBrowsing',
      rate: 20,
      timeUnit: '1s',
      duration: '3m',
      preAllocatedVUs: 40,
      maxVUs: 80,
    },
    seat_map_loading: {
      executor: 'constant-arrival-rate',
      exec: 'seatMapLoading',
      rate: 12,
      timeUnit: '1s',
      duration: '3m',
      preAllocatedVUs: 40,
      maxVUs: 80,
    },
    hot_seat_competition: {
      executor: 'constant-arrival-rate',
      exec: 'hotSeatCompetition',
      rate: 30,
      timeUnit: '1s',
      duration: '90s',
      preAllocatedVUs: 80,
      maxVUs: 120,
      startTime: '15s',
    },
    popular_event_seat_selection: {
      executor: 'constant-arrival-rate',
      exec: 'popularEventSeatSelection',
      rate: 15,
      timeUnit: '1s',
      duration: '3m',
      preAllocatedVUs: 50,
      maxVUs: 100,
    },
    payment_retries: {
      executor: 'constant-arrival-rate',
      exec: 'paymentRetries',
      rate: 4,
      timeUnit: '1s',
      duration: '2m',
      preAllocatedVUs: 30,
      maxVUs: 60,
      startTime: '20s',
    },
  },
};

export const options = {
  scenarios: profiles[PROFILE] || profiles.smoke,
  summaryTrendStats: ['min', 'avg', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    seatflow_unexpected_error_rate: ['rate<0.01'],
    seatflow_duplicate_booking_count: ['count==0'],
    checks: ['rate>0.95'],
  },
};

export function setup() {
  waitForBackend();

  const users = createUsers();
  const events = discoverEvents();
  const hotSeats = availableSeats(loadSeatMap(events.hotSeatEventId, null, 'setup_hot_seat'));
  const popularSeats = availableSeats(loadSeatMap(events.popularEventId, null, 'setup_popular_event'));
  const paymentSeats = availableSeats(loadSeatMap(events.paymentEventId, null, 'setup_payment_event'));

  if (hotSeats.length === 0 || popularSeats.length === 0 || paymentSeats.length === 0) {
    throw new Error('SF-048 seed data has no available seats. Re-run load-tests/sql/seed_sf048.sql and clear Redis holds.');
  }

  return {
    users,
    events,
    hotSeat: hotSeats[0],
    popularSeats,
    paymentSeats,
  };
}

export function eventBrowsing(data) {
  const page = randomInt(0, 2);
  const size = randomItem([12, 24, 36]);
  const sort = randomItem(['start_asc', 'start_desc', 'price_asc', 'price_desc']);
  const search = randomItem(['SF-048', 'Browse', 'Load Test', 'Ho Chi Minh']);
  const res = http.get(
    `${BASE_URL}/api/v1/events?search=${encodeURIComponent(search)}&page=${page}&size=${size}&sort=${sort}`,
    taggedParams('event_browsing')
  );

  recordUnexpected(res, [200], 'event_browsing');
  check(res, {
    'event browsing returns events': (response) => response.status === 200 && safeJson(response)?.items?.length > 0,
  });
  sleep(randomPause());
}

export function seatMapLoading(data) {
  const eventId = randomItem(data.events.browseEventIds.concat([
    data.events.hotSeatEventId,
    data.events.popularEventId,
    data.events.paymentEventId,
  ]));
  const res = http.get(`${BASE_URL}/api/v1/events/${eventId}/seats`, taggedParams('seat_map_loading'));

  recordUnexpected(res, [200], 'seat_map_loading');
  check(res, {
    'seat map has sections': (response) => response.status === 200 && safeJson(response)?.sections?.length > 0,
  });
  sleep(randomPause());
}

export function hotSeatCompetition(data) {
  const user = selectUser(data);
  const res = createHold(data.events.hotSeatEventId, [data.hotSeat.eventSeatId], user.token, 'hot_seat_competition');

  if (res.status === 409) {
    holdConflicts.add(1, { scenario: 'hot_seat_competition' });
  }

  recordUnexpected(res, [201, 409], 'hot_seat_competition');
  check(res, {
    'hot-seat hold accepted or conflicted': (response) => response.status === 201 || response.status === 409,
  });
  sleep(randomPause());
}

export function popularEventSeatSelection(data) {
  const user = selectUser(data);
  const seat = randomItem(data.popularSeats);
  const res = createHold(data.events.popularEventId, [seat.eventSeatId], user.token, 'popular_event_seat_selection');

  if (res.status === 409) {
    holdConflicts.add(1, { scenario: 'popular_event_seat_selection' });
  }

  recordUnexpected(res, [201, 409], 'popular_event_seat_selection');
  check(res, {
    'popular-event hold accepted or conflicted': (response) => response.status === 201 || response.status === 409,
  });

  const body = safeJson(res);
  if (res.status === 201 && body?.holdId) {
    releaseHold(body.holdId, user.token, 'popular_event_seat_selection');
  }
  sleep(randomPause());
}

export function paymentRetries(data) {
  const user = selectUser(data);
  const seat = randomItem(data.paymentSeats);
  const hold = createHold(data.events.paymentEventId, [seat.eventSeatId], user.token, 'payment_retries');

  if (hold.status === 409) {
    holdConflicts.add(1, { scenario: 'payment_retries' });
    recordUnexpected(hold, [201, 409], 'payment_retries');
    return;
  }

  if (!recordUnexpected(hold, [201], 'payment_retries')) {
    return;
  }

  const holdBody = safeJson(hold);
  if (!holdBody?.holdId) {
    flowErrors.add(1, { scenario: 'payment_retries' });
    unexpectedErrorRate.add(true, { scenario: 'payment_retries' });
    return;
  }

  const reservation = createReservation(holdBody.holdId, user.token);
  if (!recordUnexpected(reservation, [201], 'payment_retries')) {
    releaseHold(holdBody.holdId, user.token, 'payment_retries');
    return;
  }

  const reservationBody = safeJson(reservation);
  const order = createOrder(reservationBody?.id, user.token);
  if (!recordUnexpected(order, [201], 'payment_retries')) {
    releaseHold(holdBody.holdId, user.token, 'payment_retries');
    return;
  }

  const orderBody = safeJson(order);
  const idempotencyKey = `sf048-${exec.vu.idInTest}-${exec.scenario.iterationInTest}-${Date.now()}`;
  const firstPayment = createPayment(orderBody?.id, user.token, idempotencyKey);
  if (!recordUnexpected(firstPayment, [201], 'payment_retries')) {
    releaseHold(holdBody.holdId, user.token, 'payment_retries');
    return;
  }

  const firstPaymentBody = safeJson(firstPayment);
  if (firstPaymentBody?.status === 'SUCCEEDED') {
    successfulPurchases.add(1, { scenario: 'payment_retries' });
  }

  const retryPayment = createPayment(orderBody?.id, user.token, idempotencyKey);
  recordUnexpected(retryPayment, [201], 'payment_retries');

  const retryPaymentBody = safeJson(retryPayment);
  if (retryPaymentBody?.id && retryPaymentBody.id === firstPaymentBody?.id) {
    paymentDuplicateCount.add(1, { scenario: 'payment_retries' });
  }

  countDuplicateTicketsForSeat(user.token, seat.eventSeatId);
  sleep(randomPause());
}

function waitForBackend() {
  for (let attempt = 0; attempt < 30; attempt++) {
    const res = http.get(`${BASE_URL}/api/v1/health`, taggedParams('setup_health'));
    if (res.status === 200) {
      return;
    }
    sleep(1);
  }
  throw new Error(`Backend is not healthy at ${BASE_URL}`);
}

function createUsers() {
  const users = [];
  for (let index = 1; index <= USER_COUNT; index++) {
    const email = `sf048-user-${String(index).padStart(3, '0')}@seatflow.local`;
    const register = http.post(
      `${BASE_URL}/api/v1/auth/register`,
      JSON.stringify({ email, password: USER_PASSWORD }),
      jsonParams('setup_register')
    );
    if (register.status !== 201 && register.status !== 409) {
      throw new Error(`Failed to register ${email}: HTTP ${register.status}`);
    }

    const login = http.post(
      `${BASE_URL}/api/v1/auth/login`,
      JSON.stringify({ email, password: USER_PASSWORD }),
      jsonParams('setup_login')
    );
    const body = safeJson(login);
    if (login.status !== 200 || !body?.accessToken) {
      throw new Error(`Failed to log in ${email}: HTTP ${login.status}`);
    }
    users.push({ email, token: body.accessToken });
  }
  return users;
}

function discoverEvents() {
  const res = http.get(
    `${BASE_URL}/api/v1/events?search=${encodeURIComponent('SF-048')}&size=100&sort=start_asc`,
    taggedParams('setup_discover_events')
  );
  if (res.status !== 200) {
    throw new Error(`Failed to discover SF-048 events: HTTP ${res.status}`);
  }

  const items = safeJson(res)?.items || [];
  const browseEventIds = items
    .filter((event) => event.name && event.name.indexOf('SF-048 Browse Event') === 0)
    .map((event) => event.id);
  const hotSeatEventId = findEventId(items, 'SF-048 Hot Seat Competition');
  const popularEventId = findEventId(items, 'SF-048 Popular Event Seat Selection');
  const paymentEventId = findEventId(items, 'SF-048 Payment Retry Event');

  if (browseEventIds.length === 0 || !hotSeatEventId || !popularEventId || !paymentEventId) {
    throw new Error('SF-048 event fixtures were not found. Run load-tests/sql/seed_sf048.sql first.');
  }

  return {
    browseEventIds,
    hotSeatEventId,
    popularEventId,
    paymentEventId,
  };
}

function findEventId(items, name) {
  const event = items.find((item) => item.name === name);
  return event ? event.id : null;
}

function loadSeatMap(eventId, token, scenario) {
  const params = token ? authParams(token, scenario) : taggedParams(scenario);
  const res = http.get(`${BASE_URL}/api/v1/events/${eventId}/seats`, params);
  if (res.status !== 200) {
    throw new Error(`Failed to load seat map for ${eventId}: HTTP ${res.status}`);
  }
  return safeJson(res);
}

function availableSeats(layout) {
  const seats = [];
  for (const section of layout?.sections || []) {
    for (const row of section.rows || []) {
      for (const seat of row.seats || []) {
        if (seat.status === 'AVAILABLE' && seat.permanentStatus === 'AVAILABLE') {
          seats.push(seat);
        }
      }
    }
  }
  return seats;
}

function createHold(eventId, eventSeatIds, token, scenario) {
  return http.post(
    `${BASE_URL}/api/v1/events/${eventId}/holds`,
    JSON.stringify({ eventSeatIds }),
    authParams(token, scenario)
  );
}

function releaseHold(holdId, token, scenario) {
  const res = http.del(`${BASE_URL}/api/v1/holds/${holdId}`, null, authParams(token, scenario));
  recordUnexpected(res, [204, 404], scenario);
  return res;
}

function createReservation(holdId, token) {
  return http.post(
    `${BASE_URL}/api/v1/reservations`,
    JSON.stringify({ holdId }),
    authParams(token, 'payment_retries')
  );
}

function createOrder(reservationId, token) {
  return http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({ reservationId }),
    authParams(token, 'payment_retries')
  );
}

function createPayment(orderId, token, idempotencyKey) {
  const params = authParams(token, 'payment_retries');
  params.headers['Idempotency-Key'] = idempotencyKey;
  return http.post(
    `${BASE_URL}/api/v1/orders/${orderId}/payments`,
    JSON.stringify({ token: 'tok_success' }),
    params
  );
}

function countDuplicateTicketsForSeat(token, eventSeatId) {
  const res = http.get(`${BASE_URL}/api/v1/users/me/tickets`, authParams(token, 'payment_retries'));
  if (!recordUnexpected(res, [200], 'payment_retries')) {
    return;
  }
  const tickets = safeJson(res) || [];
  const matches = tickets.filter((ticket) => ticket.eventSeatId === eventSeatId);
  if (matches.length > 1) {
    duplicateBookingCount.add(matches.length - 1, { scenario: 'payment_retries' });
  }
}

function selectUser(data) {
  const index = (exec.vu.idInTest + exec.scenario.iterationInTest) % data.users.length;
  return data.users[index];
}

function recordUnexpected(response, expectedStatuses, scenario) {
  const ok = expectedStatuses.indexOf(response.status) !== -1;
  unexpectedErrorRate.add(!ok, { scenario });
  if (!ok) {
    flowErrors.add(1, { scenario });
  }
  return ok;
}

function authParams(token, scenario) {
  const params = jsonParams(scenario);
  params.headers.Authorization = `Bearer ${token}`;
  return params;
}

function jsonParams(scenario) {
  const params = taggedParams(scenario);
  params.headers = { 'Content-Type': 'application/json' };
  return params;
}

function taggedParams(scenario) {
  return { tags: { scenario } };
}

function safeJson(response) {
  try {
    return response.json();
  } catch (error) {
    return null;
  }
}

function randomItem(items) {
  return items[randomInt(0, items.length - 1)];
}

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function randomPause() {
  return Math.random() * 0.25;
}

export function handleSummary(data) {
  return {
    stdout: markdownSummary(data),
    [`${RESULTS_PREFIX}-summary.json`]: JSON.stringify(data, null, 2),
    [`${RESULTS_PREFIX}-summary.md`]: markdownSummary(data),
  };
}

function markdownSummary(data) {
  const duration = data.metrics.http_req_duration?.values || {};
  const requests = data.metrics.http_reqs?.values || {};
  const httpFailed = data.metrics.http_req_failed?.values || {};
  const unexpected = data.metrics.seatflow_unexpected_error_rate?.values || {};

  return [
    '# SF-048 k6 Summary',
    '',
    `Generated: ${new Date().toISOString()}`,
    `Profile: ${PROFILE}`,
    `Base URL: ${BASE_URL}`,
    '',
    '| Metric | Value |',
    '| --- | ---: |',
    `| Throughput | ${formatNumber(requests.rate)} req/s |`,
    `| p50 latency | ${formatMs(duration['p(50)'] || duration.med)} |`,
    `| p95 latency | ${formatMs(duration['p(95)'])} |`,
    `| p99 latency | ${formatMs(duration['p(99)'])} |`,
    `| HTTP error rate | ${formatPercent(httpFailed.rate)} |`,
    `| Unexpected flow error rate | ${formatPercent(unexpected.rate)} |`,
    `| Hold conflicts | ${formatCount(data.metrics.seatflow_hold_conflicts)} |`,
    `| Successful purchases | ${formatCount(data.metrics.seatflow_successful_purchases)} |`,
    `| Duplicate booking count detected in-flow | ${formatCount(data.metrics.seatflow_duplicate_booking_count)} |`,
    `| Payment duplicate submissions deduped | ${formatCount(data.metrics.seatflow_payment_duplicate_count)} |`,
    '',
    'Run `load-tests/sql/verify_sf048.sql` after the test and save its output beside this file for DB-side duplicate-sales verification.',
    '',
  ].join('\n');
}

function formatMs(value) {
  return typeof value === 'number' ? `${value.toFixed(2)} ms` : 'not measured';
}

function formatNumber(value) {
  return typeof value === 'number' ? value.toFixed(2) : 'not measured';
}

function formatPercent(value) {
  return typeof value === 'number' ? `${(value * 100).toFixed(2)}%` : 'not measured';
}

function formatCount(metric) {
  const value = metric?.values?.count;
  return typeof value === 'number' ? String(value) : '0';
}
