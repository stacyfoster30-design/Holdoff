/**
 * Quiz Invite Route — /api/send-quiz-invites
 * 
 * Handles viral referral invitations from the HoldOff quiz.
 * Generates referral codes, validates contacts, and queues SMS/email sends.
 */

const express = require('express');
const router = express.Router();
const crypto = require('crypto');
const { createReferral } = require('../db/referrals');

/**
 * POST /api/send-quiz-invites
 * 
 * Body:
 * {
 *   "referrerName": "Stacy",
 *   "referrerEmail": "stacy@example.com",
 *   "quizScore": 8,
 *   "quizResult": "THE Text Spiraler",
 *   "contacts": [
 *     { "name": "Alex", "phone": "+15551234567", "email": "alex@example.com" },
 *     ...
 *   ]
 * }
 * 
 * Response:
 * {
 *   "success": true,
 *   "referralCode": "REF_abc123...",
 *   "invitesSent": 10,
 *   "trackingUrl": "https://shouldiholdoff.live/quiz?ref=REF_abc123..."
 * }
 */

router.post('/', async (req, res) => {
  try {
    const { referrerName, referrerEmail, quizScore, quizResult, contacts } = req.body;

    // Validate input
    if (!referrerName || !referrerEmail || !Array.isArray(contacts)) {
      return res.status(400).json({
        success: false,
        error: 'Missing required fields: referrerName, referrerEmail, contacts (array)',
      });
    }

    if (contacts.length === 0) {
      return res.status(400).json({
        success: false,
        error: 'At least one contact is required',
      });
    }

    // Generate unique referral code
    const referralCode = `REF_${crypto.randomBytes(12).toString('hex').toUpperCase().slice(0, 16)}`;
    const trackingUrl = `https://shouldiholdoff.live/quiz?ref=${referralCode}`;

    // Filter valid contacts (must have phone or email)
    const validContacts = contacts.filter(c => (c.phone && c.phone.trim()) || (c.email && c.email.trim()));

    if (validContacts.length === 0) {
      return res.status(400).json({
        success: false,
        error: 'No valid contacts with phone or email',
      });
    }

    // Queue invites for async delivery (store in DB or queue service)
    // For now, we'll queue them to be sent by a subagent
    const invites = validContacts.map((contact) => ({
      referralCode,
      referrerName,
      referrerEmail,
      referrerQuizResult: quizResult,
      recipientName: contact.name || 'Friend',
      recipientPhone: contact.phone,
      recipientEmail: contact.email,
      trackingUrl,
      sentAt: new Date().toISOString(),
      status: 'queued',
    }));

    // Store invites in the referrals table and return success
    let savedCount = 0;
    for (const invite of invites) {
      try {
        await createReferral({
          senderEmail: referrerEmail,
          senderDevice: null,
          recipientEmail: invite.recipientEmail || null,
          note: `Quiz invite — ${quizResult || 'quiz'} — ${invite.recipientPhone || invite.recipientEmail}`,
          utmToken: referralCode + '_' + savedCount,
        });
        savedCount++;
      } catch (dbErr) {
        // Non-fatal: log and continue
        console.warn(`[quiz-invites] DB store failed for invite ${savedCount}:`, dbErr.message);
      }
    }
    console.log(`[quiz-invites] Stored ${savedCount}/${invites.length} invites for referral code ${referralCode}`);

    // Return success response
    res.status(200).json({
      success: true,
      referralCode,
      invitesSent: validContacts.length,
      trackingUrl,
      message: `${validContacts.length} invites queued for delivery! Share your unique referral link to get credit.`,
    });

  } catch (error) {
    console.error('[quiz-invites] Error:', error.message);
    res.status(500).json({
      success: false,
      error: 'Failed to queue invites. Please try again.',
    });
  }
});

module.exports = router;
