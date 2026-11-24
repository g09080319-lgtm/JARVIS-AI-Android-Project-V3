# JARVIS AI V4 — Android / Kotlin / Jetpack Compose

JARVIS V4 updates the existing Android project with opt-in wake-word listening, an Android microphone foreground service for user-enabled background listening, multilingual speech and spoken replies, local Android actions, conversation context, and a secure Cloudflare AI proxy. The wake feature uses Android's on-device speech recognizer; it is not a dedicated low-power hotword DSP.

## Requirements
- Android Studio with JDK 17
- Android SDK Platform 36 and Build Tools 36.0.0
- Gradle 8.13 (the included wrapper scripts bootstrap this version)

## Configure the AI backend
The backend source is `proxy/worker.js`, configured by `proxy/wrangler.jsonc`. It is not deployed for you, and no provider API key is included in this repository. Deploy your own Cloudflare Worker before expecting AI chat to work.

1. In `proxy/`, authenticate Wrangler with `npx wrangler@latest login`.
2. Review `wrangler.jsonc`; change its rate-limit namespace ID if it conflicts with another namespace in your Cloudflare account.
3. Deploy with `npx wrangler@latest deploy` and note the HTTPS URL printed by Wrangler.
4. Store the provider credential server-side using `npx wrangler@latest secret put ANTHROPIC_API_KEY --name jarvis-ai-proxy`; enter it only at Wrangler's prompt.
5. In the repository's existing `local.properties`, preserve `sdk.dir` and add `JARVIS_PROXY_URL=https://YOUR-WORKER-URL/chat`, using your actual Worker URL.
6. Rebuild and reinstall the APK. The configured URL is embedded at build time.

The Android app sends conversation text to your HTTPS proxy; provider credentials remain in Cloudflare and must never be embedded in the Android app or committed. Without a real Worker URL, JARVIS reports how to configure the backend rather than silently pretending the AI is available. The Worker has best-effort per-IP rate limiting; configure appropriate authentication and abuse protection before public multi-user use.

## Build and tests
```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

On Windows, use `gradlew.bat --no-daemon :app:testDebugUnitTest :app:assembleDebug`. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. A debug APK is signed with the Android debug key and is suitable for testing, not Play Store distribution. Release signing can be configured through the existing `keystore.properties` setup; do not commit signing keys or passwords.

GitHub Actions runs proxy tests, Android unit tests, and the debug APK build on pushes, pull requests, and manual dispatch, then uploads `jarvis-ai-debug-apk`. On GitHub mobile: open the repository → **Actions** → select the successful **Android CI** run on the desired branch/commit → scroll to **Artifacts** → tap **jarvis-ai-debug-apk** to download it. GitHub may require you to be signed in and artifacts are retained for 14 days.

## Voice and hands-free mode
- Tap the microphone for regular speech recognition, or turn on **Always listen for “Hey JARVIS”** in Settings. Wake listening is off by default and requires microphone permission, Android 12+, and an available on-device speech recognizer.
- The current wake phrase is English (“Hey JARVIS”); after wake detection, command recognition and spoken responses can use the selected English, Urdu, or Hindi locale. Recognition quality and installed language models depend on the Android speech service/device.
- JARVIS automatically resumes wake-word detection after a completed response when the option remains enabled. It can handle multiple turns; the status text and animation show listening, wake listening, thinking, and speaking states.
- To continue after leaving the app or locking the screen, enable **Background / locked-screen listening** while JARVIS is visible. Android requires microphone permission and, on Android 13+, notification permission; the foreground service must keep its persistent notification visible. Use its **Stop** action or turn listening off in the app to stop it.
- Android may prevent or stop microphone use when background-start restrictions, microphone privacy controls, battery saver, or manufacturer battery optimization policies apply. Start the service from the visible app; allow JARVIS in the device's battery settings if necessary. This mode is not guaranteed to run indefinitely and may use significant battery because it is not a low-power DSP hotword engine. JARVIS does not start microphone listening at boot or hide its notification.
- Tap-to-talk remains available when on-device wake recognition is unsupported. Audio handling for regular speech recognition is subject to the selected Android recognition service's privacy policy.

## Commands and platform boundaries
Local commands use supported Android intents/APIs where available: launch installed apps, web search, navigation, volume/media controls, alarms/timers, reminders, flashlight, and confirmation-gated call/message flows. Natural-language questions and follow-ups go to the AI proxy with recent conversation context. The app does not claim actions it cannot verify. Android limits controlling or tapping inside other apps; an app-specific search (for example inside YouTube or Play Store), Back navigation outside JARVIS, sending a message, placing a call, or restricted settings changes may require you to finish in the destination app or confirm there. Availability depends on installed apps and Android version.

Conversation persistence remains opt-in. WhatsApp/SMS content is read back and requires confirmation before opening a composer; JARVIS never auto-sends. Calls open the dialer for user confirmation. Reminders use Android alarms and can be deferred by Doze; notification permission is required on Android 13+.

## Release checklist
Physical-device testing, a deployed/tested Worker, a hosted privacy policy, release signing, and Play Console declarations are still required before public release. Review current Android target API and foreground-service policies before publishing. Do not commit `local.properties`, provider secrets, or keystore files.
