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
const { buildCompanionFallback } = require('../services/resilient-ai');
const { isCapabilityAvailable } = require('../config/dependency-policy');

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

    const OpenAI = require('openai');
    const client = new OpenAI({ apiKey: process.env.OPENAI_API_KEY });

    if (!isCapabilityAvailable('ai.openai')) {
      const fallback = buildCompanionFallback({ soul: prompt.soul.key, style: prompt.style.key, message });
      return res.json({
        ...fallback,
        soul: prompt.soul.key,
        style: prompt.style.key,
        styleLabel: prompt.style.label,
      });
    }

    const response = await client.chat.completions.create({
      model: 'gpt-4o-mini',
      max_tokens: 1024,
      messages: [
        { role: 'system', content: prompt.system },
        ...prompt.conversationMessages,
        { role: 'user', content: message },
      ],
    });

    const reply = response.choices[0].message.content;

    return res.json({
      reply,
      soul: prompt.soul.key,
      style: prompt.style.key,
      styleLabel: prompt.style.label,
    });
  } catch (error) {
    console.error('[companion] Error:', error.message);
    const fallback = buildCompanionFallback({ soul, style, message });
    return res.status(200).json({
      ...fallback,
      soul: soul || 'Sadie',
      style: style || null,
      styleLabel: style || null,
    });
  }
});

module.exports = router;
