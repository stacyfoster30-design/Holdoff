/**
 * Verdict Route for HoldOff
 * Analyzes OUTGOING messages: "How will they receive this?"
 * Shows reader's perspective + user's condition anxiety about response
 */

const express = require('express');
const router = express.Router();
const db = require('../db/messages');
const { requireAuth } = require('../lib/auth');
const { getVerdictHistory, getStreak } = require('../db/verdict-history');
const { callAI } = require('../services/ai-provider');
const { buildOutgoingVerdictFallback } = require('../services/resilient-ai');

router.post('/', async (req, res) => {
  try {
    const { outgoingMessage, message_text, userConditions, contactId, userId } = req.body || {};
    const message = typeof outgoingMessage === 'string' ? outgoingMessage : message_text;

    if (message === undefined || message === null) {
      return res.status(400).json({ error: 'message_text is required' });
    }
    if (!String(message).trim()) {
      return res.status(400).json({ error: 'message_text cannot be empty' });
    }

    // Auto-fetch user conditions from DB if not provided
    let conditions = userConditions;
    if (!conditions && userId) {
      try {
        conditions = await db.getUserConditions(userId);
      } catch (err) {
        console.error('Error fetching user conditions:', err);
        conditions = [];
      }
    }

    const conditionsList = conditions && conditions.length > 0
      ? conditions.join(', ')
      : 'None specified';

    const systemPrompt = `You are HoldOff's Verdict AI. Analyze OUTGOING messages for HOW THEY WILL BE RECEIVED + emotional attachment pattern.

TASK:
1. Read the message as the RECIPIENT would read it (neutral, objective perspective)
2. Assess: Is this message likely to be well-received? Will it escalate? Is it landing safely?
3. Then consider: User has [${conditionsList}] — what anxiety might they have about this recipient's response?
4. Detect emotional pattern: Is the user showing ANXIOUS (over-explaining, seeking validation), AVOIDANT (dismissive, cold, shutting down), FEARFUL (hot-cold whiplash, contradictory), or SECURE (clear, grounded) communication?
5. Check for ANGRY sentiment (hostile, aggressive, rage-tinged language)
6. Check for RISKY behavior (reckless, dangerous, impulsive tone)

ANALYSIS STRUCTURE:
- recipientRead: How will THEY likely read this? (1-2 sentences, neutral)
- userAnxiety: Given their conditions, what might they fear about the response? (1-2 sentences)
- safetyLevel: GREEN (safe), YELLOW (caution), RED (high risk), SPIRAL (rapid-fire escalation detected)
- attachmentPattern: ANX (Anxious-Preoccupied), AVO (Avoidant-Dismissive), FA (Fearful-Avoidant), or SEC (Secure)
- emotionalState: ANGRY, RISKY, or null if not dominant
- reasoning: Explain the verdict (2-3 sentences)
- spiralLockout: If SPIRAL, milliseconds until cooldown ends (300000 = 5 min)

ATTACHMENT PATTERN SIGNALS:
- ANX: Excessive explanation, apologizing, "are you mad", seeking reassurance, long justifications
- AVO: One-word responses, going silent, dismissive tone, "whatever", stonewalling
- FA: Hot-cold whiplash, contradictory messages, "nevermind", immediate undo attempts
- SEC: Clear, direct, grounded, appropriate vulnerability

SPIRAL DETECTION: If user sent 3+ messages to same contact in <2 min OR message contains excessive punctuation/caps or repeats same idea, SPIRAL.

Return ONLY valid JSON.`;

    const aiResult = await callAI({
      systemPrompt,
      userContent: `User's message to send: "${message}"\n\nUser conditions: ${conditionsList}`,
      maxTokens: 500
    });

    if (!aiResult) {
      const verdict = normalizeVerdictResponse(buildOutgoingVerdictFallback(message));
      verdict.analysis = `**How they'll read it:** ${verdict.recipientRead}\n\n**Your concern:** ${verdict.userAnxiety}`;
      verdict.themeCode = verdict.attachmentPattern || 'SEC';
      return res.json(verdict);
    }

    const content = aiResult.content;

    // Parse JSON from response
    let verdict;
    try {
      verdict = JSON.parse(content);
    } catch (e) {
      console.error('Failed to parse verdict response:', content);
      verdict = {
        recipientRead: 'Unable to analyze',
        userAnxiety: 'Try rephrasing',
        safetyLevel: 'yellow',
        reasoning: 'Could not fully process this message.',
        spiralLockout: 0,
      };
    }

    // Default to yellow if no level provided
    if (!['green', 'yellow', 'red', 'spiral'].includes(verdict.safetyLevel)) {
      verdict.safetyLevel = 'yellow';
    }

    // Default to SEC if no attachment pattern provided
    if (!['ANX', 'AVO', 'FA', 'SEC'].includes(verdict.attachmentPattern)) {
      verdict.attachmentPattern = 'SEC';
    }

    // For compatibility with frontend
    verdict.analysis = `**How they'll read it:** ${verdict.recipientRead}\n\n**Your concern:** ${verdict.userAnxiety}`;
    
    // Determine theme code (emotionalState takes priority over attachmentPattern)
    let themeCode = verdict.attachmentPattern || 'SEC';
    if (verdict.emotionalState === 'ANGRY') {
      themeCode = 'ANGRY';
    } else if (verdict.emotionalState === 'RISKY') {
      themeCode = 'RISKY';
    }
    verdict.themeCode = themeCode;

    res.json(normalizeVerdictResponse(verdict));
  } catch (error) {
    console.error('Verdict API error:', error.message || error);
    const verdict = normalizeVerdictResponse(buildOutgoingVerdictFallback(req.body?.outgoingMessage || req.body?.message_text || ''));
    verdict.analysis = `**How they'll read it:** ${verdict.recipientRead}\n\n**Your concern:** ${verdict.userAnxiety}`;
    verdict.themeCode = verdict.attachmentPattern || 'SEC';
    res.status(200).json(verdict);
  }
});

