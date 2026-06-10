/**
 * Welcome email — fires once per user within 60s of account creation.
 * Owns: welcome email HTML + text template, welcome event logging.
 * Does NOT own: sending infrastructure (services/email.js), dedup (db/users.js markWelcomeSent).
 */

const BASE_URL = process.env.APP_URL || 'https://shouldiholdoff.live';

const FILTER_UTM = `${BASE_URL}/filter?utm_source=email&utm_medium=transactional&utm_campaign=welcome`;
const FILTER_INBOUND = `${BASE_URL}/filter?tab=inbound&utm_source=email&utm_medium=transactional&utm_campaign=welcome`;
const REFERRALS = `${BASE_URL}/referrals?utm_source=email&utm_medium=transactional&utm_campaign=welcome`;

/**
 * Build the welcome email payload.
 * @param {Object} opts
 * @param {string} opts.email  — recipient email
 * @param {string} [opts.name] — display name (optional)
 * @returns {{ subject, html, text }}
 */
function buildWelcomeEmail({ email, name }) {
  const subject = 'Your hold-off starts now.';
  const greeting = name ? name.split(' ')[0] : null;
  const greetingLine = greeting ? `Hey ${greeting},` : 'Hey,';

  const html = `
<div style="font-family:Georgia,serif;max-width:520px;margin:0 auto;color:#2A2522;line-height:1.7;">
  <h2 style="font-size:1.4rem;font-weight:600;letter-spacing:-0.02em;margin-bottom:1.25rem;">
    Your hold-off starts now.
  </h2>
  <p style="margin-bottom:1rem;">${greetingLine}</p>
  <p style="margin-bottom:1.5rem;">
    You signed up because something at 11 PM kept winning. Here's how to flip that.
  </p>
  <ul style="padding-left:1.25rem;margin-bottom:1.75rem;line-height:2;">
    <li>
      Paste the text before you send it —
      <a href="${FILTER_UTM}" style="color:#C97B5D;text-decoration:none;">open HoldOff</a>
    </li>
    <li>
      Try the inbound interpreter when you're reading too much into their reply —
      <a href="${FILTER_INBOUND}" style="color:#C97B5D;text-decoration:none;">inbound mode</a>
    </li>
    <li>
      Got a friend whose 11 PM texts run their life? Send them your link —
      <a href="${REFERRALS}" style="color:#C97B5D;text-decoration:none;">/referrals</a>
    </li>
  </ul>
  <p style="margin-bottom:2rem;">
    <a href="${FILTER_UTM}"
       style="display:inline-block;background:#C97B5D;color:#fff;padding:0.75rem 1.75rem;
              text-decoration:none;border-radius:4px;font-weight:600;letter-spacing:0.01em;">
      Open HoldOff →
    </a>
  </p>
  <hr style="border:none;border-top:1px solid #E5DED4;margin:1.5rem 0;" />
  <p style="font-size:0.8rem;color:#8A7F79;margin:0;">
    PS — Reply to this email if anything's broken. A human reads it.
  </p>
</div>`;

  const text = `${greetingLine}

You signed up because something at 11 PM kept winning. Here's how to flip that.

• Paste the text before you send it: ${FILTER_UTM}
• Try the inbound interpreter when you're reading too much into their reply: ${FILTER_INBOUND}
• Got a friend whose 11 PM texts run their life? Send them your link: ${REFERRALS}

Open HoldOff: ${FILTER_UTM}

---
PS — Reply to this email if anything's broken. A human reads it.`;

  return { subject, html, text };
}

module.exports = { buildWelcomeEmail };
