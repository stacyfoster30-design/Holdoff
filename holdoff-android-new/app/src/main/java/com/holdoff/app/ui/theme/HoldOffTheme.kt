package com.holdoff.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Brand palette ─────────────────────────────────────────────────────────────

object HoldOffColors {
    val Background = Color(0xFF0D0D14)
    val Surface = Color(0xFF16161F)
    val SurfaceVariant = Color(0xFF1E1E2A)
    val Divider = Color(0xFF2A2A38)

    val Purple = Color(0xFF8B5CF6)
    val PurpleContainer = Color(0xFF2D1F4E)
    val PurpleDeep = Color(0xFF6D28D9)

    val Text = Color(0xFFF1F0FF)
    val TextMuted = Color(0xFF8B8AA8)

    val Green = Color(0xFF22C55E)
    val GreenBg = Color(0xFF0D2B1A)

    val Amber = Color(0xFFF59E0B)
    val AmberBg = Color(0xFF2B1F0A)

    val Rose = Color(0xFFF43F5E)
    val RoseBg = Color(0xFF2B0D14)

    // Companion accent colors
    val CompanionSadie = Color(0xFF34D399)   // Emerald — warm, grounded
    val CompanionStacy = Color(0xFFF472B6)   // Pink — raw, edgy
    val CompanionDanny = Color(0xFF60A5FA)   // Blue — calm, logical
    val CompanionDan = Color(0xFFFBBF24)     // Amber — energetic, hype
}

private val DarkColorScheme = darkColorScheme(
    primary = HoldOffColors.Purple,
    onPrimary = Color.White,
    primaryContainer = HoldOffColors.PurpleContainer,
    onPrimaryContainer = HoldOffColors.Text,
    secondary = HoldOffColors.CompanionSadie,
    onSecondary = Color.Black,
    background = HoldOffColors.Background,
    onBackground = HoldOffColors.Text,
    surface = HoldOffColors.Surface,
    onSurface = HoldOffColors.Text,
    surfaceVariant = HoldOffColors.SurfaceVariant,
    onSurfaceVariant = HoldOffColors.TextMuted,
    error = HoldOffColors.Rose,
    onError = Color.White,
    outline = HoldOffColors.Divider
)

@Composable
fun HoldOffTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
