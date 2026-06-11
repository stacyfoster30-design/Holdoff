# HoldOff Render Exit / Cutover Plan

## Recommended replacement
Railway is the fastest replacement for the current HoldOff app because it supports:

- Long-running Node/Express web services
- PostgreSQL
- custom domains
- Stripe webhooks
- GitHub auto-deploys
- Dockerfile-based deploys

## What is now prepared in this repository

- `Dockerfile` — portable production container for non-Render hosting
- `railway.json` — Railway deploy config using Dockerfile
- `Procfile` — fallback for Heroku-like platforms
- `.dockerignore` — keeps secrets/build junk out of the image

## Required production environment variables

Minimum to boot public pages:

```txt
NODE_ENV=production
APP_URL=https://shouldiholdoff.live
JWT_SECRET=<strong random secret>
```

Needed for full app functionality:

```txt
DATABASE_URL=<postgres connection string>
ANTHROPIC_API_KEY=<primary verdict AI provider>
OPENAI_API_KEY=<fallback verdict AI provider>
STRIPE_SECRET_KEY=<Stripe secret key>
STRIPE_WEBHOOK_SECRET=<Stripe webhook secret starting with whsec_>
ADMIN_TOKEN=<strong admin token>
RESEND_API_KEY=<if using Resend transactional email>
POSTMARK_API_KEY=<if using Postmark transactional email>
POLSIA_* variables=<only if still used by email/proxy integrations>
VAPID_PUBLIC_KEY=<push notification public key>
VAPID_PRIVATE_KEY=<push notification private key>
VAPID_SUBJECT=mailto:hello@shouldiholdoff.live
```

## DNS cutover after new host is live

Do not change DNS until the replacement host has a healthy deployment URL.

Then update Namecheap DNS:

- Root `@` — follow the new host's required A/ALIAS/CNAME instruction
- `www` — CNAME to the new host domain

Current Render DNS can be removed only after the new host passes:

```txt
/health returns 200
/ returns the HoldOff landing page
/filter loads
/checkout route responds safely
Stripe webhook endpoint exists
```

## Render cleanup

Once DNS is fully moved and verified, delete or suspend the old Render service so it stops consuming attention and sending failed deploy emails.
