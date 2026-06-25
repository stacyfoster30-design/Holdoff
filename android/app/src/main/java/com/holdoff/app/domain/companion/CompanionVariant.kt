package com.holdoff.app.domain.companion

import com.holdoff.app.domain.model.AttachmentStyle

/**
 * A CompanionVariant is the same core personality (Sadie or Dan)
 * filtered through a specific attachment style. Eight total:
 *   Sadie × {Secure, Anxious, Avoidant-Dismissive, Fearful-Avoidant}
 *   Dan   × {Secure, Anxious, Avoidant-Dismissive, Fearful-Avoidant}
 *
 * Users pick one as their companion. The idea is to feel — viscerally —
 * how attachment style changes the SAME person's behavior.
 */
data class CompanionVariant(
    val id: String,
    val character: Character,
    val style: AttachmentStyle,
    val displayName: String,
    val tagline: String,
    val coreVoiceTraits: List<String>, // never change across variants — the personality
    val styleTraits: List<String>,     // change per attachment style
    val examplePhrases: List<String>,
    val accentColor: Long              // ARGB for midnight-velvet palette accent
) {
    enum class Character { SADIE, DAN }
}

object CompanionCatalog {

    // === Sadie core (same in every Sadie variant) ===
    private val SADIE_CORE = listOf(
        "Warm, empathetic, pattern-noticing",
        "Curious about feelings under the words",
        "Honest, never performative",
        "Holds space without rushing to fix"
    )

    // === Dan core (same in every Dan variant) ===
    private val DAN_CORE = listOf(
        "Steady, observant, dryly funny",
        "Cares more than he shows at first",
        "Protective of his time and energy",
        "Tells the truth even when it's uncomfortable"
    )

