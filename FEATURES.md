# JARVIS AI V3 — Feature status

The debug APK compiles successfully. App behavior has not been tested on a physical device; treat all features as requiring device testing.

## Implemented in source (device testing pending)
| Area | Details |
|---|---|
| AI chat | HTTPS proxy call; last 30 messages; bounded request/response handling; retry for transient network, throttling, and server failures |
| AI proxy | Cloudflare Worker validates requests, limits message count/size, handles OPTIONS, uses configurable model, times out upstream calls, retries transient failures, and returns sanitized errors |
| Voice | Tap-to-talk; opt-in “Hey JARVIS” wake phrase using Android on-device speech recognition; on-device follow-up turns; TextToSpeech; English, Urdu, and Hindi settings; animated waveform |
| UI | Existing Compose interface, chat, waveform, settings, notes/tasks, dark theme, splash, adaptive icon |
| Background controls | Optional microphone foreground service and persistent notification; background/locked-screen wake listening requires explicit switches and Android permissions |
| WhatsApp / SMS | Read-back and confirmation required; opens the chat/composer only; never auto-sends |
| Calls | Opens dialer; user confirms call |
| Alarms / timers | Uses Android Clock intents; user checks/starts them in Clock |
| Scheduled reminders | One-shot reminders such as “remind me to stretch in 10 minutes”; saved in Room, scheduled via AlarmManager, notified when due, pending alarms restored after reboot |
| Notes / tasks | Stored on-device in Room |
| Weather / device commands | Existing V2 behavior retained |

## Platform and product limitations
- Android may defer non-exact reminder alarms during Doze or battery-saving modes. Exact-to-the-second delivery is not promised.
- Reminder notifications require notification permission on Android 13+.
- Wake-word support requires Android 12+ and an available on-device recognizer; the wake phrase is English (“Hey JARVIS”). Audio remains with Android's on-device recognition service; recognized commands may be sent as text to the configured AI backend. This is not a dedicated low-power DSP engine and can use noticeable battery.
- Background microphone service must be started while the app is visible. Android and device-manufacturer battery policies may stop it; there is no boot-time microphone start or hidden listening.
- WhatsApp / SMS are not automatically sent; user taps Send in the destination app.
- Calls are not automatically placed.
- Android restricts direct changes to some system settings; JARVIS opens settings pages instead.
- Locked-screen/background app launches remain subject to Android restrictions.
- A public privacy policy, release signing, Play Console declarations, and device testing are still the owner's responsibility.
