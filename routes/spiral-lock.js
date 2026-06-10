const express = require('express');
const router = express.Router();

// Mock store for spiral lock status per user/IP
const spiralLocks = {};

// Check spiral lock status
router.get('/status', (req, res) => {
  const identifier = req.user ? req.user.id : req.ip;
  const lockInfo = spiralLocks[identifier];

  if (!lockInfo || lockInfo.expiresAt < Date.now()) {
    return res.json({ locked: false });
  }

  res.json({
    locked: true,
    expiresAt: lockInfo.expiresAt,
    remainingMs: lockInfo.expiresAt - Date.now()
  });
});

// Trigger spiral lock (typically called by Android app or verdict route)
router.post('/trigger', (req, res) => {
  const identifier = req.user ? req.user.id : req.ip;
  
  spiralLocks[identifier] = {
    expiresAt: Date.now() + 5 * 60 * 1000, // 5 minutes cool-down
    reason: '3+ messages with no reply within 1 hour'
  };

  res.json({ success: true, locked: true });
});

// Unlock via quiz completion
router.post('/unlock', (req, res) => {
  const identifier = req.user ? req.user.id : req.ip;
  const { answers } = req.body;
  
  // Basic validation for 3-question cognitive reappraisal quiz
  if (answers && answers.length >= 3) {
    delete spiralLocks[identifier];
    return res.json({ success: true, locked: false });
  }
  
  res.status(400).json({ error: 'Quiz incomplete' });
});

module.exports = router;
