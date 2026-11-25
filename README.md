# AIVO — One AI. Every Task.

AIVO is being delivered in phases. The current milestone has Android and Windows chat/voice clients, a shared Cloudflare Worker API, and an opt-in Android notification-announcement service. It is not a complete personal operating system yet.

## Implemented in this milestone

- **Android:** Kotlin/Jetpack Compose app branded AIVO with a custom robotic launcher icon and splash screen, responsive midnight-blue/cyan dashboard, animated AI core, voice waveform, truthful status indicators, a pulsing circuit-line composer, explicit tap-to-talk speech recognition, English/Urdu/Hindi language selection, spoken replies, in-memory conversation context, and Android Keystore encryption for the API access token.
- **Android notifications:** opt-in `NotificationListenerService` for WhatsApp and WhatsApp Business notifications; English/Urdu/Hindi announcements; message previews hidden by default; voice/call switches, quiet hours, Android-Keystore-encrypted muted sender names, duplicate suppression, and Android settings access guidance. The implementation uses only notification content Android exposes and does not access WhatsApp storage or call audio.
- **Windows 10/11:** Native .NET 10 WPF client sharing the dashboard visual identity, with animated holographic rings/circuit line, chat, Windows speech recognition and speech output, language selection, session-only conversation context, and per-user DPAPI encryption for the API access token.
- **Shared API:** Cloudflare Worker `POST /v1/chat`, RS256/ES256 OIDC JWT verification, fail-closed per-user rate limiting, allowlisted browser origins, request-size/message limits, and Anthropic forwarding with a server-side secret.
- **Build setup:** Android APK and AAB build tasks, a self-contained Windows publish, an Inno Setup installer definition, backend contract tests, and CI jobs for all three.

The clients accept a short-lived access token from an external OIDC identity provider; Google Sign-In and interactive sign-in are not implemented yet. Android accepts the canonical `AIVO_PROXY_URL` build setting and the legacy `JARVIS_PROXY_URL` / `JARVIS_API_BASE_URL` aliases; Windows accepts the same environment variables. A device-specific HTTPS origin can still be entered in Settings. Conversations are kept in process memory only and are sent to the configured AI provider when a user submits a prompt; the Worker does not store them. Notification parsing and background announcements have automated parser coverage but have not been tested against WhatsApp notifications on a physical Android device.

## Configure the shared backend

The Worker is source code only. This repository does not contain a deployed URL, OIDC tenant, Anthropic key, or Cloudflare account credentials.

1. Use an OIDC provider that issues RS256- or ES256-signed **access tokens** with an audience for this API. Set `OIDC_ISSUER`, `OIDC_JWKS_URL`, and `OIDC_AUDIENCE` in `proxy/wrangler.jsonc` to the exact issuer, HTTPS JWKS endpoint, and API audience from that provider.
2. Set `ALLOWED_ORIGINS` to the exact comma-separated browser origins that may call the API. Native Android and Windows clients do not send a browser origin.
3. Change the rate-limit `namespace_id` in `proxy/wrangler.jsonc` to a positive integer available in your Cloudflare account.
4. From `proxy`, run `npx wrangler@latest login`, then `npx wrangler@latest deploy`.
5. Add the provider key only as a Cloudflare secret: `npx wrangler@latest secret put ANTHROPIC_API_KEY --name jarvis-v4-api`.
6. Configure `AIVO_PROXY_URL` as the actual HTTPS Worker origin (without `/v1/chat`) in Android's ignored `local.properties`, as a Gradle property/environment variable, or enter the origin in Android Settings. Set `AIVO_PROXY_URL` for the Windows process or enter it in Windows Settings. GitHub Actions accepts repository secret `AIVO_PROXY_URL` (the legacy `JARVIS_PROXY_URL` secret is still accepted); this URL is an endpoint, not an API credential. Obtain a valid short-lived OIDC access token from your identity provider and enter it in each client. Google sign-in and automatic token issuance are not implemented.

Do not commit provider keys, access tokens, `.dev.vars`, local properties, or signing keys. The checked-in `wrangler.jsonc` values are configuration placeholders and are not live service details. No deployment or live provider call is claimed.

### Enable WhatsApp announcements on Android

1. Install and open AIVO, then open **Settings**.
2. Choose **Open notification access settings** and explicitly enable AIVO in Android's special app access screen.
3. Return to AIVO and enable **Enable announcements**. Preview reading is off by default; voice-message, call, missed-call, quiet-hour, and muted-sender settings are available there.
4. Ensure Android's notification settings allow WhatsApp to show notifications. Speech uses the selected TTS locale when installed and the system notification audio volume.

The listener is driven by Android's notification-access service and can receive posted notifications while the UI is backgrounded or the screen is locked, subject to Android/OEM policy and notification availability. It does not keep the microphone open. WhatsApp may omit caller identity, call type, or message details; AIVO announces unavailable identity instead of inventing it. Detection of call/voice-message types is based on notification category/text and requires on-device testing for each WhatsApp version/language.

## Build and test

Prerequisites: JDK 17, Android SDK Platform 36 and Build Tools 36.0.0, Gradle 8.13, Node.js 22+, .NET 10, and Inno Setup 6 for the Windows installer.

On Windows, copy `local.properties.example` to `local.properties`, preserve/add `sdk.dir`, and set the real `AIVO_PROXY_URL`. Then run:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:bundleRelease
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Release AAB: `app/build/outputs/bundle/release/app-release.aab`. Release signing is used only when a valid local `keystore.properties` and keystore are supplied; without them, neither package is a production-ready Google Play release. The Android application ID remains `com.jarvis.ai.v4` to preserve upgrade identity.

