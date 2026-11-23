package com.jarvis.ai.actions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Live weather from Open-Meteo (free, no API key). */
class WeatherClient {
    private fun get(url: String): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 10000
        return JSONObject(c.inputStream.bufferedReader().use { it.readText() })
    }

    private fun describe(code: Int) = when (code) {
        0 -> "clear sky"; 1, 2 -> "mostly clear"; 3 -> "overcast"; 45, 48 -> "foggy"
        in 51..57 -> "drizzle"; in 61..67 -> "rain"; in 71..77 -> "snow"; in 80..82 -> "rain showers"
        in 95..99 -> "thunderstorm"; else -> "mixed conditions"
    }

    suspend fun current(city: String): String = withContext(Dispatchers.IO) {
        try {
            val g = get("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + URLEncoder.encode(city, "UTF-8"))
            val r = g.optJSONArray("results")?.optJSONObject(0) ?: return@withContext "I couldn't find a city called $city."
            val w = get("https://api.open-meteo.com/v1/forecast?current=temperature_2m,weather_code,wind_speed_10m&latitude=${r.getDouble("latitude")}&longitude=${r.getDouble("longitude")}")
                .getJSONObject("current")
            "Live weather for ${r.getString("name")}: ${w.getDouble("temperature_2m")} degrees Celsius, ${describe(w.getInt("weather_code"))}, " +
                "wind ${w.getDouble("wind_speed_10m")} kilometres per hour. Source: Open-Meteo."
        } catch (e: Exception) { "I couldn't fetch live weather right now." }
    }
}
