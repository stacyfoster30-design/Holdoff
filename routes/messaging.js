/**
 * Messaging API routes for HoldOff.
 */
const express = require('express');
const router = express.Router();
const { requireAuth, getCookieTokens } = require('../lib/auth');
const msgDb = require('../db/messages');

/** GET /api/messaging/threads — list threads for the logged-in user. */
router.get('/threads', requireAuth, async (req, res) => {
  try {
    const threads = await msgDb.getThreadsByUser(req.user.id);
    res.json({ threads: threads || [] });
  } catch (err) {
    console.error('[messaging/threads] error:', err.message);
    res.json({ threads: [] });
  }
});

/** GET /api/messaging/contacts — list contacts for the logged-in user. */
router.get('/contacts', requireAuth, async (req, res) => {
  try {
    const contacts = await msgDb.getContactsByUser(req.user.id);
    res.json({ contacts: contacts || [] });
  } catch (err) {
    console.error('[messaging/contacts] error:', err.message);
    res.json({ contacts: [] });
  }
});

/** GET /api/messaging/threads/:threadId — messages for a specific thread. */
router.get('/threads/:threadId', requireAuth, async (req, res) => {
  try {
    const messages = await msgDb.getMessagesByThread(req.params.threadId);
    res.json({ messages: messages || [] });
  } catch (err) {
    console.error('[messaging/threads/:id] error:', err.message);
    res.json({ messages: [] });
  }
});

/** POST /api/messaging/native-sync — sync messages from the Android native SMS reader. */
router.post('/native-sync', requireAuth, async (req, res) => {
  try {
    const { contacts = [], messages = [] } = req.body || {};
    // Upsert contacts and messages from native SMS sync
    for (const c of contacts) {
      await msgDb.upsertContact(req.user.id, {
        name: c.name || c.phone_number,
        phoneNumber: c.phone_number,
        isFavorited: false,
      }).catch(() => {});
    }
    for (const m of messages) {
      if (!m.thread_id || !m.body) continue;
      await msgDb.insertMessage(m.thread_id, {
        senderType: m.sender_type || 'contact',
        body: m.body,
        externalId: m.external_id || null,
        timestamp: m.timestamp ? new Date(m.timestamp) : new Date(),
      }).catch(() => {});
    }
    res.json({ ok: true, synced: { contacts: contacts.length, messages: messages.length } });
  } catch (err) {
    console.error('[messaging/native-sync] error:', err.message);
    res.status(500).json({ ok: false, error: 'Sync failed.' });
  }
});

module.exports = router;
