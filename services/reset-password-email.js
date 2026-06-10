/**
 * Password reset email — HoldOff transactional email.
 * Calm, reassuring tone for anxious-attachment users.
 */
const BASE_URL = process.env.APP_URL || 'https://shouldiholdoff.live';

/**
 * Build a password reset email.
 * @param {Object} opts
 * @param {string} opts.email   — recipient email
 * @param {string} opts.token  — raw reset token (64-char hex)
 * @param {string} [opts.name] — recipient first name
 * @returns {{ subject: string, text: string, html: string }}
 */
function buildResetPasswordEmail({ email, token, name }) {
  const resetUrl = `${BASE_URL}/reset-password?token=${token}`;
  const greeting = name ? `Hi ${name}` : 'Hi there';

  const html = `
<div style="font-family:Georgia,serif;max-width:520px;margin:0 auto;color:#2A2522;line-height:1.7;">
  <h2 style="font-size:1.5rem;font-weight:600;letter-spacing:-0.02em;margin-bottom:1rem;">
    Reset your HoldOff password
  </h2>
  <p style="margin-bottom:1rem;">${greeting},</p>
  <p style="margin-bottom:1.5rem;">
    You asked to reset your HoldOff password. Click the button below — it expires in 1 hour
    and can only be used once.
  </p>
  <p style="margin-bottom:1.5rem;">
    <a href="${resetUrl}"
       style="display:inline-block;background:#C97B5D;color:#fff;padding:0.75rem 1.5rem;
              text-decoration:none;border-radius:4px;font-weight:600;">
      Reset my password →
    </a>
  </p>
  <p style="color:#8A7F79;font-size:0.875rem;margin-bottom:1.5rem;">
    If you didn't request this, you can ignore this email — your account is safe.
    This link will expire in 1 hour.
  </p>
  <hr style="border:none;border-top:1px solid #E5DED4;margin:1.5rem 0;" />
  <p style="font-size:0.75rem;color:#8A7F79;">
    Don't send it yet. — HoldOff
  </p>
</div>`;

  const text = `${greeting},

Reset your HoldOff password:
${resetUrl}

This link expires in 1 hour. If you didn't request this, ignore this email — your account is safe.

Don't send it yet. — HoldOff`;

  return {
    subject: 'Reset your HoldOff password',
    text,
    html,
  };
}

module.exports = { buildResetPasswordEmail };
