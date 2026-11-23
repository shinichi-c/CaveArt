package com.android.CaveArt.clockkit

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.WallpaperColors
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.graphics.*
import android.hardware.camera2.CameraManager
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.text.TextPaint
import android.text.TextUtils
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

enum class AtAGlanceStyle(val id: Int, val label: String) {
    MINIMAL(0, "Minimal"),
    FROSTED_PILL(1, "Frosted Pill"),
    TWO_LINE(2, "Two-Line"),
    SPLIT_PILLS(3, "Split Pills");

    companion object {
        fun fromId(id: Int): AtAGlanceStyle = values().firstOrNull { it.id == id } ?: MINIMAL
    }
}

enum class GlanceIconKind {
    NONE, SUN, MOON, PARTLY_CLOUDY, CLOUD, RAIN, STORM, SNOW, FOG,
    ALARM, BOLT, MUSIC, DND, TORCH
}

data class AtAGlanceWeatherData(
    val tempC: Float,
    val weatherCode: Int,
    val isDay: Boolean,
    val summary: String,
    val source: String,
    val updatedAtMs: Long
)

data class AtAGlanceSnapshot(
    val dateText: String,
    val weather: AtAGlanceWeatherData? = null,
    val nextAlarmText: String? = null,
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val chargingEtaMinutes: Int = -1,
    val mediaTitle: String? = null,
    val mediaArtist: String? = null,
    val isDndActive: Boolean = false,
    val isFlashlightOn: Boolean = false
)

data class AtAGlanceMeasureResult(
    val widthPx: Float,
    val heightPx: Float,
    val primaryLine: String,
    val secondaryLine: String?,
    val primaryIcon: GlanceIconKind,
    val secondaryIcon: GlanceIconKind,
    val fittedPrimarySp: Float,
    val weatherPillText: String? = null,
    val weatherPillIcon: GlanceIconKind = GlanceIconKind.NONE
)

object AtAGlanceDataCollector {

    @Volatile
    private var torchCallbackRegistered = false

    @Volatile
    private var isTorchCurrentlyOn = false

    @Volatile
    private var cachedWeather: AtAGlanceWeatherData? = null

    @Volatile
    private var lastWeatherReadMs = 0L

    fun updateCachedWeather(data: AtAGlanceWeatherData?) {
        cachedWeather = data
        lastWeatherReadMs = System.currentTimeMillis()
    }

    fun parseWeatherConditionToCode(description: String?): Int {
        val d = description?.lowercase(Locale.ENGLISH) ?: return 0
        return when {
            d.contains("thunder") || d.contains("storm") || d.contains("lightning") -> 95
            d.contains("snow") || d.contains("sleet") || d.contains("ice") || d.contains("flurr") -> 71
            d.contains("rain") || d.contains("shower") || d.contains("drizzle") -> 61
            d.contains("fog") || d.contains("mist") || d.contains("haze") || d.contains("smoke") -> 45
            d.contains("partly") || d.contains("mostly clear") || d.contains("few clouds") || d.contains("scattered") -> 2
            d.contains("cloud") || d.contains("overcast") -> 3
            else -> 0
        }
    }

    fun parseTemperatureCelsius(rawText: String?): Float? {
        if (rawText.isNullOrBlank()) return null
        val match = Regex("""(-?\d+(?:\.\d+)?)\s*°\s*([CFcf])?""").find(rawText) ?: return null
        val value = match.groupValues[1].toFloatOrNull() ?: return null
        val unit = match.groupValues.getOrNull(2)?.uppercase(Locale.ENGLISH) ?: ""
        return if (unit == "F") ((value - 32f) * 5f / 9f) else value
    }

    fun isCurrentlyDaytime(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour in 6..18
    }

