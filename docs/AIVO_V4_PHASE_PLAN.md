# AIVO V4 Development Plan

This plan follows the 39-section master prompt without changing the current Android application ID or repository name.

## Phase 1 - Repository audit and preservation
Audit branches, source, dependencies, workflows, configuration, legacy references and blockers. Preserve backup branches and create reviewable commits.

## Phase 2 - Android foundation and AIVO branding
Replace user-facing JARVIS branding, add the AIVO identity, responsive dashboard, animated core, theme and onboarding foundation. Preserve `com.jarvis.ai.v4` and existing stored data.

## Phase 3 - Backend configuration and Google authentication
Add canonical `AIVO_PROXY_URL` configuration with compatibility for `JARVIS_PROXY_URL` and `JARVIS_API_BASE_URL`; implement Google Sign-In and secure backend session validation.

## Phase 4 - Voice assistant and Hey AIVO
Add opt-in wake-word listening, Hindi/Urdu/English voice support, listening/speaking states and battery-conscious foreground operation.

## Phase 5 - Phone controls and announcements
Implement supported phone commands and privacy-controlled notification, WhatsApp, SIM call and SMS announcements.

## Phase 6 - Reminders, productivity, weather and device information
Complete recurring reminders, alarms, timers, notes, tasks, real weather and device data.

## Phase 7 - Cleaner, storage optimization and security
Add confirmed storage analysis and cleanup, permission checks, secret handling, account logout/deletion and privacy controls.

## Phase 8 - Healthcare and smartwatch integrations
Add opt-in Health Connect and smartwatch support only when SDKs, permissions and test devices are available.

## Phase 9 - AI development and video studios
Add clearly scoped code assistance first, followed by genuinely implemented video import, editing and export capabilities.

## Phase 10 - Cross-platform expansion
Extend Android and the shared backend before independent Windows, macOS, Linux, iOS, iPadOS, web and PWA work.

## Phase 11 - QA, release and documentation
Run backend and Android tests, verify APK/AAB output, configure secure signing, publish artifacts and document known limitations.
