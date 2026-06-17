/**
 * Contact Insights Route for HoldOff
 * Fetches relationship profile, flags, compatibility, and trends for a contact
 */

const express = require('express');
const router = express.Router();
const { requireAuth } = require('../lib/auth');
const db = require('../db/messages');

/**
 * GET /api/contact-insights/:contactId
 * Fetch full insights profile for a contact
 */
router.get('/:contactId', requireAuth, async (req, res) => {
  try {
    const { contactId } = req.params;
    const userId = req.user.id;

    // Verify contact belongs to this user
    const contact = await db.getContactById(contactId);
    if (!contact) {
      return res.status(404).json({ error: 'Contact not found' });
    }

    // Fetch insights
    const insights = await db.getContactInsights(contactId);

    if (!insights) {
      // Return empty profile if no insights yet
      return res.json({
        contactId,
        contact: {
          id: contact.id,
          name: contact.name,
          phoneNumber: contact.phone_number,
        },
        insights: {
          redFlags: [],
          yellowFlags: [],
          greenFlags: [],
          riskLevel: 'Not yet analyzed',
          trustLevel: 'Unknown',
          attachmentStyleFit: null,
          communicationStyleMatch: 0,
          compatibilityScore: 0,
          analysisCount: 0,
          updatedAt: null,
        },
      });
    }

    // Parse JSON fields
    res.json({
      contactId,
      contact: {
        id: contact.id,
        name: contact.name,
        phoneNumber: contact.phone_number,
      },
      insights: {
        redFlags: JSON.parse(insights.red_flags || '[]'),
        yellowFlags: JSON.parse(insights.yellow_flags || '[]'),
        greenFlags: JSON.parse(insights.green_flags || '[]'),
        riskLevel: insights.risk_level,
        trustLevel: insights.trust_level,
        attachmentStyleFit: insights.attachment_style_fit,
        communicationStyleMatch: insights.communication_style_match,
        compatibilityScore: insights.compatibility_score,
        lastAnalyzedMessage: insights.last_analyzed_message,
        analysisCount: insights.analysis_count,
        updatedAt: insights.updated_at,
      },
    });
  } catch (err) {
    console.error('[API /contact-insights] Error:', err.message);
    res.status(500).json({ error: 'Failed to fetch insights', details: err.message });
  }
});

/**
 * POST /api/contact-insights/:contactId
 * Store/update insights for a contact after interpretation
 */
router.post('/:contactId', requireAuth, async (req, res) => {
  try {
    const { contactId } = req.params;
    const insights = req.body;

    // Verify contact exists
    const contact = await db.getContactById(contactId);
    if (!contact) {
      return res.status(404).json({ error: 'Contact not found' });
    }

    // Store insights
    const updated = await db.upsertContactInsights(contactId, insights);

    res.json({
      contactId,
      insights: {
        redFlags: JSON.parse(updated.red_flags || '[]'),
        yellowFlags: JSON.parse(updated.yellow_flags || '[]'),
        greenFlags: JSON.parse(updated.green_flags || '[]'),
        riskLevel: updated.risk_level,
        trustLevel: updated.trust_level,
        attachmentStyleFit: updated.attachment_style_fit,
        communicationStyleMatch: updated.communication_style_match,
        compatibilityScore: updated.compatibility_score,
        analysisCount: updated.analysis_count,
        updatedAt: updated.updated_at,
      },
    });
  } catch (err) {
    console.error('[API /contact-insights POST] Error:', err.message);
    res.status(500).json({ error: 'Failed to store insights', details: err.message });
  }
});

module.exports = router;