    fun ensureTorchListener(context: Context) {
        if (torchCallbackRegistered) return
        synchronized(this) {
            if (torchCallbackRegistered) return
            torchCallbackRegistered = true
            try {
                val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
                cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        isTorchCurrentlyOn = enabled
                    }

                    override fun onTorchModeUnavailable(cameraId: String) {
                        isTorchCurrentlyOn = false
                    }
                }, null)
            } catch (_: Throwable) {}
        }
    }

    fun collect(context: Context, config: ClockKitConfig, nowMillis: Long = System.currentTimeMillis()): AtAGlanceSnapshot {
        ensureTorchListener(context)

        val dateText = ClockTypefaceResolver.formatDate(nowMillis, config.dateFormat)
        val weather = if (config.atGlanceWeather) readLocalWeather(context, nowMillis) else null
        val alarmText = if (config.atGlanceAlarm) readNextAlarm(context, nowMillis) else null
        val (pct, charging, etaMins) = if (config.atGlanceBattery) readBattery(context) else Triple(100, false, -1)
        val (mediaTitle, mediaArtist) = if (config.atGlanceMedia) readActiveMedia(context) else Pair(null, null)
        val dnd = if (config.atGlanceStatus) readDndState(context) else false
        val torch = if (config.atGlanceStatus) isTorchCurrentlyOn else false

        return AtAGlanceSnapshot(
            dateText = dateText,
            weather = weather,
            nextAlarmText = alarmText,
            batteryPercent = pct,
            isCharging = charging,
            chargingEtaMinutes = etaMins,
            mediaTitle = mediaTitle,
            mediaArtist = mediaArtist,
            isDndActive = dnd,
            isFlashlightOn = torch
        )
    }

    private fun readLocalWeather(context: Context, nowMillis: Long): AtAGlanceWeatherData? {
        if (cachedWeather != null && (nowMillis - lastWeatherReadMs) < 30_000L) {
            return cachedWeather
        }

        try {
            val safeCtx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.createDeviceProtectedStorageContext()
            } else context
            val prefs = safeCtx.getSharedPreferences(ClockKitConfigStore.PREFS_NAME, Context.MODE_PRIVATE)
            val updated = prefs.getLong("glance_weather_updated", 0L)
            if (updated > 0L) {
                val data = AtAGlanceWeatherData(
                    tempC = prefs.getFloat("glance_weather_temp", 0f),
                    weatherCode = prefs.getInt("glance_weather_code", 0),
                    isDay = prefs.getBoolean("glance_weather_is_day", isCurrentlyDaytime()),
                    summary = prefs.getString("glance_weather_summary", "Clear") ?: "Clear",
                    source = prefs.getString("glance_weather_source", "System") ?: "System",
                    updatedAtMs = updated
                )
                cachedWeather = data
                lastWeatherReadMs = nowMillis
                return data
            }
        } catch (_: Throwable) {}

        try {
            val updated = queryString(context, "glance_weather_updated", "0").toLongOrNull() ?: 0L
            if (updated > 0L) {
                val data = AtAGlanceWeatherData(
                    tempC = queryFloat(context, "glance_weather_temp", 0f),
                    weatherCode = queryInt(context, "glance_weather_code", 0),
                    isDay = queryInt(context, "glance_weather_is_day", 1) == 1,
                    summary = queryString(context, "glance_weather_summary", "Clear"),
                    source = queryString(context, "glance_weather_source", "System"),
                    updatedAtMs = updated
                )
                cachedWeather = data
                lastWeatherReadMs = nowMillis
                return data
            }
        } catch (_: Throwable) {}

        readOmniJawsLocal(context, nowMillis)?.let {
            cachedWeather = it
            lastWeatherReadMs = nowMillis
            return it
        }

        return cachedWeather
    }

    fun readOmniJawsLocal(context: Context, nowMillis: Long = System.currentTimeMillis()): AtAGlanceWeatherData? {
        return try {
            val uri = Uri.parse("content://org.omnirom.omnijaws.provider/weather")
            val cursor = context.contentResolver.query(
                uri,
                arrayOf("condition", "condition_code", "temperature", "units"),
                null, null, null
            ) ?: return null
            cursor.use { c ->
                if (!c.moveToFirst()) return null
                val cond = c.getString(0) ?: "Clear"
                val codeRaw = c.getInt(1)
                val tempRaw = c.getString(2)?.toFloatOrNull() ?: return null
                val units = c.getString(3) ?: "C"
                val tempC = if (units.equals("F", ignoreCase = true)) ((tempRaw - 32f) * 5f / 9f) else tempRaw
                val mappedCode = parseWeatherConditionToCode(cond).let { if (it == 0 && codeRaw in 200..804) mapOwmCode(codeRaw) else it }
                AtAGlanceWeatherData(
                    tempC = tempC,
                    weatherCode = mappedCode,
                    isDay = isCurrentlyDaytime(),
                    summary = cond.ifBlank { weatherSummaryForCode(mappedCode) },
                    source = "ROM Weather",
                    updatedAtMs = nowMillis
                )
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun mapOwmCode(owm: Int): Int = when (owm) {
        in 200..232 -> 95
        in 300..531 -> 61
        in 600..622 -> 71
        in 701..781 -> 45
        800 -> 0
        801, 802 -> 2
        else -> 3
    }

    private fun queryFloat(ctx: Context, path: String, default: Float): Float {
        var res = default
        try {
            val c: Cursor? = ctx.contentResolver.query(Uri.parse("content://com.android.CaveArt.settings/$path"), null, null, null, null)
            c?.use { if (it.moveToFirst()) res = it.getFloat(0) }
        } catch (_: Exception) {}
        return res
    }

    private fun queryInt(ctx: Context, path: String, default: Int): Int {
        var res = default
        try {
            val c: Cursor? = ctx.contentResolver.query(Uri.parse("content://com.android.CaveArt.settings/$path"), null, null, null, null)
            c?.use { if (it.moveToFirst()) res = it.getInt(0) }
        } catch (_: Exception) {}
        return res
    }

    private fun queryString(ctx: Context, path: String, default: String): String {
        var res = default
        try {
            val c: Cursor? = ctx.contentResolver.query(Uri.parse("content://com.android.CaveArt.settings/$path"), null, null, null, null)
            c?.use { if (it.moveToFirst()) res = it.getString(0) ?: default }
        } catch (_: Exception) {}
        return res
    }

    private fun readNextAlarm(context: Context, nowMillis: Long): String? {
        return try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return null
            val info = am.nextAlarmClock ?: return null
            val trigger = info.triggerTime
            if (trigger <= nowMillis || (trigger - nowMillis) > 24L * 60L * 60L * 1000L) return null
            val is24 = DateFormat.is24HourFormat(context)
            SimpleDateFormat(if (is24) "HH:mm" else "h:mm a", Locale.getDefault()).format(Date(trigger))
        } catch (_: Throwable) {
            null
        }
    }

    private fun readBattery(context: Context): Triple<Int, Boolean, Int> {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val pct = if (level >= 0 && scale > 0) ((level * 100f) / scale).roundToInt().coerceIn(0, 100) else 100
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            var etaMins = -1
            if (charging && status != BatteryManager.BATTERY_STATUS_FULL && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                val remMs = bm?.computeChargeTimeRemaining() ?: -1L
                if (remMs > 0L) etaMins = (remMs / 60_000L).toInt().coerceAtLeast(1)
            }
            Triple(pct, charging, etaMins)
        } catch (_: Throwable) {
            Triple(100, false, -1)
        }
    }

    private fun readActiveMedia(context: Context): Pair<String?, String?> {
        return try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return null to null
            val controllers = msm.getActiveSessions(null)
            for (ctrl in controllers) {
                if (ctrl.playbackState?.state == PlaybackState.STATE_PLAYING) {
                    val meta = ctrl.metadata
                    val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
                        ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                    val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    if (!title.isNullOrBlank()) return title to artist
                }
            }
            null to null
        } catch (_: Throwable) {
            null to null
        }
    }

    private fun readDndState(context: Context): Boolean {
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
            val filter = nm.currentInterruptionFilter
            filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        } catch (_: Throwable) {
            false
        }
    }

    fun weatherIconForCode(code: Int, isDay: Boolean): GlanceIconKind = when (code) {
        0 -> if (isDay) GlanceIconKind.SUN else GlanceIconKind.MOON
        1, 2 -> GlanceIconKind.PARTLY_CLOUDY
        3 -> GlanceIconKind.CLOUD
        45, 48 -> GlanceIconKind.FOG
        in 51..67, in 80..82 -> GlanceIconKind.RAIN
        in 71..77, 85, 86 -> GlanceIconKind.SNOW
        95, 96, 99 -> GlanceIconKind.STORM
        else -> GlanceIconKind.CLOUD
    }

    fun weatherSummaryForCode(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly Clear"
        2 -> "Partly Cloudy"
        3 -> "Cloudy"
        45, 48 -> "Foggy"
        in 51..57 -> "Drizzle"
        in 61..67, in 80..82 -> "Rain"
        in 71..77, 85, 86 -> "Snow"
        95, 96, 99 -> "Storm"
        else -> "Clear"
    }
}

