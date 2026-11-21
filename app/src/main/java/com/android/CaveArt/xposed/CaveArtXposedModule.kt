package com.android.CaveArt.xposed

import android.animation.ValueAnimator
import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.graphics.*
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.TextClock
import android.widget.TextView
import com.android.CaveArt.clockkit.*
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.io.File
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

fun getSettingFloat(ctx: Context, path: String, default: Float): Float {
    var res = default
    try {
        val cursor: Cursor? = ctx.contentResolver.query(
            Uri.parse("content://com.android.CaveArt.settings/$path"),
            null, null, null, null
        )
        cursor?.use { if (it.moveToFirst()) res = it.getFloat(0) }
    } catch (_: Exception) {}
    return res
}

fun getSettingInt(ctx: Context, path: String, default: Int): Int {
    var res = default
    try {
        val cursor: Cursor? = ctx.contentResolver.query(
            Uri.parse("content://com.android.CaveArt.settings/$path"),
            null, null, null, null
        )
        cursor?.use { if (it.moveToFirst()) res = it.getInt(0) }
    } catch (_: Exception) {}
    return res
}

fun getSettingString(ctx: Context, path: String, default: String): String {
    var res = default
    try {
        val cursor: Cursor? = ctx.contentResolver.query(
            Uri.parse("content://com.android.CaveArt.settings/$path"),
            null, null, null, null
        )
        cursor?.use { if (it.moveToFirst()) res = it.getString(0) ?: default }
    } catch (_: Exception) {}
    return res
}

fun loadClockKitConfigFromProvider(ctx: Context): ClockKitConfig {
    val json = getSettingString(ctx, "clockkit_config", "")
    ClockKitConfigStore.fromJson(json)?.let { return it }

    val base = ClockKitConfig(
        styleId = "adaptive_vector",
        clockX = getSettingFloat(ctx, "clock_x", 0f),
        clockY = getSettingFloat(ctx, "clock_y", 110f),
        dateX = getSettingFloat(ctx, "date_x", 0f),
        dateY = getSettingFloat(ctx, "date_y", 75f),
        dateAttached = getSettingInt(ctx, "date_attached", 1) == 1,
        dateFormat = getSettingInt(ctx, "date_format", 0),
        color = getSettingInt(ctx, "clock_color", Color.WHITE),
        font = getSettingString(ctx, "clock_font", "default"),
        isDepthEnabled = getSettingInt(ctx, "clock_depth_enabled", 1) == 1,
        depthOcclusionLimit = getSettingFloat(ctx, "clock_depth_occlusion", 0.45f),
        isGravityEnabled = getSettingInt(ctx, "clock_gravity", 0) == 1,
        gravityIntensity = getSettingFloat(ctx, "clock_gravity_intensity", 1.0f)
    )
    return base
        .withFloat("adaptive_vector", "hour_size", getSettingFloat(ctx, "clock_hour_size", 100f))
        .withFloat("adaptive_vector", "min_size", getSettingFloat(ctx, "clock_minute_size", 75f))
        .withFloat("adaptive_vector", "stroke_width", getSettingFloat(ctx, "clock_stroke_width", 8f))
        .withFloat("adaptive_vector", "roundness", getSettingFloat(ctx, "clock_roundness", 30f))
        .withBool("adaptive_vector", "dual_tone", getSettingInt(ctx, "clock_dual_tone", 1) == 1)
        .withBool("adaptive_vector", "stretch", getSettingInt(ctx, "clock_stretch", 0) == 1)
        .withString("adaptive_vector", "layout", if (getSettingInt(ctx, "clock_layout", 0) == 1) "vertical" else "horizontal")
}

