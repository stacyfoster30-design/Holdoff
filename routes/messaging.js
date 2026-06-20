/**
 * Messaging API routes for HoldOff.
 */
const express = require('express');
const router = express.Router();
const { requireAuth } = require('../lib/auth');
const msgDb = require('../db/messages');

router.get('/threads', requireAuth, async (req, res) => {
  try {
    const userId = req.user.id;
    const threads = await msgDb.getThreadsByUser(userId) || [];
    res.json({ threads });
  } catch (err) {
    console.error('[API] GET /threads error:', err.message);
    res.status(500).json({ error: 'Failed to load threads', details: err.message });
  }
});

module.exports = router;
