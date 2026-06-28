package com.holdoff.app.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.holdoff.app.ui.companions.Companion
import com.holdoff.app.ui.companions.CompanionChatScreen
import com.holdoff.app.ui.companions.CompanionsScreen
import com.holdoff.app.ui.companions.companions
import com.holdoff.app.ui.dialer.DialerScreen
import com.holdoff.app.ui.filter.FilterScreen
import com.holdoff.app.ui.settings.SettingsScreen
import com.holdoff.app.ui.sms.SmsConversationScreen
import com.holdoff.app.ui.sms.SmsInboxScreen
import com.holdoff.app.ui.sms.SmsThread
import com.holdoff.app.ui.theme.HoldOffColors
import com.holdoff.app.ui.theme.HoldOffTheme
import dagger.hilt.android.AndroidEntryPoint

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dialer : Screen("dialer", "Phone", Icons.Default.Phone)
    object Messages : Screen("messages", "Messages", Icons.Default.Message)
    object Filter : Screen("filter", "Filter", Icons.Default.Psychology)
    object Companions : Screen("companions", "Companions", Icons.Default.People)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

val bottomNavScreens = listOf(
    Screen.Dialer,
    Screen.Messages,
    Screen.Filter,
    Screen.Companions
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requiredPermissions = buildList {
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.CALL_PHONE)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.WRITE_CALL_LOG)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.WRITE_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HoldOffTheme {
                HoldOffApp(
                    openSmsThread = intent?.getStringExtra("sms_sender"),
                    openCompose = intent?.getBooleanExtra("open_compose", false) == true,
                    composeRecipient = intent?.getStringExtra("compose_recipient")
                )
            }
        }
    }
}

