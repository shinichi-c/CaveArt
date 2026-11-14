package com.android.CaveArt.xposed

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
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
import kotlin.math.abs
import kotlin.math.max
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

        var isShiftedForNotifications = false
        var currentShiftY = 0f

        fun updateNotificationShift(context: Context, hasNotifications: Boolean) {
            isShiftedForNotifications = hasNotifications
            activeClock?.setClockState(hasNotifications)
            activeDate?.setDateState(hasNotifications)
        }

        fun computeEffectiveMargins(context: Context) {
            val density = context.resources.displayMetrics.density
            val clock = activeClock
            val date = activeDate
            val style = clock?.activeStyle()
            val cfg = clock?.clockConfig

            val needsCollision = style != null && cfg != null && style.needsCollisionMap(cfg)
            val isAttached = cfg?.dateAttached == true && style?.datePolicy(cfg) == ClockDatePolicy.EXTERNAL_STANDARD

            val dateH = (date?.measuredHeight?.toFloat() ?: (20f * density)).coerceAtLeast(18f * density)
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

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        super.onPackageLoaded(param)

        if (param.packageName == "com.android.systemui") {
            val classLoader = try {
                param.javaClass.getMethod("getDefaultClassLoader").invoke(param) as ClassLoader
            } catch (_: Exception) {
                param.javaClass.getMethod("getClassLoader").invoke(param) as ClassLoader
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

                        if (rootLayout.findViewById<View>(customClockId) == null) {
                            val initialConfig = loadClockKitConfigFromProvider(context)
                            val collMap = getSettingString(context, "clock_collision_map", "")
                            val hSize = initialConfig.getFloat("adaptive_vector", "hour_size", 100f)
                            val autoDateSize = (hSize * 0.24f).coerceIn(14f, 34f)
                            val dateSize = getSettingFloat(context, "date_size", autoDateSize)

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
                            myCustomClock.reloadDepthMask()
                            activeClock = myCustomClock

                            val myCustomDate = IndependentDateView(context).apply {
                                id = customDateId
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
                                visibility = if (myCustomClock.activeDatePolicy() == ClockDatePolicy.EXTERNAL_STANDARD) View.VISIBLE else View.GONE
                            }
                            activeDate = myCustomDate

                            computeEffectiveMargins(context)

                            rootLayout.viewTreeObserver.addOnPreDrawListener {
                                if (largeId != 0) rootLayout.findViewById<View>(largeId)?.apply { if (visibility != View.GONE) visibility = View.GONE; if (alpha != 0f) alpha = 0f; scaleX = 0f; scaleY = 0f }
                                if (smallId != 0) rootLayout.findViewById<View>(smallId)?.apply { if (visibility != View.GONE) visibility = View.GONE; if (alpha != 0f) alpha = 0f; scaleX = 0f; scaleY = 0f }
                                SystemUIHider.forceHideOnPreDraw(rootLayout)
                                true
                            }

                            val lpClass = classLoader.loadClass("androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams")
                            val lpConstructor = lpClass.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)

                            rootLayout.addView(myCustomClock, lpConstructor.newInstance(-1, -2) as ViewGroup.LayoutParams)
                            rootLayout.addView(myCustomDate, lpConstructor.newInstance(-2, -2) as ViewGroup.LayoutParams)

                            OverlapPreventionHelper.forceElementsBelow(rootLayout, myCustomClock, myCustomDate)

                            val filter = IntentFilter().apply {
                                addAction(ClockKitConfigStore.ACTION_CLOCKKIT_UPDATED)
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
                                addAction(Intent.ACTION_USER_PRESENT)
                                addAction(Intent.ACTION_SCREEN_ON)
                                addAction(Intent.ACTION_TIME_TICK)
                            }

                            val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                Context.RECEIVER_EXPORTED
                            } else 0

                            context.registerReceiver(object : BroadcastReceiver() {
                                override fun onReceive(ctx: Context?, intent: Intent?) {
                                    when (intent?.action) {
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
                                            myCustomClock.telemetryZoom = getSettingFloat(context, "telemetry_zoom", 1.10f)
                                            val refreshed = loadClockKitConfigFromProvider(context)
                                            applyFullConfigToViews(context, rootLayout, classLoader, myCustomClock, myCustomDate, refreshed)
                                        }
                                    }
                                }
                            }, filter, receiverFlags)
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
                                hasNotifications = (largeVis == 8 || largeVis == 4)
                                updateNotificationShift(context, hasNotifications)
                            } catch (_: Exception) {}

                            if (largeId != 0) { setVisibility.invoke(cs, largeId, 8); setAlpha?.invoke(cs, largeId, 0f) }
                            if (smallId != 0) { setVisibility.invoke(cs, smallId, 8); setAlpha?.invoke(cs, smallId, 0f) }

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
                            setAlpha?.invoke(cs, customClockId, 1f)

                            val showExternalDate = clock.activeDatePolicy() == ClockDatePolicy.EXTERNAL_STANDARD
                            constrainWidth?.invoke(cs, customDateId, -2)
                            constrainHeight?.invoke(cs, customDateId, -2)
                            connect.invoke(cs, customDateId, 3, 0, 3, effectiveDateTopMarginPx)
                            connect.invoke(cs, customDateId, 6, 0, 6, 0)
                            connect.invoke(cs, customDateId, 7, 0, 7, 0)
                            setVisibility.invoke(cs, customDateId, if (showExternalDate) 0 else 8)
                            setAlpha?.invoke(cs, customDateId, if (showExternalDate) 1f else 0f)

                            try {
                                val clockCurrentY = if (hasNotifications) NOTIF_CLOCK_TOP_DP * density else effectiveTopMarginPx.toFloat()
                                val clockHeight = clock.measureHeightForNotificationPush(hasNotifications)

                                val lowestPoint = if (showExternalDate) {
                                    val dateCurrentY = if (hasNotifications) 56f * density else effectiveDateTopMarginPx.toFloat()
                                    val dateHeight = (if (hasNotifications) 18f else activeDate?.dynamicDateSizeSp ?: 20f) * density * 1.5f
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

        val dateX = if (cfg.dateAttached) cfg.clockX else cfg.dateX
        date.userTranslationX = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dateX, dm)
        date.isAttachedToClock = cfg.dateAttached
        date.dateFormatIndex = cfg.dateFormat
        date.setTextColor(cfg.color)

        val dateSp = if (cfg.styleId == "adaptive_vector") {
            (cfg.getFloat("adaptive_vector", "hour_size", 100f) * 0.24f).coerceIn(14f, 34f)
        } else {
            (20f * cfg.scale).coerceIn(14f, 32f)
        }
        date.updateSizeFromClock(dateSp)

        val showExternalDate = clock.activeDatePolicy() == ClockDatePolicy.EXTERNAL_STANDARD
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

        clock.reloadDepthMask()
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

class IndependentDateView(context: Context) : TextView(context) {
    var notificationProgress = 0f
    private var notifAnimator: ValueAnimator? = null
    var userTranslationX = 0f
    var dateFormatIndex = 0
    var isAttachedToClock = true
    var dynamicDateSizeSp = 22f
    var compactDateSizeSp = 18f
    var overlapShiftY = 0f

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
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setShadowLayer(10f, 0f, 3f, Color.argb(160, 0, 0, 0))
        isSingleLine = true
        includeFontPadding = false
        setPadding(0, 0, 0, 0)
        isClickable = false
        isFocusable = false
        isLongClickable = false
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean = false

    fun updateSizeFromClock(sizeSp: Float) {
        dynamicDateSizeSp = sizeSp
        applyCurrentTextSize()
        requestLayout()
        invalidate()
        post { checkAndResolveOverlap(animated = true) }
    }

    fun applyCurrentTextSize() {
        val currentSizeSp = dynamicDateSizeSp + (compactDateSizeSp - dynamicDateSizeSp) * notificationProgress
        setTextSize(TypedValue.COMPLEX_UNIT_SP, currentSizeSp)
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
        val targetYPx = 56f * density
        val userY = CaveArtXposedModule.effectiveDateTopMarginPx.toFloat()
        val notifTargetTranslationY = targetYPx - userY
        val textWidth = paint.measureText(text.toString())
        val halfWidth = textWidth / 2f
        val screenW = CaveArtXposedModule.screenW
        val sideSafeMargin = 18f * density
        val minAllowedTransX = (-(screenW / 2f) + halfWidth + sideSafeMargin).coerceAtMost(0f)
        val maxAllowedTransX = ((screenW / 2f) - halfWidth - sideSafeMargin).coerceAtLeast(0f)

        val safeUserTranslationX = userTranslationX.coerceIn(minAllowedTransX, maxAllowedTransX)

        if (isAttachedToClock && notificationProgress < 0.05f) {
            val clock = CaveArtXposedModule.activeClock
            val clockY = CaveArtXposedModule.effectiveTopMarginPx.toFloat()
            val dateH = measuredHeight.toFloat().coerceAtLeast(textSize * 1.4f)
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
        if (clock.activeDatePolicy() != ClockDatePolicy.EXTERNAL_STANDARD || isAttachedToClock || notificationProgress > 0.5f) {
            if (overlapSpring.targetValue != 0f) {
                if (animated) overlapSpring.animateTo(0f) else { overlapSpring.stop(); overlapShiftY = 0f; updatePosition() }
            }
            return
        }

        val density = resources.displayMetrics.density
        val dateY = CaveArtXposedModule.effectiveDateTopMarginPx.toFloat()
        val dateH = measuredHeight.toFloat().coerceAtLeast(textSize * 1.4f)
        val dateW = measuredWidth.toFloat().coerceAtLeast(paint.measureText(text.toString()))
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
            text = ClockTypefaceResolver.formatDate(System.currentTimeMillis(), dateFormatIndex)
        } catch (_: Exception) {}
    }

    fun refreshSettings() {
        val cfg = loadClockKitConfigFromProvider(context)
        val dateSp = if (cfg.styleId == "adaptive_vector") {
            (cfg.getFloat("adaptive_vector", "hour_size", 100f) * 0.24f).coerceIn(14f, 34f)
        } else {
            (20f * cfg.scale).coerceIn(14f, 32f)
        }
        dynamicDateSizeSp = dateSp
        compactDateSizeSp = 18f
        applyCurrentTextSize()

        setTextColor(cfg.color)
        dateFormatIndex = cfg.dateFormat
        isAttachedToClock = cfg.dateAttached

        val showExternal = ClockKitEngine.getStyle(cfg.styleId).datePolicy(cfg) == ClockDatePolicy.EXTERNAL_STANDARD
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
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) {
            updateTimeDirectly()
            checkAndResolveOverlap(animated = false)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (measuredWidth == 0 || measuredHeight == 0) {
            val fallbackW = View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.AT_MOST)
            val fallbackH = View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.AT_MOST)
            super.onMeasure(fallbackW, fallbackH)
        }
    }
}

