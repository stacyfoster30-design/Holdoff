# HoldOff

AI-powered message filter for anxious attachment. Users compose texts late at night; the app intercepts and encourages delay before sending.

## Stack

Express.js + EJS + PostgreSQL (Neon) + Resend (email) + Stripe (payments) + Render (hosting)

## Setup

```bash
npm install
cp .env.example .env   # edit with real values
npm run dev
```

## Environment Variables

| Variable | Description |
|---|---|
| `DATABASE_URL` | Neon PostgreSQL connection string |
| `RESEND_API_KEY` | Resend API key for sending emails |
| `STRIPE_SECRET_KEY` | Stripe secret key |
| `STRIPE_WEBHOOK_SECRET` | Stripe webhook signing secret |
| `APP_URL` | Public URL (default: https://shouldiholdoff.live) |
| `JWT_SECRET` | Secret for signing JWT tokens |
| `BLAST_SECRET` | Secret for triggering blast jobs |
| `ANTHROPIC_API_KEY` | **Preferred** direct AI key — used when Polsia proxy is unavailable. Get from console.anthropic.com. |
| `OPENAI_DIRECT_API_KEY` | Backup direct AI key — used if Anthropic also fails. Get from platform.openai.com. |
| `OPENAI_BASE_URL` | Polsia proxy URL (set by platform). Leave blank to skip proxy and go direct. |
| `OPENAI_API_KEY` | Polsia proxy auth token (set by platform). |

## Email Sending (Resend)

HoldOff uses **Resend** for transactional and outreach emails from `hello@shouldiholdoff.live`.

### One-time DNS setup (required before first send)

1. Create a free account at [resend.com](https://resend.com)
2. Add a domain: `shouldiholdoff.live`
3. Resend will show 2-3 DNS records to add. Add them to your DNS provider:
   - **SPF** — TXT record: `v=spf1 include:_spf.resend.io ~all`
   - **DKIM** — CNAME record (specific to your domain, Resend provides the value)
   - **MX** — only if receiving emails (not needed for sending only)
4. Click "Verify" in Resend dashboard. Domain shows green checkmark when verified.
5. Copy the API key from Resend → add to Render env vars as `RESEND_API_KEY`
6. Deploy — emails will now go out from `hello@shouldiholdoff.live` with proper DKIM/SPF

### Checking domain status

Run this in the Render shell to check verification status:

```bash
node -e "const {getDomainStatus}=require('./services/email');getDomainStatus().then(r=>console.log(JSON.stringify(r,null,2))).catch(e=>console.error(e.message))"
```

## Endpoints

- `GET /` — Landing page
- `GET /filter` — Message filter tool
- `GET /download` — Android APK download
- `GET /health` — Health check (no DB query — safe for Neon auto-suspend)
- `POST /api/blast/send-waitlist` — Trigger waitlist email blast (requires `x-blast-secret` header)

## Deployment

Render deploys automatically from the `main` branch. Migrations run on every deploy via `npm run build`.