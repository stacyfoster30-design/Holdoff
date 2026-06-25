/**
 * HoldOff application entry point.
 * Handles landing page, filter page, and API routes.
 */
const express = require('express');
const path = require('path');
const cookieParser = require('cookie-parser');
const { buildLandingContext } = require(path.join(__dirname, 'lib', 'landing-context'));
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
const { verifyToken, getCookieTokens } = require(path.join(__dirname, 'lib', 'auth'));
const authRouter = require(path.join(__dirname, 'routes', 'auth'));
const { mountSharePages } = require(path.join(__dirname, 'routes', 'share'));
const { ensureCommunityTables } = require(path.join(__dirname, 'db', 'community'));
const googleAuthHandler = require(path.join(__dirname, 'routes', 'google-auth'));
const checkoutRouter = require(path.join(__dirname, 'routes', 'checkout'));
const messagingRouter = require(path.join(__dirname, 'routes', 'messaging'));
const contactsRouter = require(path.join(__dirname, 'routes', 'contacts'));
const filterRouter = require(path.join(__dirname, 'routes', 'filter'));
const journalRouter = require(path.join(__dirname, 'routes', 'journal'));
const pushRouter = require(path.join(__dirname, 'routes', 'push'));
const referralRouter = require(path.join(__dirname, 'routes', 'referral'));
const stripeWebhookRouter = require(path.join(__dirname, 'routes', 'stripe-webhook'));
const abandonedCheckoutRouter = require(path.join(__dirname, 'routes', 'abandoned-checkout'));
const detoxRouter = require(path.join(__dirname, 'routes', 'detox'));
const waitlistRouter = require(path.join(__dirname, 'routes', 'waitlist'));
const adminRouter = require(path.join(__dirname, 'routes', 'admin'));

const app = express();
const port = process.env.PORT || 3000;

app.use(cookieParser());
// Inject Google Client ID into all views
app.use((_req, res, next) => {
  res.locals.googleClientId = process.env.GOOGLE_CLIENT_ID || '251734222269-l5fn6rbfcmtmm161q3g7e7k840lavf3f.apps.googleusercontent.com';
  next();
});

// Capture raw body for Stripe webhook signature verification.
app.use(express.json({
  verify: (req, _res, buf) => {
    if (req.originalUrl === '/api/stripe-webhook') {
      req.rawBody = buf.toString('utf8');
    }
  },
}));
app.use(express.urlencoded({ extended: true }));

