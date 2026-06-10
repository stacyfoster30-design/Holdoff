/**
 * Database connection pool. Only this file may construct new Pool().
 *
 * Production note:
 * - With DATABASE_URL set, this exports a normal PostgreSQL pool.
 * - Without DATABASE_URL, the app boots in a guarded degraded mode so public
 *   pages and /health can render, but any DB-backed action fails explicitly.
 *   This prevents Render from serving the default placeholder while still
 *   protecting user/payment data until the real database URL is configured.
 */
const { Pool } = require('pg');

function databaseUnavailableError() {
  const err = new Error('DATABASE_URL environment variable is required for this database operation');
  err.code = 'DATABASE_UNAVAILABLE';
  err.status = 503;
  return err;
}

let pool;

if (!process.env.DATABASE_URL) {
  console.warn('[pg pool] DATABASE_URL is not set — starting in guarded degraded mode');
  pool = {
    query: async () => {
      throw databaseUnavailableError();
    },
    connect: async () => {
      throw databaseUnavailableError();
    },
    end: async () => undefined,
    on: () => undefined,
  };
} else {
  pool = new Pool({
    connectionString: process.env.DATABASE_URL,
    ssl: process.env.DATABASE_URL.includes('localhost')
      ? false
      : { rejectUnauthorized: false },
  });

  // Neon auto-suspends idle connections; the client emits 'error' on reconnect
  // failure. Log and keep running — pool reconnects automatically on next query.
  pool.on('error', (err) => {
    console.error('[pg pool] idle client error (non-fatal):', err?.message);
  });
}

module.exports = { pool };
