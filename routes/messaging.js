/**
 * Messaging API routes for HoldOff.
 */
const express = require('express');
const router = express.Router();
const { requireAuth } = require('../lib/auth');
const msgDb = require('../db/messages');

router.get('/threads', (req, res) => {
  console.log('[DEBUG] /api/messaging/threads hit');
  res.json({ threads: [{ id: 'test', contact_name: 'Test Contact' }] });
});

module.exports = router;
