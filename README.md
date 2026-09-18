# Alerting — email alert on mains power (220V) loss

Android app that monitors the phone's mains power. As soon as the phone is
unplugged from the wall (switches to battery), it sends an alert email, then
**resends it every N minutes** while the phone stays on battery. An email is also
sent when mains power is restored, and — optionally — when the battery drops below
a configurable threshold.

Emails are sent through a Gmail account (SMTP) using a **Google app password**.

## Screenshots

> These are high-fidelity **design mockups** of the app (the layout matches the
> actual UI), not device screenshots.

| Settings screen | Notification & alert emails |
|---|---|
| ![Settings screen](docs/images/settings-screen.svg) | ![Notification and emails](docs/images/alert-notification.svg) |

## Features

- Immediate email when mains power is lost.
- Periodic resend every N minutes (N configurable, ≥ 1).
- Restored-power email when mains comes back.
- **Multiple recipients** (comma / semicolon / newline separated).
- **Low-battery alert** with an on/off toggle and a configurable threshold —
  sent once when the battery crosses below the threshold, only while on battery.
- **"Test send now"** button to verify the settings immediately.
- Automatic restart of monitoring after a phone reboot.
- All parameters editable in the app (sender email, app password, recipients,
  frequency, low-battery threshold, SMTP host/port).
- App password stored encrypted (EncryptedSharedPreferences).

## 1. Google prerequisite (app password)

Google no longer allows SMTP sending with your normal account password.

1. Enable **2-Step Verification** on the sending Gmail account:
   https://myaccount.google.com/security
2. Generate an **app password**:
   https://myaccount.google.com/apppasswords
3. Copy the 16-character value — that is what you enter in the app
   ("Google app password" field).

## 2. Build the APK (cloud build, nothing to install)

The project builds automatically via **GitHub Actions**.

1. Push the project to a GitHub repository:
   ```bash
   git remote add origin https://github.com/<you>/alerting.git
   git push -u origin main
   ```
2. Open the repository's **Actions** tab. The "Build APK" workflow runs on every
   push (and manually via "Run workflow").
3. When it finishes, download the **`alerting-debug-apk`** artifact — it contains
   `app-debug.apk`.

## 3. Install on the phone

1. Transfer `app-debug.apk` to the phone.
2. Allow installation from unknown sources, then install the APK.
3. Open the app, fill in the fields, tap **Start**.
4. Accept the **notifications** permission prompt (Android 13+).

## 4. Background reliability (IMPORTANT)

Some manufacturers (Samsung, Xiaomi, Huawei, Oppo…) aggressively kill background
services. To make sure alerts are sent:

- Settings → Apps → **Alerting** → Battery → **Unrestricted / Allow background
  activity**.
- Disable battery optimization for this app.

Without this, the system may kill the service and no emails will be sent.

## 5. How it works

| Event | Action |
|---|---|
| Mains power unplugged | Immediate email + resend every N min |
| On battery | One email every N minutes |
| Battery drops below threshold (if enabled) | One low-battery email (once per crossing) |
| Mains power restored | Restored-power email, resends stop |
| Phone reboot | Monitoring resumes if it was active |

> Note: detection is based on power connect/disconnect (mains or USB). A 220V wall
> charger maps to the "powered" state.

## 6. Development / tests

Business logic is tested on the pure JVM (no device required):

```bash
gradle testDebugUnitTest
```

- `AlertStateMachine` — mains ↔ battery transitions.
- `AlertSettings` — parameter validation, multi-recipient parsing, low-battery
  threshold rules.

## Project structure

```
app/src/main/java/com/sophiaengineering/alerting/
  AlertSettings.kt         # data class + validation + recipient parsing (pure)
  AlertStateMachine.kt     # transition logic (pure)
  EmailSender.kt           # interface
  SmtpEmailSender.kt       # SMTP sending (JavaMail), multi-recipient
  SettingsStore.kt         # encrypted persistence
  MonitoringService.kt     # foreground service + timer loop + battery watch
  PowerConnectionReceiver.kt
  BootReceiver.kt
  SettingsActivity.kt      # settings UI + test-send button
```

Design spec (French): `docs/superpowers/specs/2026-09-18-alerting-power-email-design.md`
