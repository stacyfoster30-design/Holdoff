package com.holdoff.app.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.holdoff.app.ui.theme.HoldOffColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val prefs by viewModel.prefs.collectAsState()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        containerColor = HoldOffColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = HoldOffColors.Text) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Go back", tint = HoldOffColors.Text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HoldOffColors.Surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {

            // ── AI Mode ───────────────────────────────────────────────────────
            SettingsSection(title = "AI Mode") {
                SettingsCard {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = HoldOffColors.Purple)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("On-device AI (Private)", color = HoldOffColors.Text, fontWeight = FontWeight.Medium)
                                Text(
                                    "Gemini Nano — your messages never leave your phone",
                                    color = HoldOffColors.TextMuted,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Switch(
                                checked = prefs.useOnDeviceAi,
                                onCheckedChange = { viewModel.setOnDeviceAi(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = HoldOffColors.Purple, checkedTrackColor = HoldOffColors.PurpleContainer),
                                modifier = Modifier.semantics {
                                    contentDescription = "On-device AI mode. ${if (prefs.useOnDeviceAi) "Enabled" else "Disabled"}. Toggle to ${if (prefs.useOnDeviceAi) "use cloud AI" else "use on-device AI"}."
                                }
                            )
                        }

                        if (!prefs.useOnDeviceAi) {
                            HorizontalDivider(color = HoldOffColors.Divider, modifier = Modifier.padding(horizontal = 16.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(HoldOffColors.AmberBg)
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = HoldOffColors.Amber, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Cloud AI sends your message text to HoldOff servers for analysis. See our Privacy Policy for details.",
                                        color = HoldOffColors.Text,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = HoldOffColors.Divider, modifier = Modifier.padding(horizontal = 16.dp))

                        SettingsToggleRow(
                            icon = Icons.Default.FilterAlt,
                            title = "Auto-filter outgoing texts",
                            subtitle = "Show HoldOff analysis before every text you send",
                            checked = prefs.autoFilterTexts,
                            onCheckedChange = { viewModel.setAutoFilterTexts(it) },
                            contentDesc = "Auto-filter outgoing texts. ${if (prefs.autoFilterTexts) "Enabled" else "Disabled"}."
                        )

                        HorizontalDivider(color = HoldOffColors.Divider, modifier = Modifier.padding(horizontal = 16.dp))

                        SettingsToggleRow(
                            icon = Icons.Default.PhoneCallback,
                            title = "Analyze incoming calls",
                            subtitle = "Show caller context and attachment pattern hints",
                            checked = prefs.analyzeIncomingCalls,
                            onCheckedChange = { viewModel.setAnalyzeIncomingCalls(it) },
                            contentDesc = "Analyze incoming calls. ${if (prefs.analyzeIncomingCalls) "Enabled" else "Disabled"}."
                        )
                    }
                }
            }

            // ── Accessibility ─────────────────────────────────────────────────
            SettingsSection(title = "Accessibility") {
                SettingsCard {
                    Column {
                        // Font size
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.TextFields, contentDescription = null, tint = HoldOffColors.Purple)
                                Spacer(Modifier.width(12.dp))
                                Text("Text size", color = HoldOffColors.Text, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                Text(
                                    "${(prefs.fontScale * 100).toInt()}%",
                                    color = HoldOffColors.Purple,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Slider(
                                value = prefs.fontScale,
                                onValueChange = { viewModel.setFontScale(it) },
                                valueRange = 0.85f..1.5f,
                                steps = 5,
                                colors = SliderDefaults.colors(thumbColor = HoldOffColors.Purple, activeTrackColor = HoldOffColors.Purple),
                                modifier = Modifier.semantics {
                                    contentDescription = "Text size slider. Current: ${(prefs.fontScale * 100).toInt()}%. Range: 85% to 150%."
                                }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("A", color = HoldOffColors.TextMuted, fontSize = 12.sp)
                                Text("A", color = HoldOffColors.TextMuted, fontSize = 20.sp)
                            }
                        }

                        HorizontalDivider(color = HoldOffColors.Divider, modifier = Modifier.padding(horizontal = 16.dp))

                        SettingsToggleRow(
                            icon = Icons.Default.Contrast,
                            title = "High contrast mode",
                            subtitle = "Increases text and border contrast for better readability",
                            checked = prefs.highContrast,
                            onCheckedChange = { viewModel.setHighContrast(it) },
                            contentDesc = "High contrast mode. ${if (prefs.highContrast) "Enabled" else "Disabled"}."
                        )

                        HorizontalDivider(color = HoldOffColors.Divider, modifier = Modifier.padding(horizontal = 16.dp))

                        SettingsToggleRow(
                            icon = Icons.Default.Vibration,
                            title = "Haptic feedback",
                            subtitle = "Vibrate on verdict results and important alerts",
                            checked = prefs.hapticFeedback,
                            onCheckedChange = { viewModel.setHapticFeedback(it) },
                            contentDesc = "Haptic feedback. ${if (prefs.hapticFeedback) "Enabled" else "Disabled"}."
                        )

                        HorizontalDivider(color = HoldOffColors.Divider, modifier = Modifier.padding(horizontal = 16.dp))

                        SettingsToggleRow(
                            icon = Icons.Default.RecordVoiceOver,
                            title = "Screen reader support",
                            subtitle = "Optimized content descriptions for TalkBack",
                            checked = prefs.screenReaderOptimized,
                            onCheckedChange = { viewModel.setScreenReaderOptimized(it) },
                            contentDesc = "Screen reader support. ${if (prefs.screenReaderOptimized) "Enabled" else "Disabled"}."
                        )

                        HorizontalDivider(color = HoldOffColors.Divider, modifier = Modifier.padding(horizontal = 16.dp))

                        SettingsToggleRow(
                            icon = Icons.Default.Animation,
                            title = "Reduce motion",
                            subtitle = "Minimize avatar animations and transitions",
                            checked = prefs.reduceMotion,
                            onCheckedChange = { viewModel.setReduceMotion(it) },
                            contentDesc = "Reduce motion. ${if (prefs.reduceMotion) "Enabled" else "Disabled"}."
                        )
                    }
                }
            }

            // ── Spiral Lock ───────────────────────────────────────────────────
            SettingsSection(title = "Spiral Lock") {
                SettingsCard {
                    Column {
                        SettingsToggleRow(
                            icon = Icons.Default.Lock,
                            title = "Enable Spiral Lock",
                            subtitle = "Lock the filter for 30 min after 3 DO NOT SEND verdicts in a row",
                            checked = prefs.spiralLockEnabled,
                            onCheckedChange = { viewModel.setSpiralLockEnabled(it) },
                            contentDesc = "Spiral Lock. ${if (prefs.spiralLockEnabled) "Enabled" else "Disabled"}."
                        )

                        HorizontalDivider(color = HoldOffColors.Divider, modifier = Modifier.padding(horizontal = 16.dp))

                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Timer, contentDescription = null, tint = HoldOffColors.Purple)
                                Spacer(Modifier.width(12.dp))
                                Text("Lock duration", color = HoldOffColors.Text, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                Text(
                                    "${prefs.spiralLockMinutes} min",
                                    color = HoldOffColors.Purple,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Slider(
                                value = prefs.spiralLockMinutes.toFloat(),
                                onValueChange = { viewModel.setSpiralLockMinutes(it.toInt()) },
                                valueRange = 5f..120f,
                                steps = 22,
                                enabled = prefs.spiralLockEnabled,
                                colors = SliderDefaults.colors(thumbColor = HoldOffColors.Purple, activeTrackColor = HoldOffColors.Purple),
                                modifier = Modifier.semantics {
                                    contentDescription = "Lock duration slider. Current: ${prefs.spiralLockMinutes} minutes."
                                }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("5 min", color = HoldOffColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                                Text("2 hrs", color = HoldOffColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // ── Privacy ───────────────────────────────────────────────────────
            SettingsSection(title = "Privacy") {
                SettingsCard {
                    Column {
                        SettingsToggleRow(
                            icon = Icons.Default.History,
                            title = "Save analysis history",
                            subtitle = "Store filter results locally for Chronicle insights",
                            checked = prefs.saveHistory,
                            onCheckedChange = { viewModel.setSaveHistory(it) },
                            contentDesc = "Save analysis history. ${if (prefs.saveHistory) "Enabled" else "Disabled"}."
                        )

                        HorizontalDivider(color = HoldOffColors.Divider, modifier = Modifier.padding(horizontal = 16.dp))

                        SettingsActionRow(
                            icon = Icons.Default.DeleteSweep,
                            title = "Clear all history",
                            subtitle = "Permanently delete all saved filter results",
                            tint = HoldOffColors.Rose,
                            onClick = { viewModel.clearHistory() },
                            contentDesc = "Clear all analysis history"
                        )

                        HorizontalDivider(color = HoldOffColors.Divider, modifier = Modifier.padding(horizontal = 16.dp))

                        SettingsActionRow(
                            icon = Icons.Default.Policy,
                            title = "Privacy Policy",
                            subtitle = "How HoldOff handles your data",
                            onClick = { /* open browser */ },
                            contentDesc = "View Privacy Policy"
                        )
                    }
                }
            }

            // ── About ─────────────────────────────────────────────────────────
            SettingsSection(title = "About") {
                SettingsCard {
                    Column {
                        SettingsActionRow(
                            icon = Icons.Default.Info,
                            title = "HoldOff",
                            subtitle = "Version 1.0.0 — Build your pause",
                            onClick = {},
                            contentDesc = "App version 1.0.0"
                        )
                        HorizontalDivider(color = HoldOffColors.Divider, modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsActionRow(
                            icon = Icons.Default.Star,
                            title = "Rate on Google Play",
                            subtitle = "Help others find HoldOff",
                            onClick = { /* open Play Store */ },
                            contentDesc = "Rate HoldOff on Google Play"
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Reusable setting row components ──────────────────────────────────────────

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Text(
        text = title.uppercase(),
        color = HoldOffColors.Purple,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
    content()
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = HoldOffColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        content()
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDesc: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { contentDescription = contentDesc },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = HoldOffColors.Purple, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = HoldOffColors.Text, fontWeight = FontWeight.Medium)
            Text(subtitle, color = HoldOffColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = HoldOffColors.Purple, checkedTrackColor = HoldOffColors.PurpleContainer)
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color = HoldOffColors.Purple,
    onClick: () -> Unit,
    contentDesc: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics { contentDescription = contentDesc },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (tint == HoldOffColors.Rose) HoldOffColors.Rose else HoldOffColors.Text, fontWeight = FontWeight.Medium)
            Text(subtitle, color = HoldOffColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = HoldOffColors.TextMuted)
    }
}
