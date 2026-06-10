/**
 * Database connection pool. Only this file may construct new Pool().
 */
const { Pool } = require('pg');

if (!process.env.DATABASE_URL) {
  throw new Error('DATABASE_URL environment variable is required');
}

const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: process.env.DATABASE_URL?.includes('localhost')
    ? false
    : { rejectUnauthorized: false },
});

// Neon auto-suspends idle connections; the client emits 'error' on reconnect
// failure. Log and keep running — pool reconnects automatically on next query.
pool.on('error', (err) => {
  console.error('[pg pool] idle client error (non-fatal):', err?.message);
});

module.exports = { pool };