CI uploads the debug APK as `aivo-android-debug-apk` and the release AAB build as `aivo-android-release-aab`. The debug APK is development-signed; the AAB must be signed with the app's release key before Play publishing. These artifacts are separate and must not be presented as production-signed packages.

With an Android device or emulator connected, run the Compose launch/settings smoke test with:

```powershell
.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest
```

Run backend tests from `proxy` with `npm test`. Publish and package the Windows client from the repository root with:

```powershell
dotnet publish windows/Jarvis.Desktop/Jarvis.Desktop.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -o windows/publish
& "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe" windows\installer.iss
```

GitHub Actions runs backend tests, Android unit tests/lint/APK/AAB builds, an Android 11 (API 30) Google APIs emulator smoke test, and the Windows publish/installer build. It uploads build packages and test reports as workflow artifacts. The software-emulated hosted runner has previously crashed before instrumentation started; such a CI run is not evidence that the UI smoke test passed.

## Platform support

| Platform | Current status |
| --- | --- |
| Android | Native Kotlin/Compose client; chat, tap-to-talk/TTS, and opt-in notification announcements are implemented. |
| Windows 10/11 | Separate native WPF client; chat, speech recognition/synthesis, and installer are implemented. |
| iOS / iPadOS | Not implemented. |
| macOS / Linux | Not implemented. |
| Web / PWA | Not implemented. |

## Current feature status

| Capability | Status | Scope and evidence |
| --- | --- | --- |
| Android Kotlin/Gradle foundation, SDK 36, Java 17, Debug APK, unit tests, and lint | **WORKING AND TESTED** | `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug` pass locally. |
| Android/Windows neon AIVO dashboard, animated AI core, waveform, status panel, responsive composer, app icon, and splash screen | **IMPLEMENTED; PLATFORM QA INCOMPLETE** | Implemented in native Compose and WPF; Android build and Windows installer launch have prior verification, but no current physical-device test is available. |
| Android launch/settings/privacy-controls UI smoke test | **IMPLEMENTED BUT NOT TESTED** | Compose instrumentation test is wired to an Android 11 (API 30) Google APIs emulator in GitHub Actions; the API 36 software-emulated runner failed to attach the instrumentation process. |
| Android debug APK, release AAB build, and separate CI artifacts | **BUILD PASSED; RELEASE SIGNING REQUIRED** | Debug APK is signed with the development key; release AAB is currently unsigned. No production release key is configured. |
| `AIVO_PROXY_URL` to BuildConfig and Actions secret wiring | **IMPLEMENTED; LIVE CONNECTION NOT VERIFIED** | Legacy `JARVIS_PROXY_URL` and `JARVIS_API_BASE_URL` aliases remain supported. No actual deployment URL is configured here. |
| Live AI chat, OIDC login, Anthropic requests | **REQUIRES API OR SERVER CONFIGURATION** | Existing Worker contract tests passed in earlier CI; this branch has no deployed Worker, identity provider, or Anthropic secret. Interactive sign-in is not implemented. |
| Android tap-to-talk, multilingual chat TTS, stop-speaking control | **IMPLEMENTED BUT NOT TESTED** | Needs manual device/locale testing; no wake-word listening is included. |
| WhatsApp notification announcements, available sender parsing, Urdu/Hindi/English phrases, preview privacy, quiet hours, muted senders, deduplication | **IMPLEMENTED BUT NOT TESTED** | Parser and quiet-hour unit tests pass; listener and TTS require real-device testing with WhatsApp notifications. |
| WhatsApp notification delivery with screen locked/backgrounded | **PLATFORM RESTRICTED** | Android/OEM process policy and WhatsApp notification settings determine delivery; this device behavior is not verified. |
| Notification-access controls, and Android-Keystore encryption for access tokens and muted sender filters | **IMPLEMENTED BUT NOT TESTED** | Requires explicit user-granted notification access; Keystore behavior has not been tested on hardware. |
| Windows 10/11 chat, voice, futuristic dashboard, and installer | **IMPLEMENTED BUT NOT TESTED** | The WPF source has matching cyan/navy dashboard styling; current-branch Windows CI and interactive visual verification are required. |
| Wake word, continuous/background microphone, configurable speech voice/rate/pitch, themes, persisted chat history | **NOT IMPLEMENTED** | The current voice UI is tap-to-talk and chat history is process-memory only. |
| Calls/SMS, app launching, alarms, reminders, flashlight/volume/settings controls, cleaner, device management | **NOT IMPLEMENTED** | No device-action code or confirmation flow is present yet. |
| Web search, file understanding, document summaries, social account integrations, publishing/scheduling | **NOT IMPLEMENTED** | No authorized providers or APIs are connected. |
| Video editing and AI content creation | **NOT IMPLEMENTED** | No processing or export pipeline is present. |
| Coding workspace, isolated code execution, GitHub integrations, cloud storage/compute, cross-device sync | **NOT IMPLEMENTED** | No cloud provider, job service, storage API, or editor is present. |
| Security Center, 2FA, session/device revocation, release signing and distribution | **NOT IMPLEMENTED** | Current token entry is manual; no store publication or security certification is claimed. |

## Remaining AIVO scope

The broader AIVO specification remains in progress as listed above. The Windows application currently contains chat and voice only. No live backend, Google Sign-In, cloud files/compute, wake-word service, Play Store release signing, or real-device announcement verification is claimed. The product rebrand does not change the Android application ID, Kotlin package names, Windows install/data directories, or existing Worker deployment name.
