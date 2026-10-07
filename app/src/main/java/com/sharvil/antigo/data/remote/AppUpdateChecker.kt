package com.sharvil.antigo.data.remote

import android.content.Context
import com.sharvil.antigo.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AvailableAppUpdate(val version: String)

object AppUpdateChecker {
    private const val PREFS = "app_updates"
    private const val LAST_CHECK = "last_check_ms"
    private const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L
    private const val RELEASE_API = "https://api.github.com/repos/SharvilMishra/AntiGO/releases/latest"

    suspend fun check(context: Context): AvailableAppUpdate? = withContext(Dispatchers.IO) {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - preferences.getLong(LAST_CHECK, 0L) < CHECK_INTERVAL_MS) return@withContext null

        runCatching {
            val connection = URL(RELEASE_API).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "AntiGO-Android")
                if (connection.responseCode !in 200..299) return@withContext null
                preferences.edit().putLong(LAST_CHECK, now).apply()

                val release = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
                if (release.optBoolean("draft") || release.optBoolean("prerelease")) return@withContext null
                val hasApk = release.optJSONArray("assets")?.let { assets ->
                    (0 until assets.length()).any { index -> assets.optJSONObject(index)?.optString("name") == "antigo.apk" }
                } == true
                if (!hasApk) return@withContext null
                val tag = release.optString("tag_name").trim()
                val latest = numericVersion(tag) ?: return@withContext null
                val current = numericVersion(BuildConfig.VERSION_NAME) ?: return@withContext null
                if (latest > current) AvailableAppUpdate(tag) else null
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    private fun numericVersion(value: String): List<Int>? {
        val normalized = value.removePrefix("v").removePrefix("V")
        if (!normalized.matches(Regex("\\d+(\\.\\d+)*"))) return null
        return normalized.split('.').mapNotNull(String::toIntOrNull).takeIf { it.size == normalized.count { ch -> ch == '.' } + 1 }
    }

    private operator fun List<Int>.compareTo(other: List<Int>): Int {
        for (index in 0 until maxOf(size, other.size)) {
            val left = getOrElse(index) { 0 }
            val right = other.getOrElse(index) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return 0
    }
}
