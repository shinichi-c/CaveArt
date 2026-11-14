package com.android.CaveArt.clockkit

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import com.android.CaveArt.GravityMode
import org.json.JSONObject

object ClockKitConfigStore {

    const val PREFS_NAME = "cave_art_clock_prefs"
    const val KEY_JSON = "clockkit_config"
    const val ACTION_CLOCKKIT_UPDATED = "com.android.CaveArt.UPDATE_CLOCKKIT_CONFIG"

    fun toJson(config: ClockKitConfig): String {
        val root = JSONObject().apply {
            put("styleId", config.styleId)
            put("clockX", config.clockX.toDouble())
            put("clockY", config.clockY.toDouble())
            put("dateX", config.dateX.toDouble())
            put("dateY", config.dateY.toDouble())
            put("dateAttached", config.dateAttached)
            put("dateFormat", config.dateFormat)
            put("color", config.color)
            put("secondaryColor", config.secondaryColor)
            put("font", config.font)
            put("scale", config.scale.toDouble())
            put("isDepthEnabled", config.isDepthEnabled)
            put("depthOcclusionLimit", config.depthOcclusionLimit.toDouble())
            put("isGravityEnabled", config.isGravityEnabled)
            put("gravityIntensity", config.gravityIntensity.toDouble())
            put("gravityMode", config.gravityMode.name)
        }

        val floatsObj = JSONObject()
        config.styleFloats.forEach { (sId, map) ->
            val inner = JSONObject()
            map.forEach { (k, v) -> inner.put(k, v.toDouble()) }
            floatsObj.put(sId, inner)
        }
        root.put("styleFloats", floatsObj)

        val stringsObj = JSONObject()
        config.styleStrings.forEach { (sId, map) ->
            val inner = JSONObject()
            map.forEach { (k, v) -> inner.put(k, v) }
            stringsObj.put(sId, inner)
        }
        root.put("styleStrings", stringsObj)

        return root.toString()
    }

    fun fromJson(jsonStr: String?): ClockKitConfig? {
        if (jsonStr.isNullOrBlank()) return null
        return try {
            val root = JSONObject(jsonStr)
            val floatsMap = mutableMapOf<String, Map<String, Float>>()
            root.optJSONObject("styleFloats")?.let { floatsObj ->
                floatsObj.keys().forEach { sId ->
                    val innerObj = floatsObj.optJSONObject(sId) ?: return@forEach
                    val m = mutableMapOf<String, Float>()
                    innerObj.keys().forEach { k ->
                        m[k] = innerObj.optDouble(k, 0.0).toFloat()
                    }
                    floatsMap[sId] = m
                }
            }

            val stringsMap = mutableMapOf<String, Map<String, String>>()
            root.optJSONObject("styleStrings")?.let { stringsObj ->
                stringsObj.keys().forEach { sId ->
                    val innerObj = stringsObj.optJSONObject(sId) ?: return@forEach
                    val m = mutableMapOf<String, String>()
                    innerObj.keys().forEach { k ->
                        m[k] = innerObj.optString(k, "")
                    }
                    stringsMap[sId] = m
                }
            }

            val gravMode = try {
                GravityMode.valueOf(root.optString("gravityMode", GravityMode.SURFACE_PLANE.name))
            } catch (_: Exception) {
                GravityMode.SURFACE_PLANE
            }

            ClockKitConfig(
                styleId = root.optString("styleId", "adaptive_vector"),
                clockX = root.optDouble("clockX", 0.0).toFloat(),
                clockY = root.optDouble("clockY", 110.0).toFloat(),
                dateX = root.optDouble("dateX", 0.0).toFloat(),
                dateY = root.optDouble("dateY", 75.0).toFloat(),
                dateAttached = root.optBoolean("dateAttached", true),
                dateFormat = root.optInt("dateFormat", 0),
                color = root.optInt("color", Color.WHITE),
                secondaryColor = root.optInt("secondaryColor", Color.WHITE),
                font = root.optString("font", "default"),
                scale = root.optDouble("scale", 1.0).toFloat(),
                isDepthEnabled = root.optBoolean("isDepthEnabled", true),
                depthOcclusionLimit = root.optDouble("depthOcclusionLimit", 0.45).toFloat(),
                isGravityEnabled = root.optBoolean("isGravityEnabled", false),
                gravityIntensity = root.optDouble("gravityIntensity", 1.0).toFloat(),
                gravityMode = gravMode,
                styleFloats = floatsMap,
                styleStrings = stringsMap
            )
        } catch (_: Exception) {
            null
        }
    }

