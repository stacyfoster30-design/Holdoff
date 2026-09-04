/**
 * Pattern journal queries — HoldOff.
 * Owns: journal_entries table (trigger tracking, pattern history, verdict integration).
 * Does NOT own: users table, verdict_logs table (read-only references).
 */
const { pool } = require('./index');

/**
 * Create a journal entry.
 * If source='verdict', verdict_log_id should reference the verdict_logs row.
 */
async function createEntry({
  userId,
  triggerText,
  messageText = null,
  outcome = null,
  patternName = null,
  reframe = null,
  verdict = null,
  hourOfDay = null,
  source = 'manual',
  verdictLogId = null,
}) {
  const { rows } = await pool.query(
    `INSERT INTO journal_entries
       (user_id, trigger_text, message_text, outcome, pattern_name, reframe, verdict, hour_of_day, source, verdict_log_id)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
     RETURNING *`,
    [userId, triggerText, messageText, outcome, patternName, reframe, verdict, hourOfDay ?? new Date().getHours(), source, verdictLogId]
  );
  return rows[0];
}

/**
 * Get all journal entries for a user, newest first.
 */
async function getEntries(userId, limit = 50, offset = 0) {
  const { rows } = await pool.query(
    `SELECT id, trigger_text, message_text, outcome, pattern_name, reframe, verdict,
            created_at, hour_of_day, source
     FROM journal_entries
     WHERE user_id = $1
     ORDER BY created_at DESC
     LIMIT $2 OFFSET $3`,
    [userId, limit, offset]
  );
  return rows;
}

/**
 * Get a single journal entry.
 */
async function getEntry(entryId, userId) {
  const { rows } = await pool.query(
    `SELECT * FROM journal_entries WHERE id = $1 AND user_id = $2`,
    [entryId, userId]
  );
  return rows[0] || null;
}

/**
 * Update a journal entry (outcome only).
 */
async function updateEntry(entryId, userId, { outcome, triggerText }) {
  const { rows } = await pool.query(
    `UPDATE journal_entries
     SET outcome = COALESCE($3, outcome),
         trigger_text = COALESCE($4, trigger_text)
     WHERE id = $1 AND user_id = $2
     RETURNING *`,
    [entryId, userId, outcome, triggerText]
  );
  return rows[0] || null;
}

/**
 * Delete a journal entry.
 */
async function deleteEntry(entryId, userId) {
  const { rows } = await pool.query(
    `DELETE FROM journal_entries WHERE id = $1 AND user_id = $2 RETURNING id`,
    [entryId, userId]
  );
  return rows.length > 0;
}

/**
 * Top N patterns by frequency for a user.
 */
async function topPatterns(userId, limit = 3) {
  const { rows } = await pool.query(
    `SELECT pattern_name, COUNT(*) as count
     FROM journal_entries
     WHERE user_id = $1 AND pattern_name IS NOT NULL AND pattern_name != ''
     GROUP BY pattern_name
     ORDER BY count DESC
     LIMIT $2`,
    [userId, limit]
  );
  return rows;
}

/**
 * Time-of-day heatmap: count of entries per hour bucket (0–23).
 * Returns an array of { hour, count } for all 24 hours (0 = missing hours).
 */
async function timeHeatmap(userId) {
  const { rows } = await pool.query(
    `SELECT hour_of_day as hour, COUNT(*) as count
     FROM journal_entries
     WHERE user_id = $1
     GROUP BY hour_of_day
     ORDER BY hour`,
    [userId]
  );
  const map = {};
  for (const r of rows) map[r.hour] = Number(r.count);
  return Array.from({ length: 24 }, (_, i) => ({ hour: i, count: map[i] ?? 0 }));
}

/**
 * Get patterns with recency in a single query (replaces N+1 daysSincePattern calls).
 * Returns top patterns with days_since calculated efficiently.
 */
async function topPatternsWithRecency(userId, limit = 3) {
  const { rows } = await pool.query(
    `WITH top_pats AS (
       SELECT pattern_name, COUNT(*) as count
       FROM journal_entries
       WHERE user_id = $1 AND pattern_name IS NOT NULL AND pattern_name != ''
       GROUP BY pattern_name
       ORDER BY count DESC
       LIMIT $2
     )
     SELECT tp.pattern_name, tp.count,
            FLOOR((EXTRACT(EPOCH FROM NOW()) - EXTRACT(EPOCH FROM MAX(je.created_at))) / 86400)::int as days_since
     FROM top_pats tp
     LEFT JOIN journal_entries je ON je.user_id = $1 AND je.pattern_name = tp.pattern_name
     GROUP BY tp.pattern_name, tp.count
     ORDER BY tp.count DESC`,
    [userId, limit]
  );
  return rows;
}

