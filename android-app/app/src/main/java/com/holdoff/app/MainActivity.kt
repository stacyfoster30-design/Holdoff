package com.holdoff.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.holdoff.app.navigation.AppNavigation
import com.holdoff.app.navigation.Routes
import com.holdoff.app.ui.theme.HoldOffTheme

/**
 * Single-activity host. All screens are Composables; navigation
 * is handled by Navigation Compose in AppNavigation.kt.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HoldOffTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    // TODO: read isPremium + isFirstLaunch from DataStore
                    var isPremium by remember { mutableStateOf(false) }
                    val isFirstLaunch by remember { mutableStateOf(true) }
                    AppNavigation(
                        navController = navController,
                        startDestination = if (isFirstLaunch) Routes.ONBOARDING else Routes.HOME,
                        isPremium = isPremium
                    )
                }
            }
        }
    }
}