    fun loadFromPrefs(prefs: SharedPreferences): ClockKitConfig {
        fromJson(prefs.getString(KEY_JSON, null))?.let { return it }

        val base = ClockKitConfig(
            styleId = "adaptive_vector",
            clockX = prefs.getFloat("clock_x", 0f),
            clockY = prefs.getFloat("clock_y", 110f),
            dateX = prefs.getFloat("date_x", 0f),
            dateY = prefs.getFloat("date_y", 75f),
            dateAttached = prefs.getBoolean("date_attached", true),
            dateFormat = prefs.getInt("date_format", 0),
            color = prefs.getInt("clock_color", Color.WHITE),
            font = prefs.getString("clock_font", "default") ?: "default",
            isDepthEnabled = prefs.getBoolean("clock_depth_enabled", true),
            depthOcclusionLimit = prefs.getFloat("clock_depth_occlusion", 0.45f),
            isGravityEnabled = prefs.getBoolean("clock_gravity", false),
            gravityIntensity = prefs.getFloat("clock_gravity_intensity", 1.0f)
        )
        return base
            .withFloat("adaptive_vector", "hour_size", prefs.getFloat("clock_hour_size", 100f))
            .withFloat("adaptive_vector", "min_size", prefs.getFloat("clock_minute_size", 75f))
            .withFloat("adaptive_vector", "stroke_width", prefs.getFloat("clock_stroke_width", 8f))
            .withFloat("adaptive_vector", "roundness", prefs.getFloat("clock_roundness", 30f))
            .withBool("adaptive_vector", "dual_tone", prefs.getBoolean("clock_dual_tone", true))
            .withBool("adaptive_vector", "stretch", prefs.getBoolean("clock_stretch", false))
            .withString(
                "adaptive_vector",
                "layout",
                if (prefs.getInt("clock_layout", 0) == 1) "vertical" else "horizontal"
            )
    }

    fun saveAndBroadcast(
        context: Context,
        config: ClockKitConfig,
        collisionMap: String? = null
    ) {
        val safeCtx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext()
        } else context

        val json = toJson(config)
        val hourSize = config.getFloat("adaptive_vector", "hour_size", 100f)
        val autoDateSize = (hourSize * 0.24f).coerceIn(14f, 34f)

        val editor = safeCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_JSON, json)
            .putFloat("clock_x", config.clockX)
            .putFloat("clock_y", config.clockY)
            .putFloat("date_x", config.dateX)
            .putFloat("date_y", config.dateY)
            .putBoolean("date_attached", config.dateAttached)
            .putInt("date_format", config.dateFormat)
            .putFloat("date_size", autoDateSize)
            .putInt("clock_color", config.color)
            .putString("clock_font", config.font)
            .putBoolean("clock_depth_enabled", config.isDepthEnabled)
            .putInt("clock_layer_order", if (config.isDepthEnabled) 1 else 0)
            .putFloat("clock_depth_occlusion", config.depthOcclusionLimit)
            .putBoolean("clock_gravity", config.isGravityEnabled)
            .putFloat("clock_gravity_intensity", config.gravityIntensity)

        if (collisionMap != null) {
            editor.putString("clock_collision_map", collisionMap)
        }
        editor.apply()

        context.sendBroadcast(Intent(ACTION_CLOCKKIT_UPDATED).apply {
            putExtra("clockkit_config", json)
            if (collisionMap != null) putExtra("collision_map", collisionMap)
        })
    }
}
