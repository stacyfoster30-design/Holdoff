/**
 * AI Companion chat route
 * Handles conversations with Stacy (fearful-avoidant) and Danny (avoidant-dismissive/discovering/anxious/secure)
 */
const express = require('express');
const router = express.Router();
const { verifyToken } = require('../lib/auth');
const { buildCompanionPrompt, getCharacterDefinition } = require('../lib/companion-ai');

// POST /api/companion/chat — chat with an AI character
router.post('/chat', verifyToken, async (req, res) => {
  const user = req.user;
  if (!user) {
    return res.status(401).json({ error: 'Not authenticated' });
  }

  const { characterName, message, conversationHistory } = req.body;

  if (!characterName || !message) {
    return res.status(400).json({ error: 'Missing characterName or message' });
  }

  const selectedCharacter = getCharacterDefinition(characterName);
  if (!selectedCharacter) {
    return res.status(400).json({ error: 'Invalid character name' });
  }

  try {
    // Build personalized prompt with character personality + user context
    const prompt = await buildCompanionPrompt(
      characterName,
      message,
      conversationHistory || [],
      user
    );

    // Call Claude to generate response
    const Anthropic = require('@anthropic-ai/sdk');
    const client = new Anthropic({
      apiKey: process.env.ANTHROPIC_API_KEY,
    });

    const response = await client.messages.create({
      model: 'claude-3-5-sonnet-20241022',
      max_tokens: 1024,
      system: prompt.system,
      messages: [
        ...prompt.conversationMessages,
        {
          role: 'user',
          content: message,
        },
      ],
    });

    const reply = response.content[0].type === 'text' ? response.content[0].text : '';

    return res.json({ reply });
  } catch (error) {
    console.error('[companion] Error:', error.message);
    return res.status(500).json({
      error: 'Failed to generate response',
      message: error.message,
    });
  }
});

module.exports = router;
