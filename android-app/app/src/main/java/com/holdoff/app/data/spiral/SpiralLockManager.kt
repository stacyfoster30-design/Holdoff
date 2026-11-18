package com.holdoff.app.data.spiral

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * SpiralLockManager — the Spiral Lock engine (Phase 1 rebuild).
 *
 * The audit found Spiral Lock was a label, not a lock: a server string
 * mapped to overlay text, with no timer and no blocking. This manager is
 * the real engine:
 *
 *  - State is PERSISTED (SharedPreferences) so a lock survives process
 *    death and reboot — a lock that evaporates when the app restarts is
 *    not a lock.
 *  - While locked, the send gate blocks ALL sends except messages to
 *    trusted contacts (the product rule from the Trusted Contacts
 *    screen: "During Spiral Lock, all messaging is paused — except to
 *    your trusted contacts").
 *  - Release paths:
 *      TIMER_EXPIRED   — the cooldown runs out.
 *      TRUSTED_CONTACT — a trusted contact texts the unlock phrase
 *                        ("unlock") to the user; the incoming-SMS
 *                        processor releases the lock.
 *      CALM_SENTENCE   — the user types "unlock" plus a calming
 *                        sentence (>= 4 words) in the lock UI
 *                        (carried over from the archived design).
 *
 * Default cooldown: 10 minutes (archived SpiralLock.DEFAULT_LOCK_MS).
 */
object SpiralLockManager {

    private const val PREFS_NAME = "holdoff_spiral_lock"
    private const val KEY_LOCKED_UNTIL = "locked_until_ms"
    private const val KEY_ENGAGED_AT = "engaged_at_ms"
    private const val KEY_ENGAGE_REASON = "engage_reason"
    private const val KEY_LAST_RELEASE = "last_release_reason"

    const val DEFAULT_LOCK_MS = 10L * 60L * 1000L
    const val UNLOCK_PHRASE = "unlock"

    enum class ReleaseReason { TIMER_EXPIRED, TRUSTED_CONTACT, CALM_SENTENCE }

    data class LockState(
        val locked: Boolean = false,
        val untilMs: Long = 0L,
        val remainingMs: Long = 0L,
        val engageReason: String? = null
    )

    private val _state = MutableStateFlow(LockState())
    val state: StateFlow<LockState> = _state

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Recomputes state from persisted values, auto-releasing on expiry.
     * Call before trusting [state] or [isLocked] — cheap and idempotent.
     */
    fun refresh(ctx: Context): LockState {
        val until = prefs(ctx).getLong(KEY_LOCKED_UNTIL, 0L)
        val now = System.currentTimeMillis()
        val s = when {
            until <= 0L -> LockState(locked = false)
            now >= until -> {
                // Cooldown elapsed: release with TIMER_EXPIRED exactly once.
                prefs(ctx).edit()
                    .remove(KEY_LOCKED_UNTIL)
                    .remove(KEY_ENGAGED_AT)
                    .putString(KEY_LAST_RELEASE, ReleaseReason.TIMER_EXPIRED.name)
                    .apply()
                LockState(locked = false)
            }
            else -> LockState(
                locked = true,
                untilMs = until,
                remainingMs = until - now,
                engageReason = prefs(ctx).getString(KEY_ENGAGE_REASON, null)
            )
        }
        _state.value = s
        return s
    }

    fun isLocked(ctx: Context): Boolean = refresh(ctx).locked

    fun remainingMs(ctx: Context): Long = refresh(ctx).remainingMs

    /** Engages (or extends) the lock. [reason] is shown in the lock UI. */
    fun engage(ctx: Context, durationMs: Long = DEFAULT_LOCK_MS, reason: String = "Spiral detected") {
        val now = System.currentTimeMillis()
        val until = maxOf(prefs(ctx).getLong(KEY_LOCKED_UNTIL, 0L), now + durationMs)
        prefs(ctx).edit()
            .putLong(KEY_LOCKED_UNTIL, until)
            .putLong(KEY_ENGAGED_AT, now)
            .putString(KEY_ENGAGE_REASON, reason)
            .apply()
        refresh(ctx)
    }

    fun release(ctx: Context, reason: ReleaseReason) {
        prefs(ctx).edit()
            .remove(KEY_LOCKED_UNTIL)
            .remove(KEY_ENGAGED_AT)
            .putString(KEY_LAST_RELEASE, reason.name)
            .apply()
        refresh(ctx)
    }

    /**
     * Trusted-contact release: true when [body] contains the unlock
     * phrase. Caller must separately verify the sender is a trusted
     * contact (see TrustedContactsStore.isTrusted) — a stranger texting
     * "unlock" must never release a lock.
     */
    fun isUnlockPhrase(body: String): Boolean =
        body.lowercase().contains(UNLOCK_PHRASE)

    /**
     * Calm-sentence release (archived design): the user types "unlock"
     * plus a calming sentence. Requires the phrase and at least 4 words
     * so a reflexive one-word tap can't defeat the lock.
     */
    fun isCalmSentence(text: String): Boolean {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return words.size >= 4 && text.lowercase().contains(UNLOCK_PHRASE)
    }
}