/**
 * Single Source of Truth for At a Glance colors, thresholds, and pill styling.
 * Edit these constants here and all previews, carousels, and SystemUI views update together.
 */
object AtAGlanceColorResolver {
    const val DEFAULT_INK_COLOR: Int = Color.WHITE
    const val DARK_INK_COLOR: Int = 0xFF14161A.toInt()
    const val ADAPT_TO_LIGHT_WALLPAPER: Boolean = true
    const val LIGHT_WALLPAPER_LUM_THRESHOLD: Float = 0.72f

    const val PILL_DARK_FILL_ALPHA: Float = 0.42f
    const val PILL_LIGHT_FILL_ALPHA: Float = 0.72f
    const val PILL_LIGHT_INK_BORDER_ALPHA: Float = 0.28f
    const val PILL_DARK_INK_BORDER_ALPHA: Float = 0.24f

    fun colorLuminance(colorInt: Int): Float {
        val r = Color.red(colorInt) / 255f
        val g = Color.green(colorInt) / 255f
        val b = Color.blue(colorInt) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    fun isDarkInkFromWallpaperColors(colors: WallpaperColors?): Boolean {
        if (!ADAPT_TO_LIGHT_WALLPAPER || colors == null) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if ((colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0) {
                return true
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                val hints = colors.javaClass.getMethod("getColorHints").invoke(colors) as? Int ?: 0
                val darkTextFlag = WallpaperColors::class.java.getField("HINT_SUPPORTS_DARK_TEXT").getInt(null)
                if ((hints and darkTextFlag) != 0) return true
            } catch (_: Throwable) {}
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            if (colorLuminance(colors.primaryColor.toArgb()) >= LIGHT_WALLPAPER_LUM_THRESHOLD) {
                return true
            }
        }
        return false
    }

    fun resolveForWallpaper(
        wallpaperBitmap: Bitmap?,
        screenW: Float,
        screenH: Float,
        zoom: Float,
        centerX: Float,
        topY: Float,
        widthPx: Float,
        heightPx: Float,
        bgOpacity: Float = 1f,
        fallbackColors: WallpaperColors? = null
    ): Int {
        if (!ADAPT_TO_LIGHT_WALLPAPER) return DEFAULT_INK_COLOR

        if (wallpaperBitmap == null || wallpaperBitmap.isRecycled || screenW <= 0f || screenH <= 0f) {
            return if (isDarkInkFromWallpaperColors(fallbackColors)) DARK_INK_COLOR else DEFAULT_INK_COLOR
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && wallpaperBitmap.config == Bitmap.Config.HARDWARE) {
            return if (isDarkInkFromWallpaperColors(fallbackColors)) DARK_INK_COLOR else DEFAULT_INK_COLOR
        }

        val bw = wallpaperBitmap.width.toFloat()
        val bh = wallpaperBitmap.height.toFloat()
        if (bw <= 0f || bh <= 0f) return DEFAULT_INK_COLOR

        val baseScale = max(screenW / bw, screenH / bh) * zoom.coerceAtLeast(0.5f)
        val destW = bw * baseScale
        val destH = bh * baseScale
        val tx = (screenW - destW) / 2f
        val ty = (screenH - destH) / 2f

        val safeW = widthPx.coerceAtLeast(48f)
        val safeH = heightPx.coerceAtLeast(24f)
        val left = (centerX - safeW / 2f).coerceIn(0f, screenW)
        val right = (centerX + safeW / 2f).coerceIn(0f, screenW)
        val top = topY.coerceIn(0f, screenH)
        val bottom = (topY + safeH).coerceIn(0f, screenH)

        if (right <= left || bottom <= top) {
            return if (isDarkInkFromWallpaperColors(fallbackColors)) DARK_INK_COLOR else DEFAULT_INK_COLOR
        }

        val cols = 12
        val rows = 5
        var lumSum = 0f
        var count = 0
        val effBgAlpha = bgOpacity.coerceIn(0f, 1f)
        val maxBx = wallpaperBitmap.width - 1
        val maxBy = wallpaperBitmap.height - 1

        for (ix in 0 until cols) {
            val sx = left + ((ix + 0.5f) / cols) * (right - left)
            val bx = ((sx - tx) / baseScale).toInt().coerceIn(0, maxBx)
            for (iy in 0 until rows) {
                val sy = top + ((iy + 0.5f) / rows) * (bottom - top)
                val by = ((sy - ty) / baseScale).toInt().coerceIn(0, maxBy)
                val px = wallpaperBitmap.getPixel(bx, by)
                val alpha = (((px ushr 24) and 0xFF) / 255f) * effBgAlpha
                val r = ((px ushr 16) and 0xFF) / 255f * alpha
                val g = ((px ushr 8) and 0xFF) / 255f * alpha
                val b = (px and 0xFF) / 255f * alpha
                lumSum += (0.2126f * r + 0.7152f * g + 0.0722f * b)
                count++
            }
        }

        val avgLum = if (count > 0) lumSum / count else 0f
        return if (avgLum >= LIGHT_WALLPAPER_LUM_THRESHOLD) DARK_INK_COLOR else DEFAULT_INK_COLOR
    }
}

object AtAGlanceRenderer {

