/**
 * Relationship anatomy queries — contacts, message_history, relationship_analysis.
 * Owns: contacts, message_history, relationship_analysis tables.
 */
const { pool } = require('./index');

// ── contacts ──────────────────────────────────────────────────────────────────

async function createContact({ userId, displayName, relationship, durationDays }) {
  const { rows } = await pool.query(
    `INSERT INTO contacts (user_id, display_name, relationship, duration_days)
     VALUES ($1, $2, $3, $4)
     RETURNING *`,
    [userId, displayName, relationship || null, durationDays || null]
  );
  return rows[0];
}

async function getContact(contactId, userId) {
  const { rows } = await pool.query(
    `SELECT * FROM contacts WHERE id = $1 AND user_id = $2 AND deleted_at IS NULL`,
    [contactId, userId]
  );
  return rows[0] || null;
}

async function getContacts(userId, { includeDeleted = false } = {}) {
  const where = includeDeleted ? 'user_id = $1' : 'user_id = $1 AND deleted_at IS NULL';
  const { rows } = await pool.query(
    `SELECT * FROM contacts WHERE ${where} ORDER BY created_at DESC`,
    [userId]
  );
  return rows;
}

async function updateContact(contactId, userId, { displayName, relationship, durationDays }) {
  const { rows } = await pool.query(
    `UPDATE contacts SET
       display_name = COALESCE($3, display_name),
       relationship = COALESCE($4, relationship),
       duration_days = COALESCE($5, duration_days),
       updated_at = NOW()
     WHERE id = $1 AND user_id = $2 AND deleted_at IS NULL
     RETURNING *`,
    [contactId, userId, displayName, relationship, durationDays]
  );
  return rows[0] || null;
}

async function softDeleteContact(contactId, userId) {
  const { rows } = await pool.query(
    `UPDATE contacts SET deleted_at = NOW(), updated_at = NOW()
     WHERE id = $1 AND user_id = $2 AND deleted_at IS NULL
     RETURNING id`,
    [contactId, userId]
  );
  return rows.length > 0;
}

// ── message_history ───────────────────────────────────────────────────────────

async function addMessage({ userId, contactId, direction, patternName, verdict, sentAt, metadata }) {
  const sentDate = sentAt ? new Date(sentAt) : new Date();
  const hour = sentDate.getHours();
  const dow = sentDate.getDay();
  const { rows } = await pool.query(
    `INSERT INTO message_history (user_id, contact_id, direction, pattern_name, verdict, hour_of_day, day_of_week, metadata, sent_at)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
     RETURNING *`,
    [userId, contactId, direction, patternName || null, verdict || null, hour, dow, metadata || '{}', sentDate]
  );
  return rows[0];
}

async function getMessageHistory(userId, contactId, { limit = 50, offset = 0 } = {}) {
  const { rows } = await pool.query(
    `SELECT id, direction, pattern_name, verdict, hour_of_day, day_of_week, metadata, sent_at, created_at
     FROM message_history
     WHERE user_id = $1 AND contact_id = $2
     ORDER BY sent_at DESC
     LIMIT $3 OFFSET $4`,
    [userId, contactId, limit, offset]
  );
  return rows;
}

async function getMessageStats(userId, contactId) {
  const { rows } = await pool.query(
    `SELECT
       COUNT(*) FILTER (WHERE direction = 'sent') AS sent_count,
       COUNT(*) FILTER (WHERE direction = 'received') AS received_count,
       COUNT(*) FILTER (WHERE verdict = 'HOLD') AS hold_count,
       COUNT(*) FILTER (WHERE verdict = 'REWRITE') AS rewrite_count,
       COUNT(*) FILTER (WHERE hour_of_day BETWEEN 22 AND 23 OR hour_of_day BETWEEN 0 AND 5) AS night_count,
       COUNT(*) FILTER (WHERE day_of_week IN (0, 6)) AS weekend_count
     FROM message_history
     WHERE user_id = $1 AND contact_id = $2`,
    [userId, contactId]
  );
  return rows[0];
}

// ── relationship_analysis ───────────────────────────────────────────────────

async function saveAnalysis({ userId, contactId, analysisText, healthScore, attachmentPattern, exitWarning }) {
  const { rows } = await pool.query(
    `INSERT INTO relationship_analysis (user_id, contact_id, analysis_text, health_score, attachment_pattern, exit_warning)
     VALUES ($1, $2, $3, $4, $5, $6)
     RETURNING *`,
    [userId, contactId, analysisText, healthScore || null, attachmentPattern || null, exitWarning || null]
  );
  return rows[0];
}

async function getLatestAnalysis(userId, contactId) {
  const { rows } = await pool.query(
    `SELECT * FROM relationship_analysis
     WHERE user_id = $1 AND contact_id = $2
     ORDER BY analyzed_at DESC
     LIMIT 1`,
    [userId, contactId]
  );
  return rows[0] || null;
}

async function getAnalysisHistory(userId, contactId, { limit = 12 } = {}) {
  const { rows } = await pool.query(
    `SELECT id, health_score, attachment_pattern, exit_warning, analyzed_at
     FROM relationship_analysis
     WHERE user_id = $1 AND contact_id = $2
     ORDER BY analyzed_at DESC
     LIMIT $3`,
    [userId, contactId, limit]
  );
  return rows;
}

module.exports = {
  createContact,
  getContact,
  getContacts,
  updateContact,
  softDeleteContact,
  addMessage,
  getMessageHistory,
  getMessageStats,
  saveAnalysis,
  getLatestAnalysis,
  getAnalysisHistory,
};
