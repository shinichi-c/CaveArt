package com.android.CaveArt

import android.content.Context
import android.os.Build
import com.android.CaveArt.animations.AnimationStyle
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class LiveWallpaperConfig(
    val imagePath: String? = null,
    val cutoutPath: String? = null,
    val resourceId: Int = 0,
    val shapeName: String = "SQUIRCLE",
    val backgroundColor: Int = 0xFF4CAF50.toInt(),
    val is3DPopEnabled: Boolean = false,
    val scale: Float = 1.0f,
    val isCentered: Boolean = false,
    val animationStyle: String = AnimationStyle.NANO_ASSEMBLY.name,
    val isMagicShapeEnabled: Boolean = true,
    val isAnimationEnabled: Boolean = true,
    val isFilamentEnabled: Boolean = false,
    val animParams: Map<String, Float> = emptyMap()
)

object WallpaperConfigManager {
    
    private const val PREFS_NAME = "cave_art_live_prefs"
    private const val CONFIG_KEY = "live_config_json"

    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    private fun getDeviceProtectedContext(context: Context): Context {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }
    }
    
    suspend fun saveConfig(context: Context, config: LiveWallpaperConfig) {
        val safeContext = getDeviceProtectedContext(context)
        val prefs = safeContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(CONFIG_KEY, json.encodeToString(config)).apply()
    }
    
    suspend fun loadConfig(context: Context): LiveWallpaperConfig {
        return try {
            val safeContext = getDeviceProtectedContext(context)
            val prefs = safeContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(CONFIG_KEY, null)
            
            if (jsonStr != null) {
                json.decodeFromString(jsonStr)
            } else {
                LiveWallpaperConfig()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            LiveWallpaperConfig()
        }
    }
}
