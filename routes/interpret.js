/**
 * Interpret handler — aliased to both /api/interpret and /api/filter/interpret.
 * Owns: the POST /interpret handler function.
 * Does NOT own: /api/filter/interpret route registration (see routes/filter.js).
 */
const crypto = require('crypto');
const { logVerdictCall } = require('../db/healthchecks');
const {
  callWithFallback,
  HANDLER_HARD_TIMEOUT_MS,
  INTERPRET_SYSTEM_PROMPT,
  buildPersonalizedInterpretPrompt,
  parseCookies,
  extractProInfo,
  getVerdictCount,
} = require('../lib/verdict-ai');
const { isProEmail } = require('../db/subscriptions');
const { verifyToken, getCookieTokens } = require('../lib/auth');
const { getUserPreferences, getUserConditions } = require('../db/preferences');

const FREE_VERDICT_LIMIT = 3;

const NEUTRAL_INTERPRET_FALLBACK = {
  detected_style: 'Unclear',
  confidence: 'low',
  red_flags: [],
  what_it_means: "I can't confidently read their attachment style from this message alone. The grounded read is: there isn't enough evidence yet, and uncertainty does not automatically mean rejection.",
  how_you_misread_it: "When you're anxious, your brain can treat missing context like proof. This is a moment to slow down, not assign a label or build a story around the gap.",
  what_they_need: "A calm, simple response if one is needed — or a pause while you let your nervous system settle before deciding.",
};

function interpretHandler(req, res, next) {
  const reqId = crypto.randomBytes(4).toString('hex');
  const t0 = Date.now();
  const log = (phase, extra = '') =>
    console.log(`[filter] reqId=${reqId} phase=${phase} elapsed=${Date.now() - t0}ms${extra ? ' ' + extra : ''}`);

  log('received');

  // Express passes (req, res, next) when used as a middleware; call next() after handling
  const done = () => {
    if (!res.headersSent) {
      res.status(404).json({ error: 'Not found' });
    }
  };
  // Detect if called as middleware (Express passes next) or direct handler
  const isDirect = next === undefined;

  (async () => {
    try {
      const { message, style } = req.body || {};

      if (!message || !message.trim()) {
        log('rejected', 'reason=missing_message');
        return res.status(400).json({ error: 'message is required' });
      }

      log('input_parsed', `msgLen=${message.length}`);

      // --- Entitlement check ---
      const cookies = parseCookies(req.headers.cookie);
      const authHeader = req.headers.authorization;
      let isLoggedIn = false;
      let membership = null;

      let jwtPayload = null;
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
          return res.status(402).json({
            error: 'free_limit_reached',
            verdicts_used: count,
            limit: freeLimit,
            membership_tier: null,
          });
        }
      }

      const userStyle = style && style.trim() ? style.trim() : 'Not sure — figure it out';
      const userContent = `Partner's message:\n${message}\n\nSuspected style: ${userStyle}`;

      log('model_call_started');

      // Fetch user preferences if logged in
      let systemPrompt = INTERPRET_SYSTEM_PROMPT;
      if (jwtPayload?.id) {
        try {
          const [prefs, conditions] = await Promise.all([
            getUserPreferences(jwtPayload.id),
            getUserConditions(jwtPayload.id),
          ]);
          
          if (prefs) {
            const personalizedPrefs = {
              language_style: prefs.language_style || 'clinical',
              tone: prefs.tone || 'direct',
              tracking_depth: prefs.tracking_depth || 'moderate',
              show_why: prefs.show_why !== false,
              show_what: prefs.show_what !== false,
              show_meaning: prefs.show_meaning !== false,
              show_action: prefs.show_action !== false,
              conditions: conditions || []
            };
            systemPrompt = buildPersonalizedInterpretPrompt(personalizedPrefs);
            log('personalized_prompt_built', `tone=${personalizedPrefs.tone} style=${personalizedPrefs.language_style}`);
          }
        } catch (err) {
          log('prefs_fetch_error', `err=${err.message}`);
          // Fall back to default prompt if preferences can't be fetched
        }
      }

      let raw, source;
      try {
        const result = await Promise.race([
          callWithFallback(systemPrompt, userContent, log),
          new Promise((_, reject) => setTimeout(() => {
            const err = new Error('Handler hard timeout');
            err._hardTimeout = true;
            reject(err);
          }, HANDLER_HARD_TIMEOUT_MS)),
        ]);
        raw = result.raw;
        source = result.source;
      } catch (err) {
        log('hard_timeout_or_error', `msg=${err.message} hardTimeout=${!!err._hardTimeout}`);
        raw = null;
        source = 'fallback';
      }

      const latencyMs = Date.now() - t0;

      if (source === 'fallback') {
        log('all_paths_failed', `latency=${latencyMs}ms`);
        logVerdictCall({ verdictSource: 'fallback', verdict: 'INTERPRET_FALLBACK', latencyMs, errorMessage: 'All AI paths failed' }).catch(() => {});
        return res.status(200).json({
          ...NEUTRAL_INTERPRET_FALLBACK,
          source: 'fallback',
        });
      }

      log('model_call_returned', `source=${source}`);

      let parsed;
      try {
        let cleanRaw = raw.trim();
        if (cleanRaw.startsWith('```')) {
          cleanRaw = cleanRaw.replace(/^```(?:json)?\n?/, '').replace(/\n?```$/, '').trim();
        }
        parsed = JSON.parse(cleanRaw);
      } catch (_) {
        log('json_parse_failed', `rawLen=${(raw || '').length}`);
        logVerdictCall({ verdictSource: source, verdict: null, latencyMs, errorMessage: 'JSON parse failed' }).catch(() => {});
        return res.status(500).json({ error: 'Interpretation failed. Try again.' });
      }

      // Validate required fields
      const required = ['detected_style', 'what_it_means'];
      for (const field of required) {
        if (!parsed[field]) {
          log('missing_field', `field=${field}`);
          logVerdictCall({ verdictSource: source, verdict: null, latencyMs, errorMessage: `Missing field: ${field}` }).catch(() => {});
          return res.status(500).json({ error: 'Interpretation incomplete. Try again.' });
        }
      }

      logVerdictCall({ verdictSource: source, verdict: 'INTERPRET_OK', latencyMs }).catch(() => {});

      parsed.source = source;
      log('response_sent', `style=${parsed.detected_style}`);
      return res.status(200).json(parsed);

    } catch (fatalErr) {
      const latencyMs = Date.now() - t0;
      console.error(`[filter] reqId=${reqId} FATAL UNCAUGHT: ${fatalErr.message}`);
      logVerdictCall({ verdictSource: 'fallback', verdict: 'INTERPRET_FALLBACK', latencyMs, errorMessage: `Fatal: ${fatalErr.message}` }).catch(() => {});
      if (!res.headersSent) {
        return res.status(200).json({
          ...NEUTRAL_INTERPRET_FALLBACK,
          source: 'fallback',
        });
      }
    }
  })();
}

// Export as the default export for use as a route handler
module.exports = interpretHandler;