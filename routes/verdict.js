/**
 * Verdict Route for HoldOff
 * Analyzes OUTGOING messages: "How will they receive this?"
 * Shows reader's perspective + user's condition anxiety about response
 */

const express = require('express');
const router = express.Router();
const { Anthropic } = require('@anthropic-ai/sdk');
const db = require('../db/messages');

const client = new Anthropic();

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

    const systemPrompt = `You are HoldOff's Verdict AI. Analyze OUTGOING messages for HOW THEY WILL BE RECEIVED.

TASK:
1. Read the message as the RECIPIENT would read it (neutral, objective perspective)
2. Assess: Is this message likely to be well-received? Will it escalate? Is it landing safely?
3. Then consider: User has [${conditionsList}] — what anxiety might they have about this recipient's response?
4. Determine safety level: GREEN (safe), YELLOW (caution), RED (high risk), SPIRAL (rapid-fire escalation detected)

ANALYSIS STRUCTURE:
- recipientRead: How will THEY likely read this? (1-2 sentences, neutral)
- userAnxiety: Given their conditions, what might they fear about the response? (1-2 sentences)
- safetyLevel: GREEN, YELLOW, RED, or SPIRAL
- reasoning: Explain the verdict (2-3 sentences)
- spiralLockout: If SPIRAL, milliseconds until cooldown ends (300000 = 5 min)

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

    // For compatibility with frontend
    verdict.analysis = `**How they'll read it:** ${verdict.recipientRead}\n\n**Your concern:** ${verdict.userAnxiety}`;

    res.json(verdict);
  } catch (error) {
    console.error('Verdict API error:', error);
    res.status(500).json({
      error: 'Could not analyze message',
      safetyLevel: 'yellow',
      analysis: 'Try rephrasing.',
    });
  }
});

module.exports = router;
