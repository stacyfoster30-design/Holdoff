package com.holdoff.app.data.model

/**
 * A trusted contact — someone the user can always reach, and who can
 * release a Spiral Lock. Persisted by
 * [com.holdoff.app.data.prefs.TrustedContactsStore]; consumed by the
 * Spiral Lock engine and the Trusted Contacts screen.
 */
data class TrustedContact(
    val name: String,
    val phone: String,
    val relation: String
)
