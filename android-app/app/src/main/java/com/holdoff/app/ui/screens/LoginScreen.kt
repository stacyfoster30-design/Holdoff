package com.holdoff.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.holdoff.app.ui.components.SadieAvatar
import com.holdoff.app.ui.components.SadieSize
import com.holdoff.app.ui.theme.*

/** Sign In / Sign Up / Forgot Password — all in one. */
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var forgotMode by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(MidnightNavy, DeepPurple, RomanticBlue)))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            SadieAvatar(size = SadieSize.LARGE)
            Spacer(Modifier.height(16.dp))
            Text("HoldOff", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = OnDarkText)
            Text(
                when {
                    forgotMode -> "Reset your password"
                    isSignUp   -> "Create your account"
                    else       -> "Welcome back"
                },
                color = OnDarkTextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(40.dp))

            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GlowPurple, unfocusedBorderColor = DividerColor,
                    focusedTextColor = OnDarkText, unfocusedTextColor = OnDarkText,
                    cursorColor = GlowPurple, focusedLabelColor = GlowPurple
                ),
                singleLine = true
            )

            if (!forgotMode) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GlowPurple, unfocusedBorderColor = DividerColor,
                        focusedTextColor = OnDarkText, unfocusedTextColor = OnDarkText,
                        cursorColor = GlowPurple, focusedLabelColor = GlowPurple
                    ),
                    singleLine = true
                )
            }

            if (!isSignUp && !forgotMode) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { forgotMode = true }, modifier = Modifier.align(Alignment.End)) {
                    Text("Forgot Password?", color = SoftLavender, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { isLoading = true; onLoginSuccess() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VelvetPurple),
                enabled = !isLoading && email.isNotBlank()
            ) {
                Text(
                    when {
                        forgotMode -> "Send Reset Email"
                        isSignUp   -> "Create Account \u2014 Free Trial"
                        else       -> "Sign In"
                    },
                    fontWeight = FontWeight.Bold
                )
            }

            if (!forgotMode) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { onLoginSuccess() },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("  Continue with Google", color = OnDarkText)
                }
            }

            Spacer(Modifier.height(24.dp))

            if (!forgotMode) {
                TextButton(onClick = { isSignUp = !isSignUp }) {
                    Text(
                        if (isSignUp) "Already have an account? Sign In"
                        else "New here? Start free trial",
                        color = SoftLavender,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                TextButton(onClick = { forgotMode = false }) {
                    Text("Back to Sign In", color = SoftLavender)
                }
            }
        }
    }
}
