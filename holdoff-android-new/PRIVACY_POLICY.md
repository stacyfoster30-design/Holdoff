# HoldOff Privacy Policy

**Last updated:** June 2026

## Overview

HoldOff ("the App") is a default dialer and SMS replacement app that uses AI to help you pause before sending emotionally reactive messages. This policy explains exactly what data we collect, how we use it, and your rights.

---

## What Data We Collect

### On-Device AI Mode (Default)
When you use **On-Device AI** (the default setting), your message content is processed entirely on your device using Google's Gemini Nano model. **No message content is transmitted to HoldOff servers or any third party.**

### Cloud AI Mode (Optional)
If you choose to enable **Cloud AI** in Settings, the text of messages you submit for analysis is sent to HoldOff's servers for processing. This data is:
- Used solely to generate the AI verdict for that message
- Not stored beyond the duration of the API call
- Not used to train AI models
- Not shared with third parties

### Call and SMS Data
HoldOff requests access to your phone calls and SMS messages to function as your default dialer and messaging app. This data:
- **Never leaves your device** unless you explicitly enable Cloud AI
- Is not uploaded, synced, or shared with HoldOff or any third party
- Is accessed locally only to display your call history, contacts, and message threads

### Account Data (Optional)
If you create a HoldOff account, we collect your email address and authentication token. This is used solely for account management and subscription billing.

### Analytics
We collect anonymous, aggregated usage statistics (e.g., number of filter analyses performed, feature usage frequency). This data cannot be linked to individual users or messages.

---

## Sensitive Permissions

| Permission | Why We Need It |
|---|---|
| `READ_PHONE_STATE` | Detect incoming calls to show the dialer |
| `CALL_PHONE` | Make outgoing calls |
| `READ_CALL_LOG` / `WRITE_CALL_LOG` | Display and update call history |
| `READ_CONTACTS` | Show caller names in the dialer |
| `SEND_SMS` | Send text messages on your behalf |
| `RECEIVE_SMS` / `READ_SMS` | Receive and display incoming messages |
| `WRITE_SMS` | Mark messages as read |

---

## AI Disclosure

Before your first message analysis, the App displays a clear disclosure that your message text will be processed by AI. You can opt out at any time by disabling the filter in Settings.

The AI companions (Sadie, Stacy, Danny, Dan) are AI characters, not licensed therapists. If you are in crisis, please call or text **988** (Suicide & Crisis Lifeline).

---

## Data Retention

- On-device analysis history: stored locally, deleted when you clear history in Settings
- Cloud AI requests: not retained after processing
- Account data: retained until account deletion

---

## Your Rights

You may request deletion of your account and any associated data by emailing **privacy@holdoff.app**.

---

## Contact

**HoldOff, Inc.**
privacy@holdoff.app
