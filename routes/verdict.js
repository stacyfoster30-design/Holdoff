/**
 * Verdict Route for HoldOff
 * - POST /api/verdict: outgoing-message recipient-read analysis (legacy clients)
 * - GET /api/verdict/history: paginated verdict history for the logged-in user
 * - GET /api/verdict/streak: current streak + total verdict count
 * - GET /api/verdict/count/:userId: total verdict count for the logged-in user
 */

const express = require('express');
const router = express.Router();
const db = require('../db/messages');
const { requireAuth } = require('../lib/auth');
const {
  getVerdictHistory,
  getStreak,
  getTotalVerdictCount,
} = require('../db/verdict-history');
const { validateHistoryQuery } = require('../lib/request-validators');
const { callAI } = require('../services/ai-provider');
const { buildOutgoingVerdictFallback } = require('../services/resilient-ai');

const VALID_SAFETY_LEVELS = new Set(['green', 'yellow', 'red', 'spiral']);
const VALID_ATTACHMENT_PATTERNS = new Set(['ANX', 'AVO', 'FA', 'SEC']);

function buildLegacyAnalysis(verdict) {
  return `**How they'll read it:** ${verdict.recipientRead}\n\n**Your concern:** ${verdict.userAnxiety}`;
}

function normalizeOutgoingVerdict(verdict) {
  const normalized = {
    ...verdict,
    safetyLevel: VALID_SAFETY_LEVELS.has(String(verdict?.safetyLevel || '').toLowerCase())
      ? String(verdict.safetyLevel).toLowerCase()
      : 'yellow',
    attachmentPattern: VALID_ATTACHMENT_PATTERNS.has(verdict?.attachmentPattern)
      ? verdict.attachmentPattern
      : 'SEC',
  };

  normalized.analysis = buildLegacyAnalysis(normalized);

  let themeCode = normalized.attachmentPattern;
  if (normalized.emotionalState === 'ANGRY') {
    themeCode = 'ANGRY';
  } else if (normalized.emotionalState === 'RISKY') {
    themeCode = 'RISKY';
  }
  normalized.themeCode = themeCode || 'SEC';

  return normalized;
}

function buildHistoryResponse(result) {
  return {
    ...result,
    entries: result.entries || [],
    verdicts: result.entries || [],
  };
}

function isDatabaseUnavailable(err) {
  return err?.code === 'DATABASE_UNAVAILABLE';
}

router.post('/', async (req, res) => {
  try {
    const { outgoingMessage, userConditions, userId } = req.body || {};
    const normalizedMessage = typeof outgoingMessage === 'string' ? outgoingMessage.trim() : '';

    if (!normalizedMessage) {
      return res.status(400).json({ error: 'outgoingMessage is required' });
    }

    let conditions = Array.isArray(userConditions) ? userConditions.filter(Boolean) : null;
    if ((!conditions || conditions.length === 0) && userId) {
      try {
        conditions = await db.getUserConditions(userId);
      } catch (err) {
        console.error('Error fetching user conditions:', err.message || err);
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
      userContent: `User's message to send: "${normalizedMessage}"\n\nUser conditions: ${conditionsList}`,
      maxTokens: 500,
    });

    if (!aiResult?.content) {
      return res.json(normalizeOutgoingVerdict(buildOutgoingVerdictFallback(normalizedMessage)));
    }

    let parsed;
    try {
      parsed = JSON.parse(aiResult.content);
    } catch (err) {
      console.error('Failed to parse verdict response:', aiResult.content);
      parsed = {
        recipientRead: 'Unable to analyze',
        userAnxiety: 'Try rephrasing',
        safetyLevel: 'yellow',
        attachmentPattern: 'SEC',
        emotionalState: null,
        reasoning: 'Could not fully process this message.',
        spiralLockout: 0,
      };
    }

    return res.json(normalizeOutgoingVerdict(parsed));
  } catch (error) {
    console.error('Verdict API error:', error.message || error);
    return res.status(200).json(
      normalizeOutgoingVerdict(buildOutgoingVerdictFallback(req.body?.outgoingMessage || ''))
    );
  }
});

router.get('/history', validateHistoryQuery, requireAuth, async (req, res) => {
  try {
    const verdictType = req.query.verdict_type || req.query.verdictType || req.query.type || null;
    const result = await getVerdictHistory(req.user.id, {
      verdictType,
      cursor: req.query.cursor || null,
      limit: req.query.limit ? parseInt(req.query.limit, 10) : 50,
    });
    res.json(buildHistoryResponse(result));
  } catch (err) {
    if (isDatabaseUnavailable(err)) {
      return res.json(buildHistoryResponse({ entries: [], nextCursor: null, hasMore: false }));
    }
    console.error('[verdict/history] error:', err.message);
    res.status(500).json({ error: 'Failed to fetch verdict history' });
  }
});

router.get('/streak', requireAuth, async (req, res) => {
  try {
    const streak = await getStreak(req.user.id);
    res.json(streak || { currentStreak: 0, longestStreak: 0, totalVerdicts: 0, lastVerdictAt: null });
  } catch (err) {
    if (isDatabaseUnavailable(err)) {
      return res.json({ currentStreak: 0, longestStreak: 0, totalVerdicts: 0, lastVerdictAt: null });
    }
    console.error('[verdict/streak] error:', err.message);
    res.status(500).json({ error: 'Failed to fetch streak' });
  }
});

router.get('/count/:userId', requireAuth, async (req, res) => {
  try {
    if (String(req.user.id) !== String(req.params.userId)) {
      return res.status(403).json({ error: 'Forbidden' });
    }
    const count = await getTotalVerdictCount(req.user.id);
    res.json({ count });
  } catch (err) {
    if (isDatabaseUnavailable(err)) {
      return res.json({ count: 0 });
    }
    console.error('[verdict/count] error:', err.message);
    res.status(500).json({ error: 'Failed to fetch verdict count' });
  }
});

module.exports = router;
