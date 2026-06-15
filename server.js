/**
 * HoldOff application entry point.
 * Handles landing page, filter page, and API routes.
 */
const express = require('express');
const path = require('path');
const cookieParser = require('cookie-parser');
const { buildLandingContext } = require('./lib/landing-context');
const rateLimit = require('express-rate-limit');

// Sentry error tracking — initialized before any other middleware so all downstream errors are captured.
const Sentry = require('@sentry/node');
Sentry.init({
  dsn: process.env.SENTRY_DSN,
  environment: process.env.NODE_ENV || 'development',
  sampleRate: 0.1,
  tracesSampler: (ctx) => {
    if (ctx.tag?.request?.status === 200) return 0.1;
    return 1.0;
  },
});
const { verifyToken, getCookieTokens } = require('./lib/auth');
const routes = require('./routes');
const { mountSharePages } = require('./routes/share');
const { ensureCommunityTables } = require('./db/community');
const googleAuthHandler = require('./routes/google-auth');

// Kick off the one-shot waitlist blast on startup if BLAST_TRIGGER=1
if (process.env.BLAST_TRIGGER === '1') {
  require('./jobs/blast-on-start')().catch(err => {
    console.error('[blast] Fatal error:', err.message);
  });
}

const app = express();
const port = process.env.PORT || 3000;

app.use(cookieParser());
// Inject Google Client ID into all views
app.use((_req, res, next) => {
  res.locals.googleClientId = process.env.GOOGLE_CLIENT_ID || '';
  next();
});
// Sentry request handler — guarded for @sentry/node v8+ which removed Handlers API.
// WHY guard: newer Sentry SDK drops Handlers.requestHandler(); graceful fallback prevents crash.
if (Sentry.Handlers && typeof Sentry.Handlers.requestHandler === 'function') {
  app.use(Sentry.Handlers.requestHandler());
}
// Capture raw body for Stripe webhook signature verification.
// WHY verify callback: the old approach used a router.use() in stripe-webhook.js that
// called req.on('end', next) — but express.json() had already consumed the stream,
// so the 'end' event never re-fired and next() was never called, blocking ALL routes.
app.use(express.json({
  verify: (req, _res, buf) => {
    if (req.originalUrl === '/api/stripe-webhook') {
      req.rawBody = buf.toString('utf8');
    }
  },
}));
app.use(express.urlencoded({ extended: true }));

// Middleware safety net for /api/filter/analyze — guarantees a response within 16s.
// WHY: the handler has a 14s hard timeout; this fires 2s later as a last-ditch fallback.
app.use('/api/filter', (req, res, next) => {
  const t0 = Date.now();
  console.log(`[mw] ${req.method} ${req.path} started`);

  if (req.method === 'POST' && req.path === '/analyze') {
    // 16s safety net — handler should respond by 14s; this is the absolute backstop.
    const safetyTimer = setTimeout(() => {
      if (!res.headersSent) {
        console.log(`[mw] SAFETY TIMEOUT at ${Date.now() - t0}ms — returning static HOLD`);
        res.status(200).json({
          verdict: 'HOLD',
          pattern: 'late-night spiral',
          whats_happening: "Something's off tonight — maybe the timing, maybe the headspace. Either way, this message can wait.",
          grounded_voice: "You know yourself. Send this tomorrow when you can actually see it clearly. Tonight is not the moment.",
          rewrite: 'send nothing tonight',
          verdict_source: 'fallback',
        });
      }
    }, 16000);

    res.on('finish', () => {
      clearTimeout(safetyTimer);
      console.log(`[mw] POST /analyze finished ${res.statusCode} ${Date.now() - t0}ms`);
    });
    res.on('close', () => {
      clearTimeout(safetyTimer);
      if (!res.writableFinished) console.log(`[mw] POST /analyze CLIENT DISCONNECTED ${Date.now() - t0}ms`);
    });
  }

  next();
});

