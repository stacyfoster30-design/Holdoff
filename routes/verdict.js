/**
 * Verdict Route for HoldOff
 * Analyzes OUTGOING messages: "How will they receive this?"
 * Shows reader's perspective + user's condition anxiety about response
 */

const express = require('express');
const router = express.Router();
const { Anthropic } = require('@anthropic-ai/sdk');
const db = require('../db/messages');
const { getCookieTokens } = require('../lib/auth');
const { getVerdictHistory, getStreak } = require('../db/verdict-history');

const client = new Anthropic();

/** Require auth via JWT cookie — returns 401 if not logged in. */
function requireAuth(req, res, next) {
  const tokens = getCookieTokens(req);
  const payload = tokens.accessPayload || tokens.refreshPayload;
  if (!payload?.id) return res.status(401).json({ error: 'Not authenticated' });
  req.userId = payload.id;
  next();
}
router.post('/', async (req, res) => {
  try {
    const { outgoingMessage, userConditions, contactId, userId } = req.body;

    if (!outgoingMessage) {
      return res.status(400).json({ error: 'No message provided' });
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

    const response = await client.messages.create({
      model: 'claude-3-5-sonnet-20241022',
      max_tokens: 500,
      system: systemPrompt,
      messages: [
        {
          role: 'user',
          content: `User's message to send: "${outgoingMessage}"\n\nUser conditions: ${conditionsList}`,
        },
      ],
    });

    const content = response.content[0].type === 'text' ? response.content[0].text : '{}';

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

    res.json(verdict);
  } catch (error) {
    console.error('Verdict API error:', error.message || error);
    if (process.env.NODE_ENV === 'development') {
      res.status(500).json({
        error: 'Could not analyze message',
        details: error.message,
        safetyLevel: 'yellow',
        analysis: 'Try rephrasing.',
      });
    } else {
      res.status(500).json({
        error: 'Could not analyze message',
        safetyLevel: 'yellow',
        analysis: 'Try rephrasing.',
      });
    }
  }
});

/** GET /api/verdict/history — paginated verdict history for logged-in user. */
router.get('/history', requireAuth, async (req, res) => {
  try {
    const verdictType = req.query.type || null;
    const cursor = req.query.cursor || null;
    const limit = Math.min(parseInt(req.query.limit, 10) || 50, 200);
    const { entries, nextCursor, hasMore } = await getVerdictHistory(req.userId, { verdictType, cursor, limit });
    res.json({ verdicts: entries, nextCursor, hasMore });
  } catch (err) {
    console.error('[verdict/history] error:', err.message);
    res.status(500).json({ error: 'Failed to fetch history' });
  }
});

/** GET /api/verdict/streak — current streak + stats for logged-in user. */
router.get('/streak', requireAuth, async (req, res) => {
  try {
    const streak = await getStreak(req.userId);
    res.json(streak);
  } catch (err) {
    console.error('[verdict/streak] error:', err.message);
    res.status(500).json({ error: 'Failed to fetch streak' });
  }
});

module.exports = router;
