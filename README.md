# AIVO V4

V4 development starts with a working chat-and-voice client on Android and Windows, connected to a shared Cloudflare Worker API. This is an initial implementation, not the complete V4 feature set.

## Implemented in this milestone

- **Android:** Kotlin/Jetpack Compose chat, explicit tap-to-talk speech recognition, English/Urdu/Hindi language selection, spoken replies, in-memory conversation context, Android Keystore token storage, and Google Credential Manager sign-in when OAuth is configured.
- **Windows 10/11:** .NET 10 WPF chat client, Windows speech recognition and speech output, language selection, session-only conversation context, and per-user DPAPI encryption for the API access token.
- **Shared API:** Cloudflare Worker `POST /v1/chat`, RS256/ES256 OIDC JWT verification, Google ID-token exchange, 15-minute signed sessions, fail-closed rate limiting, allowlisted browser origins, request-size/message limits, and Anthropic forwarding with a server-side secret.
- **Build setup:** Android APK and AAB build tasks, a self-contained Windows publish, an Inno Setup installer definition, backend contract tests, and CI jobs for all three.

Google authentication is only enabled when a valid Google OAuth web client ID is configured for the Android build and Worker. Conversations are kept in process memory only and are sent to the configured AI provider when the user submits a prompt. They are not stored by this Worker.

## Configure the shared backend

The Worker is source code only. This repository does not contain a deployed URL, Google OAuth client, OIDC tenant, Anthropic key, session-signing secret, or Cloudflare account credentials.

1. In Google Cloud Console, create an Android OAuth client for package `com.jarvis.ai.v4` with the applicable signing-certificate SHA-1 fingerprint, and a Web application OAuth client for token audience. Do not use the example/blank value as a credential.
2. Set the Web client ID as Worker variable `GOOGLE_CLIENT_ID` and Android build property `GOOGLE_WEB_CLIENT_ID`. They must be the same client ID. Set `GOOGLE_ANDROID_CLIENT_ID` to the Android OAuth client ID for package `com.jarvis.ai.v4`; the Worker checks it against an ID token's authorized-party claim when present. Configure both OAuth clients and signing fingerprints in the same Google Cloud project.
3. Generate a random secret of at least 32 characters and add it as a Cloudflare secret: `npx wrangler@latest secret put SESSION_SIGNING_SECRET --name jarvis-v4-api`. Never put this value in client configuration.
4. Configure `OIDC_ISSUER`, `OIDC_JWKS_URL`, and `OIDC_AUDIENCE` only if retaining the legacy external-OIDC token path. Google sign-in has its own fixed Google issuer and JWKS endpoint.
5. Set `ALLOWED_ORIGINS` to the exact comma-separated browser origins that may call the API. Native Android and Windows clients do not send a browser origin.
6. Change the rate-limit `namespace_id` in `proxy/wrangler.jsonc` to a positive integer available in your Cloudflare account.
7. From `proxy`, run `npx wrangler@latest login`, then `npx wrangler@latest deploy`.
8. Add the provider key only as a Cloudflare secret: `npx wrangler@latest secret put ANTHROPIC_API_KEY --name jarvis-v4-api`.
9. Set `AIVO_PROXY_URL` to the actual HTTPS Worker origin (without `/v1/chat`) in Android `local.properties` or the build environment. `JARVIS_PROXY_URL` and `JARVIS_API_BASE_URL` remain supported fallbacks. Windows uses the same canonical variable and fallbacks.

Google ID tokens are verified against Google's published signing keys, issuer, audience and verified-email claim. The Worker issues 15-minute bearer sessions; `/v1/auth/refresh` requires a fresh Google ID token. Android sign-out clears the local encrypted token; because sessions are stateless, an already-issued token can remain valid until its 15-minute expiry.

Do not commit provider keys, access tokens, `.dev.vars`, local properties, or signing keys. The checked-in `wrangler.jsonc` values are configuration placeholders and are not live service details. No deployment or live provider call is claimed.

## Build and test

Prerequisites: JDK 17, Android SDK Platform 36 and Build Tools 36.0.0, Gradle 8.13, Node.js 22+, .NET 10, and Inno Setup 6 for the Windows installer.

On Windows, copy `local.properties.example` to `local.properties`, preserve/add `sdk.dir`, and set real `AIVO_PROXY_URL` and `GOOGLE_WEB_CLIENT_ID` values. Then run:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:bundleRelease
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. A release AAB is signed only when a valid local `keystore.properties` and keystore are supplied; without them it is not ready for Play Store upload.

Run backend tests from `proxy` with `npm test`. Publish and package the Windows client from the repository root with:

```powershell
dotnet publish windows/Jarvis.Desktop/Jarvis.Desktop.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -o windows/publish
& "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe" windows\installer.iss
```

GitHub Actions runs the backend tests, Android unit tests/APK/AAB builds, and Windows publish/installer build. Successful runs upload `jarvis-ai-v4-android` and `jarvis-ai-v4-windows` artifacts.

## Not implemented yet

The broader V4 specification remains in progress: a consumer sign-in flow, always-on wake word/background microphone service, WhatsApp notification announcements, device cleaner, AI coding workspace/software developer, hosted cloud-computing workspace and persistent cloud data, video-editing studio, content creator, and social-media manager. No APK, AAB, or Windows installer is included in the repository until CI builds and uploads them.
