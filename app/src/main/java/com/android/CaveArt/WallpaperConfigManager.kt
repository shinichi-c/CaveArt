package com.android.CaveArt

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.CaveArt.animations.AnimationStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


private val Context.dataStore by preferencesDataStore(name = "cave_art_live_prefs")

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
    val animationStyle: String = AnimationStyle.NANO_ASSEMBLY.name
)

object WallpaperConfigManager {
    
    private val CONFIG_KEY = stringPreferencesKey("live_config_json")

    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    suspend fun saveConfig(context: Context, config: LiveWallpaperConfig) {
        context.dataStore.edit { preferences ->
            preferences[CONFIG_KEY] = json.encodeToString(config)
        }
    }
    
    suspend fun loadConfig(context: Context): LiveWallpaperConfig {
        return try {
            val preferences = context.dataStore.data.first()
            val jsonStr = preferences[CONFIG_KEY]
            
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
