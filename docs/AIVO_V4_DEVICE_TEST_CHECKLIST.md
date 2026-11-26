# AIVO V4 Real-Device Test Checklist

**Status: not run.** No Android device was attached during the latest local verification, and live OAuth/Worker configuration is not available. Do not mark items passed based only on unit tests or CI.

## Test record

Record one row per device/build. Do not include account email, ID/access tokens, message contents, API keys, or notification contents in evidence.

| Field | Value |
|---|---|
| Device model | |
| Android version/API | |
| App version/build SHA | |
| App package | `com.jarvis.ai.v4` |
| Signing certificate SHA-256 | |
| Backend environment (staging/production) | |
| Tester/date | |

## Preconditions

- [ ] Use a non-production test account and a configured staging Worker/provider where possible.
- [ ] Confirm Google Web and Android OAuth clients match the app package and certificate signing the installed APK.
- [ ] Confirm the Worker HTTPS origin, rate limiter, session secret, and provider key are configured by an authorized operator.
- [ ] Install the APK signed with the certificate registered for that Android OAuth client. Do not switch signing keys between upgrade tests.
- [ ] Enable Android Studio/ADB logs only as needed; verify logs do not disclose tokens, prompts, responses, or secrets.

## Functional checks

| ID | Test | Expected result | Status / evidence |
|---|---|---|---|
| D01 | Install the APK and launch it after a clean install. | App installs for `com.jarvis.ai.v4`, opens without a crash, and reports missing configuration clearly if backend/OAuth setup is incomplete. | Not run |
| D02 | Start Google Sign-In with a valid test account. | Credential Manager returns a Google ID token; the app sends it only over HTTPS; the Worker validates it and returns a short-lived backend session. No token appears in UI or logs. | Not run |
| D03 | Try sign-in with no account available, cancel the chooser, and test with invalid/mismatched OAuth configuration. | A clear recoverable error is shown; the app does not claim success or store an invalid session. | Not run |
| D04 | Verify a newly issued session and wait beyond its 15-minute expiry, then send chat. | Worker rejects the expired session; app shows a reauthentication error. Sign in again to exchange a fresh Google ID token; the app must not silently claim automatic refresh. | Not run |
| D05 | Send an authenticated AI chat request using English, Hindi, and Urdu text. | Requests carry the backend bearer session over HTTPS; a real configured provider response is displayed; service/auth/rate-limit errors are visible and do not look like successful answers. | Not run |
| D06 | Start a slow request and press **Stop**. Repeat by starting a new chat and by signing out during a request. | UI stops waiting, the app cancels its in-flight HTTP call and speech, and stale responses are not appended afterward. | Not run |
| D07 | Sign out, relaunch the app, and sign in again. | Local encrypted token and in-memory conversation are cleared on sign-out; protected chat requires a new session; sign-in works again. Note: an already copied stateless token remains usable until expiry. | Not run |
| D08 | Test airplane mode, unavailable DNS, timeout, invalid TLS, Worker 401/429/5xx, and provider failure. | Requests fail closed with actionable errors; no plaintext HTTP fallback, fake assistant reply, or sensitive logging occurs. | Not run |
| D09 | Tap the voice-input control with microphone permission granted; then deny, revoke, and retry. | Android requests microphone permission in context; granted permission enables explicit tap-to-talk if speech recognition is available; denied/revoked permission is handled without a crash or hidden listening. | Not run |
| D10 | Test speech recognition and Text-to-Speech in English, Hindi, and Urdu on the device's installed speech services. | Listening/speaking states and unsupported-language/service errors are accurate; Stop cancels speech. Device speech-service quality/availability is recorded separately from app behavior. | Not run |
| D11 | Send a chat reply, press Home, lock the screen, and leave the app idle. | No always-on microphone or wake-word behavior is claimed. Voice recognition does not continue in the background; no background microphone foreground service is implemented in this phase. | Not run |
| D12 | Revoke microphone/network access where supported, clear app storage, and repeat first-run setup. | Permission/configuration failures are clear; clean setup can authenticate again; logs and UI never reveal secrets. | Not run |

## Completion evidence

- [ ] Record pass/fail for every test and attach only redacted screenshots/log excerpts.
- [ ] Record the exact APK SHA-256 and signing certificate SHA-256 used for the run.
- [ ] Record backend environment and Worker deployment revision without copying secret values.
- [ ] File actionable bugs for failures; do not mark Phase 3 production integration complete until OAuth and authenticated chat are verified end to end on a real device.