class VectorTextClock(context: Context) : TextClock(context) {
    var clockConfig: ClockKitConfig = ClockKitConfig()
    var collisionMap: FloatArray? = null
    var userTranslationX = 0f
    var telemetryZoom = 1.10f

    var notificationProgress = 0f
    private var notificationAnimator: ValueAnimator? = null

    private var depthMaskBitmap: Bitmap? = null
    private var rawDepthBitmap: Bitmap? = null

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

    fun reloadDepthMask() {
        val oldMask = depthMaskBitmap
        depthMaskBitmap = null
        oldMask?.takeIf { !it.isRecycled }?.recycle()

        val oldRaw = rawDepthBitmap
        rawDepthBitmap = null
        oldRaw?.takeIf { !it.isRecycled }?.recycle()

        if (clockConfig.isGravityEnabled) {
            try {
                val depthUri = Uri.parse("content://com.android.CaveArt.settings/raw_depth")
                context.contentResolver.openInputStream(depthUri)?.use { stream ->
                    rawDepthBitmap = BitmapFactory.decodeStream(stream)
                }
            } catch (_: Exception) {}

            if (rawDepthBitmap == null) {
                val candidateDepthPaths = listOf(
                    "/data/user_de/0/com.android.CaveArt/files/lock_raw_depth.png",
                    "/data/user_de/0/com.android.CaveArt/no_backup/lock_raw_depth.png",
                    "/data/data/com.android.CaveArt/files/lock_raw_depth.png"
                )
                for (path in candidateDepthPaths) {
                    val file = File(path)
                    if (file.exists() && file.length() > 0) {
                        try {
                            rawDepthBitmap = BitmapFactory.decodeFile(file.absolutePath)
                            if (rawDepthBitmap != null) break
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        if (!clockConfig.isDepthEnabled) return

        try {
            val uri = Uri.parse("content://com.android.CaveArt.settings/depth_mask")
            context.contentResolver.openInputStream(uri)?.use { stream ->
                depthMaskBitmap = BitmapFactory.decodeStream(stream)
            }
        } catch (_: Exception) {}

        if (depthMaskBitmap == null) {
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
                        depthMaskBitmap = BitmapFactory.decodeFile(file.absolutePath)
                        if (depthMaskBitmap != null) break
                    } catch (_: Exception) {}
                }
            }
        }
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
        reloadDepthMask()
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
            reloadDepthMask()
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
        val density = resources.displayMetrics.density
        val userTopY = CaveArtXposedModule.effectiveTopMarginPx.toFloat()
        val notifTopY = CaveArtXposedModule.NOTIF_CLOCK_TOP_DP * density
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
        val notifTopY = CaveArtXposedModule.NOTIF_CLOCK_TOP_DP * density
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

        ClockKitEngine.renderForSystemUI(canvas, style, frame, clockConfig)

        canvas.restore()
    }
}