    private val primaryPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val secondaryPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        setStyle(Paint.Style.FILL)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        setStyle(Paint.Style.STROKE)
    }

    private fun formatTemp(tempC: Float, fahrenheit: Boolean): String {
        return if (fahrenheit) {
            "${((tempC * 9f / 5f) + 32f).roundToInt()}°F"
        } else {
            "${tempC.roundToInt()}°"
        }
    }

    private fun extractContextSignal(snapshot: AtAGlanceSnapshot): Pair<String?, GlanceIconKind> {
        return when {
            !snapshot.mediaTitle.isNullOrBlank() -> {
                val txt = if (!snapshot.mediaArtist.isNullOrBlank()) {
                    "${snapshot.mediaTitle} • ${snapshot.mediaArtist}"
                } else {
                    snapshot.mediaTitle
                }
                txt to GlanceIconKind.MUSIC
            }
            snapshot.isFlashlightOn -> "Flashlight On" to GlanceIconKind.TORCH
            snapshot.nextAlarmText != null -> "Alarm ${snapshot.nextAlarmText}" to GlanceIconKind.ALARM
            snapshot.isCharging -> {
                val txt = if (snapshot.batteryPercent >= 100) {
                    "Charged 100%"
                } else if (snapshot.chargingEtaMinutes > 0) {
                    "Charging ${snapshot.batteryPercent}% • ${snapshot.chargingEtaMinutes}m left"
                } else {
                    "Charging ${snapshot.batteryPercent}%"
                }
                txt to GlanceIconKind.BOLT
            }
            snapshot.batteryPercent <= 18 -> "Low Battery ${snapshot.batteryPercent}%" to GlanceIconKind.BOLT
            snapshot.isDndActive -> "Do Not Disturb" to GlanceIconKind.DND
            else -> null to GlanceIconKind.NONE
        }
    }

