/**
 * Verdict API routes — HoldOff core verdict endpoint.
 *
 * POST /api/verdict  — requireAuth optional (anonymous allowed, limited)
 * GET  /api/verdict/history — requireAuth, paginated
 * GET  /api/verdict/streak  — requireAuth, streak + risk flag
 */
const express = require('express');
const crypto = require('crypto');
const router = express.Router();
const { requireAuth } = require('../lib/auth');
const { verifyToken, getCookieTokens } = require('../lib/auth');
const { validateVerdictRequest, validateHistoryQuery } = require('../lib/request-validators');
const {
  callWithFallback,
  HANDLER_HARD_TIMEOUT_MS,
  STATIC_HOLD,
  parseCookies,
  extractProInfo,
  getVerdictCount,
  normalizeRegulatedBoundaryVerdict,
  regulatedPauseBoundaryResponse,
} = require('../lib/verdict-ai');
const { getVerdictHistory, getStreak, recordVerdict } = require('../db/verdict-history');
const { getAttachmentProfile } = require('../db/quiz');
const { logVerdictCall } = require('../db/healthchecks');
const { isProEmail } = require('../db/subscriptions');
const { setPaywallHitAt, updateUserStreak } = require('../db/users');
const { logVerdictWithContext } = require('../db/verdict-logs');

const FREE_VERDICT_LIMIT = 3;

