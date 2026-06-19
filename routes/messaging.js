/**
 * Messaging API routes for HoldOff.
 * Handles contacts, threads, messages, and spiral lock.
 */

const express = require('express');
const router = express.Router();
const { requireAuth } = require('../lib/auth');
const msgDb = require('../db/messages');

// ─── CONTACTS ────────────────────────────────────────────────────────────────

/**
 * GET /api/messaging/contacts
 * List all contacts for the authenticated user.
 */
router.get('/contacts', requireAuth, async (req, res) => {
  try {
    const userId = req.user.id;
    const contacts = await msgDb.getContactsByUser(userId);
    res.json({ contacts });
  } catch (err) {
    console.error('[API] GET /contacts error:', err.message);
    res.status(500).json({ error: 'Failed to load contacts' });
  }
});

/**
 * POST /api/messaging/contacts
 * Create or update a contact.
 * Body: { name, phoneNumber, isFavorited? }
 */
router.post('/contacts', requireAuth, async (req, res) => {
  try {
    const userId = req.user.id;
    const { name, phoneNumber, isFavorited } = req.body;

    if (!phoneNumber) {
      return res.status(400).json({ error: 'phoneNumber required' });
    }

    // Upsert contact
    const contact = await msgDb.upsertContact(userId, {
      name: name || phoneNumber,
      phoneNumber,
      isFavorited: isFavorited ?? false,
    });

    res.json({ contact });
  } catch (err) {
    console.error('[API] POST /contacts error:', err.message);
    res.status(500).json({ error: 'Failed to create contact' });
  }
});

/**
 * DELETE /api/messaging/contacts/:contactId
 * Delete a contact.
 */
router.delete('/contacts/:contactId', requireAuth, async (req, res) => {
  try {
    const { contactId } = req.params;
    await msgDb.deleteContact(contactId);
    res.json({ ok: true });
  } catch (err) {
    console.error('[API] DELETE /contacts error:', err.message);
    res.status(500).json({ error: 'Failed to delete contact' });
  }
});


// ─── NATIVE ANDROID SYNC ─────────────────────────────────────────────────────

/**
 * POST /api/messaging/native-sync
 * Import Android contacts and queued SMS into HoldOff threads.
 * Body: { contacts?: [{ name, phoneNumber }], messages?: [{ from|to|phoneNumber, body, direction, timestamp }] }
 */
router.post('/native-sync', requireAuth, async (req, res) => {
  try {
    const userId = req.user.id;
    const { contacts = [], messages = [] } = req.body || {};

    if (!Array.isArray(contacts) || !Array.isArray(messages)) {
      return res.status(400).json({ error: 'contacts and messages must be arrays' });
    }

    const sync = await msgDb.importNativeSync(userId, { contacts, messages });
    res.json({ ok: true, sync });
  } catch (err) {
    console.error('[API] POST /native-sync error:', err.message);
    res.status(500).json({ error: 'Failed to sync native SMS data' });
  }
});

// ─── THREADS ─────────────────────────────────────────────────────────────────

/**
 * GET /api/messaging/threads
 * List all message threads for the authenticated user.
 */
router.get('/threads', requireAuth, async (req, res) => {
  try {
    const userId = req.user.id;
    const threads = await msgDb.getThreadsByUser(userId);
    res.json({ threads });
  } catch (err) {
    console.error('[API] GET /threads error:', err.message);
    res.status(500).json({ error: 'Failed to load threads' });
  }
});

/**
 * GET /api/messaging/threads/:threadId
 * Get a specific thread with message history.
 */
router.get('/threads/:threadId', requireAuth, async (req, res) => {
  try {
    const { threadId } = req.params;
    const userId = req.user.id;

    const thread = await msgDb.getThreadById(threadId);
    if (!thread || String(thread.user_id) !== String(userId)) {
      return res.status(404).json({ error: 'Thread not found' });
    }

    const messages = await msgDb.getMessagesByThread(threadId, 50);
    const spiralState = await msgDb.getSpiralLockState(threadId);

    res.json({ thread, messages, spiralState });
  } catch (err) {
    console.error('[API] GET /threads/:threadId error:', err.message);
    res.status(500).json({ error: 'Failed to load thread' });
  }
});

// ─── MESSAGES ─────────────────────────────────────────────────────────────────

/**
 * POST /api/messaging/threads/:threadId/messages
 * Add a message to a thread.
 * Body: { body, senderType?, timestamp? }
 */
