/**
 * User routes — profile reads, profile updates, and user stats.
 * All routes require authentication via JWT (Bearer header or cookie).
 */
const express = require('express');
const router = express.Router();
const { requireAuth } = require('../lib/auth');
const { findUserById, getUserStats, updateAttachmentStyle, updateUserProfile } = require('../db/users');
const { getVerdictLogCount } = require('../db/verdict-logs');

// GET /api/users/me — current user profile
router.get('/me', requireAuth, async (req, res) => {
  const user = await findUserById(req.user.id);
  if (!user) return res.status(404).json({ error: 'Not found.' });
  res.json({
    id: user.id,
    email: user.email,
    name: user.name,
    attachment_style: user.attachment_style,
    quiz_completed: user.quiz_completed,
    created_at: user.created_at,
  });
});

// PUT /api/users/me — update profile (name, attachment_style)
router.put('/me', requireAuth, async (req, res) => {
  const { name, attachment_style } = req.body || {};
  const updated = await updateUserProfile(req.user.id, { name, attachment_style });
  if (!updated) return res.status(400).json({ error: 'Nothing to update.' });
  res.json({ ok: true, user: updated });
});

// GET /api/users/stats — streak count, lifetime holds, subscription status
router.get('/stats', requireAuth, async (req, res) => {
  const stats = await getUserStats(req.user.id);
  if (!stats) return res.status(404).json({ error: 'Not found.' });
  res.json(stats);
});

// GET /api/users/profile — full profile including pattern journal streak data
router.get('/profile', requireAuth, async (req, res) => {
  const user = await findUserById(req.user.id);
  if (!user) return res.status(404).json({ error: 'Not found.' });

  const verdictCount = await getVerdictLogCount(req.user.id);

  res.json({
    id: user.id,
    email: user.email,
    name: user.name,
    attachment_style: user.attachment_style,
    quiz_completed: user.quiz_completed,
    created_at: user.created_at,
    current_streak: user.current_streak ?? 0,
    last_active_at: user.last_active_at ?? null,
    verdict_count: verdictCount,
  });
});

// PUT /api/users/attachment-style — update after quiz completion
router.put('/attachment-style', requireAuth, async (req, res) => {
  const { attachment_style } = req.body || {};
  if (!attachment_style) return res.status(400).json({ error: 'attachment_style required.' });
  try {
    await updateAttachmentStyle(req.user.id, attachment_style);
    res.json({ ok: true });
  } catch (e) {
    res.status(400).json({ error: e.message });
  }
});

module.exports = router;
