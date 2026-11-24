# JARVIS AI V4 — Feature status

The existing V3 Android project has been upgraded in source. CI is configured to run tests and produce a downloadable debug APK. Hardware-dependent voice behavior, a deployed AI Worker, and release signing still require owner/device setup and testing.

## Implemented in source (device testing pending)
| Area | Details |
|---|---|
| Hands-free voice | Opt-in “Hey JARVIS” detection with Android on-device speech recognition, automatic wake-listening restart and follow-up turns; explicit visible status in the UI |
| Background listening | User-started microphone foreground service and persistent notification controls; microphone and notification permission handling |
| Languages and speech | English, Urdu, Hindi locale choices; text-to-speech response; language/script-aware reply speech |
| AI conversation | HTTPS proxy client with bounded recent conversation context, transport/error handling, retry for transient failures; conversation history persistence is opt-in |
| AI proxy | Cloudflare Worker source keeps the provider key server-side, validates/bounds requests, applies configured rate limiting, and returns sanitized errors |
| Android actions | Existing command router uses supported intents/APIs for app launching, web search, navigation, volume/media, alarms/timers, reminders, flashlight, notes/tasks, and confirmation-gated messaging/calls |
| UI | Existing Compose chat, voice status/visualizer, settings, notes/tasks, theme, splash, adaptive icon |
| Reminders | Room-backed one-shot reminders scheduled with AlarmManager and restored after reboot |
| CI | GitHub Actions proxy tests, Android unit tests, debug APK build, and `jarvis-ai-debug-apk` artifact |

## Setup and platform limitations
- AI chat requires deploying `proxy/worker.js`, storing the provider key as a Cloudflare secret, and setting `JARVIS_PROXY_URL` to the actual HTTPS `/chat` endpoint in local `local.properties`; rebuild after changing it. The URL is not a secret. The API key must never be shipped in the APK.
- Wake phrase is English (“Hey JARVIS”), and requires Android 12+ plus an installed on-device recognizer. Command locale is selectable, but recognition availability/quality and Urdu/Hindi models depend on the device speech service.
- Wake detection uses a repeated speech-recognition session, not a dedicated low-power DSP hotword engine; expect increased battery use. Background/locked-screen mode must be explicitly started while the app is visible and requires a persistent microphone notification. Android restrictions, microphone privacy settings, battery saver, and manufacturer optimization may still stop it; there is no boot-time auto-start or hidden listening.
- App-specific operations inside YouTube, Play Store, Chrome, and other apps may not be exposed through supported Android intents. JARVIS can launch apps or open general web searches where supported but cannot invisibly tap/type in third-party apps. Some system actions require user confirmation or manual completion.
- Android may defer non-exact reminders during Doze. Reminder and foreground-service notification behavior depends on runtime notification permission (Android 13+).
- WhatsApp/SMS are not auto-sent; the user reviews and taps Send. Calls open the dialer; the user places the call. Android restricts changing some settings directly.
- Physical-device testing, live Worker validation, public privacy policy, release signing, and Play Console declarations remain necessary before release.