// Mount API routes — all /api/auth/* routes are rate-limited
app.use('/api/auth', rateLimit({
  windowMs: 60 * 1000,
  max: 100,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Too many requests. Please wait a moment.', code: 'RATE_LIMITED' },
}));
app.use('/api/verdict', rateLimit({
  windowMs: 60 * 1000,
  max: 30,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Too many requests. Please wait a moment.', code: 'RATE_LIMITED' },
}));

// Additional API endpoints — MUST be before app.use('/api', routes) to take priority
// GET /api/health — health check for monitoring dashboards
app.get('/api/health', (_req, res) => res.json({ status: 'ok' }));
// POST /api/interpret → aliased /api/filter/interpret (same AI logic)
const interpretHandler = require('./routes/interpret');
app.post('/api/interpret', interpretHandler);
app.post('/api/auth/google', googleAuthHandler);

// Mount SEO routes at root
app.use('/', require('./routes/seo'));

// Main API router — catches all /api/* not already matched above
app.use('/api/spiral-lock', require('./routes/spiral-lock'));
app.use('/api/community', require('./routes/community'));
app.use('/api/contacts', require('./routes/contacts'));
app.use('/api', routes);

// Sentry error handler — guarded for @sentry/node v8+ compatibility.
if (Sentry.Handlers && typeof Sentry.Handlers.errorHandler === 'function') {
  app.use(Sentry.Handlers.errorHandler());
}

// Catch-all 500 for unhandled errors caught by Sentry.
app.use((err, req, res, _next) => {
  if (!res.headersSent) {
    res.status(500).json({ error: 'Internal server error' });
  }
});

// EJS view engine — templates live in ./views/ (entry point: layout.ejs)
app.set('view engine', 'ejs');
app.set('views', path.join(__dirname, 'views'));


// Legacy/root conversion route aliases — keep old public links from 404ing.
// WHY: SEO/Site Health found root-level links like /waitlist, /contact, /verdict,
// /interpret, /referral, and /share returning 404 while their API or canonical
// equivalents exist elsewhere. These redirects preserve the funnel without
// changing API behavior.
app.get('/waitlist', (_req, res) => res.redirect(301, '/filter'));
app.get('/contact', (_req, res) => res.redirect(301, '/#contact'));
app.get('/interpret', (_req, res) => res.redirect(301, '/filter'));
app.get('/verdict', (_req, res) => res.redirect(301, '/filter'));
app.get('/referral', (_req, res) => res.redirect(301, '/referrals'));
app.get('/share', (_req, res) => res.redirect(301, '/filter'));
app.get('/checkout', (_req, res) => res.redirect(301, '/filter#pricing'));
app.post('/checkout', (req, res) => res.redirect(307, '/api/checkout/session'));

// Health check (Render requirement — no DB query so Neon can auto-suspend)
app.get('/robots.txt', (_req, res) => {
  res.setHeader('Content-Type', 'text/plain');
  res.send('User-agent: *\nAllow: /\n\nSitemap: https://shouldiholdoff.live/sitemap.xml');
});
app.get('/health', (_req, res) => res.json({ status: 'healthy' }));

// Static files — `index: false` so `/` hits the EJS render below, not index.html
app.use(express.static(path.join(__dirname, 'public'), { index: false }));

// Serve APK directly — bypasses dynamic router dispatcher entirely.
// Render's static CDN blocks .apk by default (403), so we serve it here.
app.get('/android-app.apk', (_req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'holdoff.apk'), {
    headers: {
      'Content-Type': 'application/vnd.android.package-archive',
      'Content-Disposition': 'attachment; filename="holdoff.apk"',
      'Cache-Control': 'no-cache, no-store, must-revalidate',
    },
  });
});

/** Extract logged-in user from JWT cookies, or null. */
async function getUserFromCookies(req) {
  const tokens = getCookieTokens(req);
  const payload = tokens.accessPayload || tokens.refreshPayload;
  if (!payload?.id) return null;

  // Enrich with membership_type for paywall decisions
  const { findUserById } = require('./db/users');
  const fullUser = await findUserById(payload.id).catch(() => null);
  return {
    id: payload.id,
    email: payload.email,
    membership_type: fullUser?.membership_type || null,
    subscription_status: fullUser?.subscription_status || null,
  };
}