    fun measure(
        config: ClockKitConfig,
        snapshot: AtAGlanceSnapshot,
        density: Float,
        dateSizeSp: Float,
        maxScreenWidthPx: Float = 1080f
    ): AtAGlanceMeasureResult {
        val style = AtAGlanceStyle.fromId(config.atGlanceStyle)
        val weather = snapshot.weather
        val tempStr = weather?.let { formatTemp(it.tempC, config.atGlanceFahrenheit) }
        val weatherIcon = weather?.let {
            AtAGlanceDataCollector.weatherIconForCode(it.weatherCode, it.isDay)
        } ?: GlanceIconKind.NONE
        val (contextText, contextIcon) = extractContextSignal(snapshot)

        val basePrimarySp = dateSizeSp.coerceIn(13f, 20f)
        primaryPaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        if (style == AtAGlanceStyle.SPLIT_PILLS) {
            var fittedSp = (basePrimarySp * 0.84f).coerceIn(12f, 17.5f)
            val secondarySp = (fittedSp * 0.84f).coerceIn(11f, 15f)
            val maxContentW = (maxScreenWidthPx - 40f * density).coerceAtLeast(160f * density)

            primaryPaint.textSize = fittedSp * density
            val padH = 13f * density
            val padV = 6.5f * density
            val pillGap = 7f * density

            val weatherIconSize = if (weatherIcon != GlanceIconKind.NONE) fittedSp * density * 1.05f else 0f
            val weatherIconGap = if (weatherIcon != GlanceIconKind.NONE) 5f * density else 0f
            val weatherPillW = if (tempStr != null) {
                primaryPaint.measureText(tempStr) + weatherIconSize + weatherIconGap + padH * 2f
            } else 0f

            val maxDateTextW = (maxContentW - weatherPillW - (if (weatherPillW > 0f) pillGap else 0f) - padH * 2f)
                .coerceAtLeast(60f * density)
            val dateRawW = primaryPaint.measureText(snapshot.dateText)
            if (dateRawW > maxDateTextW && dateRawW > 1f) {
                fittedSp = (fittedSp * (maxDateTextW / dateRawW)).coerceAtLeast(11f)
                primaryPaint.textSize = fittedSp * density
            }
            val dateText = TextUtils.ellipsize(snapshot.dateText, primaryPaint, maxDateTextW, TextUtils.TruncateAt.END).toString()
            val datePillW = primaryPaint.measureText(dateText) + padH * 2f
            val row1H = (primaryPaint.fontMetrics.descent - primaryPaint.fontMetrics.ascent) + padV * 2f
            val row1W = datePillW + (if (weatherPillW > 0f) pillGap + weatherPillW else 0f)

            var sTextEllipsized: String? = null
            var sRowW = 0f
            var sRowH = 0f
            if (!contextText.isNullOrBlank()) {
                secondaryPaint.textSize = secondarySp * density
                val iconSz2 = if (contextIcon != GlanceIconKind.NONE) secondarySp * density * 1.02f else 0f
                val iconGap2 = if (contextIcon != GlanceIconKind.NONE) 5f * density else 0f
                sTextEllipsized = TextUtils.ellipsize(contextText, secondaryPaint, maxContentW - iconSz2 - iconGap2 - 20f * density, TextUtils.TruncateAt.END).toString()
                sRowW = secondaryPaint.measureText(sTextEllipsized) + iconSz2 + iconGap2 + 22f * density
                sRowH = (secondaryPaint.fontMetrics.descent - secondaryPaint.fontMetrics.ascent) + 8f * density
            }

            val rowGap = if (sTextEllipsized != null) 5f * density else 0f
            return AtAGlanceMeasureResult(
                widthPx = max(row1W, sRowW),
                heightPx = row1H + rowGap + sRowH,
                primaryLine = dateText,
                secondaryLine = sTextEllipsized,
                primaryIcon = GlanceIconKind.NONE,
                secondaryIcon = contextIcon,
                fittedPrimarySp = fittedSp,
                weatherPillText = tempStr,
                weatherPillIcon = weatherIcon
            )
        }

        val primaryRaw: String
        val primaryIcon: GlanceIconKind
        val secRaw: String?
        val secIcon: GlanceIconKind

        when (style) {
            AtAGlanceStyle.MINIMAL, AtAGlanceStyle.FROSTED_PILL -> {
                primaryRaw = if (tempStr != null) "$tempStr  •  ${snapshot.dateText}" else snapshot.dateText
                primaryIcon = weatherIcon
                secRaw = contextText
                secIcon = contextIcon
            }
            AtAGlanceStyle.TWO_LINE -> {
                primaryRaw = snapshot.dateText
                primaryIcon = GlanceIconKind.NONE
                val summary = weather?.summary?.trim().orEmpty()
                val weatherDesc = when {
                    tempStr != null && summary.isNotEmpty() -> "$tempStr $summary"
                    tempStr != null -> tempStr
                    else -> null
                }
                secRaw = when {
                    contextText != null && weatherDesc != null -> "$weatherDesc  •  $contextText"
                    contextText != null -> contextText
                    tempStr != null && summary.isNotEmpty() -> "$tempStr  •  $summary"
                    tempStr != null -> tempStr
                    else -> null
                }
                secIcon = if (weatherIcon != GlanceIconKind.NONE) weatherIcon else contextIcon
            }
            AtAGlanceStyle.SPLIT_PILLS -> {
                primaryRaw = snapshot.dateText
                primaryIcon = GlanceIconKind.NONE
                secRaw = contextText
                secIcon = contextIcon
            }
        }

        val padH = if (style == AtAGlanceStyle.FROSTED_PILL) 16f * density else 4f * density
        val padV = if (style == AtAGlanceStyle.FROSTED_PILL) 7f * density else 2f * density
        val maxContentW = (maxScreenWidthPx - 40f * density - padH * 2f).coerceAtLeast(160f * density)

        var fittedSp = basePrimarySp
        primaryPaint.textSize = fittedSp * density
        var iconSize1 = if (primaryIcon != GlanceIconKind.NONE) fittedSp * density * 1.05f else 0f
        var iconGap1 = if (primaryIcon != GlanceIconKind.NONE) 6f * density else 0f
        val rawTextW = primaryPaint.measureText(primaryRaw)
        val availTextW = (maxContentW - iconSize1 - iconGap1).coerceAtLeast(80f * density)

        if (rawTextW > availTextW && rawTextW > 1f) {
            fittedSp = (fittedSp * (availTextW / rawTextW)).coerceAtLeast(11.5f)
            primaryPaint.textSize = fittedSp * density
            iconSize1 = if (primaryIcon != GlanceIconKind.NONE) fittedSp * density * 1.05f else 0f
            iconGap1 = if (primaryIcon != GlanceIconKind.NONE) 5f * density else 0f
        }

        val primaryEllipsized = TextUtils.ellipsize(
            primaryRaw, primaryPaint, (maxContentW - iconSize1 - iconGap1).coerceAtLeast(60f * density), TextUtils.TruncateAt.END
        ).toString()
        val pRowW = primaryPaint.measureText(primaryEllipsized) + iconSize1 + iconGap1
        val pRowH = primaryPaint.fontMetrics.descent - primaryPaint.fontMetrics.ascent

        val secondarySp = (fittedSp * 0.76f).coerceIn(10.5f, 15.5f)
        secondaryPaint.textSize = secondarySp * density
        var sTextEllipsized: String? = null
        var sRowW = 0f
        var sRowH = 0f
        if (!secRaw.isNullOrBlank()) {
            val iconSize2 = if (secIcon != GlanceIconKind.NONE) secondarySp * density * 1.05f else 0f
            val iconGap2 = if (secIcon != GlanceIconKind.NONE) 5f * density else 0f
            sTextEllipsized = TextUtils.ellipsize(
                secRaw, secondaryPaint, (maxContentW - iconSize2 - iconGap2).coerceAtLeast(60f * density), TextUtils.TruncateAt.END
            ).toString()
            sRowW = secondaryPaint.measureText(sTextEllipsized) + iconSize2 + iconGap2
            sRowH = secondaryPaint.fontMetrics.descent - secondaryPaint.fontMetrics.ascent
        }

        val rowGap = if (sTextEllipsized != null) 4f * density else 0f
        return AtAGlanceMeasureResult(
            widthPx = max(pRowW, sRowW) + padH * 2f,
            heightPx = pRowH + rowGap + sRowH + padV * 2f,
            primaryLine = primaryEllipsized,
            secondaryLine = sTextEllipsized,
            primaryIcon = primaryIcon,
            secondaryIcon = secIcon,
            fittedPrimarySp = fittedSp
        )
    }

    /**
     * Single entry point used by App Preview, Carousel, Xposed Editor, and Live SystemUI.
     * Automatically resolves pure white vs dark ink from the wallpaper behind the widget.
     */
    fun drawAuto(
        canvas: Canvas,
        centerX: Float,
        topY: Float,
        config: ClockKitConfig,
        snapshot: AtAGlanceSnapshot,
        density: Float,
        dateSizeSp: Float,
        maxScreenWidthPx: Float = 1080f,
        wallpaperBitmap: Bitmap? = null,
        screenW: Float = maxScreenWidthPx,
        screenH: Float = 0f,
        wallpaperZoom: Float = 1.10f,
        bgOpacity: Float = 1f,
        fallbackColors: WallpaperColors? = null,
        sampleCenterX: Float = centerX,
        sampleTopY: Float = topY,
        alphaScale: Float = 1f
    ): AtAGlanceMeasureResult {
        val m = measure(config, snapshot, density, dateSizeSp, maxScreenWidthPx)
        val resolvedColor = AtAGlanceColorResolver.resolveForWallpaper(
            wallpaperBitmap = wallpaperBitmap,
            screenW = screenW,
            screenH = screenH,
            zoom = wallpaperZoom,
            centerX = sampleCenterX,
            topY = sampleTopY,
            widthPx = m.widthPx,
            heightPx = m.heightPx,
            bgOpacity = bgOpacity,
            fallbackColors = fallbackColors
        )
        return drawMeasured(
            canvas = canvas,
            m = m,
            centerX = centerX,
            topY = topY,
            config = config,
            density = density,
            textColor = resolvedColor,
            alphaScale = alphaScale
        )
    }

