/**
 * Fast JWT decoding for rate limiting purposes.
 * Decodes without verification (safe because it's only used for grouping rate limits).
 * Avoids expensive crypto verification on every request.
 */

/**
 * Decode JWT payload without verification.
 * Returns null if token is malformed.
 *
 * @param {string} token - JWT token
 * @returns {object|null} Decoded payload or null
 */
function decodeJwtFast(token) {
  if (!token || typeof token !== 'string') return null;

  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;

    // Decode the payload (second part)
    const payload = parts[1];
    const padded = payload + '=='.substring(0, (4 - (payload.length % 4)) % 4);
    const decoded = Buffer.from(padded, 'base64').toString('utf-8');
    return JSON.parse(decoded);
  } catch (_err) {
    return null;
  }
}

module.exports = { decodeJwtFast };
