# JARVIS AI V3 — Android / Kotlin / Jetpack Compose

This is an update of the supplied V2 project. V3 adds Room-backed scheduled reminders, more resilient AI proxy communication, and a GitHub Actions Android build. Existing confirmation and Android-safety behavior is retained.

## Requirements
- Android Studio with JDK 17
- Android SDK Platform 36 and Build Tools 36.0.0 installed
- Gradle 8.13 (the included `gradlew` scripts bootstrap this version on first run)

## Configure the AI proxy
The backend implementation is `proxy/worker.js`, configured by `proxy/wrangler.jsonc`. It is source code only: this repository does not include a deployed Worker URL or an Anthropic API key. Deploy it to your own Cloudflare account before configuring the app.

The Android app sends an HTTPS `POST` to the exact `/chat` URL. The request JSON is `{"messages":[{"role":"user","content":"..."}],"language":"en-US"}` (up to 30 user/assistant messages); the Worker returns `{"reply":"..."}`. It forwards the request to Anthropic's Messages API. The provider key stays in a Cloudflare Worker secret. The Worker also requires its IP rate-limit binding and rejects requests if that binding or the provider secret is missing.

### Deploy and test the backend
1. Create an Anthropic API key in your Anthropic account. Do not paste it into the Android project or commit it.
2. Install Node.js/npm if needed, open a terminal in the repository's `proxy` directory, then authenticate Wrangler:

   ```powershell
   npx wrangler@latest login
   ```

3. Before deploying, check `wrangler.jsonc`. Its rate-limit namespace ID (`1001`) must be a positive integer unique within your Cloudflare account; change it if that ID is already in use.
4. Deploy the Worker:

   ```powershell
   npx wrangler@latest deploy
   ```

   Wrangler prints the actual HTTPS `workers.dev` URL for your account. The app endpoint is that URL with `/chat` appended; use the URL Wrangler actually reports, not a sample or placeholder.
5. Add the provider key as a server-side secret. Wrangler prompts for the value; do not put it in the command line:

   ```powershell
   npx wrangler@latest secret put ANTHROPIC_API_KEY --name jarvis-ai-proxy
   ```

6. Test the live endpoint. Paste the actual URL from step 4 when prompted. This sends one request to Anthropic and may incur provider usage:

   ```powershell
   $proxyUrl = Read-Host "Paste the actual HTTPS Worker URL ending in /chat"
   $body = '{"messages":[{"role":"user","content":"Reply exactly: JARVIS backend test OK"}],"language":"en-US"}'
   Invoke-RestMethod -Uri $proxyUrl -Method Post -ContentType "application/json" -Body $body
   ```

   A successful test returns a JSON object with a non-empty `reply`. Do not treat deployment alone as a successful test.

### Configure and test the Android app
1. In the repository root, open your existing `local.properties` and keep its `sdk.dir` entry. Add a new line containing `JARVIS_PROXY_URL=` followed by the actual HTTPS Worker URL from step 4, including `/chat`. Do not copy the blank example over your existing file.
2. Sync Gradle and rebuild/reinstall the app; the URL is embedded at build time:

   ```powershell
   .\gradlew.bat --no-daemon :app:assembleDebug
   ```

   Install `app/build/outputs/apk/debug/app-debug.apk`, ensure the device/emulator has internet access, and send a chat message.
3. Keep `local.properties`, signing keys, and provider credentials out of Git. The Android app must contain only the Worker URL, never `ANTHROPIC_API_KEY`.

The Worker rate limit is a best-effort per-IP limit (20 requests per 60 seconds per Cloudflare location); users behind the same NAT may share the limit. Add real user authentication and stricter account-level abuse controls before offering this public endpoint to multiple users.

## Build
Open the extracted `JARVIS-AI-Android-Project-V3` project folder in Android Studio and allow Gradle sync. For a command-line build, use the included bootstrap script (it downloads Gradle 8.13 on first run):

```bash
./gradlew --no-daemon assembleDebug
```

On Windows, use `gradlew.bat --no-daemon assembleDebug`.

The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions runs Android unit tests and the debug build on pushes to `main` / `master`, pull requests, and manual workflow dispatch. The APK is uploaded as the `jarvis-ai-debug-apk` workflow artifact.

## V3 scheduled reminders
Say a command such as:
- `remind me to stretch in 10 minutes`
- `remind me to call Ali in 2 hours`

The reminder is stored in Room and scheduled with Android `AlarmManager`. Pending reminders are restored after device reboot. Android may defer non-exact alarms during Doze/battery-saving modes; exact-to-the-second delivery is not guaranteed. The reminder notification requires notification permission on Android 13+.

## Voice, wake word, and background privacy
- In **Settings**, enable **Always listen for “Hey JARVIS”** to use the wake phrase while JARVIS is open. It is off by default. Wake detection requires Android 12+ and an available on-device speech recognizer; unsupported devices leave the setting off. Say the English phrase “Hey JARVIS”; choose English, Urdu, or Hindi for the command and spoken response.
- After a response, JARVIS listens for one follow-up utterance using on-device recognition; after silence, wake-word mode resumes.
- Wake detection and follow-up speech recognition use Android's on-device recognizer. This is not a dedicated low-power DSP hotword engine and can use noticeable battery. The recognized command text is then handled locally or sent to the configured AI proxy; audio is not sent to the proxy.
- To keep wake listening active after leaving the app or locking the screen, also enable **Background / locked-screen listening** while JARVIS is open. Android requires microphone permission, notification permission on Android 13+, and the persistent foreground-service notification. Use **Stop** in that notification to disable both settings and stop listening.
- Android, manufacturer battery policies, and microphone privacy controls can still suspend or stop listening. JARVIS does not start microphone listening at boot, hide its notification, or bypass Android background-start restrictions. Turn off either switch at any time to stop wake listening.
- Tap-to-talk remains available when wake-word support is unavailable. The device's standard speech-recognition service may process tap-to-talk audio according to that service's privacy policy.

## Existing safety behavior retained
- WhatsApp and SMS messages are read back and require a spoken confirmation before the relevant app/composer is opened. JARVIS does not send them.
- Calls open the dialer; the user places the call.
- Alarms and timers are handed to the Clock app and must be checked there.
- The app does not claim to change restricted Android settings or operate other apps invisibly.
- Wake-word listening is opt-in, visible in the UI, uses on-device recognition, and requires a foreground notification to continue in the background.
- Conversation history remains opt-in.

## Release notes / limitations
- The debug APK has been built successfully; physical-device testing is still required before release.
- Configure and test your own Cloudflare Worker and provider account. Provider model availability can change.
- Complete and host the privacy policy template in `docs/PRIVACY_POLICY_TEMPLATE.md` before public release.
- For a Play Store release, verify current target API, foreground-service declarations, data safety, and signing requirements.
- Do not commit `local.properties`, signing keys, or provider secrets.