    fun draw(
        canvas: Canvas,
        centerX: Float,
        topY: Float,
        config: ClockKitConfig,
        snapshot: AtAGlanceSnapshot,
        density: Float,
        textColor: Int,
        dateSizeSp: Float,
        maxScreenWidthPx: Float = 1080f,
        alphaScale: Float = 1f
    ): AtAGlanceMeasureResult {
        val m = measure(config, snapshot, density, dateSizeSp, maxScreenWidthPx)
        return drawMeasured(canvas, m, centerX, topY, config, density, textColor, alphaScale)
    }

    private fun drawMeasured(
        canvas: Canvas,
        m: AtAGlanceMeasureResult,
        centerX: Float,
        topY: Float,
        config: ClockKitConfig,
        density: Float,
        textColor: Int,
        alphaScale: Float
    ): AtAGlanceMeasureResult {
        val style = AtAGlanceStyle.fromId(config.atGlanceStyle)
        val alphaInt = (alphaScale.coerceIn(0f, 1f) * 255).roundToInt().coerceIn(0, 255)
        if (alphaInt <= 2) return m

        val lum = (0.299 * Color.red(textColor) + 0.587 * Color.green(textColor) + 0.114 * Color.blue(textColor)) / 255.0
        val isLightInk = lum > 0.45
        val effectiveTextColor = Color.argb(alphaInt, Color.red(textColor), Color.green(textColor), Color.blue(textColor))

        primaryPaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        primaryPaint.clearShadowLayer()
        secondaryPaint.clearShadowLayer()

        if (style == AtAGlanceStyle.SPLIT_PILLS) {
            val fittedSp = m.fittedPrimarySp
            val secondarySp = (fittedSp * 0.84f).coerceIn(11f, 15f)
            primaryPaint.textSize = fittedSp * density
            primaryPaint.color = effectiveTextColor

            val padH = 13f * density
            val padV = 6.5f * density
            val pillGap = 7f * density
            val pHeight = (primaryPaint.fontMetrics.descent - primaryPaint.fontMetrics.ascent) + padV * 2f

            val datePillW = primaryPaint.measureText(m.primaryLine) + padH * 2f
            val weatherText = m.weatherPillText
            val wIconSz = if (m.weatherPillIcon != GlanceIconKind.NONE) fittedSp * density * 1.05f else 0f
            val wIconGap = if (m.weatherPillIcon != GlanceIconKind.NONE) 5f * density else 0f
            val weatherPillW = if (weatherText != null) {
                primaryPaint.measureText(weatherText) + wIconSz + wIconGap + padH * 2f
            } else 0f

            val row1W = datePillW + (if (weatherPillW > 0f) pillGap + weatherPillW else 0f)
            val row1StartX = centerX - (row1W / 2f)

            val pillFill = if (isLightInk) {
                Color.argb((alphaInt * AtAGlanceColorResolver.PILL_DARK_FILL_ALPHA).roundToInt(), 14, 16, 22)
            } else {
                Color.argb((alphaInt * AtAGlanceColorResolver.PILL_LIGHT_FILL_ALPHA).roundToInt(), 236, 239, 244)
            }
            val borderAlpha = if (isLightInk) {
                AtAGlanceColorResolver.PILL_LIGHT_INK_BORDER_ALPHA
            } else {
                AtAGlanceColorResolver.PILL_DARK_INK_BORDER_ALPHA
            }
            val pillStroke = Color.argb((alphaInt * borderAlpha).roundToInt(), Color.red(textColor), Color.green(textColor), Color.blue(textColor))
            borderPaint.strokeWidth = 1.1f * density

            val dateRect = RectF(row1StartX, topY, row1StartX + datePillW, topY + pHeight)
            pillPaint.color = pillFill
            borderPaint.color = pillStroke
            canvas.drawRoundRect(dateRect, pHeight / 2f, pHeight / 2f, pillPaint)
            canvas.drawRoundRect(dateRect, pHeight / 2f, pHeight / 2f, borderPaint)
            val pBaseline = topY + padV - primaryPaint.fontMetrics.ascent
            canvas.drawText(m.primaryLine, row1StartX + padH, pBaseline, primaryPaint)

            if (weatherText != null) {
                val wLeft = row1StartX + datePillW + pillGap
                val wRect = RectF(wLeft, topY, wLeft + weatherPillW, topY + pHeight)
                pillPaint.color = pillFill
                canvas.drawRoundRect(wRect, pHeight / 2f, pHeight / 2f, pillPaint)
                canvas.drawRoundRect(wRect, pHeight / 2f, pHeight / 2f, borderPaint)
                if (m.weatherPillIcon != GlanceIconKind.NONE) {
                    val iconCenterY = topY + pHeight / 2f
                    drawGlanceIcon(canvas, m.weatherPillIcon, wLeft + padH, iconCenterY - wIconSz / 2f, wIconSz, effectiveTextColor, density)
                }
                canvas.drawText(weatherText, wLeft + padH + wIconSz + wIconGap, pBaseline, primaryPaint)
            }

            val secText = m.secondaryLine
            if (!secText.isNullOrBlank()) {
                secondaryPaint.textSize = secondarySp * density
                secondaryPaint.color = Color.argb((alphaInt * 0.90f).roundToInt(), Color.red(effectiveTextColor), Color.green(effectiveTextColor), Color.blue(effectiveTextColor))
                val sPadV = 4f * density
                val sPadH = 11f * density
                val iconSz2 = if (m.secondaryIcon != GlanceIconKind.NONE) secondarySp * density * 1.02f else 0f
                val iconGap2 = if (m.secondaryIcon != GlanceIconKind.NONE) 5f * density else 0f
                val sRowW = secondaryPaint.measureText(secText) + iconSz2 + iconGap2 + sPadH * 2f
                val sRowH = (secondaryPaint.fontMetrics.descent - secondaryPaint.fontMetrics.ascent) + sPadV * 2f
                val sTop = topY + pHeight + 5f * density
                val sLeft = centerX - sRowW / 2f
                val sRect = RectF(sLeft, sTop, sLeft + sRowW, sTop + sRowH)

                pillPaint.color = pillFill
                canvas.drawRoundRect(sRect, sRowH / 2f, sRowH / 2f, pillPaint)
                canvas.drawRoundRect(sRect, sRowH / 2f, sRowH / 2f, borderPaint)

                if (m.secondaryIcon != GlanceIconKind.NONE) {
                    val iconCenterY = sTop + sRowH / 2f
                    drawGlanceIcon(canvas, m.secondaryIcon, sLeft + sPadH, iconCenterY - iconSz2 / 2f, iconSz2, secondaryPaint.color, density)
                }
                val sBaseline = sTop + sPadV - secondaryPaint.fontMetrics.ascent
                canvas.drawText(secText, sLeft + sPadH + iconSz2 + iconGap2, sBaseline, secondaryPaint)
            }

            return m
        }

        val primarySp = m.fittedPrimarySp
        val secondarySp = (primarySp * 0.76f).coerceIn(10.5f, 15.5f)
        val padV = if (style == AtAGlanceStyle.FROSTED_PILL) 7f * density else 2f * density

        if (style == AtAGlanceStyle.FROSTED_PILL) {
            val rect = RectF(
                centerX - m.widthPx / 2f,
                topY,
                centerX + m.widthPx / 2f,
                topY + m.heightPx
            )
            val corner = m.heightPx / 2f
            pillPaint.color = if (isLightInk) {
                Color.argb((alphaInt * AtAGlanceColorResolver.PILL_DARK_FILL_ALPHA).roundToInt(), 14, 16, 22)
            } else {
                Color.argb((alphaInt * AtAGlanceColorResolver.PILL_LIGHT_FILL_ALPHA).roundToInt(), 236, 239, 244)
            }
            canvas.drawRoundRect(rect, corner, corner, pillPaint)

            borderPaint.strokeWidth = 1.1f * density
            val borderAlpha = if (isLightInk) {
                AtAGlanceColorResolver.PILL_LIGHT_INK_BORDER_ALPHA
            } else {
                AtAGlanceColorResolver.PILL_DARK_INK_BORDER_ALPHA
            }
            borderPaint.color = Color.argb((alphaInt * borderAlpha).roundToInt(), Color.red(textColor), Color.green(textColor), Color.blue(textColor))
            canvas.drawRoundRect(rect, corner, corner, borderPaint)
        }

        primaryPaint.textSize = primarySp * density
        primaryPaint.color = effectiveTextColor

        val pTextW = primaryPaint.measureText(m.primaryLine)
        val iconSize1 = if (m.primaryIcon != GlanceIconKind.NONE) primarySp * density * 1.05f else 0f
        val iconGap1 = if (m.primaryIcon != GlanceIconKind.NONE) 6f * density else 0f
        val pRowW = pTextW + iconSize1 + iconGap1
        val pStartX = centerX - (pRowW / 2f)
        val pTop = topY + padV
        val pBaseline = pTop - primaryPaint.fontMetrics.ascent

        if (m.primaryIcon != GlanceIconKind.NONE) {
            val iconCenterY = pTop + (primaryPaint.fontMetrics.descent - primaryPaint.fontMetrics.ascent) / 2f
            drawGlanceIcon(canvas, m.primaryIcon, pStartX, iconCenterY - iconSize1 / 2f, iconSize1, effectiveTextColor, density)
        }
        canvas.drawText(m.primaryLine, pStartX + iconSize1 + iconGap1, pBaseline, primaryPaint)

        val secText = m.secondaryLine
        if (!secText.isNullOrBlank()) {
            secondaryPaint.textSize = secondarySp * density
            secondaryPaint.color = Color.argb((alphaInt * 0.88f).roundToInt(), Color.red(effectiveTextColor), Color.green(effectiveTextColor), Color.blue(effectiveTextColor))

            val sTextW = secondaryPaint.measureText(secText)
            val iconSize2 = if (m.secondaryIcon != GlanceIconKind.NONE) secondarySp * density * 1.05f else 0f
            val iconGap2 = if (m.secondaryIcon != GlanceIconKind.NONE) 5f * density else 0f
            val sRowW = sTextW + iconSize2 + iconGap2
            val sStartX = centerX - (sRowW / 2f)
            val sTop = pTop + (primaryPaint.fontMetrics.descent - primaryPaint.fontMetrics.ascent) + (4f * density)
            val sBaseline = sTop - secondaryPaint.fontMetrics.ascent

            if (m.secondaryIcon != GlanceIconKind.NONE) {
                val iconCenterY = sTop + (secondaryPaint.fontMetrics.descent - secondaryPaint.fontMetrics.ascent) / 2f
                drawGlanceIcon(canvas, m.secondaryIcon, sStartX, iconCenterY - iconSize2 / 2f, iconSize2, secondaryPaint.color, density)
            }
            canvas.drawText(secText, sStartX + iconSize2 + iconGap2, sBaseline, secondaryPaint)
        }

        return m
    }