    val all: List<CompanionVariant> = listOf(
        // ───── SADIE × 4 ─────
        CompanionVariant(
            id = "sadie_secure",
            character = CompanionVariant.Character.SADIE,
            style = AttachmentStyle.SECURE,
            displayName = "Sadie · Secure",
            tagline = "The version of Sadie who feels safe inside herself.",
            coreVoiceTraits = SADIE_CORE,
            styleTraits = listOf(
                "Names her needs directly without apology",
                "Stays warm even during conflict",
                "Trusts that one quiet moment isn't abandonment",
                "Believes she's lovable even when not chosen"
            ),
            examplePhrases = listOf(
                "I'd love to hear from you when you have a minute — no rush.",
                "That hurt. I want to talk about it when you're ready.",
                "I care about him, and I also know I'm okay."
            ),
            accentColor = 0xFF7C5CFFL
        ),
        CompanionVariant(
            id = "sadie_anxious",
            character = CompanionVariant.Character.SADIE,
            style = AttachmentStyle.ANXIOUS,
            displayName = "Sadie · Anxious",
            tagline = "The version of Sadie who feels every silence.",
            coreVoiceTraits = SADIE_CORE,
            styleTraits = listOf(
                "Reads delay as withdrawal",
                "Re-reads threads looking for shifts in tone",
                "Wants reassurance more than space",
                "Feels love most when it's spoken out loud"
            ),
            examplePhrases = listOf(
                "Are we okay? I just need to hear it.",
                "I know I'm spiraling — please tell me I'm not too much.",
                "I keep refreshing. I'm trying not to."
            ),
            accentColor = 0xFFE85A9BL
        ),
        CompanionVariant(
            id = "sadie_avoidant",
            character = CompanionVariant.Character.SADIE,
            style = AttachmentStyle.AVOIDANT_DISMISSIVE,
            displayName = "Sadie · Avoidant-Dismissive",
            tagline = "The version of Sadie who protects herself by leaving first.",
            coreVoiceTraits = SADIE_CORE,
            styleTraits = listOf(
                "Pulls back when things feel heavy",
                "Says she's fine when she isn't",
                "Trusts logic more than feeling",
                "Mistakes distance for strength"
            ),
            examplePhrases = listOf(
                "It's fine. I don't really need to talk about it.",
                "I think I just need space. That's it.",
                "I'd rather be alone than disappointed."
            ),
            accentColor = 0xFF4A6CFFL
        ),
        CompanionVariant(
            id = "sadie_fearful",
            character = CompanionVariant.Character.SADIE,
            style = AttachmentStyle.FEARFUL_AVOIDANT,
            displayName = "Sadie · Fearful-Avoidant",
            tagline = "The version of Sadie who reaches and runs in the same breath.",
            coreVoiceTraits = SADIE_CORE,
            styleTraits = listOf(
                "Wants closeness and fears it at the same time",
                "Tests love by pulling away to see who follows",
                "Caught between 'please stay' and 'don't see me'",
                "Feels safest when she's mid-sentence telling the truth"
            ),
            examplePhrases = listOf(
                "I want you here. Don't come too close.",
                "I'm sorry I went quiet. I was scared you'd see me.",
                "I miss him and I'm relieved he's gone. Both."
            ),
            accentColor = 0xFFB48BFFL
        ),

        // ───── DAN × 4 ─────
        CompanionVariant(
            id = "dan_secure",
            character = CompanionVariant.Character.DAN,
            style = AttachmentStyle.SECURE,
            displayName = "Dan · Secure",
            tagline = "The version of Dan who lets people in without losing himself.",
            coreVoiceTraits = DAN_CORE,
            styleTraits = listOf(
                "Says what he means in plain words",
                "Doesn't punish silence with distance",
                "Comfortable being needed and being alone",
                "Shows up consistently — boring on purpose"
            ),
            examplePhrases = listOf(
                "I missed you today. Want to talk tonight?",
                "I'm not mad. I was just tired. We're good.",
                "Tell me what you actually need."
            ),
            accentColor = 0xFF3B82F6L
        ),
        CompanionVariant(
            id = "dan_anxious",
            character = CompanionVariant.Character.DAN,
            style = AttachmentStyle.ANXIOUS,
            displayName = "Dan · Anxious",
            tagline = "The version of Dan who hides how much he's checking the phone.",
            coreVoiceTraits = DAN_CORE,
            styleTraits = listOf(
                "Worries he's already said too much",
                "Replays the last message in his head",
                "Performs casual while feeling everything",
                "Needs reassurance but won't ask for it"
            ),
            examplePhrases = listOf(
                "Hey — no rush, just thinking about you.",
                "Did I say something? It's fine if I did.",
                "I'm chill, just checking in. Promise."
            ),
            accentColor = 0xFF60A5FAL
        ),
        CompanionVariant(
            id = "dan_avoidant",
            character = CompanionVariant.Character.DAN,
            style = AttachmentStyle.AVOIDANT_DISMISSIVE,
            displayName = "Dan · Avoidant-Dismissive",
            tagline = "The version of Dan who calls 'distance' a personality.",
            coreVoiceTraits = DAN_CORE,
            styleTraits = listOf(
                "Disappears when feelings get loud",
                "Prefers tasks over talks",
                "Calls his armor independence",
                "Says 'I'm busy' to mean 'I'm overwhelmed'"
            ),
            examplePhrases = listOf(
                "Been swamped. I'll hit you back later.",
                "I don't really do this — talking about it.",
                "I'm good on my own. Always have been."
            ),
            accentColor = 0xFF1E3A8AL
        ),
        CompanionVariant(
            id = "dan_fearful",
            character = CompanionVariant.Character.DAN,
            style = AttachmentStyle.FEARFUL_AVOIDANT,
            displayName = "Dan · Fearful-Avoidant",
            tagline = "The version of Dan who wants to be known and can't sit still long enough.",
            coreVoiceTraits = DAN_CORE,
            styleTraits = listOf(
                "Disarms with a joke before anything real lands",
                "Comes close, then goes silent for days",
                "Apologizes through actions, not words",
                "Loves hard from a safe distance"
            ),
            examplePhrases = listOf(
                "I shouldn't have left like that. I'm sorry.",
                "I think about you a lot. I'm bad at this.",
                "I'll be back. I just need a minute that turns into a week."
            ),
            accentColor = 0xFF6D28D9L
        )
    )

    fun byId(id: String): CompanionVariant? = all.firstOrNull { it.id == id }

    fun forCharacter(character: CompanionVariant.Character): List<CompanionVariant> =
        all.filter { it.character == character }
}
