# JARVIS AI V4 — repository preparation

This repository is being prepared for the JARVIS AI V4 rebuild. This cleanup removes the obsolete V3 Android application and its V3-specific build and release files; it does not implement V4 features.

## V4 target

The planned product includes an Android application, Windows 10/11 desktop application, JARVIS AI Cloud and cloud computing, AI software development and coding workspace, mobile/PC cleaner, advanced voice assistant, WhatsApp notification announcements, video editing, content creation, social media management, and a secure shared AI backend.

These are planned capabilities, not implemented or verified deliverables. The V3 project and its full Git history are preserved on the `backup/jarvis-v3-before-v4` branch.

## Retained backend reference

`proxy/worker.js` is the existing Cloudflare Worker source retained for future backend work. It is not a deployed service and has not been verified as a production-ready shared backend. Do not put provider keys or other secrets in this repository; configure them as server-side secrets when a backend is deployed.

`local.properties.example` and `keystore.properties.example` contain placeholders only. Copy and configure them locally when the Android build is restored; never commit the resulting local configuration or signing keys.

## Repository status

The V4 application, desktop, cloud, and feature modules still need to be designed and implemented. The GitHub Actions workflow now verifies the retained scaffold; V4-specific build and test jobs should be added with their modules. The obsolete V3 Android APK workflow behavior and Codemagic configuration were removed because they referred to the deleted V3 app module.
