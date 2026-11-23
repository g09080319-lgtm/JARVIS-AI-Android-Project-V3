package com.jarvis.ai.actions

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.jarvis.ai.data.Note
import com.jarvis.ai.data.NoteDao
import com.jarvis.ai.data.Reminder
import com.jarvis.ai.data.ReminderDao
import com.jarvis.ai.data.ReminderScheduler
import java.text.DateFormat
import java.util.Date

/**
 * Local commands using official Android APIs only. route() returns the spoken reply, or null
 * when the text should go to the AI. Replies describe what actually happened: opening a chat or
 * composer is never reported as "sent".
 */
class CommandRouter(private val ctx: Context, private val notes: NoteDao, private val reminders: ReminderDao) {
    private class Pending(val run: () -> String)
    private var pending: Pending? = null
    private var torchOn = false
    private var blocked = false
    private val weather = WeatherClient()

    private val yes = setOf("yes", "confirm", "yes confirm", "send it", "go ahead", "ok", "okay", "do it", "yeah",
        "haan", "han", "हाँ", "हां", "ہاں", "جی ہاں", "جی")
    private val no = setOf("no", "cancel", "stop", "don't", "nahi", "नहीं", "نہیں")
    private val blockedMsg = "Android doesn't let apps open other apps while JARVIS is in the background or the phone is locked. Open JARVIS and try again. Nothing was done."

    suspend fun route(raw: String): String? {
        blocked = false
        val r = inner(raw)
        return if (blocked) blockedMsg else r
    }