router.post('/threads/:threadId/messages', requireAuth, async (req, res) => {
  try {
    const { threadId } = req.params;
    const { body, senderType = 'user', timestamp } = req.body;
    const userId = req.user.id;

    if (!body || !body.trim()) {
      return res.status(400).json({ error: 'Message body required' });
    }

    const thread = await msgDb.getThreadById(threadId);
    if (!thread || String(thread.user_id) !== String(userId)) {
      return res.status(404).json({ error: 'Thread not found' });
    }

    const msg = await msgDb.insertMessage(threadId, {
      senderType,
      body: body.trim(),
      externalId: null,
      timestamp: timestamp ? new Date(timestamp) : new Date(),
    });

    res.json({ message: msg });
  } catch (err) {
    console.error('[API] POST /messages error:', err.message);
    res.status(500).json({ error: 'Failed to add message' });
  }
});

// ─── SPIRAL LOCK ──────────────────────────────────────────────────────────────

/**
 * GET /api/messaging/threads/:threadId/spiral-state
 * Get spiral lock state for a thread.
 */
router.get('/threads/:threadId/spiral-state', requireAuth, async (req, res) => {
  try {
    const { threadId } = req.params;
    const userId = req.user.id;

    const thread = await msgDb.getThreadById(threadId);
    if (!thread || String(thread.user_id) !== String(userId)) {
      return res.status(404).json({ error: 'Thread not found' });
    }

    let spiralState = await msgDb.getSpiralLockState(threadId);
    if (!spiralState) {
      spiralState = await msgDb.updateSpiralLockState(threadId, {
        isLocked: false,
        lockedUntil: null,
        spiralCount: 0,
        quizPassed: false,
      });
    }

    // Check if lock has expired
    const now = new Date();
    const hasExpired = spiralState.is_locked && spiralState.locked_until && spiralState.locked_until < now;
    if (hasExpired) {
      spiralState = await msgDb.resetSpiralCount(threadId);
    }

    res.json({ spiralState });
  } catch (err) {
    console.error('[API] GET /spiral-state error:', err.message);
    res.status(500).json({ error: 'Failed to load spiral state' });
  }
});

/**
 * POST /api/messaging/threads/:threadId/spiral-quiz
 * Mark spiral lock quiz as passed.
 */
router.post('/threads/:threadId/spiral-quiz', requireAuth, async (req, res) => {
  try {
    const { threadId } = req.params;
    const userId = req.user.id;
    const { answers } = req.body; // Optional quiz answers for validation

    const thread = await msgDb.getThreadById(threadId);
    if (!thread || String(thread.user_id) !== String(userId)) {
      return res.status(404).json({ error: 'Thread not found' });
    }

    // TODO: Validate quiz answers if needed
    // For now, just mark as passed
    const spiralState = await msgDb.markQuizPassed(threadId);

    res.json({ spiralState });
  } catch (err) {
    console.error('[API] POST /spiral-quiz error:', err.message);
    res.status(500).json({ error: 'Failed to mark quiz passed' });
  }
});

/**
 * POST /api/messaging/threads/:threadId/spiral-reset
 * Reset spiral lock (after successful send or manual reset).
 */
router.post('/threads/:threadId/spiral-reset', requireAuth, async (req, res) => {
  try {
    const { threadId } = req.params;
    const userId = req.user.id;

    const thread = await msgDb.getThreadById(threadId);
    if (!thread || String(thread.user_id) !== String(userId)) {
      return res.status(404).json({ error: 'Thread not found' });
    }

    const spiralState = await msgDb.resetSpiralCount(threadId);
    res.json({ spiralState });
  } catch (err) {
    console.error('[API] POST /spiral-reset error:', err.message);
    res.status(500).json({ error: 'Failed to reset spiral lock' });
  }
});

// ─── USER CONDITIONS ──────────────────────────────────────────────────────────

/**
 * GET /api/messaging/user/conditions
 * Get user's selected mental health conditions.
 */
router.get('/user/conditions', requireAuth, async (req, res) => {
  try {
    const userId = req.user.id;
    const conditions = await msgDb.getUserConditions(userId);
    res.json({ conditions });
  } catch (err) {
    console.error('[API] GET /user/conditions error:', err.message);
    res.status(500).json({ error: 'Failed to load conditions' });
  }
});

/**
 * POST /api/messaging/user/conditions
 * Update user's mental health conditions.
 * Body: { conditions: ['RSD', 'Anxiety', ...] }
 */