// Middleware safety net for /api/filter/analyze
app.use('/api/filter', (req, res, next) => {
  const t0 = Date.now();
  console.log(`[mw] ${req.method} ${req.path} started`);

  if (req.method === 'POST' && req.path === '/analyze') {
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

// Mount API routes
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

// Additional API endpoints
app.get('/api/health', (_req, res) => res.json({ status: 'ok' }));

// Mount SEO routes at root
app.use('/', require(path.join(__dirname, 'routes', 'seo')));

// Main API router
app.use('/api/auth', authRouter);
app.use('/api/spiral-lock', require(path.join(__dirname, 'routes', 'spiral-lock')));
app.use('/api/checkout', checkoutRouter);
app.use('/api/community', require(path.join(__dirname, 'routes', 'community')));
app.use('/api/contacts', require(path.join(__dirname, 'routes', 'contacts')));
app.use('/api/contact-insights', require(path.join(__dirname, 'routes', 'contact-insights')));
app.use('/api/questionnaire', require(path.join(__dirname, 'routes', 'questionnaire')));
app.use('/api/quiz-invites', require(path.join(__dirname, 'routes', 'quiz-invites')));
app.use('/api/messaging', require(path.join(__dirname, 'routes', 'messaging')));
app.use('/api/verdict', require(path.join(__dirname, 'routes', 'verdict')));
app.use('/api/interpreter', require(path.join(__dirname, 'routes', 'interpreter')));
app.use('/api/companion', require(path.join(__dirname, 'routes', 'companion')));
app.use('/api/filter', filterRouter);
app.use('/api/journal', journalRouter);
app.use('/api/push', pushRouter);
app.use('/api/referral', referralRouter);
app.use('/api/stripe-webhook', stripeWebhookRouter);
app.use('/api/abandoned-checkout', abandonedCheckoutRouter);
app.use('/api/detox', detoxRouter);
app.use('/api/waitlist', waitlistRouter);
app.use('/api/admin', adminRouter);
app.use('/api/users', require(path.join(__dirname, 'routes', 'users')));
app.use('/api/quiz', require(path.join(__dirname, 'routes', 'quiz')));
app.use('/api/chronicle', require(path.join(__dirname, 'routes', 'chronicle')));
app.use('/api/affiliates', require(path.join(__dirname, 'routes', 'affiliates')));
app.use('/api/contact', require(path.join(__dirname, 'routes', 'contact')));
app.use('/api/download', require(path.join(__dirname, 'routes', 'download')));
app.use('/api/blast', require(path.join(__dirname, 'routes', 'blast')));
app.use('/api/outreach', require(path.join(__dirname, 'routes', 'outreach')));

// EJS view engine
app.set('view engine', 'ejs');
app.set('views', path.join(__dirname, 'views'));

// Redirect aliases
app.get('/waitlist', (_req, res) => res.redirect(301, '/filter'));
app.get('/contact', (_req, res) => res.redirect(301, '/#contact'));
app.get('/interpret', (_req, res) => res.redirect(301, '/filter'));
app.get('/verdict', (_req, res) => res.redirect(301, '/filter'));
app.get('/referral', (_req, res) => res.redirect(301, '/referrals'));
app.get('/share', (_req, res) => res.redirect(301, '/filter'));
app.get('/checkout', (_req, res) => res.redirect(301, '/filter#pricing'));

// Health check
app.get('/robots.txt', (_req, res) => {
  res.setHeader('Content-Type', 'text/plain');
  res.send('User-agent: *\nAllow: /\n\nSitemap: https://shouldiholdoff.live/sitemap.xml');
});
app.get('/health', (_req, res) => res.json({ status: 'healthy' }));
app.get('/favicon.ico', (_req, res) => res.redirect(302, '/icon.svg'));
app.get('/notifications', async (_req, res) => {
  res.render('notifications', { user: null });
});
app.get('/onboarding', (_req, res) => res.render('onboarding'));

// Digital Asset Links — verifies the Android app (TWA) owns this domain.
// Served explicitly because express.static ignores dot-folders like /.well-known.
app.get('/.well-known/assetlinks.json', (_req, res) => {
  res.type('application/json').send(JSON.stringify([
    {
      relation: ['delegate_permission/common.handle_all_urls'],
      target: {
        namespace: 'android_app',
        package_name: 'com.stacymartin.holdoff',
        sha256_cert_fingerprints: [
          '7B:C2:57:02:73:AB:25:A5:52:B6:73:1B:F6:AF:2D:2E:FF:BF:02:D7:AF:2E:33:CC:92:D3:FA:74:EB:61:2D:95',
          '5C:01:7D:6E:E0:E5:5C:B1:9E:DF:7C:26:39:32:8C:62:04:71:12:A6:10:CE:40:D6:36:D2:E1:5D:D6:02:2F:D1',
        ],
      },
    },
  ], null, 2));
});

// Static files
app.use(express.static(path.join(__dirname, 'public'), { index: false }));

// Serve APK directly
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
  const { findUserById } = require(path.join(__dirname, 'db', 'users'));
  const fullUser = await findUserById(payload.id).catch(() => null);
  return {
    id: payload.id,
    email: payload.email,
    membership_type: fullUser?.membership_type || null,
    subscription_status: fullUser?.subscription_status || null,
  };
}

// Landing page
app.get('/', async (req, res) => {
  const user = await getUserFromCookies(req);
  const affCode = req.query.aff;
  const refCode = req.query.ref;
  if (refCode && /^[a-z0-9]{8,32}$/.test(refCode)) {
    res.cookie('ref', refCode, {
      httpOnly: false,
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'lax',
      maxAge: 7 * 24 * 60 * 60 * 1000,
    });
  }
  if (affCode && /^[a-z0-9-]{4,32}$/.test(affCode)) {
    res.cookie('aff', affCode, {
      httpOnly: false,
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'lax',
      maxAge: 90 * 24 * 60 * 60 * 1000,
    });
  }
  res.render('index', { user });
});

// Inbox
app.get('/inbox', async (req, res) => {
  const user = await getUserFromCookies(req);
  if (!user) return res.redirect('/login?returnTo=/inbox');
  res.render('inbox', { user });
});

// Contacts
app.get('/contacts', async (req, res) => {
  const user = await getUserFromCookies(req);
  if (!user) return res.redirect('/login?returnTo=/contacts');
  res.render('contacts', { user });
});

// Filter page
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

// Pricing page
app.get('/pricing', async (req, res) => {
  const tokens = getCookieTokens(req);
  const user = tokens.accessPayload || tokens.refreshPayload || null;
  res.render('pricing', { user });
});

// Settings page
app.get('/settings', async (req, res) => {
  const user = await getUserFromCookies(req);
  if (!user) {
    return res.redirect('/login?next=/settings');
  }
  res.render('settings', { user });
});

// Redeem page
app.get('/redeem', async (req, res) => {
  const user = await getUserFromCookies(req);
  const code = (req.query.code || '').toString().replace(/[^a-zA-Z0-9]/g, '').slice(0, 32);
  res.render('redeem', { user: user || null, code });
});

// Redeem endpoint
app.post('/api/redeem', async (req, res) => {
  try {
    const user = await getUserFromCookies(req);
    if (!user?.id) {
      return res.status(401).json({ ok: false, error: 'Please sign in or create your account first, then redeem.' });
    }
    const raw = (req.body?.code || '').toString().trim().toUpperCase();
    const FREE_ACCESS_CODES = {
      DNA: { membership: 'lifetime', label: 'Founder Free Access' },
    };
    const entry = FREE_ACCESS_CODES[raw];
    if (!entry) {
      return res.status(400).json({ ok: false, error: "That code isn't valid. Double-check it and try again." });
    }
    const { updateMembershipType } = require(path.join(__dirname, 'db', 'users'));
    await updateMembershipType(user.id, entry.membership);
    try {
      const { upsertSubscription } = require(path.join(__dirname, 'db', 'subscriptions'));
      await upsertSubscription({
        email: (user.email || '').toLowerCase().trim(),
        stripeCustomerId: 'promo:' + raw,
        stripeSubscriptionId: 'promo:' + raw,
        status: 'active',
        currentPeriodEnd: new Date(Date.now() + 365 * 50 * 24 * 60 * 60 * 1000),
        membershipType: entry.membership,
      });
    } catch (subErr) {
      console.warn('[redeem] subscription mirror skipped:', subErr.message);
    }
    console.log(`[redeem] ${user.email} redeemed ${raw} → ${entry.membership}`);
    return res.json({ ok: true, membership: entry.membership });
  } catch (err) {
    console.error('[redeem] error:', err.message);
    return res.status(500).json({ ok: false, error: 'Something went wrong. Try again in a moment.' });
  }
});

// Affiliate
app.get('/affiliate', async (req, res) => {
  const user = await getUserFromCookies(req);
  res.render('affiliate', { user: user || null });
});

// Partnerships
app.get('/partnerships', async (req, res) => {
  const user = await getUserFromCookies(req);
  res.render('partnerships', { user: user || null });
});

// Suggest
app.get('/suggest', async (req, res) => {
  const user = await getUserFromCookies(req);
  res.render('suggest', { user: user || null });
});

// Legal pages
app.get('/privacy', (_req, res) => res.render('privacy'));
app.get('/terms', (_req, res) => res.render('terms'));

// ─── Premium interactive story ───────────────────────────────────────────────
// /story-preview = Stacy's real story (free, served as the preview).
// /story-experience = "put on my shoes" personalized version (premium only).
// /story = legacy alias → preview.
app.get('/story', (_req, res) => res.redirect('/story-preview'));

app.get('/story-preview', async (req, res) => {
  const user = await getUserFromCookies(req);
  res.render('story-animated', {
    user,
    isPremium: !!(user && ['premium', 'lifetime', 'founder'].includes((user.membership_type || '').toLowerCase())),
  });
});

app.get('/story-experience', async (req, res) => {
  const user = await getUserFromCookies(req);
  if (!user) {
    return res.redirect('/login?returnTo=' + encodeURIComponent('/story-experience'));
  }
  const tier = (user.membership_type || '').toLowerCase();
  const isPremium = ['premium', 'lifetime', 'founder'].includes(tier);
  if (!isPremium) {
    return res.redirect('/pricing?reason=story');
  }
  res.render('story-experience', { user, isPremium: true });
});

// Companion chat page — Sadie or Dan
app.get('/companion', async (req, res) => {
  const soul = req.query.soul === 'Dan' ? 'Dan' : 'Sadie';
  const CHARACTERS = {
    Sadie: {
      name: 'Sadie',
      tagline: 'Your soft-spoken pattern spotter',
      emoji: '🌙',
      image: '/assets/SADIE_COMPANION.png',
      greeting: "Hey… I noticed something. Want to talk about it?"
    },
    Dan: {
      name: 'Dan',
      tagline: "The mirror you didn't ask for",
      emoji: '🔥',
      image: '/assets/DAN_COMPANION.png',
      greeting: "Okay. What's actually going on here?"
    }
  };
  const user = await getUserFromCookies(req).catch(() => null);
  const userContext = {
    name: (user && (user.name || user.firstName)) || null,
    isPremium: !!(user && (user.isPremium || user.premium)),
    soul
  };
  res.render('companion', { character: CHARACTERS[soul], user, userContext });
});

// Auth pages
app.get('/login', async (req, res) => {
  const user = await getUserFromCookies(req);
  if (user) return res.redirect('/inbox');
  res.render('login', buildLandingContext({ user: null }));
});

// POST /login safety net — handles form submits (e.g. JS disabled, JS error, or
// browsers that bypass onsubmit handlers). Keeps the page from ever returning
// "Cannot POST /login". Mirrors POST /api/auth/login but supports both
// application/json (xhr) and application/x-www-form-urlencoded (plain form).
app.post('/login', async (req, res) => {
  try {
    const bcrypt = require('bcryptjs');
    const { findUserByEmail } = require(path.join(__dirname, 'db', 'users'));
    const {
      signAccessToken,
      signRefreshToken,
      holdoffTokenCookieOpts,
      refreshCookieOpts,
    } = require(path.join(__dirname, 'lib', 'auth'));

    const wantsJson =
      req.is('application/json') ||
      (req.headers.accept || '').includes('application/json') ||
      req.xhr === true;

    const { email, password } = req.body || {};
    const normalizedEmail = (email || '').toLowerCase().trim();
    const returnTo = (req.query.returnTo || req.body.returnTo || '/filter').toString();

    const fail = (status, message) => {
      if (wantsJson) return res.status(status).json({ error: message });
      return res.redirect(`/login?error=${encodeURIComponent(message)}&returnTo=${encodeURIComponent(returnTo)}`);
    };

    if (!normalizedEmail || !normalizedEmail.includes('@')) {
      return fail(400, 'Email is required.');
    }
    if (!password) {
      return fail(400, 'Password is required.');
    }

    const user = await findUserByEmail(normalizedEmail);
    if (!user || !user.password_hash) {
      return fail(401, 'Invalid email or password.');
    }
    const valid = await bcrypt.compare(password, user.password_hash).catch(() => false);
    if (!valid) {
      return fail(401, 'Invalid email or password.');
    }

    const accessToken = signAccessToken({ id: user.id, email: normalizedEmail });
    const rawRefreshToken = await signRefreshToken(user.id, normalizedEmail, req.headers['user-agent']);
    res.cookie('holdoff_token', accessToken, holdoffTokenCookieOpts());
    res.cookie('refresh_token', rawRefreshToken, refreshCookieOpts());

    if (wantsJson) {
      return res.json({
        ok: true,
        user: { id: user.id, email: normalizedEmail, name: user.name, subscription_tier: user.membership_type },
      });
    }
    return res.redirect(returnTo);
  } catch (err) {
    console.error('[POST /login] error:', err);
    if ((req.headers.accept || '').includes('application/json')) {
      return res.status(500).json({ error: 'Login failed. Please try again.' });
    }
    return res.redirect('/login?error=' + encodeURIComponent('Login failed. Please try again.'));
  }
});

// Forgot password page
app.get('/forgot-password', async (req, res) => {
  const user = await getUserFromCookies(req);
  if (user) return res.redirect('/inbox');
  res.render('forgot-password', { user: null });
});

// Reset password page
app.get('/reset-password', async (req, res) => {
  const user = await getUserFromCookies(req);
  if (user) return res.redirect('/inbox');
  res.render('reset-password', { user: null });
});

app.get('/signup', async (req, res) => {
  const user = await getUserFromCookies(req);
  if (user) return res.redirect('/inbox');
  res.render('signup', buildLandingContext({ user: null }));
});

app.get('/dashboard', async (req, res) => {
  return res.redirect('/inbox');
});

// Journal page (auth required — handled inside journalRouter at GET /)
app.get('/journal', async (req, res) => {
  const user = await getUserFromCookies(req);
  if (!user) return res.redirect('/login?returnTo=/journal');
  res.render('journal', { user });
});

// Referrals page
app.get('/referrals', async (req, res) => {
  const user = await getUserFromCookies(req);
  res.render('referrals', { user: user || null });
});

// Quiz page
app.get('/quiz', async (req, res) => {
  const user = await getUserFromCookies(req);
  res.render('quiz', { user: user || null });
});

// Chronicle (personalized tips) page
app.get('/chronicle', async (req, res) => {
  const user = await getUserFromCookies(req);
  if (!user) return res.redirect('/login?returnTo=/chronicle');
  res.render('chronicle', { user });
});

// Insights page
app.get('/insights', async (req, res) => {
  const user = await getUserFromCookies(req);
  if (!user) return res.redirect('/login?returnTo=/insights');
  res.render('insights', { user });
});

// Verdict history page
app.get('/history', async (req, res) => {
  const user = await getUserFromCookies(req);
  if (!user) return res.redirect('/login?returnTo=/history');
  res.render('history', { user });
});

// Profile page
app.get('/profile', async (req, res) => {
  const user = await getUserFromCookies(req);
  if (!user) return res.redirect('/login?returnTo=/profile');
  res.render('profile', { user });
});

// Share pages
mountSharePages(app);

// Beta tester signup page
app.get('/beta', async (req, res) => {
  const user = await getUserFromCookies(req);
  res.render('beta', { user: user || null });
});

// Beta tester signup API
app.post('/api/beta-signup', async (req, res) => {
  try {
    const { name, email, device, why } = req.body || {};
    if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      return res.status(400).json({ ok: false, error: 'Please enter a valid email address.' });
    }
    const { addBetaTester } = require(path.join(__dirname, 'db', 'beta-testers'));
    const row = await addBetaTester({ name, email, device, why });
    if (!row) {
      // Already signed up — still show success so they're not blocked
      return res.json({ ok: true, already: true });
    }
    console.log(`[beta-signup] new tester: ${email}`);
    return res.json({ ok: true });
  } catch (err) {
    console.error('[beta-signup] error:', err.message);
    return res.status(500).json({ ok: false, error: 'Something went wrong. Please try again.' });
  }
});

// Insights stats — returns held/rewritten/intercepted/holdRate for logged-in user
app.get('/api/insights/stats', rateLimit({
  windowMs: 60 * 1000,
  max: 30,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Too many requests. Please wait a moment.', code: 'RATE_LIMITED' },
}), async (req, res) => {
  try {
    const user = await getUserFromCookies(req);
    if (!user?.id) return res.status(401).json({ error: 'Not authenticated' });
    const { pool } = require(path.join(__dirname, 'db', 'index'));
    const { rows } = await pool.query(
      `SELECT
         COUNT(*) FILTER (WHERE verdict = 'HOLD') AS held,
         COUNT(*) FILTER (WHERE verdict = 'REWRITE') AS rewritten,
         COUNT(*) AS intercepted
       FROM verdict_history WHERE user_id = $1`,
      [user.id]
    );
    const row = rows[0] || { held: 0, rewritten: 0, intercepted: 0 };
    const held = parseInt(row.held || 0, 10);
    const rewritten = parseInt(row.rewritten || 0, 10);
    const intercepted = parseInt(row.intercepted || 0, 10);
    const holdRate = intercepted > 0 ? Math.round((held / intercepted) * 100) : 0;
    res.json({ held, rewritten, intercepted, holdRate });
  } catch (err) {
    console.error('[insights/stats] error:', err.message);
    res.status(500).json({ error: 'Failed to fetch stats' });
  }
});

// Preferences save — stores story-experience onboarding preferences
app.post('/api/preferences/save', async (req, res) => {
  try {
    const user = await getUserFromCookies(req);
    if (!user?.id) return res.status(401).json({ error: 'Not authenticated' });
    const { storeUserPreferences, addUserConditions } = require(path.join(__dirname, 'db', 'preferences'));
    const { personOfInterest, relationshipType, spiralTrigger, values, tone, depth, conditions } = req.body || {};
    await storeUserPreferences(user.id, {
      tone: tone || 'direct',
      tracking_depth: depth || 'moderate',
    });
    if (Array.isArray(conditions) && conditions.length > 0) {
      await addUserConditions(user.id, conditions).catch(() => {});
    }
    res.json({ ok: true });
  } catch (err) {
    console.error('[preferences/save] error:', err.message);
    res.status(500).json({ error: 'Failed to save preferences' });
  }
});

// Affiliate application form (from /affiliate page)
app.post('/api/affiliate-apply', async (req, res) => {
  try {
    const { name, email, phone, platform, followers, about } = req.body || {};
    if (!name || !name.trim()) return res.status(400).json({ ok: false, error: 'Name is required.' });
    if (!email || !email.includes('@')) return res.status(400).json({ ok: false, error: 'Valid email is required.' });
    const { addAffiliate } = require(path.join(__dirname, 'db', 'affiliates'));
    const row = await addAffiliate({
      name: name.trim(),
      practiceHandle: platform || '',
      email: email.trim(),
      audienceSize: followers || about || '',
    });
    if (!row) return res.json({ ok: true, already: true });
    console.log(`[affiliate-apply] new application: ${email}`);
    return res.json({ ok: true });
  } catch (err) {
    console.error('[affiliate-apply] error:', err.message);
    return res.status(500).json({ ok: false, error: 'Something went wrong. Please try again.' });
  }
});

// Partnership application form (from /partnerships page)
app.post('/api/partnership-apply', async (req, res) => {
  try {
    const { name, email, phone, organization, details, website } = req.body || {};
    if (!name || !name.trim()) return res.status(400).json({ ok: false, error: 'Name is required.' });
    if (!email || !email.includes('@')) return res.status(400).json({ ok: false, error: 'Email is required.' });
    if (!organization || !organization.trim()) return res.status(400).json({ ok: false, error: 'Organization is required.' });
    console.log(`[partnership-apply] ${name} <${email}> from ${organization}`);
    // Fire notification email async — non-blocking
    const { sendEmail } = require(path.join(__dirname, 'services', 'email'));
    const adminEmail = process.env.ADMIN_EMAIL || 'holdoff@shouldiholdoff.live';
    sendEmail({
      to: adminEmail,
      subject: `Partnership inquiry: ${organization}`,
      text: `Name: ${name}\nEmail: ${email}\nPhone: ${phone || '—'}\nOrganization: ${organization}\nWebsite: ${website || '—'}\n\n${details || ''}`,
      html: `<p><strong>Name:</strong> ${name}<br><strong>Email:</strong> ${email}<br><strong>Phone:</strong> ${phone || '—'}<br><strong>Organization:</strong> ${organization}<br><strong>Website:</strong> ${website || '—'}</p><p>${(details || '').replace(/\n/g, '<br>')}</p>`,
    }).catch(e => console.warn('[partnership-apply] email failed:', e.message));
    return res.json({ ok: true });
  } catch (err) {
    console.error('[partnership-apply] error:', err.message);
    return res.status(500).json({ ok: false, error: 'Something went wrong. Please try again.' });
  }
});

// Suggestion / feedback form (from /suggest page)
app.post('/api/suggestion', async (req, res) => {
  try {
    const { type, title, description, impact, followup, email } = req.body || {};
    if (!title || !title.trim()) return res.status(400).json({ ok: false, error: 'Title is required.' });
    if (!description || !description.trim()) return res.status(400).json({ ok: false, error: 'Description is required.' });
    console.log(`[suggestion] type=${type} title="${title}" followup=${followup} email=${email || '—'}`);
    const { sendEmail } = require(path.join(__dirname, 'services', 'email'));
    const adminEmail = process.env.ADMIN_EMAIL || 'holdoff@shouldiholdoff.live';
    sendEmail({
      to: adminEmail,
      subject: `[${type || 'feedback'}] ${title}`,
      text: `Type: ${type || '—'}\nTitle: ${title}\n\nDescription:\n${description}\n\nImpact:\n${impact || '—'}\n\nFollow-up: ${followup || 'no'}\nEmail: ${email || '—'}`,
      html: `<p><strong>Type:</strong> ${type || '—'}<br><strong>Follow-up:</strong> ${followup || 'no'}<br><strong>Email:</strong> ${email || '—'}</p><h3>${title}</h3><p>${(description || '').replace(/\n/g, '<br>')}</p><p><em>Impact: ${(impact || '').replace(/\n/g, '<br>')}</em></p>`,
    }).catch(e => console.warn('[suggestion] email failed:', e.message));
    return res.json({ ok: true });
  } catch (err) {
    console.error('[suggestion] error:', err.message);
    return res.status(500).json({ ok: false, error: 'Something went wrong. Please try again.' });
  }
});

// Lightweight event tracking (from pricing page and other UI events)
app.post('/api/track', async (req, res) => {
  try {
    const { event, tier, source } = req.body || {};
    if (event) console.log(`[track] event=${event} tier=${tier || '—'} source=${source || '—'}`);
    return res.json({ ok: true });
  } catch (err) {
    return res.json({ ok: true }); // Non-fatal
  }
});

// Send message — stores a user-sent message to a contact thread
app.post('/api/send-message', async (req, res) => {
  try {
    const user = await getUserFromCookies(req);
    if (!user?.id) return res.status(401).json({ ok: false, error: 'Not authenticated' });
    const { contactId, message } = req.body || {};
    if (!contactId) return res.status(400).json({ ok: false, error: 'contactId is required.' });
    if (!message || !message.trim()) return res.status(400).json({ ok: false, error: 'message is required.' });
    const msgDb = require(path.join(__dirname, 'db', 'messages'));
    const thread = await msgDb.getOrCreateThread(user.id, contactId, null);
    if (!thread) return res.status(404).json({ ok: false, error: 'Contact not found.' });
    await msgDb.insertMessage(thread.id, {
      senderType: 'user',
      body: message.trim(),
      externalId: null,
      timestamp: new Date(),
    });
    res.json({ ok: true, threadId: thread.id });
  } catch (err) {
    console.error('[send-message] error:', err.message);
    res.status(500).json({ ok: false, error: 'Failed to send message.' });
  }
});

// Ensure tables exist
ensureCommunityTables().catch(e => console.warn('[startup] community tables:', e.message));

const { initializeTables: initMessagingTables } = require(path.join(__dirname, 'db', 'messages'));
initMessagingTables().catch(e => console.warn('[startup] messaging tables:', e.message));

const { runMigrations } = require(path.join(__dirname, 'db', 'migrations'));
runMigrations().catch(e => console.warn('[startup] migrations:', e.message));

const { ensureBetaTestersTable } = require(path.join(__dirname, 'db', 'beta-testers'));
ensureBetaTestersTable().catch(e => console.warn('[startup] beta_testers table:', e.message));

app.listen(port, () => console.log(`HoldOff running on port ${port}`));
