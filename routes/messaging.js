/**
 * Messaging API routes for HoldOff.
 * Handles contacts, threads, messages, and spiral lock.
 */

const express = require('express');
const router = express.Router();
const { requireAuth } = require('../lib/auth');
const msgDb = require('../db/messages');
const contactDb = require('../db/contacts');
const smsSyncService = require('../services/sms-sync');

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

    if (!name || !phoneNumber) {
      return res.status(400).json({ error: 'Name and phoneNumber required' });
    }

    // Upsert contact
    const contact = await msgDb.upsertContact(userId, {
      name,
      phoneNumber,
      isFavorited: isFavorited ?? false,
    });

    // Auto-sync SMS from this contact (mock for now)
    try {
      await smsSyncService.syncSMSThread(userId, { name, phoneNumber }, { useMock: true });
    } catch (syncErr) {
      console.warn('[API] SMS sync warning:', syncErr.message);
      // Don't fail the contact creation if SMS sync fails
    }

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

// ─── THREADS ─────────────────────────────────────────────────────────────────

/**
 * GET /api/messaging/threads
 * List all message threads for the authenticated user.
 */
router.get('/threads', requireAuth, async (req, res) => {
  try {
    const userId = req.user.id;
    console.log('[API] GET /threads for user', userId);
    const threads = await msgDb.getThreadsByUser(userId);
    console.log('[API] GET /threads success, returning', threads?.length || 0, 'threads');
    res.json({ threads: threads || [] });
  } catch (err) {
    console.error('[API] GET /threads error:', err.message, err.code, err.stack?.split('\n')[1]);
    res.status(500).json({ error: 'Failed to load threads', details: err.message });
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
    if (!thread || thread.user_id !== userId) {
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
 * Returns sobriety warnings if drunk texting detected.
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
    if (!thread || thread.user_id !== userId) {
      return res.status(404).json({ error: 'Thread not found' });
    }

    // Detect sobriety issues on outgoing messages
    let sobrietyWarning = null;
    if (senderType === 'user') {
      sobrietyWarning = await contactDb.detectDrunkTexting(
        userId,
        thread.contact_id,
        body,
        timestamp || new Date()
      );

      // Block message send if sobriety lock triggered
      if (sobrietyWarning.shouldLock) {
        return res.status(429).json({
          error: 'Sobriety lock activated',
          sobrietyWarning,
          message: 'Please take a moment and review what you're about to send.'
        });
      }
    }

    const msg = await msgDb.insertMessage(threadId, {
      senderType,
      body: body.trim(),
      externalId: null,
      timestamp: timestamp ? new Date(timestamp) : new Date(),
    });

    res.json({
      message: msg,
      sobrietyWarning: sobrietyWarning && sobrietyWarning.shouldWarn ? sobrietyWarning : null
    });
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
    if (!thread || thread.user_id !== userId) {
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
    if (!thread || thread.user_id !== userId) {
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
    if (!thread || thread.user_id !== userId) {
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
    if (!thread || thread.user_id !== userId) {
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
    if (!thread || thread.user_id !== userId) {
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

// ─── MESSENGER PLATFORMS ─────────────────────────────────────────────────────

/**
 * GET /api/messaging/platforms
 * Get list of connected messenger platforms for the user.
 */
router.get('/platforms', requireAuth, async (req, res) => {
  try {
    const userId = req.user.id;
    const platforms = await msgDb.getConnectedPlatforms(userId);
    res.json({ platforms });
  } catch (err) {
    console.error('[API] GET /platforms error:', err.message);
    res.status(500).json({ error: 'Failed to load platforms' });
  }
});

/**
 * POST /api/messaging/platforms/:platform/connect
 * Initiate OAuth connection for a messenger platform.
 * Body: { redirectUri? }
 */
router.post('/platforms/:platform/connect', requireAuth, async (req, res) => {
  try {
    const { platform } = req.params;
    const userId = req.user.id;
    const { redirectUri } = req.body;

    const validPlatforms = ['sms', 'facebook', 'whatsapp', 'instagram', 'telegram'];
    if (!validPlatforms.includes(platform)) {
      return res.status(400).json({ error: `Invalid platform: ${platform}` });
    }

    // Generate OAuth state and store it
    const state = require('crypto').randomBytes(16).toString('hex');
    await msgDb.storeOAuthState(userId, platform, state);

    // Return OAuth URL for frontend redirect
    const oauthUrls = {
      facebook: `https://www.facebook.com/v18.0/dialog/oauth?client_id=${process.env.FACEBOOK_APP_ID}&redirect_uri=${redirectUri}&state=${state}&scope=messages_read,messages_manage`,
      instagram: `https://www.instagram.com/oauth/authorize?client_id=${process.env.INSTAGRAM_APP_ID}&redirect_uri=${redirectUri}&scope=instagram_business_basic,instagram_business_manage_messages&state=${state}`,
      whatsapp: `https://www.whatsapp.com/business/api/auth?client_id=${process.env.WHATSAPP_CLIENT_ID}&redirect_uri=${redirectUri}&state=${state}`,
      telegram: `https://telegram.org/bot/login?state=${state}`,
      sms: null, // SMS uses device permissions, not OAuth
    };

    res.json({ 
      platform, 
      oauthUrl: oauthUrls[platform] || null,
      state 
    });
  } catch (err) {
    console.error('[API] POST /platforms/:platform/connect error:', err.message);
    res.status(500).json({ error: 'Failed to initiate connection' });
  }
});

/**
 * POST /api/messaging/platforms/:platform/oauth-callback
 * Handle OAuth callback from messenger platforms.
 * Body: { code, state }
 */
router.post('/platforms/:platform/oauth-callback', requireAuth, async (req, res) => {
  try {
    const { platform } = req.params;
    const userId = req.user.id;
    const { code, state } = req.body;

    // Verify state
    const storedState = await msgDb.getOAuthState(userId, platform);
    if (storedState !== state) {
      return res.status(401).json({ error: 'Invalid OAuth state' });
    }

    // Exchange code for token (varies by platform)
    let accessToken;
    switch (platform) {
      case 'facebook':
        accessToken = await exchangeFacebookToken(code);
        break;
      case 'instagram':
        accessToken = await exchangeInstagramToken(code);
        break;
      case 'whatsapp':
        accessToken = await exchangeWhatsAppToken(code);
        break;
      case 'telegram':
        accessToken = await exchangeTelegramToken(code);
        break;
      default:
        return res.status(400).json({ error: `Unsupported platform: ${platform}` });
    }

    // Store connection
    await msgDb.storePlatformConnection(userId, platform, {
      accessToken,
      refreshToken: null,
      expiresAt: null,
      metadata: {},
    });

    // Sync initial messages
    await syncMessengerPlatform(userId, platform, accessToken);

    res.json({ ok: true, platform, connected: true });
  } catch (err) {
    console.error('[API] POST /oauth-callback error:', err.message);
    res.status(500).json({ error: 'Failed to complete OAuth' });
  }
});

/**
 * POST /api/messaging/platforms/:platform/disconnect
 * Disconnect a messenger platform.
 */
router.post('/platforms/:platform/disconnect', requireAuth, async (req, res) => {
  try {
    const { platform } = req.params;
    const userId = req.user.id;

    await msgDb.removePlatformConnection(userId, platform);
    res.json({ ok: true, platform, connected: false });
  } catch (err) {
    console.error('[API] POST /platforms/:platform/disconnect error:', err.message);
    res.status(500).json({ error: 'Failed to disconnect platform' });
  }
});

/**
 * POST /api/messaging/platforms/:platform/sync
 * Manually sync messages from a platform.
 */
router.post('/platforms/:platform/sync', requireAuth, async (req, res) => {
  try {
    const { platform } = req.params;
    const userId = req.user.id;

    // Get platform connection
    const connection = await msgDb.getPlatformConnection(userId, platform);
    if (!connection) {
      return res.status(404).json({ error: `${platform} not connected` });
    }

    // Sync messages
    const syncResult = await syncMessengerPlatform(userId, platform, connection.access_token);

    res.json({ 
      ok: true, 
      platform, 
      messagesSynced: syncResult.count,
      lastSyncAt: new Date(),
    });
  } catch (err) {
    console.error('[API] POST /platforms/:platform/sync error:', err.message);
    res.status(500).json({ error: 'Failed to sync platform' });
  }
});

// ─── CALL TRACKING ─────────────────────────────────────────────────────────

/**
 * POST /api/messaging/contacts/:contactId/call
 * Log an incoming or outgoing call.
 * Body: { direction, duration?, timestamp? }
 */
router.post('/contacts/:contactId/call', requireAuth, async (req, res) => {
  try {
    const { contactId } = req.params;
    const userId = req.user.id;
    const { direction, duration, timestamp } = req.body;

    if (!direction || !['incoming', 'outgoing'].includes(direction)) {
      return res.status(400).json({ error: 'direction must be "incoming" or "outgoing"' });
    }

    const contact = await contactDb.getContact(contactId, userId);
    if (!contact) {
      return res.status(404).json({ error: 'Contact not found' });
    }

    const call = await contactDb.addCall({
      userId,
      contactId,
      direction,
      duration: duration || 0,
      timestamp: timestamp || new Date()
    });

    res.json({ call });
  } catch (err) {
    console.error('[API] POST /call error:', err.message);
    res.status(500).json({ error: 'Failed to log call' });
  }
});

/**
 * GET /api/messaging/contacts/:contactId/calls
 * Get call history for a contact.
 */
router.get('/contacts/:contactId/calls', requireAuth, async (req, res) => {
  try {
    const { contactId } = req.params;
    const userId = req.user.id;
    const { limit = 20 } = req.query;

    const contact = await contactDb.getContact(contactId, userId);
    if (!contact) {
      return res.status(404).json({ error: 'Contact not found' });
    }

    const calls = await contactDb.getCallHistory(userId, contactId, { limit: parseInt(limit) });
    res.json({ calls });
  } catch (err) {
    console.error('[API] GET /calls error:', err.message);
    res.status(500).json({ error: 'Failed to load calls' });
  }
});

// ─── SPAM DETECTION ────────────────────────────────────────────────────────

/**
 * POST /api/messaging/contacts/:contactId/spam
 * Flag a contact as spam.
 */
router.post('/contacts/:contactId/spam', requireAuth, async (req, res) => {
  try {
    const { contactId } = req.params;
    const userId = req.user.id;

    const contact = await contactDb.getContact(contactId, userId);
    if (!contact) {
      return res.status(404).json({ error: 'Contact not found' });
    }

    const updated = await contactDb.markAsSpam(contactId, userId);
    res.json({ contact: updated });
  } catch (err) {
    console.error('[API] POST /spam error:', err.message);
    res.status(500).json({ error: 'Failed to flag contact as spam' });
  }
});

/**
 * GET /api/messaging/spam-contacts
 * Get all flagged spam contacts for the user.
 */
router.get('/spam-contacts', requireAuth, async (req, res) => {
  try {
    const userId = req.user.id;
    const spamContacts = await contactDb.getSpamContacts(userId);
    res.json({ spamContacts });
  } catch (err) {
    console.error('[API] GET /spam-contacts error:', err.message);
    res.status(500).json({ error: 'Failed to load spam contacts' });
  }
});

// ─── SOBRIETY LOCK ─────────────────────────────────────────────────────────

/**
 * POST /api/messaging/sobriety-check
 * Check if a message has sobriety warning/lock indicators.
 * Body: { messageBody, timestamp? }
 */
router.post('/sobriety-check', requireAuth, async (req, res) => {
  try {
    const userId = req.user.id;
    const { messageBody, timestamp } = req.body;

    if (!messageBody) {
      return res.status(400).json({ error: 'messageBody required' });
    }

    // Use dummy contact ID 0 for general sobriety check
    const warning = await contactDb.detectDrunkTexting(
      userId,
      0,
      messageBody,
      timestamp || new Date()
    );

    res.json({ sobrietyWarning: warning });
  } catch (err) {
    console.error('[API] POST /sobriety-check error:', err.message);
    res.status(500).json({ error: 'Failed to check sobriety' });
  }
});

// ─── HELPERS ──────────────────────────────────────────────────────────────────

/**
 * Helper: Exchange OAuth code for access token (Facebook).
 */
async function exchangeFacebookToken(code) {
  // TODO: Implement Facebook token exchange
  // POST to https://graph.instagram.com/v18.0/oauth/access_token
  return 'mock-facebook-token';
}

/**
 * Helper: Exchange OAuth code for access token (Instagram).
 */
async function exchangeInstagramToken(code) {
  // TODO: Implement Instagram token exchange
  return 'mock-instagram-token';
}

/**
 * Helper: Exchange OAuth code for access token (WhatsApp).
 */
async function exchangeWhatsAppToken(code) {
  // TODO: Implement WhatsApp Business API token exchange
  return 'mock-whatsapp-token';
}

/**
 * Helper: Exchange OAuth code for access token (Telegram).
 */
async function exchangeTelegramToken(code) {
  // TODO: Implement Telegram Bot token exchange
  return 'mock-telegram-token';
}

/**
 * Helper: Sync messages from a messenger platform.
 */
async function syncMessengerPlatform(userId, platform, accessToken) {
  // TODO: Implement platform-specific sync logic
  // For now, return mock data
  return { count: 0, lastSyncAt: new Date() };
}

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
