# HoldOff Android App

**HoldOff — Build Your Pause**

A fully Play Store-compliant native Android app (Kotlin + Jetpack Compose) that replaces your default dialer and SMS app, with on-device Gemini Nano AI and animated AI companions.

---

## Architecture

```
holdoff-android/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml          ← All permissions + intent filters
│   │   ├── java/com/holdoff/app/
│   │   │   ├── HoldOffApplication.kt    ← Hilt entry point + notification channels
│   │   │   ├── api/
│   │   │   │   ├── AiEngine.kt          ← On-device Gemini Nano + cloud fallback
│   │   │   │   └── HoldOffCloudApi.kt   ← Cloud API client (Retrofit)
│   │   │   ├── di/
│   │   │   │   └── AppModule.kt         ← Hilt dependency injection
│   │   │   ├── receiver/
│   │   │   │   ├── SmsReceiver.kt       ← Incoming SMS handler
│   │   │   │   ├── MmsReceiver.kt       ← Incoming MMS handler
│   │   │   │   └── BootReceiver.kt      ← Boot completed handler
│   │   │   ├── service/
│   │   │   │   └── HoldOffInCallService.kt  ← TelecomManager InCallService
│   │   │   ├── ui/
│   │   │   │   ├── MainActivity.kt      ← Navigation + permission flows
│   │   │   │   ├── dialer/
│   │   │   │   │   ├── DialerScreen.kt  ← Keypad + call history
│   │   │   │   │   └── InCallActivity.kt ← Active call screen
│   │   │   │   ├── sms/
│   │   │   │   │   ├── SmsScreen.kt     ← Inbox + conversation threads
│   │   │   │   │   └── SmsComposeActivity.kt ← Handles SENDTO intents
│   │   │   │   ├── filter/
│   │   │   │   │   ├── FilterScreen.kt  ← Verdict UI + spiral lock
│   │   │   │   │   ├── FilterViewModel.kt
│   │   │   │   │   └── FilterInterceptActivity.kt
│   │   │   │   ├── companions/
│   │   │   │   │   ├── CompanionsScreen.kt  ← Animated avatar cards + chat
│   │   │   │   │   └── CompanionChatViewModel.kt ← Expression detection
│   │   │   │   ├── settings/
│   │   │   │   │   ├── SettingsScreen.kt    ← AI mode, accessibility, privacy
│   │   │   │   │   └── SettingsViewModel.kt
│   │   │   │   └── theme/
│   │   │   │       └── HoldOffTheme.kt  ← Dark purple brand theme
│   │   │   └── util/
│   │   │       └── PreferencesManager.kt ← DataStore preferences
│   │   └── res/
│   │       └── values/
│   │           ├── strings.xml
│   │           ├── themes.xml
│   │           └── colors.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
├── PRIVACY_POLICY.md
├── PLAY_STORE_LISTING.md
└── README.md
```

---

## Build Requirements

- **Android Studio Hedgehog** (2023.1.1) or later
- **JDK 17**
- **Android SDK 34** (target), **SDK 26** (minimum)
- **Kotlin 1.9.x**

---

## Build Instructions

### 1. Clone and open
```bash
git clone <repo-url>
```
Open in Android Studio → File → Open → select `holdoff-android/`

### 2. Configure backend URL
In `app/src/main/java/com/holdoff/app/api/HoldOffCloudApi.kt`, update:
```kotlin
private const val BASE_URL = "https://your-holdoff-backend.manus.space/"
```
Replace with your actual HoldOff web app URL.

### 3. Update companion avatar URLs
In `app/src/main/java/com/holdoff/app/ui/companions/CompanionsScreen.kt`, update the `avatarUrl` and `expressionUrls` in the `companions` list with the actual URLs returned by `manus-upload-file --webdev` from the web project.

### 4. Build debug APK
```
Build → Build Bundle(s) / APK(s) → Build APK(s)
```
APK location: `app/build/outputs/apk/debug/app-debug.apk`

### 5. Build release APK (for Play Store)
1. Generate a signing keystore: `Build → Generate Signed Bundle/APK`
2. Select APK, create or use existing keystore
3. Build release variant
4. Submit to Google Play Console

---

## On-Device AI (Gemini Nano)

HoldOff uses **Android AICore** (Gemini Nano) for on-device AI processing. This is available on:
- Pixel 8 and later
- Samsung Galaxy S24 and later
- Other Android 14+ devices with AICore support

For devices without AICore, the app automatically falls back to the HoldOff cloud API (with user disclosure).

To check AICore availability at runtime, the `AiEngine` class handles this automatically.

---

## Companion Avatar URLs

After running `manus-upload-file --webdev` in the web project, update these URLs in `CompanionsScreen.kt`:

| Companion | Expression | File |
|---|---|---|
| Sadie | neutral | sadie-neutral.png |
| Sadie | happy | sadie-happy.png |
| Sadie | thinking | sadie-thinking.png |
| Stacy | neutral | stacy-neutral.png |
| Stacy | happy | stacy-happy.png |
| Stacy | thinking | stacy-thinking.png |
| Danny | neutral | danny-neutral.png |
| Danny | happy | danny-happy.png |
| Danny | thinking | danny-thinking.png |
| Dan | neutral | dan-neutral.png |
| Dan | happy | dan-happy.png |
| Dan | thinking | dan-thinking.png |

---

## Play Store Compliance

See `PLAY_STORE_LISTING.md` for the full store listing and `PRIVACY_POLICY.md` for the privacy policy.

Key compliance notes:
- All sensitive permissions are declared with `android:required="false"` where possible
- Default dialer/SMS role requested via `RoleManager` (Android 10+) or legacy intent
- On-device AI is the default — no message data transmitted without user consent
- Mental health disclaimer shown in all companion chat screens
- Crisis line (988) referenced in disclaimer and privacy policy

---

## License

Copyright © 2026 HoldOff, Inc. All rights reserved.
