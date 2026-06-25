/**
 * Messaging API routes for HoldOff.
 */
const express = require('express');
const router = express.Router();
const { requireAuth } = require('../lib/auth');
const msgDb = require('../db/messages');

router.get('/threads', requireAuth, async (req, res) => {
  try {
    const threads = await msgDb.getThreadsByUser(req.user.id);
    res.json({ threads });
  } catch (err) {
    console.error('[messaging/threads] error:', err.message);
    res.json({ threads: [] });
  }
});

router.get('/contacts', requireAuth, async (req, res) => {
  try {
    const contacts = await msgDb.getContactsByUser(req.user.id);
    res.json({ contacts });
  } catch (err) {
    console.error('[messaging/contacts] error:', err.message);
    res.json({ contacts: [] });
  }
});

/** POST /api/send-message — store a sent message to a contact's thread. */
router.post('/send-message', requireAuth, async (req, res) => {
  try {
    const { contactId, message } = req.body || {};
    if (!contactId) return res.status(400).json({ ok: false, error: 'contactId is required.' });
    if (!message || !message.trim()) return res.status(400).json({ ok: false, error: 'message is required.' });

    // Get or create thread for this user + contact
    const thread = await msgDb.getOrCreateThread(req.user.id, contactId, null);
    if (!thread) return res.status(404).json({ ok: false, error: 'Contact not found.' });

    // Insert the message into the thread
    await msgDb.insertMessage(thread.id, {
      senderType: 'user',
      body: message.trim(),
      externalId: null,
      timestamp: new Date(),
    });

    res.json({ ok: true, threadId: thread.id });
  } catch (err) {
    console.error('[messaging/send-message] error:', err.message);
    res.status(500).json({ ok: false, error: 'Failed to send message.' });
  }
});

/** GET /api/messaging/native-sync — placeholder for Android native sync. */
router.post('/native-sync', requireAuth, async (req, res) => {
  res.json({ ok: true, synced: 0 });
});

module.exports = router;
