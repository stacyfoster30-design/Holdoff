/**
 * AI Companion chat route
 *
 * Two souls (Sadie, Dan) × four canonical attachment styles
 * (secure, anxious, dismissive_avoidant, fearful_avoidant).
 * Same style set for both souls, soul-specific overlays.
 */
const express = require('express');
const router = express.Router();
const { verifyToken } = require('../lib/auth');
const {
  buildCompanionPrompt,
  listSouls,
  listAttachmentStyles,
  listCompanionVariants,
  STYLE_ORDER,
} = require('../lib/companion-ai');

// Legacy → canonical
function canonicalSoul(name) {
  if (!name) return null;
  const n = String(name).trim();
  if (n === 'Stacy') return 'Sadie';
  if (n === 'Danny') return 'Dan';
  if (n === 'Sadie' || n === 'Dan') return n;
  return null;
}

// GET /api/companion/souls — list available souls
router.get('/souls', (_req, res) => {
  res.json({ souls: listSouls() });
});

// GET /api/companion/styles?soul=Sadie — list the 4 styles with soul-specific blurbs
router.get('/styles', (req, res) => {
  const soul = canonicalSoul(req.query.soul);
  res.json({ soul, styles: listAttachmentStyles(soul) });
});

// GET /api/companion/variants — all 8 (4 styles × 2 souls)
router.get('/variants', (_req, res) => {
  res.json({ variants: listCompanionVariants() });
});

// POST /api/companion/chat — chat with a soul in a chosen attachment style
router.post('/chat', verifyToken, async (req, res) => {
  const user = req.user;
  if (!user) {
    return res.status(401).json({ error: 'Not authenticated' });
  }

  const { characterName, soulName, message, conversationHistory, attachmentStyle } = req.body;

  const soul = canonicalSoul(soulName || characterName);
  if (!soul) {
    return res.status(400).json({ error: 'Invalid soul (use Sadie or Dan)' });
  }
  if (!message) {
    return res.status(400).json({ error: 'Missing message' });
  }

  // If a style was provided, make sure it's one of the canonical 4.
  let style = attachmentStyle;
  if (style && !STYLE_ORDER.includes(style)) {
    style = undefined;
  }

  try {
    const prompt = await buildCompanionPrompt(
      soul,
      message,
      conversationHistory || [],
      user,
      { attachmentStyle: style }
    );

    const Anthropic = require('@anthropic-ai/sdk');
    const client = new Anthropic({ apiKey: process.env.ANTHROPIC_API_KEY });

    const response = await client.messages.create({
      model: 'claude-sonnet-4-20250514',
      max_tokens: 1024,
      system: prompt.system,
      messages: [
        ...prompt.conversationMessages,
        { role: 'user', content: message },
      ],
    });

    const reply = response.content[0].type === 'text' ? response.content[0].text : '';

    return res.json({
      reply,
      soul: prompt.soul.key,
      style: prompt.style.key,
      styleLabel: prompt.style.label,
    });
  } catch (error) {
    console.error('[companion] Error:', error.message);
    return res.status(500).json({
      error: 'Failed to generate response',
      message: error.message,
    });
  }
});

module.exports = router;