@Composable
fun HoldOffApp(
    openSmsThread: String? = null,
    openCompose: Boolean = false,
    composeRecipient: String? = null
) {
    val context = LocalContext.current
    val navController = rememberNavController()

    var permissionsGranted by remember { mutableStateOf(false) }
    var showPermissionRationale by remember { mutableStateOf(false) }
    var showDefaultDialerPrompt by remember { mutableStateOf(false) }
    var showDefaultSmsPrompt by remember { mutableStateOf(false) }
    var selectedThread by remember { mutableStateOf<SmsThread?>(null) }
    var selectedCompanion by remember { mutableStateOf<Companion?>(null) }
    var filterMessage by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
        if (!permissionsGranted) showPermissionRationale = true
        else {
            // Check default app status after permissions granted
            showDefaultDialerPrompt = !isDefaultDialer(context)
            showDefaultSmsPrompt = !isDefaultSmsApp(context)
        }
    }

    val defaultDialerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        showDefaultDialerPrompt = !isDefaultDialer(context)
        showDefaultSmsPrompt = !isDefaultSmsApp(context)
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_CALL_LOG,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.WRITE_SMS
            )
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(HoldOffColors.Background)) {
        // Main navigation
        Scaffold(
            containerColor = HoldOffColors.Background,
            bottomBar = {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                val showBottomBar = selectedThread == null && selectedCompanion == null

                if (showBottomBar) {
                    NavigationBar(
                        containerColor = HoldOffColors.Surface,
                        tonalElevation = 0.dp
                    ) {
                        bottomNavScreens.forEach { screen ->
                            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        screen.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp)
                                    )
                                },
                                label = { Text(screen.label, style = MaterialTheme.typography.labelSmall) },
                                selected = selected,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = HoldOffColors.Purple,
                                    selectedTextColor = HoldOffColors.Purple,
                                    unselectedIconColor = HoldOffColors.TextMuted,
                                    unselectedTextColor = HoldOffColors.TextMuted,
                                    indicatorColor = HoldOffColors.PurpleContainer
                                ),
                                modifier = Modifier.semantics { contentDescription = "${screen.label} tab${if (selected) ", selected" else ""}" }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            // Companion chat overlay
            AnimatedVisibility(
                visible = selectedCompanion != null,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                selectedCompanion?.let { companion ->
                    CompanionChatScreen(
                        companion = companion,
                        onBack = { selectedCompanion = null },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // SMS thread overlay
            AnimatedVisibility(
                visible = selectedThread != null && selectedCompanion == null,
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it }
            ) {
                selectedThread?.let { thread ->
                    SmsConversationScreen(
                        thread = thread,
                        onBack = { selectedThread = null },
                        onAnalyzeMessage = { msg ->
                            filterMessage = msg
                            navController.navigate(Screen.Filter.route)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (selectedThread == null && selectedCompanion == null) {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Dialer.route,
                    modifier = Modifier.padding(innerPadding)
                ) {
                    composable(Screen.Dialer.route) {
                        DialerScreen(modifier = Modifier.fillMaxSize())
                    }
                    composable(Screen.Messages.route) {
                        SmsInboxScreen(
                            onThreadClick = { selectedThread = it },
                            onNewMessage = { selectedThread = null },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    composable(Screen.Filter.route) {
                        FilterScreen(
                            initialMessage = filterMessage,
                            onSendAnyway = { filterMessage = "" },
                            onBack = { navController.popBackStack() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    composable(Screen.Companions.route) {
                        CompanionsScreen(
                            onSelectCompanion = { selectedCompanion = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // Permission rationale dialog
        if (showPermissionRationale) {
            PermissionRationaleDialog(
                onGrant = {
                    showPermissionRationale = false
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.CALL_PHONE,
                            Manifest.permission.READ_CONTACTS,
                            Manifest.permission.SEND_SMS,
                            Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.READ_SMS
                        )
                    )
                },
                onDismiss = { showPermissionRationale = false }
            )
        }

        // Default dialer prompt
        if (showDefaultDialerPrompt && !showPermissionRationale) {
            DefaultAppPrompt(
                title = "Set HoldOff as your default phone app",
                description = "To intercept calls and provide real-time AI insights, HoldOff needs to be your default phone app.",
                onSet = {
                    showDefaultDialerPrompt = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val roleManager = context.getSystemService(RoleManager::class.java)
                        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                        defaultDialerLauncher.launch(intent)
                    } else {
                        val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                            .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
                        defaultDialerLauncher.launch(intent)
                    }
                },
                onSkip = { showDefaultDialerPrompt = false }
            )
        }

        // Default SMS prompt
        if (showDefaultSmsPrompt && !showDefaultDialerPrompt && !showPermissionRationale) {
            DefaultAppPrompt(
                title = "Set HoldOff as your default messaging app",
                description = "To filter outgoing texts and receive messages, HoldOff needs to be your default SMS app.",
                onSet = {
                    showDefaultSmsPrompt = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val roleManager = context.getSystemService(RoleManager::class.java)
                        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                        defaultDialerLauncher.launch(intent)
                    } else {
                        val intent = Intent("android.provider.Telephony.ACTION_CHANGE_DEFAULT")
                            .putExtra("package", context.packageName)
                        defaultDialerLauncher.launch(intent)
                    }
                },
                onSkip = { showDefaultSmsPrompt = false }
            )
        }
    }
}

// ── Permission rationale dialog ───────────────────────────────────────────────

@Composable
private fun PermissionRationaleDialog(onGrant: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HoldOffColors.Surface,
        title = {
            Text("Permissions Required", color = HoldOffColors.Text, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    "HoldOff needs the following permissions to work as your default phone and messaging app:",
                    color = HoldOffColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                listOf(
                    "Phone — make and receive calls",
                    "SMS — send and receive messages",
                    "Contacts — display caller names",
                    "Call Log — show call history"
                ).forEach { item ->
                    Row(modifier = Modifier.padding(vertical = 3.dp)) {
                        Text("•", color = HoldOffColors.Purple, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text(item, color = HoldOffColors.Text, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Your message content is processed on-device by default and never sent to external servers without your consent.",
                    color = HoldOffColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(containerColor = HoldOffColors.Purple)
            ) {
                Text("Grant Permissions", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not Now", color = HoldOffColors.TextMuted)
            }
        }
    )
}

// ── Default app prompt ────────────────────────────────────────────────────────

@Composable
private fun DefaultAppPrompt(
    title: String,
    description: String,
    onSet: () -> Unit,
    onSkip: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = HoldOffColors.Surface)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = HoldOffColors.Purple,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(title, color = HoldOffColors.Text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(description, color = HoldOffColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onSet,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = HoldOffColors.Purple),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Set as Default", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Skip for now", color = HoldOffColors.TextMuted)
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun isDefaultDialer(context: android.content.Context): Boolean {
    return try {
        val tm = context.getSystemService(TelecomManager::class.java)
        tm.defaultDialerPackage == context.packageName
    } catch (e: Exception) { false }
}

private fun isDefaultSmsApp(context: android.content.Context): Boolean {
    return try {
        android.provider.Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    } catch (e: Exception) { false }
}