// ── POST /api/verdict ──────────────────────────────────────────────────────────
router.post('/', validateVerdictRequest, async (req, res) => {
  const reqId = crypto.randomBytes(4).toString('hex');
  const t0 = Date.now();
  const log = (phase, extra = '') =>
    console.log(`[verdict] reqId=${reqId} phase=${phase} elapsed=${Date.now() - t0}ms${extra ? ' ' + extra : ''}`);

  log('received');

  let raw, source;

  try {
    const { message_text, user_id } = req.body || {};

    // --- Entitlement check ---
    const cookies = parseCookies(req.headers.cookie);
    const authHeader = req.headers.authorization;
    let isLoggedIn = false;
    let membership = null;

    let jwtPayload = null;
    let attachmentProfile = null;
    if (authHeader?.startsWith('Bearer ')) {
      jwtPayload = verifyToken(authHeader.slice(7));
    }
    if (!jwtPayload) {
      const tokens = getCookieTokens(req);
      jwtPayload = tokens.accessPayload || tokens.refreshPayload;
    }
    if (jwtPayload?.id) {
      isLoggedIn = true;
    }

    if (!isLoggedIn) {
      const proInfo = extractProInfo(cookies);
      if (proInfo?.email) {
        const isActive = await isProEmail(proInfo.email).catch(() => false);
        if (isActive) {
          membership = proInfo.membership || 'online';
        }
      }
    }

    if (!isLoggedIn && !membership) {
      const count = getVerdictCount(cookies);
      const loggedInUser = jwtPayload?.id;
      const freeLimit = loggedInUser ? 5 : FREE_VERDICT_LIMIT;
      if (count >= freeLimit) {
        log('paywall_hit', `count=${count} limit=${freeLimit} loggedIn=${!!loggedInUser}`);
        if (loggedInUser) {
          setPaywallHitAt(loggedInUser).catch(() => {});
        }
        return res.status(402).json({
          error: 'free_limit_reached',
          verdicts_used: count,
          limit: freeLimit,
          membership_tier: null,
        });
      }
    }

    // --- Quiz gate: logged-in users must complete the quiz before getting verdicts ---
    if (isLoggedIn && jwtPayload?.id) {
      attachmentProfile = await getAttachmentProfile(jwtPayload.id).catch(() => null);
      if (!attachmentProfile || attachmentProfile.quiz_completed_at === null) {
        log('quiz_required', `userId=${jwtPayload.id}`);
        return res.status(403).json({
          error: 'quiz_required',
          message: 'Complete the attachment style quiz before using HoldOff.',
        });
      }
    }

    // --- Attachment profile lookup for style-aware verdicts ---
    // Already fetched above during the quiz gate check — reuse it
    if (!attachmentProfile && jwtPayload?.id) {
      attachmentProfile = await getAttachmentProfile(jwtPayload.id).catch(() => null);
    }

    // Deterministic quality guard: healthy pause/boundary language should be SEND even if AI keys are down.
    const regulatedBoundary = regulatedPauseBoundaryResponse(message_text);
    if (regulatedBoundary) {
      const latencyMs = Date.now() - t0;
      logVerdictCall({ verdictSource: 'rules', verdict: 'SEND', latencyMs }).catch(() => {});
      log('regulated_boundary_send');
      return res.status(200).json(regulatedBoundary);
    }

    const userContent = `Message I'm about to send:\n${message_text}`;
    log('model_call_started');

    // Hard timeout: race callWithFallback against a timer.
    let callResult;
    try {
      callResult = await Promise.race([
        callWithFallback(require('../lib/verdict-ai').SYSTEM_PROMPT, userContent, log, attachmentProfile),
        new Promise((_, reject) => setTimeout(() => {
          const err = new Error('Handler hard timeout');
          err._hardTimeout = true;
          reject(err);
        }, HANDLER_HARD_TIMEOUT_MS)),
      ]);
    } catch (err) {
      log('hard_timeout_or_error', `msg=${err.message} hardTimeout=${!!err._hardTimeout}`);
      callResult = { raw: null, source: 'fallback' };
    }

    raw = callResult.raw;
    source = callResult.source;
    const latencyMs = Date.now() - t0;

    // Static HOLD fallback — fires when all AI paths fail OR hard timeout triggers
    if (source === 'fallback') {
      log('fallback_response', `latency=${latencyMs}ms`);
      logVerdictCall({ verdictSource: 'fallback', verdict: 'HOLD', latencyMs, errorMessage: 'All AI paths failed' }).catch(() => {});
      return res.status(200).json({
        ...STATIC_HOLD,
        confidence: 0,
      });
    }

    log('model_call_returned', `source=${source}`);

    let parsed;
    try {
      let cleanRaw = (raw || '').trim();
      if (cleanRaw.startsWith('```')) {
        cleanRaw = cleanRaw.replace(/^```(?:json)?\s*\n?/, '').replace(/\n?```\s*$/, '').trim();
      }
      parsed = JSON.parse(cleanRaw);
    } catch (_) {
      console.error(`[verdict] reqId=${reqId} JSON parse failed, rawLen=${(raw || '').length}`);
      logVerdictCall({ verdictSource: source, verdict: null, latencyMs, errorMessage: 'JSON parse failed' }).catch(() => {});
      return res.status(500).json({ error: 'Analysis failed. Try again.' });
    }

    // Validate required fields
    const required = ['verdict', 'pattern', 'whats_happening', 'grounded_voice', 'rewrite'];
    for (const field of required) {
      if (!parsed[field]) {
        console.error(`[verdict] reqId=${reqId} Missing field: ${field}`);
        logVerdictCall({ verdictSource: source, verdict: null, latencyMs, errorMessage: `Missing field: ${field}` }).catch(() => {});
        return res.status(500).json({ error: 'Analysis incomplete. Try again.' });
      }
    }

    if (!['SEND', 'HOLD', 'REWRITE'].includes(parsed.verdict)) {
      parsed.verdict = 'HOLD';
    }

    parsed = normalizeRegulatedBoundaryVerdict(parsed, message_text);

    // Fire-and-forget verdict log
    logVerdictCall({ verdictSource: source, verdict: parsed.verdict, latencyMs }).catch(() => {});

    // Record verdict history + pattern journal streak — fire-and-forget, don't block response
    if (jwtPayload?.id && parsed.verdict) {
      recordVerdict({
        userId: jwtPayload.id,
        verdict: parsed.verdict,
        patternName: parsed.pattern || null,
        feedbackSnippet: parsed.whats_happening || null,
        attachmentStyle: parsed.attachment_style || null,
        source: 'verdict',
      }).catch((err) => {
        console.error('[verdict] recordVerdict error:', err.message);
      });

      // Pattern journal streak — increments on next-day check-in, resets after 48h gap
      updateUserStreak(jwtPayload.id).catch((err) => {
        console.error('[verdict] updateUserStreak error:', err.message);
      });

      // Enriched verdict log for pattern journal analytics
      logVerdictWithContext({
        userId: jwtPayload.id,
        messageLength: message_text.length,
        attachmentStyleSnapshot: parsed.attachment_style || null,
      }).catch((err) => {
        console.error('[verdict] logVerdictWithContext error:', err.message);
      });
    }

    // Increment free verdict counter for anonymous users
    if (!isLoggedIn && !membership) {
      const newCount = getVerdictCount(cookies) + 1;
      res.cookie('hf_vc', String(newCount), {
        httpOnly: true,
        secure: process.env.NODE_ENV === 'production',
        sameSite: 'lax',
        maxAge: 365 * 24 * 60 * 60 * 1000,
      });
      parsed.verdicts_used = newCount;
      const freeLimit = isLoggedIn ? 5 : FREE_VERDICT_LIMIT;
      parsed.verdicts_remaining = Math.max(0, freeLimit - newCount);
    } else {
      parsed.unlimited = true;
      parsed.membership = membership;
    }

    parsed.verdict_source = source;
    parsed.confidence = parsed.confidence || 0.9;

    log('response_sent', `verdict=${parsed.verdict} source=${source}`);
    return res.status(200).json(parsed);

  } catch (fatalErr) {
    const latencyMs = Date.now() - t0;
    console.error(`[verdict] reqId=${reqId} FATAL UNCAUGHT: ${fatalErr.message}`);
    logVerdictCall({ verdictSource: 'fallback', verdict: 'HOLD', latencyMs, errorMessage: `Fatal: ${fatalErr.message}` }).catch(() => {});
    if (!res.headersSent) {
      return res.status(200).json({
        ...STATIC_HOLD,
        confidence: 0,
      });
    }
  }
});

// ── GET /api/verdict/history ───────────────────────────────────────────────────
router.get('/history', requireAuth, validateHistoryQuery, async (req, res) => {
  try {
    const { verdict_type, cursor, limit } = req.query;

    const result = await getVerdictHistory(req.user.id, {
      verdictType: verdict_type || undefined,
      cursor: cursor || undefined,
      limit: limit || 50,
    });

    res.json(result);
  } catch (err) {
    console.error('[verdict] history error:', err.message);
    res.status(500).json({ error: 'Failed to load verdict history.' });
  }
});

// ── GET /api/verdict/streak ────────────────────────────────────────────────────
router.get('/streak', requireAuth, async (req, res) => {
  try {
    const streak = await getStreak(req.user.id);

    let streakAtRisk = false;
    if (streak.lastVerdictAt) {
      const lastDate = new Date(streak.lastVerdictAt).toISOString().slice(0, 10);
      const todayDate = new Date().toISOString().slice(0, 10);
      const currentHour = new Date().getHours();
      if (lastDate !== todayDate && currentHour >= 20) {
        streakAtRisk = true;
      }
    }

    res.json({ ...streak, streakAtRisk });
  } catch (err) {
    console.error('[verdict] streak error:', err.message);
    res.status(500).json({ error: 'Failed to load streak.' });
  }
});

module.exports = router;