// Landing page — sets a 90-day affiliate attribution cookie when ?aff=<code> is present.
app.get('/', async (req, res) => {
  const user = await getUserFromCookies(req);
  const affCode = req.query.aff;
  const refCode = req.query.ref;
  if (refCode && /^[a-z0-9]{8,32}$/.test(refCode)) {
    res.cookie('ref', refCode, {
      httpOnly: false,
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'lax',
      maxAge: 7 * 24 * 60 * 60 * 1000, // 7 days
    });
  }
  if (affCode && /^[a-z0-9-]{4,32}$/.test(affCode)) {
    res.cookie('aff', affCode, {
      httpOnly: false,
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'lax',
      maxAge: 90 * 24 * 60 * 60 * 1000, // 90 days
    });
  }
  res.render('index', { user });
});

// Referral dashboard — tiered rewards + progress tracker
app.get('/referrals', async (req, res) => {
  const user = await getUserFromCookies(req);
  if (!user) return res.redirect('/filter');
  res.render('referrals', { user });
});


// Inbox — messaging hub (replaces old dashboard as home screen)
app.get('/inbox', async (req, res) => {
  const user = await getUserFromCookies(req);
  if (!user) return res.redirect('/login?returnTo=/inbox');
  res.render('inbox', { user });
});

// Pattern journal — requires authentication
app.get('/journal', async (req, res) => {
  const user = await getUserFromCookies(req);
  if (!user) return res.redirect('/filter');
  res.render('journal', { user });
});

// Verdict history — requires authentication
app.get('/history', async (req, res) => {
  const user = await getUserFromCookies(req);
  if (!user) return res.redirect('/login?returnTo=/history');
  res.render('history', { user });
});

// Attachment style quiz — public, no auth required
app.get('/quiz', async (req, res) => {
  const user = await getUserFromCookies(req);
  res.render('quiz', { user: user || null });
});

// Detox landing page — 5-day Anxious Texting Detox email course
app.get('/detox', async (req, res) => {
  const user = await getUserFromCookies(req);
  res.render('detox', buildLandingContext({ user }));
});


// Affiliates partner program page
app.get('/affiliates', async (req, res) => {
  const user = await getUserFromCookies(req);
  res.render('affiliates', buildLandingContext({ user }));
});
// Filter page

// Pricing page — standalone route for campaigns and checkout CTAs.
app.get('/pricing', async (req, res) => {
  const tokens = getCookieTokens(req);
  const user = tokens.accessPayload || tokens.refreshPayload || null;
  res.render('layout', buildLandingContext({ user }));
});

app.get('/filter', async (req, res) => {
  const user = await getUserFromCookies(req);
  const refCode = req.query.ref;
  if (refCode && /^[a-z0-9]{8,32}$/.test(refCode)) {
    res.cookie('ref', refCode, {
      httpOnly: false,
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'lax',
      maxAge: 7 * 24 * 60 * 60 * 1000,
    });
  }
  res.render('filter', buildLandingContext({ user }));
});

// Compose messenger — AI-protected messaging with spiral lock + incoming decode
app.get('/compose', async (req, res) => {
  const user = await getUserFromCookies(req);
  if (!user) return res.redirect('/login?returnTo=/compose');
  res.render('compose', { user });
});

// Redirect old /holdoff.apk URL → static APK handler (CDN blocks raw .apk)
app.get('/holdoff.apk', (_req, res) => res.redirect(301, '/android-app.apk'));

// Download page — Android APK and PWA install instructions
app.get('/download', async (req, res) => {
  const user = await getUserFromCookies(req);
  res.render('download', buildLandingContext({ user }));
});

// Password reset page — token validated server-side on form submit
app.get('/reset-password', async (req, res) => {
  const user = await getUserFromCookies(req);
  res.render('reset-password', buildLandingContext({ user }));
});

// Success page — verifies Stripe session server-side before confirming
app.get('/success', async (req, res) => {
  const user = await getUserFromCookies(req);
  res.render('success', { ...buildLandingContext({ user }), sessionId: req.query.session_id || null });
});

