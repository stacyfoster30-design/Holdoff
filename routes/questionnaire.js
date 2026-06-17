/**
 * Questionnaire Route for HoldOff
 * Handles comprehensive conditions & wellness assessment
 */

const express = require('express');
const router = express.Router();
const db = require('../db/messages');

/**
 * GET /api/questionnaire
 * Serve the questionnaire HTML
 */
router.get('/', (req, res) => {
  res.sendFile('/tasklet/agent/home/conditions-questionnaire.html');
});

/**
 * POST /api/questionnaire/submit
 * Store user conditions and attachment style
 * 
 * Body: { userId, conditions: [], attachment_style }
 */
router.post('/submit', async (req, res) => {
  try {
    const { userId, conditions = [], attachment_style } = req.body;

    if (!userId) {
      return res.status(400).json({ error: 'userId required' });
    }

    // Validate conditions array
    const validConditions = ['rsd', 'anxiety', 'depression', 'addiction', 'trauma', 'autism', 'adhd'];
    const sanitizedConditions = conditions.filter(c => validConditions.includes(c));

    // Store each condition separately
    for (const condition of sanitizedConditions) {
      await db.addUserCondition(userId, condition);
    }

    // Store attachment style if provided
    if (attachment_style) {
      const validAttachmentStyles = ['secure', 'anxious', 'avoidant', 'fearful-avoidant'];
      if (validAttachmentStyles.includes(attachment_style)) {
        // Update user profile with attachment style (future: add to user table)
        console.log(`Attachment style for user ${userId}: ${attachment_style}`);
      }
    }

    res.json({
      success: true,
      message: 'Conditions saved successfully',
      conditions: sanitizedConditions,
      attachment_style
    });
  } catch (err) {
    console.error('Error submitting questionnaire:', err);
    res.status(500).json({ error: 'Failed to save conditions' });
  }
});

/**
 * GET /api/questionnaire/user-conditions/:userId
 * Get user's selected conditions
 */
router.get('/user-conditions/:userId', async (req, res) => {
  try {
    const { userId } = req.params;
    const conditions = await db.getUserConditions(userId);
    
    res.json({
      userId,
      conditions,
      count: conditions.length
    });
  } catch (err) {
    console.error('Error fetching user conditions:', err);
    res.status(500).json({ error: 'Failed to fetch conditions' });
  }
});

module.exports = router;