package com.holdoff.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val HoldOffTypography = Typography(
    displayLarge   = TextStyle(FontFamily.Default, FontWeight.Bold,     fontSize = 57.sp, lineHeight = 64.sp, color = OnDarkText),
    headlineLarge  = TextStyle(FontFamily.Default, FontWeight.Bold,     fontSize = 32.sp, lineHeight = 40.sp, color = OnDarkText),
    headlineMedium = TextStyle(FontFamily.Default, FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp, color = OnDarkText),
    titleLarge     = TextStyle(FontFamily.Default, FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp, color = OnDarkText),
    titleMedium    = TextStyle(FontFamily.Default, FontWeight.Medium,   fontSize = 16.sp, lineHeight = 24.sp, color = OnDarkText),
    bodyLarge      = TextStyle(FontFamily.Default, FontWeight.Normal,   fontSize = 16.sp, lineHeight = 24.sp, color = OnDarkText),
    bodyMedium     = TextStyle(FontFamily.Default, FontWeight.Normal,   fontSize = 14.sp, lineHeight = 20.sp, color = OnDarkTextMuted),
    labelLarge     = TextStyle(FontFamily.Default, FontWeight.Medium,   fontSize = 14.sp, lineHeight = 20.sp, color = GlowPurple)
)