// Cancel page
app.get('/cancel', async (req, res) => {
  const user = await getUserFromCookies(req);
  res.render('cancel', buildLandingContext({ user }));
});

// Upgrade page — Pro paywall, auth-gated
app.get('/upgrade', async (req, res) => {
  const user = await getUserFromCookies(req);
  if (!user) return res.redirect('/login?returnTo=/upgrade');
  res.render('upgrade', buildLandingContext({ user }));
});

// SEO pages (spirals + 10 article pages) — handled by routes/seo.js (mounted at root)

// /patterns/* pages — served directly (sub-path avoids /api prefix collision)
app.get('/patterns/avoidant-deactivation', (_req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'seo', 'avoidant-deactivation.html'), (err) => {
    if (err) {
      console.error('[/patterns/avoidant-deactivation] sendFile error:', err?.message);
      res.status(500).send('Internal error');
    }
  });
});

// Compare pages — /compare (index + 3 sub-pages)
app.get('/compare', (_req, res) => res.render('compare/index'));
app.get('/compare/replika', (_req, res) => res.render('compare/replika'));
app.get('/compare/character-ai', (_req, res) => res.render('compare/character-ai'));
app.get('/compare/chatgpt', (_req, res) => res.render('compare/chatgpt'));


// Insights — stats, contact analysis, forecast
app.get('/insights', async (req, res) => {
  const user = await getUserFromCookies(req);
  if (!user) return res.redirect('/login?returnTo=/insights');
  res.render('insights', { user });
});


// Account — profile, personality, settings
app.get('/account', async (req, res) => {
  const user = await getUserFromCookies(req);
  if (!user) return res.redirect('/login?returnTo=/account');
  res.render('account', { user });
});

// Legal pages
app.get('/privacy', (_req, res) => res.render('privacy'));
app.get('/terms', (_req, res) => res.render('terms'));

// Examples gallery — public, no auth required
app.get('/examples', async (req, res) => {
  const user = await getUserFromCookies(req);
  const examples = require('./data/examples.json');
  res.render('examples', { ...buildLandingContext({ user }), examples });
});


// Auth pages — login, signup, dashboard, settings
app.get('/login', async (req, res) => {
  const user = await getUserFromCookies(req);
  if (user) return res.redirect('/inbox');
  res.render('login', buildLandingContext({ user: null }));
});

app.get('/signup', async (req, res) => {
  const user = await getUserFromCookies(req);
  if (user) return res.redirect('/inbox');
  res.render('signup', buildLandingContext({ user: null }));
});

app.get('/dashboard', async (req, res) => {
  // Redirect to new inbox home screen
  return res.redirect('/inbox');
});

app.get('/settings', async (req, res) => {
  const user = await getUserFromCookies(req);
  if (!user) return res.redirect('/login?returnTo=/settings');
  res.render('settings', buildLandingContext({ user }));
});

app.get('/profile', async (req, res) => {
  const user = await getUserFromCookies(req);
  if (!user) return res.redirect('/login?returnTo=/profile');
  res.render('profile', buildLandingContext({ user }));
});

app.get('/community', async (req, res) => {
  const user = await getUserFromCookies(req);
  if (!user) return res.redirect('/login?returnTo=/community');
  res.render('community', { user });
});

// Share page + OG image — routes extracted to routes/share.js
mountSharePages(app);

// Startup validation — surface pre-flight config issues clearly rather than failing silently.
if (!process.env.POSTMARK_API_KEY && !process.env.holdoff_EMAIL_PROXY_URL) {
  console.warn('[startup] WARNING: No email provider configured — transactional emails will be logged only');
}
if (!process.env.STRIPE_WEBHOOK_SECRET) {
  console.warn('[startup] WARNING: STRIPE_WEBHOOK_SECRET not set — webhook signature verification disabled');
}

// Ensure community tables exist (idempotent)
ensureCommunityTables().catch(e => console.warn('[startup] community tables:', e.message));

app.listen(port, () => console.log(`HoldOff running on port ${port}`));

// In-process cron jobs (Render compatibility) — extracted to jobs/in-process-crons.js
require('./jobs/in-process-crons');

