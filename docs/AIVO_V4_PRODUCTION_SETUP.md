# AIVO V4 Production Setup

This is an operator checklist, not evidence that production services or credentials are configured. Do not send OAuth secrets, API keys, keystore files, passwords, or access tokens in chat or commit them to the repository.

## Repository write access

The current authenticated GitHub account is `r92856065-prog`; it does not have confirmed push permission to `g09080319-lgtm/JARVIS-AI-Android-Project-V3`.

The repository owner should:

1. Sign in to GitHub as `g09080319-lgtm` and open this repository.
2. Go to **Settings → Collaborators and teams** (or **Collaborators**) → **Add people**.
3. Invite `r92856065-prog` and grant the **Write** role. Do not grant Admin unless independently required.
4. Have the invitee accept the invitation if GitHub requires it.
5. Confirm the account appears with Write access in **Manage access**. A repository token's `repo` scope does not itself grant repository permission.

The local `main` contains unpushed work relative to `origin/main`. Once Write access is confirmed, recheck the current ahead count and remote ancestry, then push with a normal fast-forward `git push origin main`. If the remote has advanced, stop and inspect before reconciling; never force-push or rewrite these commits. Do not merge PR #2 as part of this access change.

## Google OAuth

Use the existing application ID `com.jarvis.ai.v4`; do not create a replacement ID as part of this setup.

1. In the intended Google Cloud project, configure an Android OAuth client for this package and a Web application OAuth client for the server audience.
2. Configure `GOOGLE_WEB_CLIENT_ID` in the Android build and `GOOGLE_CLIENT_ID` in the Worker with the same Web client ID. Configure `GOOGLE_ANDROID_CLIENT_ID` in the Worker with the Android OAuth client ID.
3. Register fingerprints for every certificate used to install the app. A local debug build and the signed release/upload key have different fingerprints. If Google Play App Signing is used, also account for the Play app-signing certificate for Play-installed builds.
4. The current local debug APK was signed with this machine's Android debug certificate:
   - SHA-1: `4c5ff939ed73f782059fca15e51eb2dde4e9b77a`
   - SHA-256: `6cd675a2df65d1f2bc82f39b8c8bdf2808f27bbbff08dc1b3e02249c8dcb74c3`
5. Recheck the local debug certificate with `.\gradlew.bat signingReport`; inspect a release APK with `apksigner verify --print-certs <release-apk>`. Treat the values above as this workstation's debug certificate only, never as the production signing identity.
6. Configure `GOOGLE_WEB_CLIENT_ID` in local `local.properties` for development. Do not put an OAuth client secret in Android; the Web client ID is an identifier, not a private key.

Google ID-token validation, account consent, and session creation must still be exercised on a real device with the final app-signing fingerprint and deployed Worker. Automated tests use generated test keys and do not establish live OAuth readiness.

## Cloudflare Worker and AI provider

1. In `proxy/wrangler.jsonc`, replace the rate-limit `namespace_id` placeholder with the namespace configured for the intended Cloudflare account. Keep the existing limit/period only if they match the deployment's rate-limit policy; verify the binding works during deployment.
2. Confirm Worker variables: `GOOGLE_CLIENT_ID`, `GOOGLE_ANDROID_CLIENT_ID`, and any required `ALLOWED_ORIGINS`. The OIDC values are placeholders for the optional legacy OIDC path and must be replaced or removed before relying on that path.
3. Authenticate Wrangler as the authorized Cloudflare operator and deploy from `proxy`:
   ```powershell
   npx wrangler@latest login
   npx wrangler@latest deploy
   ```
4. Add private values as Worker secrets only:
   ```powershell
   npx wrangler@latest secret put SESSION_SIGNING_SECRET --name jarvis-v4-api
   npx wrangler@latest secret put ANTHROPIC_API_KEY --name jarvis-v4-api
   ```
   Use a randomly generated session secret of at least 32 characters. Enter secret values only into Wrangler's secure prompt; never put them in `wrangler.jsonc`, Android configuration, shell history, or source control.
5. Confirm the deployed Worker HTTPS origin and health endpoint with the operator's account. Configure `AIVO_PROXY_URL` as that origin only (no `/v1/chat` suffix). Keep `JARVIS_PROXY_URL` and `JARVIS_API_BASE_URL` as compatibility fallbacks, not the preferred setting.
6. Use a staging/test account to verify Google token exchange, the 15-minute session expiry, refresh using a fresh Google ID token, authenticated chat, and provider error handling. A health check alone does not prove authentication or AI-provider connectivity.

No Worker deployment, namespace, OAuth client, or provider call is verified by this repository setup.

## Protected GitHub Actions release signing

The workflow expects a GitHub Environment named `android-release`. Configure it under **Settings → Environments**:

- Add a required reviewer and restrict deployment branches to `main`.
- Add these **environment variables**:
  - `AIVO_PROXY_URL`: the configured production HTTPS Worker origin.
  - `GOOGLE_WEB_CLIENT_ID`: the Web OAuth client ID used by the Worker.
  - `ANDROID_RELEASE_CERT_SHA256`: the expected signing certificate SHA-256 as 64 hex characters, without colons.
- Add these **environment secrets**:
  - `ANDROID_KEYSTORE_BASE64`: base64 encoding of the existing authorized release/upload keystore.
  - `ANDROID_KEYSTORE_PASSWORD`
  - `ANDROID_KEY_ALIAS`
  - `ANDROID_KEY_PASSWORD`

Only the key owner should create or approve the release keystore. Keep an encrypted backup in an owner-controlled secure location. Never replace the key for an existing app identity without a separately approved migration plan. For Play App Signing, distinguish the upload key from the Google Play app-signing key and test the fingerprints relevant to each installed build.

The workflow fails if only some signing secrets are present, checks the built APK's package and certificate digest, verifies the AAB signature, and uploads separate release artifacts. If signing secrets are absent, it skips release packaging; the debug APK remains a separate CI artifact. A successful CI signature check is not a substitute for Google Play Console validation or a real-device installation test.

The workflow does not create a keystore or set GitHub secrets. An authorized owner must configure the environment and then run the workflow on `main`. Do not paste the keystore or secret values into issues, pull requests, chat, logs, or repository variables.

## Real-device test plan

Run [AIVO V4 Device Test Checklist](AIVO_V4_DEVICE_TEST_CHECKLIST.md) only after the staging Worker and OAuth configuration are available. Record device model, Android version, build SHA, signing fingerprint, backend environment, and pass/fail evidence without recording credentials or personal message contents.
