/**
 * In-memory user data cache with TTL.
 * Prevents repeated database queries for user enrichment on every page load.
 * TTL: 5 minutes (300,000 ms)
 */

const CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes
const userCache = new Map();

/**
 * Get cached user data if fresh, otherwise null.
 */
function getCachedUser(userId) {
  const cached = userCache.get(userId);
  if (!cached) return null;

  // Check if cache expired
  if (Date.now() - cached.fetchedAt > CACHE_TTL_MS) {
    userCache.delete(userId);
    return null;
  }

  return cached.data;
}

/**
 * Set user data in cache.
 */
function setCachedUser(userId, userData) {
  userCache.set(userId, {
    data: userData,
    fetchedAt: Date.now(),
  });
}

/**
 * Clear cache for a specific user (call after membership changes).
 */
function invalidateUserCache(userId) {
  userCache.delete(userId);
}

/**
 * Clear entire cache (call after deployments or cache management).
 */
function clearAllUserCache() {
  userCache.clear();
}

/**
 * Get cache stats (for monitoring).
 */
function getCacheStats() {
  return {
    size: userCache.size,
    ttlMs: CACHE_TTL_MS,
  };
}

module.exports = {
  getCachedUser,
  setCachedUser,
  invalidateUserCache,
  clearAllUserCache,
  getCacheStats,
};