class SpringFloat(
    var value: Float = 0f,
    var targetValue: Float = 0f,
    var stiffness: Float = 320f,
    var dampingRatio: Float = 0.72f,
    val onUpdate: (Float) -> Unit
) : Choreographer.FrameCallback {
    private var velocity = 0f
    private var lastTimeNanos = 0L
    private var isRunning = false

    fun animateTo(target: Float) {
        if (abs(targetValue - target) < 0.1f && isRunning) return
        targetValue = target
        if (!isRunning) {
            isRunning = true
            lastTimeNanos = 0L
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isRunning) return
        if (lastTimeNanos == 0L) {
            lastTimeNanos = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(this)
            return
        }
        val dt = ((frameTimeNanos - lastTimeNanos) / 1_000_000_000f).coerceIn(0.001f, 0.032f)
        lastTimeNanos = frameTimeNanos

        val displacement = value - targetValue
        val naturalFreq = sqrt(stiffness)
        val dampingCoeff = 2f * dampingRatio * naturalFreq
        val acceleration = -stiffness * displacement - dampingCoeff * velocity

        velocity += acceleration * dt
        value += velocity * dt

        onUpdate(value)

        if (abs(displacement) < 0.5f && abs(velocity) < 5f) {
            value = targetValue
            velocity = 0f
            isRunning = false
            onUpdate(value)
        } else {
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun stop() {
        isRunning = false
        velocity = 0f
    }
}

class CaveArtXposedModule : XposedModule() {

    companion object {
        const val NOTIF_CLOCK_TOP_DP = 84f
        const val COMPACT_DATE_TOP_DP = 46f

        val customClockId = View.generateViewId()
        val customDateId = View.generateViewId()
        var topMarginPx = 300
        var effectiveTopMarginPx = 300
        var dateTopMarginPx = 150
        var effectiveDateTopMarginPx = 150
        var screenW = 1080f
        var screenH = 2400f

        var activeClock: VectorTextClock? = null
        var activeDate: IndependentDateView? = null
        var activeRootLayout: ViewGroup? = null
        var activeEditorOverlay: LockscreenEditorOverlay? = null

        var isShiftedForNotifications = false
        var currentShiftY = 0f

        @Volatile var isEditModeActive = false
        @Volatile var statusBarState = 1
        @Volatile var isDozing = false
        @Volatile var keyguardStateControllerObj: Any? = null
        @Volatile var lastDownWasOnClock = false
        @Volatile var keyguardSettingsButtonId = 0

        @Volatile var cachedLockEditEnabled = true
        @Volatile var cachedLockEditRequireUnlock = true
        @Volatile var cachedUdfpsX = -1f
        @Volatile var cachedUdfpsY = -1f
        @Volatile var cachedUdfpsRadius = 0f

        @Volatile private var lastWeatherSyncTemp = Float.NaN
        @Volatile private var lastWeatherSyncCode = -1
        @Volatile private var lastWeatherSyncMs = 0L

        private val savedChildAlphas = WeakHashMap<View, Float>()

        fun getCompactClockTopPx(context: Context): Float {
            val density = context.resources.displayMetrics.density
            val clock = activeClock
            val date = activeDate
            val showExternalDate = clock?.activeDatePolicy() == ClockDatePolicy.EXTERNAL_STANDARD &&
                clock.clockConfig.atGlanceEnabled &&
                date?.visibility == View.VISIBLE
            if (!showExternalDate) return NOTIF_CLOCK_TOP_DP * density

            val dateTop = COMPACT_DATE_TOP_DP * density
            val dateH = date?.measureCompactHeightPx() ?: (20f * density)
            val gap = 10f * density
            return max(NOTIF_CLOCK_TOP_DP * density, dateTop + dateH + gap)
        }

        fun refreshCachedLockEditSettings(ctx: Context) {
            Thread {
                val editEnabled = getSettingInt(ctx, "lock_edit_enabled", 1) == 1
                val requireUnlock = getSettingInt(ctx, "lock_edit_require_unlock", 1) == 1
                val ux = getSettingFloat(ctx, "udfps_x", -1f)
                val uy = getSettingFloat(ctx, "udfps_y", -1f)
                val ur = getSettingFloat(ctx, "udfps_radius", 0f)
                cachedLockEditEnabled = editEnabled
                cachedLockEditRequireUnlock = requireUnlock
                if (ur > 0f) {
                    cachedUdfpsX = ux
                    cachedUdfpsY = uy
                    cachedUdfpsRadius = ur
                }
            }.start()
        }

        fun publishLocalWeather(ctx: Context, data: AtAGlanceWeatherData) {
            AtAGlanceDataCollector.updateCachedWeather(data)
            activeDate?.post { activeDate?.updateTimeDirectly() }

            val now = System.currentTimeMillis()
            val unchanged = !lastWeatherSyncTemp.isNaN() &&
                abs(lastWeatherSyncTemp - data.tempC) < 0.2f &&
                lastWeatherSyncCode == data.weatherCode &&
                (now - lastWeatherSyncMs) < 10 * 60_000L
            if (unchanged) return

            lastWeatherSyncTemp = data.tempC
            lastWeatherSyncCode = data.weatherCode
            lastWeatherSyncMs = now

            Thread {
                try {
                    val bundle = Bundle().apply {
                        putFloat("temp_c", data.tempC)
                        putInt("weather_code", data.weatherCode)
                        putBoolean("is_day", data.isDay)
                        putString("summary", data.summary)
                        putString("source", data.source)
                        putLong("updated_at", data.updatedAtMs)
                    }
                    ctx.contentResolver.call(
                        Uri.parse("content://com.android.CaveArt.settings"),
                        "saveLocalWeather",
                        null,
                        bundle
                    )
                } catch (_: Throwable) {}
            }.start()
        }

        fun extractWeatherFromSmartspaceTargets(ctx: Context, targets: List<*>?) {
            if (targets.isNullOrEmpty()) return
            try {
                for (item in targets) {
                    if (item == null) continue
                    val featureType = item.javaClass.getMethod("getFeatureType").invoke(item) as? Int ?: -1
                    if (featureType != 1 && featureType != 10) continue

                    val headerAction = try {
                        item.javaClass.getMethod("getHeaderAction").invoke(item)
                    } catch (_: Throwable) { null }
                    val baseAction = try {
                        item.javaClass.getMethod("getBaseAction").invoke(item)
                    } catch (_: Throwable) { null }

                    val title = headerAction?.javaClass?.getMethod("getTitle")?.invoke(headerAction)?.toString()
                    val subtitle = headerAction?.javaClass?.getMethod("getSubtitle")?.invoke(headerAction)?.toString()
                        ?: baseAction?.javaClass?.getMethod("getSubtitle")?.invoke(baseAction)?.toString()
                    val contentDesc = headerAction?.javaClass?.getMethod("getContentDescription")?.invoke(headerAction)?.toString()

                    val tempC = AtAGlanceDataCollector.parseTemperatureCelsius(title)
                        ?: AtAGlanceDataCollector.parseTemperatureCelsius(contentDesc)
                        ?: AtAGlanceDataCollector.parseTemperatureCelsius(subtitle)
                        ?: continue

                    val descText = listOfNotNull(contentDesc, subtitle, title)
                        .firstOrNull { it.isNotBlank() && !it.trim().matches(Regex("""-?\d+°.*""")) }
                    val code = AtAGlanceDataCollector.parseWeatherConditionToCode(descText)
                    val summary = descText?.take(24)?.trim()
                        ?.ifBlank { AtAGlanceDataCollector.weatherSummaryForCode(code) }
                        ?: AtAGlanceDataCollector.weatherSummaryForCode(code)

                    publishLocalWeather(
                        ctx,
                        AtAGlanceWeatherData(
                            tempC = tempC,
                            weatherCode = code,
                            isDay = AtAGlanceDataCollector.isCurrentlyDaytime(),
                            summary = summary,
                            source = "System Smartspace",
                            updatedAtMs = System.currentTimeMillis()
                        )
                    )
                    return
                }
            } catch (_: Throwable) {}
        }

        fun scanHiddenSmartspaceOrOmniJaws(ctx: Context, rootLayout: ViewGroup?) {
            if (rootLayout != null) {
                val found = scanViewTreeForWeather(rootLayout)
                if (found != null) {
                    publishLocalWeather(ctx, found)
                    return
                }
            }
            Thread {
                val omni = AtAGlanceDataCollector.readOmniJawsLocal(ctx)
                if (omni != null) {
                    publishLocalWeather(ctx, omni)
                }
            }.start()
        }

        private fun scanViewTreeForWeather(group: ViewGroup): AtAGlanceWeatherData? {
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i)
                val name = child.javaClass.simpleName.lowercase()
                if (name.contains("smartspace") || name.contains("slice") || name.contains("weather")) {
                    val res = findWeatherTextInSubtree(child)
                    if (res != null) return res
                }
                if (child is ViewGroup) {
                    val nested = scanViewTreeForWeather(child)
                    if (nested != null) return nested
                }
            }
            return null
        }

        private fun findWeatherTextInSubtree(view: View): AtAGlanceWeatherData? {
            if (view is TextView) {
                val txt = view.text?.toString()
                val desc = view.contentDescription?.toString()
                val tempC = AtAGlanceDataCollector.parseTemperatureCelsius(txt)
                    ?: AtAGlanceDataCollector.parseTemperatureCelsius(desc)
                if (tempC != null) {
                    val condStr = desc ?: txt
                    val code = AtAGlanceDataCollector.parseWeatherConditionToCode(condStr)
                    return AtAGlanceWeatherData(
                        tempC = tempC,
                        weatherCode = code,
                        isDay = AtAGlanceDataCollector.isCurrentlyDaytime(),
                        summary = AtAGlanceDataCollector.weatherSummaryForCode(code),
                        source = "System Smartspace",
                        updatedAtMs = System.currentTimeMillis()
                    )
                }
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    val found = findWeatherTextInSubtree(view.getChildAt(i))
                    if (found != null) return found
                }
            }
            return null
        }

        fun getClockHitRect(paddingDp: Float = 24f): RectF {
            val clock = activeClock ?: return RectF()
            val density = clock.resources.displayMetrics.density
            val padPx = paddingDp * density
            val rect = RectF()
            if (!clock.lastRenderedBounds.isEmpty) {
                rect.set(clock.lastRenderedBounds)
            } else {
                val m = clock.measureForCurrentState(clock.notificationProgress)
                val cx = (screenW / 2f) + (clock.userTranslationX * (1f - clock.notificationProgress))
                val userTopY = effectiveTopMarginPx.toFloat()
                val notifTopY = getCompactClockTopPx(clock.context)
                val top = userTopY + (notifTopY - userTopY) * clock.notificationProgress
                rect.set(
                    cx - (m.contentWidthPx / 2f),
                    top,
                    cx + (m.contentWidthPx / 2f),
                    top + m.contentHeightPx
                )
            }

            val date = activeDate
            if (date != null && date.visibility == View.VISIBLE && date.isAttachedToClock) {
                val loc = IntArray(2)
                date.getLocationOnScreen(loc)
                if (date.width > 0 && date.height > 0) {
                    rect.union(
                        loc[0].toFloat(),
                        loc[1].toFloat(),
                        (loc[0] + date.width).toFloat(),
                        (loc[1] + date.height).toFloat()
                    )
                }
            }

            rect.inset(-padPx, -padPx)
            return rect
        }

        fun hideStockSettingsPopupIfPresent(root: ViewGroup) {
            if (keyguardSettingsButtonId != 0) {
                val windowRoot = (root.rootView as? ViewGroup) ?: root
                windowRoot.findViewById<View>(keyguardSettingsButtonId)?.let { btn ->
                    btn.animate().cancel()
                    if (btn.alpha != 0f) btn.alpha = 0f
                    if (btn.visibility != View.GONE) btn.visibility = View.GONE
                }
            }
        }

        fun canEnterEditMode(ctx: Context): Pair<Boolean, Boolean> {
            val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            val isSecure = km?.isKeyguardSecure == true
            if (!isSecure || !cachedLockEditRequireUnlock) return true to false

            val canDismiss = try {
                val ksc = keyguardStateControllerObj
                if (ksc != null) {
                    (ksc.javaClass.getMethod("canDismissLockScreen").invoke(ksc) as? Boolean) ?: false
                } else {
                    km?.isKeyguardLocked == false
                }
            } catch (_: Throwable) {
                false
            }
            return if (canDismiss) (true to false) else (false to true)
        }

        fun setEditMode(active: Boolean) {
            isEditModeActive = active
            val root = activeRootLayout ?: return
            val clock = activeClock
            val date = activeDate

            if (active) {
                updateNotificationShift(root.context, false)
                hideStockSettingsPopupIfPresent(root)
                savedChildAlphas.clear()
                for (i in 0 until root.childCount) {
                    val child = root.getChildAt(i)
                    savedChildAlphas[child] = child.alpha
                    child.animate().cancel()
                    child.alpha = 0f
                }
            } else {
                for (i in 0 until root.childCount) {
                    val child = root.getChildAt(i)
                    if (child.id == customClockId) {
                        child.animate().cancel()
                        child.animate().alpha(1f).setDuration(180L).start()
                        continue
                    }
                    if (child.id == customDateId) {
                        val showExt = clock?.activeDatePolicy() == ClockDatePolicy.EXTERNAL_STANDARD &&
                            clock.clockConfig.atGlanceEnabled
                        child.animate().cancel()
                        child.animate().alpha(if (showExt) 1f else 0f).setDuration(180L).start()
                        continue
                    }
                    val prevAlpha = savedChildAlphas[child] ?: continue
                    child.animate().cancel()
                    child.animate().alpha(prevAlpha).setDuration(180L).start()
                }
                savedChildAlphas.clear()
            }
            date?.invalidate()
        }

        fun applyLiveEditPreview(cfg: ClockKitConfig, newCollisionMap: FloatArray? = null) {
            val clock = activeClock ?: return
            val date = activeDate
            val ctx = clock.context
            val dm = ctx.resources.displayMetrics

            topMarginPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, cfg.clockY, dm).toInt()
            dateTopMarginPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, cfg.dateY, dm).toInt()

            val prevDepth = clock.clockConfig.isDepthEnabled
            val prevGravity = clock.clockConfig.isGravityEnabled
            clock.clockConfig = cfg
            if (newCollisionMap != null) clock.collisionMap = newCollisionMap
            clock.userTranslationX = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, cfg.clockX, dm)

            if (prevDepth != cfg.isDepthEnabled || prevGravity != cfg.isGravityEnabled) {
                clock.reloadDepthMaskAsync()
            }

            computeEffectiveMargins(ctx)
            val measured = clock.measureForCurrentState(0f)
            val visualClockTopMarginPx = if (measured.wantsFullScreenBounds) {
                0
            } else {
                (effectiveTopMarginPx - measured.topPadPx).toInt().coerceAtLeast(0)
            }

            (clock.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                val targetW = if (measured.wantsFullScreenBounds) screenW.toInt() else -1
                val targetH = if (measured.wantsFullScreenBounds) screenH.toInt() else -2
                if (lp.topMargin != visualClockTopMarginPx || lp.width != targetW || lp.height != targetH) {
                    lp.width = targetW
                    lp.height = targetH
                    lp.topMargin = visualClockTopMarginPx
                    clock.layoutParams = lp
                }
            }
            clock.updateInterpolatedPositions()
            clock.syncFrameLoop()
            clock.requestLayout()
            clock.invalidate()

            if (date != null) {
                date.clockConfig = cfg
                val dateX = if (cfg.dateAttached) cfg.clockX else cfg.dateX
                date.userTranslationX = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dateX, dm)
                date.isAttachedToClock = cfg.dateAttached
                date.dateFormatIndex = cfg.dateFormat
                date.setTextColor(cfg.color)

                val dateSp = LockscreenSceneRenderer.computeDateSizeSp(cfg)
                date.updateSizeFromClock(dateSp)

                val showExt = clock.activeDatePolicy() == ClockDatePolicy.EXTERNAL_STANDARD && cfg.atGlanceEnabled
                date.visibility = if (showExt) View.VISIBLE else View.GONE

                (date.layoutParams as? ViewGroup.MarginLayoutParams)?.let { dlp ->
                    if (dlp.topMargin != effectiveDateTopMarginPx) {
                        dlp.topMargin = effectiveDateTopMarginPx
                        date.layoutParams = dlp
                    }
                }
                date.updateTimeDirectly()
                date.updatePosition()
                date.requestLayout()
                date.invalidate()
            }
        }

        fun updateNotificationShift(context: Context, hasNotifications: Boolean) {
            val effective = if (isEditModeActive) false else hasNotifications
            isShiftedForNotifications = effective
            activeClock?.setClockState(effective)
            activeDate?.setDateState(effective)
        }

        fun computeEffectiveMargins(context: Context) {
            val density = context.resources.displayMetrics.density
            val clock = activeClock
            val date = activeDate
            val style = clock?.activeStyle()
            val cfg = clock?.clockConfig

            val needsCollision = style != null && cfg != null && style.needsCollisionMap(cfg)
            val isAttached = cfg?.dateAttached == true && cfg.atGlanceEnabled &&
                style?.datePolicy(cfg) == ClockDatePolicy.EXTERNAL_STANDARD

            val dateH = (date?.lastMeasuredH?.takeIf { it > 0f }
                ?: date?.measuredHeight?.toFloat()?.takeIf { it > 0f }
                ?: (22f * density))
            val gapPx = 12f * density
            val minSafeTopPx = 38f * density
            val compoundOffsetPx = dateH + gapPx

            effectiveTopMarginPx = if (isAttached && needsCollision) {
                max(topMarginPx.toFloat(), minSafeTopPx + compoundOffsetPx).toInt()
            } else {
                topMarginPx
            }

            effectiveDateTopMarginPx = if (isAttached && needsCollision) {
                (effectiveTopMarginPx - compoundOffsetPx).toInt()
            } else {
                dateTopMarginPx
            }
        }

        fun sendUdfpsCoordinates(ctx: Context, centerX: Float, centerY: Float, radius: Float) {
            if (radius > 0f) {
                cachedUdfpsX = centerX
                cachedUdfpsY = centerY
                cachedUdfpsRadius = radius
            }
            try {
                val bundle = Bundle().apply {
                    putFloat("udfps_x", centerX)
                    putFloat("udfps_y", centerY)
                    putFloat("udfps_radius", radius)
                }
                ctx.contentResolver.call(
                    Uri.parse("content://com.android.CaveArt.settings"),
                    "updateUdfps",
                    null,
                    bundle
                )
                ctx.sendBroadcast(Intent("com.android.CaveArt.UPDATE_UDFPS_LOCATION").apply {
                    putExtras(bundle)
                })
            } catch (_: Exception) {}
        }
    }

    @Volatile private var windowTouchHookInstalled = false
    @Volatile private var isInjectingCancel = false

    private fun installWindowTouchHooks(windowRootClass: Class<*>) {
        if (windowTouchHookInstalled) return
        windowTouchHookInstalled = true

        val handler = Handler(Looper.getMainLooper())
        var downX = 0f
        var downY = 0f
        var downTime = 0L
        var touchSlop = -1
        var pendingLongPress: Runnable? = null
        val clockLongPressDelayMs = 340L

        fun cancelPending() {
            pendingLongPress?.let { handler.removeCallbacks(it) }
            pendingLongPress = null
        }

        try {
            val dispatchTouchMethod = windowRootClass.getMethod("dispatchTouchEvent", MotionEvent::class.java)
            hook(dispatchTouchMethod).intercept { chain ->
                val ev = chain.args[0] as MotionEvent
                if (isInjectingCancel) {
                    return@intercept chain.proceed()
                }

                val overlay = activeEditorOverlay
                if (isEditModeActive && overlay != null && overlay.isAttachedToWindow && overlay.visibility == View.VISIBLE) {
                    cancelPending()
                    return@intercept overlay.dispatchTouchEvent(ev)
                }

                val windowView = chain.thisObject as? ViewGroup
                val rootLayout = activeRootLayout
                val clock = activeClock
                if (windowView == null || rootLayout == null || clock == null) {
                    cancelPending()
                    return@intercept chain.proceed()
                }

                val ctx = windowView.context
                if (touchSlop < 0) {
                    touchSlop = android.view.ViewConfiguration.get(ctx).scaledTouchSlop
                }

                val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val isAwakeKeyguard = (pm?.isInteractive != false) &&
                    statusBarState == 1 &&
                    !isDozing &&
                    rootLayout.isAttachedToWindow &&
                    rootLayout.isShown &&
                    rootLayout.alpha > 0.6f &&
                    clock.isShown

                if (!isAwakeKeyguard || !cachedLockEditEnabled) {
                    cancelPending()
                    if (ev.actionMasked == MotionEvent.ACTION_DOWN) lastDownWasOnClock = false
                    return@intercept chain.proceed()
                }

                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        cancelPending()
                        val rawX = ev.rawX
                        val rawY = ev.rawY
                        val nearUdfps = cachedUdfpsRadius > 0f && cachedUdfpsX > 0f &&
                            hypot((rawX - cachedUdfpsX).toDouble(), (rawY - cachedUdfpsY).toDouble()) < (cachedUdfpsRadius * 1.45)

                        val hitClock = !nearUdfps && getClockHitRect(24f).contains(rawX, rawY)
                        lastDownWasOnClock = hitClock

                        if (hitClock) {
                            downX = ev.x
                            downY = ev.y
                            downTime = ev.downTime
                            val r = Runnable {
                                pendingLongPress = null
                                val currentOverlay = activeEditorOverlay ?: return@Runnable
                                if (!currentOverlay.isAttachedToWindow) return@Runnable

                                windowView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

                                val now = SystemClock.uptimeMillis()
                                val cancelEv = MotionEvent.obtain(downTime, now, MotionEvent.ACTION_CANCEL, downX, downY, 0)
                                isInjectingCancel = true
                                try {
                                    windowView.dispatchTouchEvent(cancelEv)
                                } finally {
                                    isInjectingCancel = false
                                    cancelEv.recycle()
                                }

                                val (allowed, needsUnlock) = canEnterEditMode(ctx)
                                if (allowed) {
                                    currentOverlay.openEditor()
                                } else if (needsUnlock) {
                                    currentOverlay.showUnlockPromptPill()
                                }
                            }
                            pendingLongPress = r
                            handler.postDelayed(r, clockLongPressDelayMs)
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (pendingLongPress != null) {
                            val dx = ev.x - downX
                            val dy = ev.y - downY
                            if (dx * dx + dy * dy > touchSlop * touchSlop) {
                                cancelPending()
                            }
                        }
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> cancelPending()
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        cancelPending()
                        lastDownWasOnClock = false
                    }
                }

                chain.proceed()
            }
        } catch (_: Throwable) {}

        try {
            val dispatchKeyMethod = windowRootClass.getMethod("dispatchKeyEvent", KeyEvent::class.java)
            hook(dispatchKeyMethod).intercept { chain ->
                val ke = chain.args[0] as KeyEvent
                val overlay = activeEditorOverlay
                if (isEditModeActive && overlay != null && overlay.isAttachedToWindow && overlay.visibility == View.VISIBLE) {
                    if (ke.keyCode == KeyEvent.KEYCODE_BACK && ke.action == KeyEvent.ACTION_UP) {
                        if (overlay.handleBackPressed()) {
                            return@intercept true
                        }
                    }
                    if (overlay.dispatchKeyEvent(ke)) {
                        return@intercept true
                    }
                }
                chain.proceed()
            }
        } catch (_: Throwable) {}
    }

    private fun attachOverlayToWindow(rootLayout: ViewGroup, context: Context) {
        val windowRoot = (rootLayout.rootView as? ViewGroup) ?: rootLayout
        installWindowTouchHooks(windowRoot.javaClass)

        val editor = activeEditorOverlay ?: LockscreenEditorOverlay(context).also {
            activeEditorOverlay = it
        }
        editor.visibility = if (isEditModeActive) View.VISIBLE else View.GONE
        editor.elevation = 250f
        editor.translationZ = 250f

        if (editor.parent !== windowRoot) {
            (editor.parent as? ViewGroup)?.removeView(editor)
            windowRoot.addView(editor, ViewGroup.LayoutParams(-1, -1))
        } else {
            editor.bringToFront()
        }
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        super.onPackageLoaded(param)

        if (param.packageName == "com.android.systemui") {
            val classLoader = try {
                param.javaClass.getMethod("getDefaultClassLoader").invoke(param) as ClassLoader
            } catch (_: Exception) {
                param.javaClass.getMethod("getClassLoader").invoke(param) as ClassLoader
            }

            listOf(
                "com.android.systemui.statusbar.lockscreen.LockscreenSmartspaceController",
                "com.google.android.systemui.smartspace.BcSmartspaceDataProvider",
                "com.android.systemui.smartspace.BcSmartspaceDataProvider"
            ).forEach { ssName ->
                try {
                    val ssClass = classLoader.loadClass(ssName)
                    ssClass.declaredMethods.filter { it.name == "onTargetsAvailable" }.forEach { m ->
                        hook(m).intercept { chain ->
                            val targets = chain.args.firstOrNull() as? List<*>
                            val ctx = activeClock?.context ?: activeRootLayout?.context
                            if (ctx != null && targets != null) {
                                extractWeatherFromSmartspaceTargets(ctx, targets)
                            }
                            chain.proceed()
                        }
                    }
                } catch (_: Throwable) {}
            }

            try {
                val sbscClass = classLoader.loadClass("com.android.systemui.statusbar.StatusBarStateControllerImpl")
                sbscClass.declaredMethods.filter { it.name == "setState" }.forEach { m ->
                    hook(m).intercept { chain ->
                        val res = chain.proceed()
                        (chain.args.firstOrNull() as? Int)?.let { newState ->
                            statusBarState = newState
                            if (newState != 1 && isEditModeActive) {
                                activeEditorOverlay?.dismiss(save = false)
                            }
                        }
                        res
                    }
                }
                sbscClass.declaredMethods.filter { it.name == "setIsDozing" }.forEach { m ->
                    hook(m).intercept { chain ->
                        val res = chain.proceed()
                        (chain.args.firstOrNull() as? Boolean)?.let { dozing ->
                            isDozing = dozing
                            if (dozing && isEditModeActive) {
                                activeEditorOverlay?.dismiss(save = false)
                            }
                        }
                        res
                    }
                }
            } catch (_: Throwable) {}

            try {
                val kscClass = classLoader.loadClass("com.android.systemui.statusbar.policy.KeyguardStateControllerImpl")
                kscClass.declaredConstructors.forEach { ctor ->
                    hook(ctor).intercept { chain ->
                        val res = chain.proceed()
                        keyguardStateControllerObj = chain.thisObject
                        res
                    }
                }
            } catch (_: Throwable) {}

            listOf(
                "com.android.systemui.keyguard.ui.viewmodel.KeyguardTouchHandlingViewModel",
                "com.android.systemui.keyguard.ui.viewmodel.KeyguardLongPressViewModel",
                "com.android.systemui.keyguard.domain.interactor.KeyguardTouchHandlingInteractor",
                "com.android.systemui.keyguard.domain.interactor.KeyguardLongPressInteractor"
            ).forEach { targetName ->
                try {
                    val targetClass = classLoader.loadClass(targetName)
                    targetClass.declaredMethods.filter { it.name == "onLongPress" }.forEach { m ->
                        hook(m).intercept { chain ->
                            if (cachedLockEditEnabled && (lastDownWasOnClock || isEditModeActive)) {
                                if (m.returnType == Boolean::class.javaPrimitiveType) true else null
                            } else {
                                chain.proceed()
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }

            listOf(
                "com.android.systemui.shade.NotificationShadeWindowView",
                "com.android.systemui.statusbar.phone.NotificationShadeWindowView"
            ).forEach { shadeName ->
                try {
                    val shadeClass = classLoader.loadClass(shadeName)
                    installWindowTouchHooks(shadeClass)
                } catch (_: Throwable) {}
            }

            try {
                val authControllerClass = classLoader.loadClass("com.android.systemui.biometrics.AuthController")
                val targetMethods = authControllerClass.declaredMethods.filter {
                    it.name in listOf("onAodInterrupt", "setUdfpsLocation", "onUdfpsLocationChanged", "doAuthenticate", "updateSensorLocations")
                }

                targetMethods.forEach { method ->
                    hook(method).intercept { chain ->
                        val result = chain.proceed()
                        try {
                            val controller = chain.thisObject
                            val boundsMethod = controller.javaClass.declaredMethods.firstOrNull { it.name == "getUdfpsBounds" }
                            val bounds = boundsMethod?.invoke(controller) as? Rect

                            val ctx = (chain.args.firstOrNull() as? Context) ?: activeClock?.context
                            if (bounds != null && bounds.width() > 0 && ctx != null) {
                                sendUdfpsCoordinates(ctx, bounds.exactCenterX(), bounds.exactCenterY(), bounds.width() / 2f)
                            } else if (ctx != null) {
                                val locMethod = controller.javaClass.declaredMethods.firstOrNull { it.name == "getUdfpsLocation" }
                                val loc = locMethod?.invoke(controller)
                                val density = ctx.resources.displayMetrics.density
                                val defaultR = 42f * density
                                if (loc is PointF) {
                                    sendUdfpsCoordinates(ctx, loc.x, loc.y, defaultR)
                                } else if (loc is Point) {
                                    sendUdfpsCoordinates(ctx, loc.x.toFloat(), loc.y.toFloat(), defaultR)
                                }
                            }
                        } catch (_: Exception) {}
                        result
                    }
                }
            } catch (_: Exception) {}

            var largeId = 0
            var smallId = 0
            try {
                val clockViewIdsClass = classLoader.loadClass("com.android.systemui.plugins.keyguard.ui.clocks.ClockViewIds")
                val clockViewIdsInstance = clockViewIdsClass.getDeclaredField("INSTANCE").get(null)
                largeId = clockViewIdsClass.getMethod("getLOCKSCREEN_CLOCK_VIEW_LARGE").invoke(clockViewIdsInstance) as Int
                smallId = clockViewIdsClass.getMethod("getLOCKSCREEN_CLOCK_VIEW_SMALL").invoke(clockViewIdsInstance) as Int
            } catch (_: Exception) {}

            try {
                val clockSectionClass = classLoader.loadClass("com.android.systemui.keyguard.ui.view.layout.sections.ClockSection")
                val constraintLayoutClass = classLoader.loadClass("androidx.constraintlayout.widget.ConstraintLayout")
                val bindDataMethod = clockSectionClass.getDeclaredMethod("bindData", constraintLayoutClass)

                hook(bindDataMethod).intercept { chain ->
                    val result = chain.proceed()
                    val rootLayout = chain.args[0] as? ViewGroup

                    if (rootLayout != null) {
                        val context = rootLayout.context
                        activeRootLayout = rootLayout
                        if (keyguardSettingsButtonId == 0) {
                            keyguardSettingsButtonId = context.resources.getIdentifier(
                                "keyguard_settings_button", "id", context.packageName
                            )
                        }

                        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
                            val bounds = wm.maximumWindowMetrics.bounds
                            screenW = bounds.width().toFloat()
                            screenH = bounds.height().toFloat()
                        } else if (wm != null) {
                            val dm = DisplayMetrics()
                            @Suppress("DEPRECATION")
                            wm.defaultDisplay.getRealMetrics(dm)
                            screenW = dm.widthPixels.toFloat()
                            screenH = dm.heightPixels.toFloat()
                        } else {
                            screenW = context.resources.displayMetrics.widthPixels.toFloat()
                            screenH = context.resources.displayMetrics.heightPixels.toFloat()
                        }

                        SystemUIHider.initIds(context)
                        OverlapPreventionHelper.initIds(context)
                        refreshCachedLockEditSettings(context)

                        if (rootLayout.findViewById<View>(customClockId) == null) {
                            val initialConfig = loadClockKitConfigFromProvider(context)
                            val collMap = getSettingString(context, "clock_collision_map", "")
                            val dateSize = LockscreenSceneRenderer.computeDateSizeSp(initialConfig)

                            topMarginPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, initialConfig.clockY, context.resources.displayMetrics).toInt()
                            dateTopMarginPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, initialConfig.dateY, context.resources.displayMetrics).toInt()
                            val initialXPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, initialConfig.clockX, context.resources.displayMetrics)
                            val dateX = if (initialConfig.dateAttached) initialConfig.clockX else initialConfig.dateX
                            val dateXPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dateX, context.resources.displayMetrics)

                            val myCustomClock = VectorTextClock(context).apply {
                                id = customClockId
                                format12Hour = "hh:mm"
                                format24Hour = "HH:mm"
                                clockConfig = initialConfig
                                telemetryZoom = getSettingFloat(context, "telemetry_zoom", 1.10f)
                                collisionMap = if (collMap.isNotEmpty()) collMap.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray() else null
                                userTranslationX = initialXPx
                                translationX = 0f
                                translationY = currentShiftY
                                translationZ = 50f
                                elevation = 50f
                            }
                            myCustomClock.reloadDepthMaskAsync()
                            activeClock = myCustomClock

                            val showExt = myCustomClock.activeDatePolicy() == ClockDatePolicy.EXTERNAL_STANDARD &&
                                initialConfig.atGlanceEnabled
                            val myCustomDate = IndependentDateView(context).apply {
                                id = customDateId
                                clockConfig = initialConfig
                                dateFormatIndex = initialConfig.dateFormat
                                isAttachedToClock = initialConfig.dateAttached
                                dynamicDateSizeSp = dateSize
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, dateSize)
                                setTextColor(initialConfig.color)
                                userTranslationX = dateXPx
                                translationX = dateXPx
                                translationY = currentShiftY
                                translationZ = 60f
                                elevation = 60f
                                visibility = if (showExt) View.VISIBLE else View.GONE
                            }
                            activeDate = myCustomDate

                            computeEffectiveMargins(context)

                            rootLayout.viewTreeObserver.addOnPreDrawListener {
                                if (largeId != 0) rootLayout.findViewById<View>(largeId)?.apply { if (visibility != View.GONE) visibility = View.GONE; if (alpha != 0f) alpha = 0f; scaleX = 0f; scaleY = 0f }
                                if (smallId != 0) rootLayout.findViewById<View>(smallId)?.apply { if (visibility != View.GONE) visibility = View.GONE; if (alpha != 0f) alpha = 0f; scaleX = 0f; scaleY = 0f }
                                if (isEditModeActive) {
                                    hideStockSettingsPopupIfPresent(rootLayout)
                                    if (myCustomClock.alpha != 0f) myCustomClock.alpha = 0f
                                    if (myCustomDate.alpha != 0f) myCustomDate.alpha = 0f
                                }
                                SystemUIHider.forceHideOnPreDraw(rootLayout)
                                true
                            }

                            val lpClass = classLoader.loadClass("androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams")
                            val lpConstructor = lpClass.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)

                            rootLayout.addView(myCustomClock, lpConstructor.newInstance(-1, -2) as ViewGroup.LayoutParams)
                            rootLayout.addView(myCustomDate, lpConstructor.newInstance(-2, -2) as ViewGroup.LayoutParams)

                            OverlapPreventionHelper.forceElementsBelow(rootLayout, myCustomClock, myCustomDate)

                            if (rootLayout.isAttachedToWindow) {
                                attachOverlayToWindow(rootLayout, context)
                                scanHiddenSmartspaceOrOmniJaws(context, rootLayout)
                            } else {
                                rootLayout.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                                    override fun onViewAttachedToWindow(v: View) {
                                        attachOverlayToWindow(rootLayout, context)
                                        scanHiddenSmartspaceOrOmniJaws(context, rootLayout)
                                    }
                                    override fun onViewDetachedFromWindow(v: View) {}
                                })
                            }

                            val filter = IntentFilter().apply {
                                addAction(ClockKitConfigStore.ACTION_CLOCKKIT_UPDATED)
                                addAction("com.android.CaveArt.UPDATE_LOCK_EDIT_SETTINGS")
                                addAction("com.android.CaveArt.UPDATE_UDFPS_LOCATION")
                                addAction("com.android.CaveArt.UPDATE_AT_A_GLANCE")
                                addAction("com.android.CaveArt.UPDATE_TELEMETRY")
                                addAction("com.android.CaveArt.UPDATE_CLOCK_STYLE")
                                addAction("com.android.CaveArt.UPDATE_CLOCK_POSITION")
                                addAction("com.android.CaveArt.UPDATE_DATE_POSITION")
                                addAction("com.android.CaveArt.UPDATE_DATE_FORMAT")
                                addAction("com.android.CaveArt.UPDATE_DATE_ATTACHED")
                                addAction("com.android.CaveArt.UPDATE_CLOCK_STRETCH")
                                addAction("com.android.CaveArt.UPDATE_COLLISION_MAP")
                                addAction("com.android.CaveArt.UPDATE_CLOCK_COLOR")
                                addAction("com.android.CaveArt.UPDATE_CLOCK_FONT")
                                addAction("com.android.CaveArt.UPDATE_CLOCK_DUAL_TONE")
                                addAction("com.android.CaveArt.UPDATE_CLOCK_LAYOUT")
                                addAction("com.android.CaveArt.UPDATE_CLOCK_DEPTH")
                                addAction("com.android.CaveArt.UPDATE_LAYER_STACK")
                                addAction("com.android.CaveArt.UPDATE_CLOCK_GRAVITY")
                                addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
                                addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
                                addAction(Intent.ACTION_BATTERY_CHANGED)
                                addAction(Intent.ACTION_USER_PRESENT)
                                addAction(Intent.ACTION_SCREEN_ON)
                                addAction(Intent.ACTION_SCREEN_OFF)
                                addAction(Intent.ACTION_TIME_TICK)
                            }

                            val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                Context.RECEIVER_EXPORTED
                            } else 0

                            context.registerReceiver(object : BroadcastReceiver() {
                                override fun onReceive(ctx: Context?, intent: Intent?) {
                                    when (intent?.action) {
                                        "com.android.CaveArt.UPDATE_LOCK_EDIT_SETTINGS" -> {
                                            cachedLockEditEnabled = intent.getBooleanExtra("lock_edit_enabled", cachedLockEditEnabled)
                                            cachedLockEditRequireUnlock = intent.getBooleanExtra("lock_edit_require_unlock", cachedLockEditRequireUnlock)
                                        }
                                        "com.android.CaveArt.UPDATE_UDFPS_LOCATION" -> {
                                            val r = intent.getFloatExtra("udfps_radius", 0f)
                                            if (r > 0f) {
                                                cachedUdfpsX = intent.getFloatExtra("udfps_x", -1f)
                                                cachedUdfpsY = intent.getFloatExtra("udfps_y", -1f)
                                                cachedUdfpsRadius = r
                                            }
                                        }
                                        "com.android.CaveArt.UPDATE_AT_A_GLANCE",
                                        AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED,
                                        NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED,
                                        Intent.ACTION_BATTERY_CHANGED -> {
                                            if (!isEditModeActive) {
                                                myCustomDate.updateTimeDirectly()
                                                myCustomClock.updateInterpolatedPositions()
                                                myCustomClock.invalidate()
                                            }
                                        }
                                        Intent.ACTION_SCREEN_OFF -> {
                                            if (isEditModeActive) {
                                                activeEditorOverlay?.dismiss(save = false)
                                            }
                                        }
                                        ClockKitConfigStore.ACTION_CLOCKKIT_UPDATED -> {
                                            val newCfg = ClockKitConfigStore.fromJson(
                                                intent.getStringExtra("clockkit_config")
                                            ) ?: loadClockKitConfigFromProvider(context)

                                            if (intent.hasExtra("collision_map")) {
                                                val mapStr = intent.getStringExtra("collision_map") ?: ""
                                                myCustomClock.collisionMap = if (mapStr.isNotEmpty()) {
                                                    mapStr.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray()
                                                } else null
                                            }
                                            myCustomClock.telemetryZoom = getSettingFloat(context, "telemetry_zoom", 1.10f)
                                            applyFullConfigToViews(context, rootLayout, classLoader, myCustomClock, myCustomDate, newCfg)
                                        }
                                        "com.android.CaveArt.UPDATE_TELEMETRY" -> {
                                            if (intent.hasExtra("telemetry_zoom")) {
                                                myCustomClock.telemetryZoom = intent.getFloatExtra("telemetry_zoom", myCustomClock.telemetryZoom)
                                                myCustomClock.invalidate()
                                            }
                                        }
                                        "com.android.CaveArt.UPDATE_COLLISION_MAP" -> {
                                            val mapStr = intent.getStringExtra("collision_map") ?: ""
                                            myCustomClock.collisionMap = if (mapStr.isNotEmpty()) {
                                                mapStr.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray()
                                            } else null
                                            myCustomClock.requestLayout()
                                            myCustomClock.invalidate()
                                        }
                                        "com.android.CaveArt.UPDATE_CLOCK_DEPTH",
                                        "com.android.CaveArt.UPDATE_LAYER_STACK",
                                        "com.android.CaveArt.UPDATE_CLOCK_STYLE",
                                        "com.android.CaveArt.UPDATE_CLOCK_POSITION",
                                        "com.android.CaveArt.UPDATE_DATE_POSITION",
                                        "com.android.CaveArt.UPDATE_DATE_FORMAT",
                                        "com.android.CaveArt.UPDATE_DATE_ATTACHED",
                                        "com.android.CaveArt.UPDATE_CLOCK_STRETCH",
                                        "com.android.CaveArt.UPDATE_CLOCK_COLOR",
                                        "com.android.CaveArt.UPDATE_CLOCK_FONT",
                                        "com.android.CaveArt.UPDATE_CLOCK_DUAL_TONE",
                                        "com.android.CaveArt.UPDATE_CLOCK_LAYOUT",
                                        "com.android.CaveArt.UPDATE_CLOCK_GRAVITY",
                                        Intent.ACTION_TIME_TICK,
                                        Intent.ACTION_USER_PRESENT,
                                        Intent.ACTION_SCREEN_ON -> {
                                            if (intent.action == Intent.ACTION_SCREEN_ON) {
                                                refreshCachedLockEditSettings(context)
                                                scanHiddenSmartspaceOrOmniJaws(context, rootLayout)
                                            }
                                            if (isEditModeActive) return
                                            myCustomClock.telemetryZoom = getSettingFloat(context, "telemetry_zoom", 1.10f)
                                            val refreshed = loadClockKitConfigFromProvider(context)
                                            applyFullConfigToViews(context, rootLayout, classLoader, myCustomClock, myCustomDate, refreshed)
                                        }
                                    }
                                }
                            }, filter, receiverFlags)
                        } else if (rootLayout.isAttachedToWindow) {
                            attachOverlayToWindow(rootLayout, context)
                        }
                    }

                    result
                }
            } catch (_: Exception) {}

            val constraintSetClass = classLoader.loadClass("androidx.constraintlayout.widget.ConstraintSet")

            val sections = listOf(
                "com.android.systemui.keyguard.ui.view.layout.sections.ClockSection",
                "com.android.systemui.keyguard.ui.view.layout.sections.SmartspaceSection",
                "com.android.systemui.keyguard.ui.view.layout.sections.NotificationSection"
            )

            sections.forEach { sectionName ->
                try {
                    val sectionClass = classLoader.loadClass(sectionName)
                    val applyMethod = sectionClass.getDeclaredMethod("applyConstraints", constraintSetClass)
                    hook(applyMethod).intercept { chain ->
                        val result = chain.proceed()
                        val cs = chain.args[0]
                        val connect = constraintSetClass.getMethod("connect", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                        val setVisibility = constraintSetClass.getMethod("setVisibility", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                        val setAlpha = try { constraintSetClass.getMethod("setAlpha", Int::class.javaPrimitiveType, Float::class.javaPrimitiveType) } catch (_: Exception) { null }
                        val constrainWidth = try { constraintSetClass.getMethod("constrainWidth", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType) } catch (_: Exception) { null }
                        val constrainHeight = try { constraintSetClass.getMethod("constrainHeight", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType) } catch (_: Exception) { null }

                        val clock = activeClock
                        val context = clock?.context
                        if (context != null) {
                            var hasNotifications = false
                            try {
                                val getVisibility = constraintSetClass.getMethod("getVisibility", Int::class.javaPrimitiveType)
                                val largeVis = getVisibility.invoke(cs, largeId) as Int
                                hasNotifications = !isEditModeActive && (largeVis == 8 || largeVis == 4)
                                updateNotificationShift(context, hasNotifications)
                            } catch (_: Exception) {}

                            if (largeId != 0) { setVisibility.invoke(cs, largeId, 8); setAlpha?.invoke(cs, largeId, 0f) }
                            if (smallId != 0) { setVisibility.invoke(cs, smallId, 8); setAlpha?.invoke(cs, smallId, 0f) }
                            if (isEditModeActive && keyguardSettingsButtonId != 0) {
                                setVisibility.invoke(cs, keyguardSettingsButtonId, 8)
                                setAlpha?.invoke(cs, keyguardSettingsButtonId, 0f)
                            }

                            computeEffectiveMargins(context)
                            val density = context.resources.displayMetrics.density
                            val measured = clock.measureForCurrentState(notificationProgress = 0f)

                            val visualClockTopMarginPx = if (measured.wantsFullScreenBounds) {
                                0
                            } else {
                                (effectiveTopMarginPx - measured.topPadPx).toInt().coerceAtLeast(0)
                            }

                            constrainWidth?.invoke(cs, customClockId, if (measured.wantsFullScreenBounds) screenW.toInt() else -1)
                            constrainHeight?.invoke(cs, customClockId, if (measured.wantsFullScreenBounds) screenH.toInt() else -2)
                            connect.invoke(cs, customClockId, 3, 0, 3, visualClockTopMarginPx)
                            connect.invoke(cs, customClockId, 6, 0, 6, 0)
                            connect.invoke(cs, customClockId, 7, 0, 7, 0)
                            setVisibility.invoke(cs, customClockId, 0)
                            setAlpha?.invoke(cs, customClockId, if (isEditModeActive) 0f else 1f)

                            val showExternalDate = clock.activeDatePolicy() == ClockDatePolicy.EXTERNAL_STANDARD &&
                                clock.clockConfig.atGlanceEnabled
                            constrainWidth?.invoke(cs, customDateId, -2)
                            constrainHeight?.invoke(cs, customDateId, -2)
                            connect.invoke(cs, customDateId, 3, 0, 3, effectiveDateTopMarginPx)
                            connect.invoke(cs, customDateId, 6, 0, 6, 0)
                            connect.invoke(cs, customDateId, 7, 0, 7, 0)
                            setVisibility.invoke(cs, customDateId, if (showExternalDate) 0 else 8)
                            setAlpha?.invoke(cs, customDateId, if (!isEditModeActive && showExternalDate) 1f else 0f)

                            try {
                                val clockCurrentY = if (hasNotifications) getCompactClockTopPx(context) else effectiveTopMarginPx.toFloat()
                                val clockHeight = clock.measureHeightForNotificationPush(hasNotifications)

                                val lowestPoint = if (showExternalDate) {
                                    val dateCurrentY = if (hasNotifications) COMPACT_DATE_TOP_DP * density else effectiveDateTopMarginPx.toFloat()
                                    val dateHeight = if (hasNotifications) {
                                        activeDate?.measureCompactHeightPx() ?: (24f * density)
                                    } else {
                                        activeDate?.lastMeasuredH?.takeIf { it > 0f } ?: (24f * density)
                                    }
                                    max(clockCurrentY + clockHeight, dateCurrentY + dateHeight)
                                } else {
                                    clockCurrentY + clockHeight
                                }.toInt()

                                val targetMargin = lowestPoint + (26f * density).toInt()

                                val idsToPush = listOf(
                                    OverlapPreventionHelper.nsslId,
                                    OverlapPreventionHelper.mediaCarouselId,
                                    OverlapPreventionHelper.nsslPlaceholderId,
                                    OverlapPreventionHelper.keyguardStatusId,
                                    OverlapPreventionHelper.aodIconsId
                                )

                                for (id in idsToPush) {
                                    if (id != 0) {
                                        connect.invoke(cs, id, 3, 0, 3, targetMargin)
                                    }
                                }
                            } catch (_: Exception) {}
                        }

                        SystemUIHider.hideInConstraintSet(cs, constraintSetClass)
                        result
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun applyFullConfigToViews(
        context: Context,
        rootLayout: ViewGroup,
        classLoader: ClassLoader,
        clock: VectorTextClock,
        date: IndependentDateView,
        cfg: ClockKitConfig
    ) {
        val dm = context.resources.displayMetrics
        topMarginPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, cfg.clockY, dm).toInt()
        dateTopMarginPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, cfg.dateY, dm).toInt()

        clock.clockConfig = cfg
        clock.userTranslationX = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, cfg.clockX, dm)

        date.clockConfig = cfg
        val dateX = if (cfg.dateAttached) cfg.clockX else cfg.dateX
        date.userTranslationX = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dateX, dm)
        date.isAttachedToClock = cfg.dateAttached
        date.dateFormatIndex = cfg.dateFormat
        date.setTextColor(cfg.color)

        val dateSp = LockscreenSceneRenderer.computeDateSizeSp(cfg)
        date.updateSizeFromClock(dateSp)

        val showExternalDate = clock.activeDatePolicy() == ClockDatePolicy.EXTERNAL_STANDARD && cfg.atGlanceEnabled
        date.visibility = if (showExternalDate) View.VISIBLE else View.GONE

        computeEffectiveMargins(context)
        val measured = clock.measureForCurrentState(notificationProgress = 0f)
        val visualClockTopMarginPx = if (measured.wantsFullScreenBounds) {
            0
        } else {
            (effectiveTopMarginPx - measured.topPadPx).toInt().coerceAtLeast(0)
        }

        try {
            val constraintSetClass = classLoader.loadClass("androidx.constraintlayout.widget.ConstraintSet")
            val constraintLayoutClass = classLoader.loadClass("androidx.constraintlayout.widget.ConstraintLayout")
            if (constraintLayoutClass.isInstance(rootLayout)) {
                val set = constraintSetClass.getDeclaredConstructor().newInstance()
                constraintSetClass.getMethod("clone", constraintLayoutClass).invoke(set, rootLayout)
                constraintSetClass.getMethod("setMargin", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .invoke(set, customClockId, 3, visualClockTopMarginPx)
                constraintSetClass.getMethod("setMargin", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .invoke(set, customDateId, 3, effectiveDateTopMarginPx)
                constraintSetClass.getMethod("setVisibility", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .invoke(set, customDateId, if (showExternalDate) 0 else 8)
                constraintSetClass.getMethod("applyTo", constraintLayoutClass).invoke(set, rootLayout)
            }
        } catch (_: Exception) {}

        clock.reloadDepthMaskAsync()
        clock.updateInterpolatedPositions()
        clock.syncFrameLoop()
        clock.requestLayout()
        clock.invalidate()

        date.updateTimeDirectly()
        date.updatePosition()
        date.requestLayout()
        date.invalidate()
        CaveArtXposedModule.updateNotificationShift(context, CaveArtXposedModule.isShiftedForNotifications)
        date.checkAndResolveOverlap(animated = true)
    }
}

class IndependentDateView(context: Context) : View(context) {
    var clockConfig: ClockKitConfig = ClockKitConfig()
    var notificationProgress = 0f
    private var notifAnimator: ValueAnimator? = null
    var userTranslationX = 0f
    var dateFormatIndex = 0
    var isAttachedToClock = true
    var dynamicDateSizeSp = 17f
    var compactDateSizeSp = 14.5f
    var overlapShiftY = 0f

    private var currentSizeSp = 17f
    private var textColor = Color.WHITE
    private var snapshot = AtAGlanceSnapshot(dateText = "")
    var lastMeasuredW = 0f
    var lastMeasuredH = 0f

    private var torchCallback: CameraManager.TorchCallback? = null

    private val overlapSpring = SpringFloat(
        value = 0f,
        targetValue = 0f,
        stiffness = 320f,
        dampingRatio = 0.72f
    ) { currentOffset ->
        overlapShiftY = currentOffset
        updatePosition()
        invalidate()
    }

    init {
        isClickable = false
        isFocusable = false
        isLongClickable = false
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean = false

    fun measureCompactHeightPx(): Float {
        val density = resources.displayMetrics.density
        val snap = if (snapshot.dateText.isNotEmpty()) snapshot else AtAGlanceDataCollector.collect(context, clockConfig)
        return AtAGlanceRenderer.measure(clockConfig, snap, density, compactDateSizeSp, CaveArtXposedModule.screenW).heightPx
    }

    fun setTextColor(color: Int) {
        textColor = color
        invalidate()
    }

    fun setTextSize(unit: Int, sizeSp: Float) {
        currentSizeSp = sizeSp
        requestLayout()
        invalidate()
    }

    fun updateSizeFromClock(sizeSp: Float) {
        dynamicDateSizeSp = sizeSp
        applyCurrentTextSize()
        requestLayout()
        invalidate()
        post { checkAndResolveOverlap(animated = true) }
    }

    fun applyCurrentTextSize() {
        currentSizeSp = dynamicDateSizeSp + (compactDateSizeSp - dynamicDateSizeSp) * notificationProgress
        requestLayout()
    }

    fun setDateState(hasNotifications: Boolean) {
        val targetProgress = if (hasNotifications) 1f else 0f
        if (targetProgress == notificationProgress && notifAnimator?.isRunning != true) return
        notifAnimator?.cancel()
        notifAnimator = ValueAnimator.ofFloat(notificationProgress, targetProgress).apply {
            duration = 350
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener {
                notificationProgress = it.animatedValue as Float
                applyCurrentTextSize()
                updatePosition()
                invalidate()
            }
            start()
        }
    }

    fun updatePosition() {
        val density = resources.displayMetrics.density
        val targetYPx = CaveArtXposedModule.COMPACT_DATE_TOP_DP * density
        val userY = CaveArtXposedModule.effectiveDateTopMarginPx.toFloat()
        val notifTargetTranslationY = targetYPx - userY
        val contentW = lastMeasuredW.coerceAtLeast(measuredWidth.toFloat())
        val halfWidth = contentW / 2f
        val screenW = CaveArtXposedModule.screenW
        val sideSafeMargin = 18f * density
        val minAllowedTransX = (-(screenW / 2f) + halfWidth + sideSafeMargin).coerceAtMost(0f)
        val maxAllowedTransX = ((screenW / 2f) - halfWidth - sideSafeMargin).coerceAtLeast(0f)

        val safeUserTranslationX = userTranslationX.coerceIn(minAllowedTransX, maxAllowedTransX)

        if (isAttachedToClock && notificationProgress < 0.05f) {
            val clock = CaveArtXposedModule.activeClock
            val clockY = CaveArtXposedModule.effectiveTopMarginPx.toFloat()
            val dateH = lastMeasuredH.coerceAtLeast(measuredHeight.toFloat()).coerceAtLeast(currentSizeSp * density * 1.2f)
            val gap = 12f * density
            val minSafeTop = 38f * density
            val needsCollision = clock?.let { it.activeStyle().needsCollisionMap(it.clockConfig) } == true

            val idealAboveY = clockY - dateH - gap
            if (idealAboveY < minSafeTop && !needsCollision) {
                val clockH = clock?.lastContentHeightPx?.coerceAtLeast(40f * density) ?: (100f * density)
                val targetBelowY = clockY + clockH + gap
                val shiftNeeded = targetBelowY - userY
                translationY = shiftNeeded
                translationX = safeUserTranslationX
                return
            }
        }

        translationY = (notifTargetTranslationY * notificationProgress) + (overlapShiftY * (1f - notificationProgress))
        translationX = safeUserTranslationX + (0f - safeUserTranslationX) * notificationProgress
    }

    fun checkAndResolveOverlap(animated: Boolean = true) {
        val clock = CaveArtXposedModule.activeClock ?: return
        if (clock.activeDatePolicy() != ClockDatePolicy.EXTERNAL_STANDARD || !clockConfig.atGlanceEnabled || isAttachedToClock || notificationProgress > 0.5f) {
            if (overlapSpring.targetValue != 0f) {
                if (animated) overlapSpring.animateTo(0f) else { overlapSpring.stop(); overlapShiftY = 0f; updatePosition() }
            }
            return
        }

        val density = resources.displayMetrics.density
        val dateY = CaveArtXposedModule.effectiveDateTopMarginPx.toFloat()
        val dateH = lastMeasuredH.coerceAtLeast(measuredHeight.toFloat()).coerceAtLeast(currentSizeSp * density * 1.2f)
        val dateW = lastMeasuredW.coerceAtLeast(measuredWidth.toFloat())
        val dateCenterX = (CaveArtXposedModule.screenW / 2f) + userTranslationX

        val clockY = CaveArtXposedModule.effectiveTopMarginPx.toFloat()
        val clockH = clock.lastContentHeightPx.coerceAtLeast(40f * density)
        val clockW = clock.lastContentWidthPx.coerceAtLeast(CaveArtXposedModule.screenW * 0.45f)
        val clockCenterX = (CaveArtXposedModule.screenW / 2f) + clock.userTranslationX

        val xOverlap = abs(dateCenterX - clockCenterX) < ((dateW + clockW) / 2f)
        val margin = 16f * density
        val yOverlap = (dateY + dateH + margin > clockY) && (dateY < clockY + clockH + margin)

        if (xOverlap && yOverlap) {
            val clockCenterY = clockY + clockH / 2f
            val dateCenterY = dateY + dateH / 2f

            val targetY = if (dateCenterY < clockCenterY) {
                val candidateAbove = clockY - dateH - margin
                if (candidateAbove >= 36f * density) candidateAbove else clockY + clockH + margin
            } else {
                val candidateBelow = clockY + clockH + margin
                val maxSafe = CaveArtXposedModule.screenH - (120f * density)
                if (candidateBelow <= maxSafe) candidateBelow else clockY - dateH - margin
            }

            val shiftNeeded = targetY - dateY
            if (animated) {
                overlapSpring.animateTo(shiftNeeded)
            } else {
                overlapSpring.stop()
                overlapShiftY = shiftNeeded
                updatePosition()
            }
        } else {
            if (animated) {
                overlapSpring.animateTo(0f)
            } else {
                overlapSpring.stop()
                overlapShiftY = 0f
                updatePosition()
            }
        }
    }

    fun updateTimeDirectly() {
        try {
            val prevW = lastMeasuredW
            val prevH = lastMeasuredH
            snapshot = AtAGlanceDataCollector.collect(context, clockConfig)
            val m = AtAGlanceRenderer.measure(
                clockConfig, snapshot, resources.displayMetrics.density, currentSizeSp, CaveArtXposedModule.screenW
            )
            lastMeasuredW = m.widthPx
            lastMeasuredH = m.heightPx
            if (abs(prevW - m.widthPx) > 1f || abs(prevH - m.heightPx) > 1f) {
                requestLayout()
                updatePosition()
                CaveArtXposedModule.activeClock?.updateInterpolatedPositions()
                CaveArtXposedModule.activeClock?.invalidate()
            }
            invalidate()
        } catch (_: Exception) {}
    }

    fun refreshSettings() {
        val cfg = loadClockKitConfigFromProvider(context)
        clockConfig = cfg
        val dateSp = LockscreenSceneRenderer.computeDateSizeSp(cfg)
        dynamicDateSizeSp = dateSp
        compactDateSizeSp = 14.5f
        applyCurrentTextSize()

        setTextColor(cfg.color)
        dateFormatIndex = cfg.dateFormat
        isAttachedToClock = cfg.dateAttached

        val showExternal = ClockKitEngine.getStyle(cfg.styleId).datePolicy(cfg) == ClockDatePolicy.EXTERNAL_STANDARD && cfg.atGlanceEnabled
        visibility = if (showExternal) View.VISIBLE else View.GONE

        updateTimeDirectly()
        updatePosition()
        requestLayout()
        invalidate()
        post { checkAndResolveOverlap(animated = false) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshSettings()
        if (torchCallback == null) {
            try {
                val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                val cb = object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        post { updateTimeDirectly() }
                    }
                }
                torchCallback = cb
                cm?.registerTorchCallback(cb, Handler(Looper.getMainLooper()))
            } catch (_: Throwable) {}
        }
    }

    override fun onDetachedFromWindow() {
        torchCallback?.let { cb ->
            try {
                (context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager)?.unregisterTorchCallback(cb)
            } catch (_: Throwable) {}
        }
        torchCallback = null
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) {
            updateTimeDirectly()
            checkAndResolveOverlap(animated = false)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        if (snapshot.dateText.isEmpty()) {
            snapshot = AtAGlanceDataCollector.collect(context, clockConfig)
        }
        val m = AtAGlanceRenderer.measure(clockConfig, snapshot, density, currentSizeSp, CaveArtXposedModule.screenW)
        lastMeasuredW = m.widthPx
        lastMeasuredH = m.heightPx
        setMeasuredDimension(
            m.widthPx.roundToInt().coerceAtLeast(1),
            m.heightPx.roundToInt().coerceAtLeast(1)
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (!clockConfig.atGlanceEnabled || width <= 0 || height <= 0) return
        val density = resources.displayMetrics.density
        AtAGlanceRenderer.draw(
            canvas = canvas,
            centerX = width / 2f,
            topY = 0f,
            config = clockConfig,
            snapshot = snapshot,
            density = density,
            textColor = textColor,
            dateSizeSp = currentSizeSp,
            maxScreenWidthPx = CaveArtXposedModule.screenW
        )
    }
}

class VectorTextClock(context: Context) : TextClock(context) {
    var clockConfig: ClockKitConfig = ClockKitConfig()
    var collisionMap: FloatArray? = null
    var userTranslationX = 0f
    var telemetryZoom = 1.10f

    var notificationProgress = 0f
    private var notificationAnimator: ValueAnimator? = null

    internal var depthMaskBitmap: Bitmap? = null
    internal var rawDepthBitmap: Bitmap? = null
    internal val lastRenderedBounds = RectF()

    private val screenPos = IntArray(2)

    var lastTopPadPx: Float = 24f * context.resources.displayMetrics.density
    var lastContentHeightPx: Float = 110f * context.resources.displayMetrics.density
    var lastContentWidthPx: Float = 260f * context.resources.displayMetrics.density
    var lastWantsFullScreen: Boolean = false

    private var frameTickerActive = false
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!frameTickerActive) return
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        setTextColor(Color.TRANSPARENT)
        gravity = Gravity.CENTER
        isClickable = false
        isFocusable = false
        isLongClickable = false
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean = false

    fun activeStyle(): ClockStyle = ClockKitEngine.getStyle(clockConfig.styleId)

    fun activeDatePolicy(): ClockDatePolicy = activeStyle().datePolicy(clockConfig)

    fun syncFrameLoop() {
        val shouldRun = isAttachedToWindow &&
            windowVisibility == View.VISIBLE &&
            activeStyle().needsContinuousFrames(clockConfig, notificationProgress)
        if (shouldRun && !frameTickerActive) {
            frameTickerActive = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        } else if (!shouldRun && frameTickerActive) {
            stopFrameLoop()
        }
    }

    private fun stopFrameLoop() {
        frameTickerActive = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    fun measureForCurrentState(notificationProgress: Float = this.notificationProgress): ClockMeasureResult {
        val now = System.currentTimeMillis()
        val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
        val pattern = if (is24Hour) "HH:mm" else "hh:mm"
        val timeString = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
            .format(java.util.Date(now)).ifEmpty { "09:41" }
        val dateString = ClockTypefaceResolver.formatDate(now, clockConfig.dateFormat)
        val tf = ClockTypefaceResolver.resolve(context, clockConfig.font)

        val ctx = ClockMeasureContext(
            context = context,
            screenW = CaveArtXposedModule.screenW,
            screenH = CaveArtXposedModule.screenH,
            density = resources.displayMetrics.density,
            typeface = tf,
            timeString = timeString,
            dateString = dateString,
            notificationProgress = notificationProgress
        )
        return activeStyle().measure(ctx, clockConfig).also {
            lastTopPadPx = it.topPadPx
            lastContentHeightPx = it.contentHeightPx
            lastContentWidthPx = it.contentWidthPx
            lastWantsFullScreen = it.wantsFullScreenBounds
        }
    }

    fun measureHeightForNotificationPush(hasNotifications: Boolean): Float {
        val res = measureForCurrentState(if (hasNotifications) 1f else 0f)
        return if (hasNotifications) res.notificationCollapsedHeightPx else res.contentHeightPx
    }

    private fun decodeRawDepthBitmap(): Bitmap? {
        if (!clockConfig.isGravityEnabled) return null
        var decoded: Bitmap? = null
        try {
            val depthUri = Uri.parse("content://com.android.CaveArt.settings/raw_depth")
            context.contentResolver.openInputStream(depthUri)?.use { stream ->
                decoded = BitmapFactory.decodeStream(stream)
            }
        } catch (_: Exception) {}

        if (decoded == null) {
            val candidateDepthPaths = listOf(
                "/data/user_de/0/com.android.CaveArt/files/lock_raw_depth.png",
                "/data/user_de/0/com.android.CaveArt/no_backup/lock_raw_depth.png",
                "/data/data/com.android.CaveArt/files/lock_raw_depth.png"
            )
            for (path in candidateDepthPaths) {
                val file = File(path)
                if (file.exists() && file.length() > 0) {
                    try {
                        decoded = BitmapFactory.decodeFile(file.absolutePath)
                        if (decoded != null) break
                    } catch (_: Exception) {}
                }
            }
        }
        return decoded
    }

    private fun decodeDepthMaskBitmap(): Bitmap? {
        if (!clockConfig.isDepthEnabled) return null
        var decoded: Bitmap? = null
        try {
            val uri = Uri.parse("content://com.android.CaveArt.settings/depth_mask")
            context.contentResolver.openInputStream(uri)?.use { stream ->
                decoded = BitmapFactory.decodeStream(stream)
            }
        } catch (_: Exception) {}

        if (decoded == null) {
            val candidatePaths = listOf(
                "/data/user_de/0/com.android.CaveArt/files/lock_depth_mask.png",
                "/data/user_de/0/com.android.CaveArt/no_backup/boot_cutout.png",
                "/data/user_de/0/com.android.CaveArt/files/boot_cutout.png",
                "/data/data/com.android.CaveArt/files/lock_depth_mask.png"
            )
            for (path in candidatePaths) {
                val file = File(path)
                if (file.exists() && file.length() > 0) {
                    try {
                        decoded = BitmapFactory.decodeFile(file.absolutePath)
                        if (decoded != null) break
                    } catch (_: Exception) {}
                }
            }
        }
        return decoded
    }

    fun reloadDepthMaskAsync(onLoaded: (() -> Unit)? = null) {
        val needRaw = clockConfig.isGravityEnabled
        val needMask = clockConfig.isDepthEnabled
        Thread {
            val newRaw = if (needRaw) decodeRawDepthBitmap() else null
            val newMask = if (needMask) decodeDepthMaskBitmap() else null
            post {
                val oldRaw = rawDepthBitmap
                rawDepthBitmap = newRaw
                if (oldRaw != null && oldRaw !== newRaw && !oldRaw.isRecycled) oldRaw.recycle()

                val oldMask = depthMaskBitmap
                depthMaskBitmap = newMask
                if (oldMask != null && oldMask !== newMask && !oldMask.isRecycled) oldMask.recycle()

                invalidate()
                onLoaded?.invoke()
            }
        }.start()
    }

    fun reloadDepthMask() {
        val oldMask = depthMaskBitmap
        depthMaskBitmap = null
        oldMask?.takeIf { !it.isRecycled }?.recycle()

        val oldRaw = rawDepthBitmap
        rawDepthBitmap = null
        oldRaw?.takeIf { !it.isRecycled }?.recycle()

        rawDepthBitmap = decodeRawDepthBitmap()
        depthMaskBitmap = decodeDepthMaskBitmap()
    }

    fun refreshSettings() {
        clockConfig = loadClockKitConfigFromProvider(context)
        telemetryZoom = getSettingFloat(context, "telemetry_zoom", 1.10f)

        val mapStr = getSettingString(context, "clock_collision_map", "")
        collisionMap = if (mapStr.isNotEmpty()) {
            mapStr.split(",").mapNotNull { v -> v.toFloatOrNull() }.toFloatArray()
        } else null

        val dm = resources.displayMetrics
        CaveArtXposedModule.topMarginPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, clockConfig.clockY, dm).toInt()
        CaveArtXposedModule.dateTopMarginPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, clockConfig.dateY, dm).toInt()
        userTranslationX = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, clockConfig.clockX, dm)

        CaveArtXposedModule.computeEffectiveMargins(context)
        reloadDepthMaskAsync()
        updateInterpolatedPositions()
        syncFrameLoop()
        requestLayout()
        invalidate()

        CaveArtXposedModule.activeDate?.refreshSettings()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshSettings()
    }

    override fun onDetachedFromWindow() {
        stopFrameLoop()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) {
            reloadDepthMaskAsync()
            invalidate()
        }
        syncFrameLoop()
    }

    fun setClockState(hasNotifications: Boolean) {
        val targetProgress = if (hasNotifications) 1f else 0f
        if (targetProgress == notificationProgress && notificationAnimator?.isRunning != true) {
            syncFrameLoop()
            return
        }
        notificationAnimator?.cancel()
        notificationAnimator = ValueAnimator.ofFloat(notificationProgress, targetProgress).apply {
            duration = 350
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener {
                notificationProgress = it.animatedValue as Float
                updateInterpolatedPositions()
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    syncFrameLoop()
                }
            })
            start()
        }
        syncFrameLoop()
    }

    fun updateInterpolatedPositions() {
        translationX = 0f
        if (lastWantsFullScreen) {
            translationY = 0f
            return
        }
        val userTopY = CaveArtXposedModule.effectiveTopMarginPx.toFloat()
        val notifTopY = CaveArtXposedModule.getCompactClockTopPx(context)
        val currentVisualTopMargin = (userTopY - lastTopPadPx).coerceAtLeast(0f)
        val targetNotifVisualTopMargin = (notifTopY - lastTopPadPx).coerceAtLeast(0f)
        translationY = (targetNotifVisualTopMargin - currentVisualTopMargin) * notificationProgress
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val m = measureForCurrentState(0f)
        if (m.wantsFullScreenBounds) {
            setMeasuredDimension(
                CaveArtXposedModule.screenW.toInt().coerceAtLeast(1),
                CaveArtXposedModule.screenH.toInt().coerceAtLeast(1)
            )
        } else {
            val totalH = (m.contentHeightPx + m.topPadPx + m.bottomPadPx).toInt().coerceAtLeast(1)
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), totalH)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.currentTimeMillis()
        val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
        val pattern = if (is24Hour) "HH:mm" else "hh:mm"
        val timeString = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
            .format(java.util.Date(now))
        if (timeString.isEmpty()) return

        val density = resources.displayMetrics.density
        val tf = ClockTypefaceResolver.resolve(context, clockConfig.font)
        val dateString = ClockTypefaceResolver.formatDate(now, clockConfig.dateFormat)
        val style = activeStyle()

        val measureResult = style.measure(
            ClockMeasureContext(
                context = context,
                screenW = CaveArtXposedModule.screenW,
                screenH = CaveArtXposedModule.screenH,
                density = density,
                typeface = tf,
                timeString = timeString,
                dateString = dateString,
                notificationProgress = notificationProgress
            ),
            clockConfig
        )

        val halfClockW = measureResult.contentWidthPx / 2f
        val sideMargin = 18f * density
        val minAllowedTransX = (-(CaveArtXposedModule.screenW / 2f) + halfClockW + sideMargin).coerceAtMost(0f)
        val maxAllowedTransX = ((CaveArtXposedModule.screenW / 2f) - halfClockW - sideMargin).coerceAtLeast(0f)
        val safeUserX = userTranslationX.coerceIn(minAllowedTransX, maxAllowedTransX)

        val currentCenterX = (CaveArtXposedModule.screenW / 2f) + (safeUserX * (1f - notificationProgress))
        val userTopY = CaveArtXposedModule.effectiveTopMarginPx.toFloat()
        val notifTopY = CaveArtXposedModule.getCompactClockTopPx(context)
        val currentClockTopY = userTopY + (notifTopY - userTopY) * notificationProgress

        getLocationOnScreen(screenPos)
        val absX = screenPos[0].toFloat()
        val absY = screenPos[1].toFloat()

        canvas.save()
        canvas.translate(-absX, -absY)

        val frame = ClockFrameContext(
            context = context,
            screenW = CaveArtXposedModule.screenW,
            screenH = CaveArtXposedModule.screenH,
            density = density,
            clockCenterX = currentCenterX,
            clockTopY = currentClockTopY,
            timeMillis = now,
            timeString = timeString,
            dateString = dateString,
            is24Hour = is24Hour,
            typeface = tf,
            notificationProgress = notificationProgress,
            collisionMap = collisionMap,
            depthMask = depthMaskBitmap,
            rawDepthMap = rawDepthBitmap,
            zoomFactor = telemetryZoom
        )

        val renderRes = ClockKitEngine.renderForSystemUI(canvas, style, frame, clockConfig)
        lastRenderedBounds.set(renderRes.boundsOnScreen)

        canvas.restore()
    }
}