router.post('/user/conditions', requireAuth, async (req, res) => {
  try {
    const userId = req.user.id;
    const { conditions } = req.body;

    if (!Array.isArray(conditions)) {
      return res.status(400).json({ error: 'conditions must be an array' });
    }

    await msgDb.setUserConditions(userId, conditions);
    const updated = await msgDb.getUserConditions(userId);

    res.json({ conditions: updated });
  } catch (err) {
    console.error('[API] POST /user/conditions error:', err.message);
    res.status(500).json({ error: 'Failed to update conditions' });
  }
});

// ─── VERDICT & SEND ───────────────────────────────────────────────────────────

/**
 * POST /api/messaging/threads/:threadId/analyze
 * Analyze a message (run through Verdict + Spiral Lock check).
 * Body: { messageText }
 * Returns: { verdict, pattern, whats_happening, grounded_voice, spiralLocked, spiralTimeRemaining }
 */
router.post('/threads/:threadId/analyze', requireAuth, async (req, res) => {
  try {
    const { threadId } = req.params;
    const userId = req.user.id;
    const { messageText } = req.body;

    if (!messageText || !messageText.trim()) {
      return res.status(400).json({ error: 'Message text required' });
    }

    const thread = await msgDb.getThreadById(threadId);
    if (!thread || String(thread.user_id) !== String(userId)) {
      return res.status(404).json({ error: 'Thread not found' });
    }

    // Get user's conditions
    const userConditions = await msgDb.getUserConditions(userId);

    // Call the main /api/filter/analyze endpoint
    // (Reuse existing verdict-ai.js logic)
    const verdictResponse = await analyzeMessage(messageText, userConditions);

    // Update spiral lock state based on verdict
    const spiralState = await msgDb.incrementSpiralCount(threadId, verdictResponse.verdict);

    // Check if lock is active
    const now = new Date();
    const isLocked = spiralState.is_locked && (!spiralState.locked_until || spiralState.locked_until > now);
    const timeRemaining = isLocked && spiralState.locked_until 
      ? Math.ceil((spiralState.locked_until - now) / 1000) 
      : 0;

    res.json({
      ...verdictResponse,
      spiralLocked: isLocked,
      spiralTimeRemaining: timeRemaining,
      spiralCount: spiralState.spiral_count,
    });
  } catch (err) {
    console.error('[API] POST /analyze error:', err.message);
    res.status(500).json({ error: 'Failed to analyze message' });
  }
});

/**
 * POST /api/messaging/threads/:threadId/send
 * Send a message (after verdict approval).
 * Body: { messageText, verdictData }
 */
router.post('/threads/:threadId/send', requireAuth, async (req, res) => {
  try {
    const { threadId } = req.params;
    const userId = req.user.id;
    const { messageText, verdictData } = req.body;

    if (!messageText || !messageText.trim()) {
      return res.status(400).json({ error: 'Message text required' });
    }

    const thread = await msgDb.getThreadById(threadId);
    if (!thread || String(thread.user_id) !== String(userId)) {
      return res.status(404).json({ error: 'Thread not found' });
    }

    // Check spiral lock
    const spiralState = await msgDb.getSpiralLockState(threadId);
    const now = new Date();
    const isLocked = spiralState?.is_locked && (!spiralState.locked_until || spiralState.locked_until > now);
    
    if (isLocked && !spiralState.quiz_passed) {
      return res.status(423).json({ 
        error: 'Spiral lock active. Complete the quiz to send.',
        spiralTimeRemaining: Math.ceil((spiralState.locked_until - now) / 1000),
      });
    }

    // Store message
    const msg = await msgDb.insertMessage(threadId, {
      senderType: 'user',
      body: messageText.trim(),
      externalId: null,
      timestamp: new Date(),
    });

    // Log verdict
    await msgDb.logSentMessage(threadId, {
      originalText: messageText,
      verdict: verdictData?.verdict || 'SEND',
      verdictJson: verdictData || {},
      finalText: messageText,
    });

    // Reset spiral lock after successful send
    await msgDb.resetSpiralCount(threadId);

    res.json({ message: msg, ok: true });
  } catch (err) {
    console.error('[API] POST /send error:', err.message);
    res.status(500).json({ error: 'Failed to send message' });
  }
});

/**
 * Helper: Analyze message using verdict-ai logic.
 * TODO: Extract from /api/filter/analyze and reuse here.
 */
async function analyzeMessage(messageText, userConditions) {
  // For now, return a stub response
  // In production, call the main verdict-ai handler
  return {
    verdict: 'SEND',
    pattern: 'clear',
    whats_happening: 'This message looks good.',
    grounded_voice: 'Send it when you are ready.',
    confidence: 0.9,
  };
}

module.exports = router;