function normalizeVerdictResponse(verdict) {
  const safetyLevel = String(verdict.safetyLevel || '').toLowerCase();
  const normalized = {
    ...verdict,
    verdict: verdict.verdict || (safetyLevel === 'green' ? 'SEND' : safetyLevel === 'yellow' ? 'REWRITE' : 'HOLD'),
    pattern: verdict.pattern || verdict.attachmentPattern || 'SEC',
    feedback_text: verdict.feedback_text || verdict.reasoning || verdict.recipientRead || 'Pause and review before sending.',
  };
  if (!['SEND', 'HOLD', 'REWRITE'].includes(normalized.verdict)) {
    normalized.verdict = 'HOLD';
  }
  return normalized;
}

function validateHistoryQuery(req, res, next) {
  const verdictType = req.query.verdict_type || req.query.verdictType || req.query.type;
  if (verdictType && !['SEND', 'HOLD', 'REWRITE'].includes(String(verdictType).toUpperCase())) {
    return res.status(400).json({ error: 'Invalid verdict_type' });
  }
  req.verdictType = verdictType ? String(verdictType).toUpperCase() : null;
  next();
}

/** GET /api/verdict/streak — current hold streak and total verdict count. */
router.get('/streak', requireAuth, async (req, res) => {
  try {
    const streak = await getStreak(req.userId || req.user?.id);
    res.json(streak || { currentStreak: 0, longestStreak: 0, totalVerdicts: 0 });
  } catch (err) {
    console.error('[verdict/streak]', err.message);
    res.status(500).json({ error: 'Failed to fetch streak.' });
  }
});

/** GET /api/verdict/history — paginated verdict history for logged-in user. */
router.get('/history', validateHistoryQuery, requireAuth, async (req, res) => {
  try {
    const { cursor, limit } = req.query;
    const entries = await getVerdictHistory(req.userId || req.user?.id, {
      verdictType: req.verdictType,
      cursor: cursor || null,
      limit: limit ? parseInt(limit, 10) : 50,
    });
    res.json(entries);
  } catch (err) {
    console.error('[verdict/history]', err.message);
    res.status(500).json({ error: 'Failed to fetch history.' });
  }
});

module.exports = router;