/**
 * Recency: days since last entry for a given pattern name.
 * @deprecated Use topPatternsWithRecency instead to avoid N+1 queries
 */
async function daysSincePattern(userId, patternName) {
  const { rows } = await pool.query(
    `SELECT created_at FROM journal_entries
     WHERE user_id = $1 AND pattern_name = $2
     ORDER BY created_at DESC LIMIT 1`,
    [userId, patternName]
  );
  if (!rows.length) return null;
  const ms = Date.now() - new Date(rows[0].created_at).getTime();
  return Math.floor(ms / (1000 * 60 * 60 * 24));
}

/**
 * Get or create journal streak record for user.
 * Call after every entry to keep streak fresh.
 */
async function touchStreak(userId) {
  const today = new Date().toISOString().slice(0, 10);
  const { rows } = await pool.query(
    `INSERT INTO journal_streaks (user_id, current_streak, longest_streak, last_entry_date, total_entries, updated_at)
     VALUES ($1, 1, GREATEST((SELECT longest_streak FROM journal_streaks WHERE user_id = $1), 1), $2::date, 1, NOW())
     ON CONFLICT (user_id) DO UPDATE SET
       current_streak = CASE
         WHEN journal_streaks.last_entry_date = $2::date THEN journal_streaks.current_streak
         WHEN journal_streaks.last_entry_date = ($2::date - INTERVAL '1 day')::date THEN journal_streaks.current_streak + 1
         ELSE 1
       END,
       longest_streak = GREATEST(journal_streaks.longest_streak,
         CASE
           WHEN journal_streaks.last_entry_date = $2::date THEN journal_streaks.current_streak
           WHEN journal_streaks.last_entry_date = ($2::date - INTERVAL '1 day')::date THEN journal_streaks.current_streak + 1
           ELSE 1
         END),
       last_entry_date = $2::date,
       total_entries = journal_streaks.total_entries + 1,
       updated_at = NOW()
     RETURNING *`,
    [userId, today]
  );
  return rows[0];
}

/**
 * Get journal streak for user.
 */
async function getStreak(userId) {
  const { rows } = await pool.query(
    `SELECT current_streak, longest_streak, total_entries, last_entry_date
     FROM journal_streaks WHERE user_id = $1`,
    [userId]
  );
  return rows[0] || null;
}

/**
 * Journal insights: top patterns with recency, time heatmap, and streak.
 * Optimized: uses single query for patterns + recency instead of N+1.
 */
async function getInsights(userId) {
  const [patternsWithRecency, heatmap, streak] = await Promise.all([
    topPatternsWithRecency(userId, 3), // Batched query instead of N+1
    timeHeatmap(userId),
    getStreak(userId),
  ]);

  // Find peak hour (most entries)
  const peakHour = heatmap.reduce(
    (best, cur) => (cur.count > best.count ? cur : best),
    { hour: null, count: 0 }
  );

  return {
    top_patterns: patternsWithRecency.map(p => ({
      pattern_name: p.pattern_name,
      count: p.count,
      days_since: p.days_since,
    })),
    time_heatmap: heatmap,
    peak_hour: peakHour.hour,
    streak: streak ?? { current_streak: 0, longest_streak: 0, total_entries: 0 },
  };
}

/**
 * Ensure indexes exist for optimal query performance.
 * Call once during startup migrations.
 */
async function ensureIndexes() {
  try {
    await pool.query(`
      CREATE INDEX IF NOT EXISTS idx_journal_entries_user_pattern
      ON journal_entries(user_id, pattern_name)
      WHERE pattern_name IS NOT NULL;
    `);
    await pool.query(`
      CREATE INDEX IF NOT EXISTS idx_journal_entries_user_hour
      ON journal_entries(user_id, hour_of_day);
    `);
    await pool.query(`
      CREATE INDEX IF NOT EXISTS idx_journal_entries_user_created
      ON journal_entries(user_id, created_at DESC);
    `);
    console.log('[journal] Indexes ensured');
  } catch (err) {
    console.warn('[journal] Index creation warning:', err.message);
  }
}

module.exports = {
  createEntry,
  getEntries,
  getEntry,
  updateEntry,
  deleteEntry,
  topPatterns,
  topPatternsWithRecency,
  timeHeatmap,
  daysSincePattern,
  touchStreak,
  getStreak,
  getInsights,
  ensureIndexes,
};
