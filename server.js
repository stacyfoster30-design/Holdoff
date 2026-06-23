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

// Auth pages
app.get('/login', async (req, res) => {
  const user = await getUserFromCookies(req);
  if (user) return res.redirect('/inbox');
  res.render('login', buildLandingContext({ user: null }));
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

// Ensure tables exist
ensureCommunityTables().catch(e => console.warn('[startup] community tables:', e.message));

const { initializeTables: initMessagingTables } = require(path.join(__dirname, 'db', 'messages'));
initMessagingTables().catch(e => console.warn('[startup] messaging tables:', e.message));

const { runMigrations } = require(path.join(__dirname, 'db', 'migrations'));
runMigrations().catch(e => console.warn('[startup] migrations:', e.message));

const { ensureBetaTestersTable } = require(path.join(__dirname, 'db', 'beta-testers'));
ensureBetaTestersTable().catch(e => console.warn('[startup] beta_testers table:', e.message));

app.listen(port, () => console.log(`HoldOff running on port ${port}`));
