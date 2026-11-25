# AIVO V4

V4 development starts with a working chat-and-voice client on Android and Windows, connected to a shared Cloudflare Worker API. This is an initial implementation, not the complete V4 feature set.

## Implemented in this milestone

- **Android:** Kotlin/Jetpack Compose chat, explicit tap-to-talk speech recognition, English/Urdu/Hindi language selection, spoken replies, in-memory conversation context, and Android Keystore encryption for the API access token.
- **Windows 10/11:** .NET 10 WPF chat client, Windows speech recognition and speech output, language selection, session-only conversation context, and per-user DPAPI encryption for the API access token.
- **Shared API:** Cloudflare Worker `POST /v1/chat`, RS256/ES256 OIDC JWT verification, fail-closed per-user rate limiting, allowlisted browser origins, request-size/message limits, and Anthropic forwarding with a server-side secret.
- **Build setup:** Android APK and AAB build tasks, a self-contained Windows publish, an Inno Setup installer definition, backend contract tests, and CI jobs for all three.

The clients accept a short-lived access token from an external OIDC identity provider; interactive sign-in is not implemented yet. Conversations are kept in process memory only and are sent to the configured AI provider when the user submits a prompt. They are not stored by this Worker.

## Configure the shared backend

The Worker is source code only. This repository does not contain a deployed URL, OIDC tenant, Anthropic key, or Cloudflare account credentials.

1. Use an OIDC provider that issues RS256- or ES256-signed **access tokens** with an audience for this API. Set `OIDC_ISSUER`, `OIDC_JWKS_URL`, and `OIDC_AUDIENCE` in `proxy/wrangler.jsonc` to the exact issuer, HTTPS JWKS endpoint, and API audience from that provider.
2. Set `ALLOWED_ORIGINS` to the exact comma-separated browser origins that may call the API. Native Android and Windows clients do not send a browser origin.
3. Change the rate-limit `namespace_id` in `proxy/wrangler.jsonc` to a positive integer available in your Cloudflare account.
4. From `proxy`, run `npx wrangler@latest login`, then `npx wrangler@latest deploy`.
5. Add the provider key only as a Cloudflare secret: `npx wrangler@latest secret put ANTHROPIC_API_KEY --name aivo-v4-api`.
6. Configure `JARVIS_API_BASE_URL` as the actual HTTPS Worker origin (without `/v1/chat`) in Android's ignored `local.properties`; set the same origin in the Windows app. Obtain a valid short-lived OIDC access token from your identity provider and enter it in each client.

Do not commit provider keys, access tokens, `.dev.vars`, local properties, or signing keys. The checked-in `wrangler.jsonc` values are configuration placeholders and are not live service details. No deployment or live provider call is claimed.

## Build and test

Prerequisites: JDK 17, Android SDK Platform 36 and Build Tools 36.0.0, Gradle 8.13, Node.js 22+, .NET 10, and Inno Setup 6 for the Windows installer.

On Windows, copy `local.properties.example` to `local.properties`, preserve/add `sdk.dir`, and set the real `JARVIS_API_BASE_URL`. Then run:

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