    private suspend fun inner(raw: String): String? {
        val t = raw.lowercase().replace("a.m.", "am").replace("p.m.", "pm").trim().trimEnd('.', '!', '?', '۔')
        pending?.let { p ->
            pending = null
            if (t in yes) return p.run()
            if (t in no) return "Cancelled. Nothing was sent."
        }
        Regex("^(hello|hi|hey) jarvis").find(t)?.let { return "At your service. How can I help?" }

        // --- Messaging (prepare -> read back -> confirm -> open; never claims "sent") ---
        Regex("^(?:send (?:a )?whatsapp(?: message)? to|whatsapp message to|whatsapp) (.+?) (?:saying|that says|that|:) (.+)$").find(t)
            ?.let { return prepareWhatsApp(it.groupValues[1], it.groupValues[2]) }
        Regex("^tell (.+?) (?:that|to) (.+)$").find(t)
            ?.let { return prepareWhatsApp(it.groupValues[1], it.groupValues[2]) }
        Regex("^(?:send (?:an? )?(?:sms|text|message) to|text) (.+?) (?:saying|that says|that) (.+)$").find(t)
            ?.let { return prepareSms(it.groupValues[1], it.groupValues[2]) }
        if (t.contains("instagram") && (t.contains("message") || t.contains(" dm"))) {
            val ok = openByLabel("instagram")
            return (if (ok) "Opened Instagram. " else "Instagram isn't installed or couldn't open. ") +
                "Instagram has no supported way for me to find a person or send a direct message, so please do that inside the app."
        }

        // --- Time, battery, weather ---
        if (t.contains("the time") || t.contains("what time")) return "It's ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())}."
        if (t.contains("the date") || t.contains("today's date")) return "Today is ${DateFormat.getDateInstance(DateFormat.FULL).format(Date())}."
        if (t.contains("battery")) return battery()
        Regex("weather(?: (?:in|for|at))? ?(.*)$").find(t)?.let {
            val city = it.groupValues[1].trim()
            return if (city.isBlank()) "Which city? Say: weather in Karachi." else weather.current(city)
        }

        // --- Alarms, timers, reminders (via the Clock app) ---
        Regex("set (?:an )?alarm (?:for|at) (\\d{1,2})(?::(\\d{2}))? ?(am|pm)?").find(t)?.let { return alarm(it) }
        Regex("(?:set (?:a )?timer for|timer for) (\\d+) (second|minute|hour)s?").find(t)?.let {
            return timer(it.groupValues[1].toInt(), it.groupValues[2], "JARVIS timer")
        }
        Regex("^remind me to (.+?) in (\\d+) (minute|hour)s?$").find(t)?.let {
            val task = it.groupValues[1].trim()
            val amount = it.groupValues[2].toLongOrNull() ?: return "Please choose a valid reminder time."
            val unit = it.groupValues[3]
            if (task.isBlank() || amount <= 0L || amount > 525600L) return "Please choose a reminder between 1 minute and 1 year."
            val delayMs = amount * if (unit == "hour") 3_600_000L else 60_000L
            val triggerAt = System.currentTimeMillis() + delayMs
            val id = reminders.insert(Reminder(text = task, triggerAt = triggerAt))
            ReminderScheduler.schedule(ctx, id, triggerAt)
            val whenText = if (unit == "hour") "$amount hour(s)" else "$amount minute(s)"
            return "Reminder saved for $whenText from now: $task. I'll notify you when it's due."
        }

        // --- Device controls ---
        if (t in setOf("turn on the flashlight", "flashlight on", "turn on flashlight", "torch on", "turn on the torch")) return torch(true)
        if (t in setOf("turn off the flashlight", "flashlight off", "turn off flashlight", "torch off", "turn off the torch")) return torch(false)
        if (t.startsWith("volume up") || t.startsWith("increase volume") || t.startsWith("turn up the volume")) return volume(true)
        if (t.startsWith("volume down") || t.startsWith("decrease volume") || t.startsWith("turn down the volume")) return volume(false)
        if (t.contains("brightness")) return "Android doesn't let ordinary apps change brightness without special settings access, so I've opened display settings."
            .also { start(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
        if (t in setOf("pause", "pause music", "play", "play music", "resume", "next", "next song", "previous", "previous song")) return media(t)

        // --- Calculator ---
        calc(t)?.let { return it }

        // --- Notes and tasks (stored on this device) ---
        Regex("^(?:add (?:a )?note|take (?:a )?note|note)[: ]+(.+)$").find(t)?.let {
            notes.insert(Note(kind = "note", text = it.groupValues[1])); return "Note saved."
        }
        Regex("^(?:add (?:a )?task|add (?:to my )?(?:to-?do|todo)(?: list)?|task)[: ]+(.+)$").find(t)?.let {
            notes.insert(Note(kind = "task", text = it.groupValues[1])); return "Task added."
        }
        if (Regex("^(?:show|read|list)(?: me)?(?: my)? notes$").matches(t)) return readList("note", "notes")
        if (Regex("^(?:show|read|list)(?: me)?(?: my)? (?:tasks|to-?do list|todo list|to-?dos)$").matches(t)) return readList("task", "tasks")

        // --- Apps, calls, search, maps ---
        if (t.startsWith("open ")) {
            val rest = t.removePrefix("open ")
            val app = rest.substringBefore(" and ").trim()
            val r = open(app)
            return if (rest.contains(" and ")) "$r I can't browse or tap inside other apps, so please continue there." else r
        }
        if (t.startsWith("call ")) return call(t.removePrefix("call ").trim())
        Regex("^search (?:the internet |the web )?(?:for )?(.+)$").find(t)?.let {
            val q = it.groupValues[1]
            return if (start(Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", q))) "Opened a web search for $q. Those are live web results, not my own knowledge." else "I couldn't open a web search."
        }
        Regex("^(?:navigate to|directions to|take me to)\\s+(.+)$").find(t)?.let {
            val q = it.groupValues[1]
            return if (start(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(q))))
                || start(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q))))) "Opened navigation to $q in your maps app." else "No maps app could open that."
        }
        return null
    }

    // ---------- helpers ----------
    private fun foreground() = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private fun start(i: Intent): Boolean {
        if (!foreground()) { blocked = true; return false }
        return try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i); true }
        catch (e: ActivityNotFoundException) { false } catch (e: SecurityException) { false } catch (e: Exception) { false }
    }

    private fun battery(): String {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        if (level < 0) return "I couldn't read the battery level."
        return "Battery is at ${level * 100 / scale} percent and ${if (charging) "charging" else "not charging"}."
    }

    private fun open(nameRaw: String): String {
        val name = nameRaw.removePrefix("the ").removeSuffix(" app").trim()
        val sys = mapOf("settings" to Settings.ACTION_SETTINGS, "wifi" to Settings.ACTION_WIFI_SETTINGS,
            "wi-fi" to Settings.ACTION_WIFI_SETTINGS, "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
            "location settings" to Settings.ACTION_LOCATION_SOURCE_SETTINGS, "display settings" to Settings.ACTION_DISPLAY_SETTINGS,
            "sound settings" to Settings.ACTION_SOUND_SETTINGS, "battery settings" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
            "airplane mode settings" to Settings.ACTION_AIRPLANE_MODE_SETTINGS)
        sys[name]?.let { return if (start(Intent(it))) "Opened $name." else "I couldn't open $name." }
        if (name == "camera") return if (start(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))) "Opened the camera." else "I couldn't open the camera."
        if (name == "phone" || name == "dialer") return if (start(Intent(Intent.ACTION_DIAL))) "Opened the phone." else "I couldn't open the phone."
        val cat = mapOf("messages" to Intent.CATEGORY_APP_MESSAGING, "gallery" to Intent.CATEGORY_APP_GALLERY,
            "maps" to Intent.CATEGORY_APP_MAPS, "google maps" to Intent.CATEGORY_APP_MAPS,
            "browser" to Intent.CATEGORY_APP_BROWSER, "calendar" to Intent.CATEGORY_APP_CALENDAR)[name]
        if (cat != null && start(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, cat))) return "Opened $name."
        if (blocked) return ""
        return if (openByLabel(name)) "Opened $name." else "I couldn't find an app called $name on this phone, or it couldn't be opened."
    }

    private fun openByLabel(name: String): Boolean {
        val pm = ctx.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        fun label(r: android.content.pm.ResolveInfo) = r.loadLabel(pm).toString().lowercase()
        val hit = apps.firstOrNull { label(it) == name }
            ?: apps.firstOrNull { label(it).contains(name) }
            ?: apps.firstOrNull { label(it).length >= 4 && name.contains(label(it)) }
            ?: return false
        val i = pm.getLaunchIntentForPackage(hit.activityInfo.packageName) ?: return false
        return start(i)
    }

    /** Returns (display name, number). Digits are used directly; names need READ_CONTACTS. */
    private fun contact(who: String): Pair<String, String>? {
        val digits = who.replace(Regex("[\\s-]"), "")
        if (digits.matches(Regex("\\+?\\d{3,}"))) return who to digits
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$who%"), null
        )?.use { if (it.moveToFirst()) return it.getString(0) to it.getString(1) }
        return null
    }

    private fun noContact(who: String) =
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            "I need contacts permission to find $who. Enable it in JARVIS settings, or say the phone number."
        else "I couldn't find $who in your contacts. Nothing was done."

    private fun call(who: String): String {
        val (name, n) = contact(who) ?: return noContact(who)
        return if (start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n")))) "Dialer opened for $name. I haven't placed the call; tap the call button to confirm." else "I couldn't open the dialer."
    }

    private fun prepareWhatsApp(who: String, body: String): String {
        val (name, n) = contact(who) ?: return noContact(who)
        val digits = n.filter { it.isDigit() }
        pending = Pending {
            var done = false
            for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
                if (start(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits?text=${Uri.encode(body)}")).setPackage(pkg))) { done = true; break }
                if (blocked) break
            }
            if (done) "WhatsApp is open with your message ready for $name. I can't send it or see whether it was sent. Tap Send in WhatsApp. If the number has no country code, the chat may not open."
            else "WhatsApp isn't installed or couldn't open this chat. Nothing was sent."
        }
        return "Message for $name on WhatsApp: $body. Say confirm to open WhatsApp with this message, or cancel."
    }

    private fun prepareSms(who: String, body: String): String {
        val (name, n) = contact(who) ?: return noContact(who)
        pending = Pending {
            if (start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$n")).putExtra("sms_body", body)))
                "Your messaging app is open with the text for $name. I haven't sent it. Tap Send to confirm."
            else "I couldn't open the messaging app. Nothing was sent."
        }
        return "Text for $name: $body. Say confirm to open your messaging app with this text, or cancel."
    }

    private fun alarm(m: MatchResult): String {
        var h = m.groupValues[1].toInt(); val min = m.groupValues[2].ifBlank { "0" }.toInt()
        if (m.groupValues[3] == "pm" && h < 12) h += 12
        if (m.groupValues[3] == "am" && h == 12) h = 0
        if (h > 23 || min > 59) return "That isn't a valid time."
        val i = Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, h)
            .putExtra(AlarmClock.EXTRA_MINUTES, min).putExtra(AlarmClock.EXTRA_MESSAGE, "JARVIS")
        return if (start(i)) "Opened your Clock app with an alarm for %02d:%02d. Please check that it's saved there.".format(h, min)
        else "I couldn't open a clock app to set the alarm."
    }

    private fun timer(n: Int, unit: String, label: String): String {
        val secs = n * when (unit) { "hour" -> 3600; "minute" -> 60; else -> 1 }
        val i = Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, secs).putExtra(AlarmClock.EXTRA_MESSAGE, label)
        return if (start(i)) "Opened your Clock app with a $n $unit timer. Please check that it's started." else "I couldn't open a clock app for the timer."
    }

    private fun torch(on: Boolean): String = try {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        if (id == null) "This device has no flashlight I can control."
        else { cm.setTorchMode(id, on); torchOn = on; if (on) "Flashlight on." else "Flashlight off." }
    } catch (e: Exception) { "I couldn't switch the flashlight. Another app may be using the camera." }

    private fun volume(up: Boolean): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        return "Volume ${if (up) "raised" else "lowered"}."
    }

    private fun media(t: String): String {
        val code = when {
            t.startsWith("pause") -> KeyEvent.KEYCODE_MEDIA_PAUSE
            t.startsWith("next") -> KeyEvent.KEYCODE_MEDIA_NEXT
            t.startsWith("previous") -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> KeyEvent.KEYCODE_MEDIA_PLAY
        }
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return "Sent the media command. Whether it worked depends on your media app."
    }

    private suspend fun readList(kind: String, word: String): String {
        val l = notes.byKind(kind)
        if (l.isEmpty()) return "You have no $word."
        return "Your $word: " + l.mapIndexed { i, n -> "${i + 1}, ${n.text}${if (n.done) " (done)" else ""}" }.joinToString(". ")
    }

    private fun calc(t: String): String? {
        val body = listOf("calculate ", "what is ", "what's ", "how much is ").firstOrNull { t.startsWith(it) }?.let { t.removePrefix(it) } ?: return null
        val e = body.replace(" plus ", "+").replace(" minus ", "-").replace(" times ", "*").replace(" multiplied by ", "*")
            .replace(" divided by ", "/").replace(" x ", "*").replace("×", "*").replace("÷", "/").replace(" ", "")
        if (!e.matches(Regex("[0-9+\\-*/().]+")) || e.none { it in "+-*/" }) return null
        return try {
            val v = Parser(e).parse()
            if (v.isNaN() || v.isInfinite()) "That can't be calculated, for example division by zero."
            else "The answer is " + (if (v == Math.floor(v) && Math.abs(v) < 1e15) v.toLong().toString() else "%.6f".format(v).trimEnd('0').trimEnd('.'))
        } catch (e: Exception) { null }
    }

    private class Parser(val s: String) {
        var i = 0
        fun parse(): Double { val v = expr(); if (i != s.length) throw IllegalArgumentException(); return v }
        fun expr(): Double { var v = term(); while (i < s.length && (s[i] == '+' || s[i] == '-')) { val op = s[i++]; val r = term(); v = if (op == '+') v + r else v - r }; return v }
        fun term(): Double { var v = factor(); while (i < s.length && (s[i] == '*' || s[i] == '/')) { val op = s[i++]; val r = factor(); v = if (op == '*') v * r else v / r }; return v }
        fun factor(): Double {
            if (i < s.length && s[i] == '-') { i++; return -factor() }
            if (i < s.length && s[i] == '(') { i++; val v = expr(); if (i >= s.length || s[i] != ')') throw IllegalArgumentException(); i++; return v }
            val st = i; while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            return s.substring(st, i).toDouble()
        }
    }
}
