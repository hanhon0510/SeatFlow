import { execFileSync } from 'node:child_process'

import { envValue, seedNamespace } from './seatflow'

/**
 * Removes every row a seeded run created, matched by the run's seed namespace. The API has no
 * delete endpoints for events or users, so this goes straight to the database.
 *
 * Only `event_seats`, `event_sections`, `seats` and `reservation_items` cascade, so everything
 * else is deleted child-first inside one transaction: a failure half way leaves nothing behind.
 */
const purgeSql = `
BEGIN;

CREATE TEMP TABLE seed_events ON COMMIT DROP AS
SELECT id FROM events WHERE name LIKE 'Event ' || :'ns' || '-%';

CREATE TEMP TABLE seed_venues ON COMMIT DROP AS
SELECT id FROM venues WHERE name LIKE 'Venue ' || :'ns' || '-%';

CREATE TEMP TABLE seed_users ON COMMIT DROP AS
SELECT id FROM users WHERE email LIKE replace(:'ns', '-', '.') || '.%@example.test';

CREATE TEMP TABLE seed_reservations ON COMMIT DROP AS
SELECT id FROM reservations WHERE event_id IN (SELECT id FROM seed_events);

CREATE TEMP TABLE seed_orders ON COMMIT DROP AS
SELECT id FROM orders WHERE reservation_id IN (SELECT id FROM seed_reservations);

CREATE TEMP TABLE seed_outbox ON COMMIT DROP AS
SELECT id FROM outbox_events WHERE aggregate_id IN (SELECT id FROM seed_orders);

SELECT
  (SELECT count(*) FROM seed_events),
  (SELECT count(*) FROM seed_venues),
  (SELECT count(*) FROM seed_users),
  (SELECT count(*) FROM seed_orders);

DELETE FROM processed_events WHERE event_id IN (SELECT id FROM seed_outbox);
DELETE FROM outbox_events WHERE id IN (SELECT id FROM seed_outbox);
DELETE FROM order_paid_analytics WHERE order_id IN (SELECT id FROM seed_orders);
DELETE FROM tickets WHERE order_id IN (SELECT id FROM seed_orders);
DELETE FROM payments WHERE order_id IN (SELECT id FROM seed_orders);
DELETE FROM orders WHERE id IN (SELECT id FROM seed_orders);
DELETE FROM reservations WHERE id IN (SELECT id FROM seed_reservations);
DELETE FROM events WHERE id IN (SELECT id FROM seed_events);
DELETE FROM venue_sections WHERE venue_id IN (SELECT id FROM seed_venues);
DELETE FROM venues WHERE id IN (SELECT id FROM seed_venues);

-- Guarded so a seed account that somehow still owns real rows is kept rather than aborting
-- the whole purge on a foreign key.
DELETE FROM users
WHERE id IN (SELECT id FROM seed_users)
  AND NOT EXISTS (SELECT 1 FROM orders o WHERE o.user_id = users.id)
  AND NOT EXISTS (SELECT 1 FROM reservations r WHERE r.user_id = users.id);

COMMIT;
`

type PsqlAttempt = {
  label: string
  command: string
  args: string[]
  env: NodeJS.ProcessEnv
}

export function purgeSeedData(reason: string) {
  if (envValue('E2E_SKIP_CLEANUP', 'false').toLowerCase() === 'true') {
    console.warn(`[e2e] ${reason}: skipped because E2E_SKIP_CLEANUP is set.`)
    return
  }

  const namespace = seedNamespace()
  const failures: string[] = []

  for (const attempt of psqlAttempts(namespace)) {
    try {
      const output = execFileSync(attempt.command, attempt.args, {
        input: purgeSql,
        env: { ...process.env, ...attempt.env },
        encoding: 'utf8',
        stdio: ['pipe', 'pipe', 'pipe'],
      })
      console.log(
        `[e2e] ${reason} via ${attempt.label}: removed ${summarise(output)} for namespace "${namespace}".`,
      )
      return
    } catch (error) {
      failures.push(`${attempt.label}: ${describe(error)}`)
    }
  }

  // Cleanup says nothing about whether the tests passed, so this warns instead of failing.
  console.warn(
    `[e2e] ${reason} failed, so "${namespace}" seed data is still in the database.\n  ${failures.join('\n  ')}`,
  )
}

function psqlAttempts(namespace: string): PsqlAttempt[] {
  const database = envValue('POSTGRES_DB', 'seatflow')
  const user = envValue('POSTGRES_USER', 'seatflow')
  const password = envValue('POSTGRES_PASSWORD', 'seatflow')
  const host = envValue('E2E_DB_HOST', 'localhost')
  const port = envValue('POSTGRES_PORT', '5432')
  const container = envValue('E2E_DB_CONTAINER', 'seatflow-postgres')
  const flags = ['-v', 'ON_ERROR_STOP=1', '-v', `ns=${namespace}`, '-q', '-t', '-A', '-F', ' ']

  return [
    {
      label: `psql ${host}:${port}`,
      command: 'psql',
      args: ['-h', host, '-p', port, '-U', user, '-d', database, ...flags],
      env: { PGPASSWORD: password },
    },
    {
      label: `docker exec ${container}`,
      command: 'docker',
      args: ['exec', '-i', container, 'psql', '-U', user, '-d', database, ...flags],
      env: {},
    },
  ]
}

function summarise(output: string) {
  const counts = output.trim().split(/\r?\n/).at(-1)?.trim().split(' ') ?? []
  const [events = '0', venues = '0', users = '0', orders = '0'] = counts
  return `${events} events, ${venues} venues, ${users} users, ${orders} orders`
}

function describe(error: unknown) {
  if (error instanceof Error) {
    const stderr = (error as { stderr?: Buffer | string }).stderr
    const detail = typeof stderr === 'string' ? stderr : stderr?.toString('utf8')
    return (detail?.trim() || error.message).split(/\r?\n/).join(' ')
  }
  return String(error)
}