    private fun drawGlanceIcon(
        canvas: Canvas,
        kind: GlanceIconKind,
        left: Float,
        top: Float,
        size: Float,
        tint: Int,
        density: Float
    ) {
        val u = size / 24f
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tint
            setStyle(Paint.Style.STROKE)
            strokeWidth = (1.7f * density).coerceAtMost(size * 0.14f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tint
            setStyle(Paint.Style.FILL)
        }

        canvas.save()
        canvas.translate(left, top)
        when (kind) {
            GlanceIconKind.SUN -> {
                canvas.drawCircle(12f * u, 12f * u, 4.8f * u, stroke)
                for (i in 0 until 8) {
                    val a = (i * 45.0) * PI / 180.0
                    canvas.drawLine(
                        (12f + 7.4f * cos(a)).toFloat() * u, (12f + 7.4f * sin(a)).toFloat() * u,
                        (12f + 9.8f * cos(a)).toFloat() * u, (12f + 9.8f * sin(a)).toFloat() * u,
                        stroke
                    )
                }
            }
            GlanceIconKind.MOON, GlanceIconKind.DND -> {
                val moon = Path().apply {
                    addArc(4.5f * u, 4.5f * u, 19.5f * u, 19.5f * u, -60f, 220f)
                    cubicTo(11f * u, 16f * u, 10f * u, 9f * u, 15.8f * u, 5.5f * u)
                }
                canvas.drawPath(moon, stroke)
            }
            GlanceIconKind.CLOUD -> {
                val cloud = Path().apply {
                    moveTo(6.5f * u, 18f * u)
                    lineTo(17.5f * u, 18f * u)
                    cubicTo(20.5f * u, 18f * u, 21f * u, 12.5f * u, 17.5f * u, 12f * u)
                    cubicTo(17f * u, 7.5f * u, 10.5f * u, 7f * u, 9.5f * u, 11f * u)
                    cubicTo(6f * u, 10.5f * u, 4f * u, 14.5f * u, 6.5f * u, 18f * u)
                    close()
                }
                canvas.drawPath(cloud, stroke)
            }
            GlanceIconKind.PARTLY_CLOUDY -> {
                canvas.drawCircle(9f * u, 9f * u, 3.3f * u, stroke)
                val cloud = Path().apply {
                    moveTo(8f * u, 19f * u)
                    lineTo(18f * u, 19f * u)
                    cubicTo(20.8f * u, 19f * u, 21f * u, 14f * u, 18f * u, 13.5f * u)
                    cubicTo(17.5f * u, 10f * u, 12f * u, 9.5f * u, 11f * u, 13f * u)
                    cubicTo(8f * u, 12.5f * u, 6f * u, 16f * u, 8f * u, 19f * u)
                    close()
                }
                canvas.drawPath(cloud, stroke)
            }
            GlanceIconKind.RAIN -> {
                val cloud = Path().apply {
                    moveTo(6.5f * u, 14.5f * u)
                    lineTo(17.5f * u, 14.5f * u)
                    cubicTo(20.5f * u, 14.5f * u, 21f * u, 9.5f * u, 17.5f * u, 9f * u)
                    cubicTo(17f * u, 5f * u, 10.5f * u, 4.5f * u, 9.5f * u, 8.2f * u)
                    cubicTo(6f * u, 7.8f * u, 4f * u, 11.5f * u, 6.5f * u, 14.5f * u)
                    close()
                }
                canvas.drawPath(cloud, stroke)
                canvas.drawLine(8.5f * u, 17.5f * u, 7.2f * u, 20.5f * u, stroke)
                canvas.drawLine(13f * u, 17.5f * u, 11.7f * u, 20.5f * u, stroke)
                canvas.drawLine(17.5f * u, 17.5f * u, 16.2f * u, 20.5f * u, stroke)
            }
            GlanceIconKind.STORM -> {
                val bolt = Path().apply {
                    moveTo(13.5f * u, 3.5f * u)
                    lineTo(7f * u, 13f * u)
                    lineTo(12f * u, 13f * u)
                    lineTo(10.5f * u, 20.5f * u)
                    lineTo(17f * u, 11f * u)
                    lineTo(12f * u, 11f * u)
                    close()
                }
                canvas.drawPath(bolt, fill)
            }
            GlanceIconKind.SNOW -> {
                for (i in 0 until 3) {
                    val a = (i * 60.0) * PI / 180.0
                    canvas.drawLine(
                        (12f + 7f * cos(a)).toFloat() * u, (12f + 7f * sin(a)).toFloat() * u,
                        (12f - 7f * cos(a)).toFloat() * u, (12f - 7f * sin(a)).toFloat() * u,
                        stroke
                    )
                }
            }
            GlanceIconKind.FOG -> {
                canvas.drawLine(4.5f * u, 8f * u, 19.5f * u, 8f * u, stroke)
                canvas.drawLine(6.5f * u, 12.5f * u, 17.5f * u, 12.5f * u, stroke)
                canvas.drawLine(4.5f * u, 17f * u, 19.5f * u, 17f * u, stroke)
            }
            GlanceIconKind.ALARM -> {
                canvas.drawCircle(12f * u, 13f * u, 7f * u, stroke)
                canvas.drawLine(12f * u, 13f * u, 12f * u, 9.5f * u, stroke)
                canvas.drawLine(12f * u, 13f * u, 14.8f * u, 14.5f * u, stroke)
                canvas.drawLine(5f * u, 6f * u, 7.5f * u, 4f * u, stroke)
                canvas.drawLine(19f * u, 6f * u, 16.5f * u, 4f * u, stroke)
            }
            GlanceIconKind.BOLT -> {
                val bolt = Path().apply {
                    moveTo(13.5f * u, 3.5f * u)
                    lineTo(7f * u, 13f * u)
                    lineTo(12f * u, 13f * u)
                    lineTo(10.5f * u, 20.5f * u)
                    lineTo(17f * u, 11f * u)
                    lineTo(12f * u, 11f * u)
                    close()
                }
                canvas.drawPath(bolt, fill)
            }
            GlanceIconKind.MUSIC -> {
                canvas.drawCircle(8.5f * u, 17f * u, 2.8f * u, stroke)
                canvas.drawLine(11.3f * u, 17f * u, 11.3f * u, 6f * u, stroke)
                canvas.drawLine(11.3f * u, 6f * u, 18f * u, 4.5f * u, stroke)
                canvas.drawLine(18f * u, 4.5f * u, 18f * u, 10f * u, stroke)
            }
            GlanceIconKind.TORCH -> {
                val torch = Path().apply {
                    moveTo(8f * u, 5f * u); lineTo(16f * u, 5f * u)
                    lineTo(14.5f * u, 10f * u); lineTo(14.5f * u, 19.5f * u)
                    lineTo(9.5f * u, 19.5f * u); lineTo(9.5f * u, 10f * u); close()
                }
                canvas.drawPath(torch, stroke)
            }
            GlanceIconKind.NONE -> {}
        }
        canvas.restore()
    }
}
