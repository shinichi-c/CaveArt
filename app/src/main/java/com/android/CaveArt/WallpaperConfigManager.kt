package com.android.CaveArt

import android.content.Context
import android.os.Build
import com.google.gson.Gson

data class LiveWallpaperConfig(
    val imagePath: String? = null,
    val cutoutPath: String? = null,
    val resourceId: Int = 0,
    val shapeName: String = "SQUIRCLE",
    val backgroundColor: Int = 0xFF4CAF50.toInt(),
    val is3DPopEnabled: Boolean = false,
    val scale: Float = 1.0f,
    val isCentered: Boolean = false
)

object WallpaperConfigManager {
    private const val PREF_NAME = "cave_art_live_prefs"
    private const val KEY_CONFIG = "live_config"
    private val gson = Gson()

    private fun getStorageContext(context: Context): Context {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }
    }

    fun saveConfig(context: Context, config: LiveWallpaperConfig) {
        val prefs = getStorageContext(context).getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CONFIG, gson.toJson(config)).apply()
    }

    fun loadConfig(context: Context): LiveWallpaperConfig {
        val prefs = getStorageContext(context).getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CONFIG, null) ?: return LiveWallpaperConfig()
        return try {
            gson.fromJson(json, LiveWallpaperConfig::class.java)
        } catch (e: Exception) {
            LiveWallpaperConfig()
        }
    }
}