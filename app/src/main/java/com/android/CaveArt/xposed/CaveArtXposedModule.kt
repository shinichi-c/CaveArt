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
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.TextClock
import android.widget.TextView
import com.android.CaveArt.AdaptiveClockHelper
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import kotlin.math.abs
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
        val customClockId = View.generateViewId()
        val customDateId = View.generateViewId()
        var topMarginPx = 300
        var dateTopMarginPx = 150
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
                            val initialX = getSettingFloat(context, "clock_x", 0f)
                            val initialY = getSettingFloat(context, "clock_y", 110f)
                            val dAttached = getSettingInt(context, "date_attached", 1) == 1
                            val dateX = if (dAttached) initialX else getSettingFloat(context, "date_x", 0f)
                            val dateY = getSettingFloat(context, "date_y", 75f)

                            val hSize = getSettingFloat(context, "clock_hour_size", 100f)
                            val autoDateSize = (hSize * 0.24f).coerceIn(14f, 34f)
                            val dateSize = getSettingFloat(context, "date_size", autoDateSize)
                            val dFormat = getSettingInt(context, "date_format", 0)
                            val mSize = getSettingFloat(context, "clock_minute_size", 75f)
                            val sWidth = getSettingFloat(context, "clock_stroke_width", 8f)
                            val cRound = getSettingFloat(context, "clock_roundness", 30f)
                            val isStretch = getSettingInt(context, "clock_stretch", 0) == 1
                            val collMap = getSettingString(context, "clock_collision_map", "")
                            val cColor = getSettingInt(context, "clock_color", Color.WHITE)
                            val cFont = getSettingString(context, "clock_font", "default")
                            val isDualTone = getSettingInt(context, "clock_dual_tone", 1) == 1
                            val isVert = getSettingInt(context, "clock_layout", 0)

                            topMarginPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, initialY, context.resources.displayMetrics).toInt()
                            dateTopMarginPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dateY, context.resources.displayMetrics).toInt()
                            val initialXPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, initialX, context.resources.displayMetrics)
                            val dateXPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dateX, context.resources.displayMetrics)

                            val myCustomClock = VectorTextClock(context).apply {
                                id = customClockId
                                format12Hour = "hh:mm"; format24Hour = "HH:mm"
                                hourSizeDp = hSize; minuteSizeDp = mSize
                                strokeW = sWidth; curveRound = cRound
                                stretchEnabled = isStretch
                                collisionMap = if (collMap.isNotEmpty()) collMap.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray() else null
                                clockColor = cColor
                                clockFont = cFont
                                dualToneEnabled = isDualTone
                                isVertical = isVert == 1
                                penPaint.color = cColor
                                userTranslationX = initialXPx
                                translationX = initialXPx
                                translationY = currentShiftY
                                translationZ = 50f
                                elevation = 50f
                            }
                            activeClock = myCustomClock

                            val myCustomDate = IndependentDateView(context).apply {
                                id = customDateId
                                dateFormatIndex = dFormat
                                isAttachedToClock = dAttached
                                dynamicDateSizeSp = dateSize
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, dateSize)
                                setTextColor(cColor)
                                userTranslationX = dateXPx
                                translationX = dateXPx
                                translationY = currentShiftY
                                translationZ = 60f
                                elevation = 60f
                            }
                            activeDate = myCustomDate

                            rootLayout.viewTreeObserver.addOnPreDrawListener {
                                if (largeId != 0) rootLayout.findViewById<View>(largeId)?.apply { if (visibility != View.GONE) visibility = View.GONE; if (alpha != 0f) alpha = 0f; scaleX = 0f; scaleY = 0f }
                                if (smallId != 0) rootLayout.findViewById<View>(smallId)?.apply { if (visibility != View.GONE) visibility = View.GONE; if (alpha != 0f) alpha = 0f; scaleX = 0f; scaleY = 0f }
                                SystemUIHider.forceHideOnPreDraw(rootLayout)
                                true
                            }

                            val lpClass = classLoader.loadClass("androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams")
                            val lpConstructor = lpClass.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)

                            rootLayout.addView(myCustomClock, lpConstructor.newInstance(-2, -2) as ViewGroup.LayoutParams)
                            rootLayout.addView(myCustomDate, lpConstructor.newInstance(-2, -2) as ViewGroup.LayoutParams)

                            OverlapPreventionHelper.forceElementsBelow(rootLayout, myCustomClock, myCustomDate)

                            val filter = IntentFilter().apply {
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
                                        "com.android.CaveArt.UPDATE_CLOCK_STYLE" -> {
                                            val hourSize = intent.getFloatExtra("clock_hour_size", 100f)
                                            myCustomClock.hourSizeDp = hourSize
                                            myCustomClock.minuteSizeDp = intent.getFloatExtra("clock_minute_size", 75f)
                                            myCustomClock.strokeW = intent.getFloatExtra("clock_stroke_width", 8f)
                                            myCustomClock.curveRound = intent.getFloatExtra("clock_roundness", 30f)
                                            myCustomClock.requestLayout()
                                            myCustomClock.invalidate()

                                            val autoDate = intent.getFloatExtra("date_size", (hourSize * 0.24f).coerceIn(14f, 34f))
                                            myCustomDate.updateSizeFromClock(autoDate)
                                        }
                                        "com.android.CaveArt.UPDATE_DATE_POSITION" -> {
                                            val newX = intent.getFloatExtra("date_x", 0f)
                                            val newY = intent.getFloatExtra("date_y", 75f)
                                            val dateXPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, newX, context.resources.displayMetrics)
                                            myCustomDate.userTranslationX = dateXPx
                                            dateTopMarginPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, newY, context.resources.displayMetrics).toInt()

                                            try {
                                                val constraintSetClass = classLoader.loadClass("androidx.constraintlayout.widget.ConstraintSet")
                                                val constraintLayoutClass = classLoader.loadClass("androidx.constraintlayout.widget.ConstraintLayout")
                                                if (constraintLayoutClass.isInstance(rootLayout)) {
                                                    val set = constraintSetClass.getDeclaredConstructor().newInstance()
                                                    constraintSetClass.getMethod("clone", constraintLayoutClass).invoke(set, rootLayout)
                                                    constraintSetClass.getMethod("setMargin", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(set, customDateId, 3, dateTopMarginPx)
                                                    constraintSetClass.getMethod("applyTo", constraintLayoutClass).invoke(set, rootLayout)
                                                }
                                            } catch (_: Exception) {}

                                            myCustomDate.updatePosition()
                                            myCustomDate.requestLayout()
                                            myCustomDate.invalidate()
                                            updateNotificationShift(context, isShiftedForNotifications)
                                            myCustomDate.checkAndResolveOverlap(animated = true)
                                        }
                                        "com.android.CaveArt.UPDATE_DATE_FORMAT" -> {
                                            myCustomDate.dateFormatIndex = intent.getIntExtra("date_format", 0)
                                            myCustomDate.updateTimeDirectly()
                                            myCustomDate.requestLayout()
                                            myCustomDate.invalidate()
                                            myCustomDate.checkAndResolveOverlap(animated = true)
                                        }
                                        "com.android.CaveArt.UPDATE_DATE_ATTACHED" -> {
                                            val isAttached = intent.getBooleanExtra("date_attached", true)
                                            myCustomDate.isAttachedToClock = isAttached
                                            if (isAttached) {
                                                myCustomDate.userTranslationX = myCustomClock.userTranslationX
                                                val currentSavedDateY = getSettingFloat(context, "date_y", 75f)
                                                dateTopMarginPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, currentSavedDateY, context.resources.displayMetrics).toInt()

                                                try {
                                                    val constraintSetClass = classLoader.loadClass("androidx.constraintlayout.widget.ConstraintSet")
                                                    val constraintLayoutClass = classLoader.loadClass("androidx.constraintlayout.widget.ConstraintLayout")
                                                    if (constraintLayoutClass.isInstance(rootLayout)) {
                                                        val set = constraintSetClass.getDeclaredConstructor().newInstance()
                                                        constraintSetClass.getMethod("clone", constraintLayoutClass).invoke(set, rootLayout)
                                                        constraintSetClass.getMethod("setMargin", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(set, customDateId, 3, dateTopMarginPx)
                                                        constraintSetClass.getMethod("applyTo", constraintLayoutClass).invoke(set, rootLayout)
                                                    }
                                                } catch (_: Exception) {}
                                                myCustomDate.updatePosition()
                                                myCustomDate.requestLayout()
                                                myCustomDate.invalidate()
                                            }
                                            myCustomDate.checkAndResolveOverlap(animated = true)
                                        }
                                        "com.android.CaveArt.UPDATE_CLOCK_POSITION" -> {
                                            val newX = intent.getFloatExtra("clock_x", 0f)
                                            val newY = intent.getFloatExtra("clock_y", 110f)
                                            val clockXPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, newX, context.resources.displayMetrics)
                                            myCustomClock.userTranslationX = clockXPx
                                            topMarginPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, newY, context.resources.displayMetrics).toInt()

                                            if (myCustomDate.isAttachedToClock) {
                                                myCustomDate.userTranslationX = clockXPx
                                                val currentSavedDateY = getSettingFloat(context, "date_y", 75f)
                                                dateTopMarginPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, currentSavedDateY, context.resources.displayMetrics).toInt()
                                            }

                                            try {
                                                val constraintSetClass = classLoader.loadClass("androidx.constraintlayout.widget.ConstraintSet")
                                                val constraintLayoutClass = classLoader.loadClass("androidx.constraintlayout.widget.ConstraintLayout")
                                                if (constraintLayoutClass.isInstance(rootLayout)) {
                                                    val set = constraintSetClass.getDeclaredConstructor().newInstance()
                                                    constraintSetClass.getMethod("clone", constraintLayoutClass).invoke(set, rootLayout)
                                                    constraintSetClass.getMethod("setMargin", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(set, customClockId, 3, topMarginPx)
                                                    if (myCustomDate.isAttachedToClock) {
                                                        constraintSetClass.getMethod("setMargin", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(set, customDateId, 3, dateTopMarginPx)
                                                    }
                                                    constraintSetClass.getMethod("applyTo", constraintLayoutClass).invoke(set, rootLayout)
                                                }
                                            } catch (_: Exception) {}

                                            myCustomClock.updateInterpolatedPositions()
                                            myCustomClock.requestLayout()
                                            myCustomClock.invalidate()
                                            if (myCustomDate.isAttachedToClock) {
                                                myCustomDate.updatePosition()
                                                myCustomDate.requestLayout()
                                                myCustomDate.invalidate()
                                            }
                                            updateNotificationShift(context, isShiftedForNotifications)
                                            myCustomDate.checkAndResolveOverlap(animated = true)
                                        }
                                        "com.android.CaveArt.UPDATE_CLOCK_STRETCH" -> {
                                            myCustomClock.stretchEnabled = intent.getBooleanExtra("clock_stretch", false)
                                            myCustomClock.requestLayout()
                                            myCustomClock.invalidate()
                                            myCustomDate.checkAndResolveOverlap(animated = true)
                                        }
                                        "com.android.CaveArt.UPDATE_COLLISION_MAP" -> {
                                            val mapStr = intent.getStringExtra("collision_map") ?: ""
                                            myCustomClock.collisionMap = if (mapStr.isNotEmpty()) mapStr.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray() else null
                                            myCustomClock.requestLayout()
                                            myCustomClock.invalidate()
                                        }
                                        "com.android.CaveArt.UPDATE_CLOCK_COLOR" -> {
                                            val newColor = intent.getIntExtra("clock_color", Color.WHITE)
                                            myCustomClock.clockColor = newColor
                                            myCustomClock.penPaint.color = newColor
                                            myCustomClock.invalidate()
                                            myCustomDate.setTextColor(newColor)
                                            myCustomDate.invalidate()
                                        }
                                        "com.android.CaveArt.UPDATE_CLOCK_FONT" -> {
                                            myCustomClock.clockFont = intent.getStringExtra("clock_font") ?: "default"
                                            myCustomClock.reloadTypeface()
                                            myCustomClock.requestLayout()
                                            myCustomClock.invalidate()
                                        }
                                        "com.android.CaveArt.UPDATE_CLOCK_DUAL_TONE" -> {
                                            myCustomClock.dualToneEnabled = intent.getBooleanExtra("clock_dual_tone", true)
                                            myCustomClock.invalidate()
                                        }
                                        "com.android.CaveArt.UPDATE_CLOCK_LAYOUT" -> {
                                            myCustomClock.isVertical = intent.getIntExtra("clock_layout", 0) == 1
                                            myCustomClock.requestLayout()
                                            myCustomClock.invalidate()
                                            myCustomDate.checkAndResolveOverlap(animated = true)
                                        }
                                        Intent.ACTION_TIME_TICK, Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_ON -> {
                                            myCustomClock.refreshSettings()
                                            myCustomDate.refreshSettings()
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

                        val context = activeClock?.context
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

                            val density = context.resources.displayMetrics.density
                            val isStretch = activeClock?.stretchEnabled == true
                            val isAttached = activeDate?.isAttachedToClock == true

                            val dateH = (activeDate?.measuredHeight?.toFloat() ?: (20f * density)).coerceAtLeast(18f * density)
                            val gapPx = 12f * density
                            val minSafeTopPx = 38f * density
                            val compoundOffsetPx = dateH + gapPx
                            
                            val effectiveClockTopMarginPx = if (isAttached && isStretch) {
                                Math.max(topMarginPx.toFloat(), minSafeTopPx + compoundOffsetPx).toInt()
                            } else {
                                topMarginPx
                            }

                            val effectiveDateTopMarginPx = if (isAttached && isStretch) {
                                (effectiveClockTopMarginPx - compoundOffsetPx).toInt()
                            } else {
                                dateTopMarginPx
                            }

                            connect.invoke(cs, customClockId, 3, 0, 3, effectiveClockTopMarginPx)
                            connect.invoke(cs, customClockId, 6, 0, 6, 0)
                            connect.invoke(cs, customClockId, 7, 0, 7, 0)
                            setVisibility.invoke(cs, customClockId, 0)
                            setAlpha?.invoke(cs, customClockId, 1f)

                            constrainWidth?.invoke(cs, customDateId, -2)
                            constrainHeight?.invoke(cs, customDateId, -2)

                            connect.invoke(cs, customDateId, 3, 0, 3, effectiveDateTopMarginPx)
                            connect.invoke(cs, customDateId, 6, 0, 6, 0)
                            connect.invoke(cs, customDateId, 7, 0, 7, 0)
                            setVisibility.invoke(cs, customDateId, 0)
                            setAlpha?.invoke(cs, customDateId, 1f)

                            try {
                                val dateCurrentY = if (hasNotifications) 65f * density else effectiveDateTopMarginPx.toFloat()
                                val dateHeight = (if (hasNotifications) 15f else 20f) * density * 1.5f

                                val clockCurrentY = if (hasNotifications) 100f * density else effectiveClockTopMarginPx.toFloat()
                                val clockHeight = (if (hasNotifications) {
                                    if ((activeClock?.hourSizeDp ?: 100f) > 85f) 85f else (activeClock?.hourSizeDp ?: 100f)
                                } else {
                                    activeClock?.hourSizeDp ?: 100f
                                }) * density * (if (activeClock?.isVertical == true && !hasNotifications) 2.2f else 1.2f)

                                val lowestPoint = Math.max(
                                    clockCurrentY + clockHeight,
                                    dateCurrentY + dateHeight
                                ).toInt()

                                val targetMargin = lowestPoint + (32f * density).toInt()

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
    }

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
        val targetYPx = 65f * density
        val userY = CaveArtXposedModule.dateTopMarginPx.toFloat()
        val notifTargetTranslationY = targetYPx - userY
        val textWidth = paint.measureText(text.toString())
        val halfWidth = textWidth / 2f
        val screenW = CaveArtXposedModule.screenW
        val sideSafeMargin = 18f * density
        val minAllowedTransX = -(screenW / 2f) + halfWidth + sideSafeMargin
        val maxAllowedTransX = (screenW / 2f) - halfWidth - sideSafeMargin

        val safeUserTranslationX = if (minAllowedTransX <= maxAllowedTransX) {
            userTranslationX.coerceIn(minAllowedTransX, maxAllowedTransX)
        } else 0f
        
        if (isAttachedToClock && notificationProgress < 0.05f) {
            val clock = CaveArtXposedModule.activeClock
            val clockY = CaveArtXposedModule.topMarginPx.toFloat()
            val dateH = measuredHeight.toFloat().coerceAtLeast(textSize * 1.4f)
            val gap = 12f * density
            val minSafeTop = 38f * density

            val idealAboveY = clockY - dateH - gap
            if (idealAboveY < minSafeTop && clock?.stretchEnabled != true) {
                
                val isVert = clock?.isVertical == true
                val clockH = clock?.measuredHeight?.toFloat()?.coerceAtLeast(clock.hourSizeDp * density * (if (isVert) 2.15f else 1.15f)) ?: (100f * density)
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
        if (isAttachedToClock || notificationProgress > 0.5f) {
            if (overlapSpring.targetValue != 0f) {
                if (animated) overlapSpring.animateTo(0f) else { overlapSpring.stop(); overlapShiftY = 0f; updatePosition() }
            }
            return
        }

        val clock = CaveArtXposedModule.activeClock ?: return
        val density = resources.displayMetrics.density

        val dateY = CaveArtXposedModule.dateTopMarginPx.toFloat()
        val dateH = measuredHeight.toFloat().coerceAtLeast(textSize * 1.4f)
        val dateW = measuredWidth.toFloat().coerceAtLeast(paint.measureText(text.toString()))
        val dateCenterX = (CaveArtXposedModule.screenW / 2f) + userTranslationX

        val clockY = CaveArtXposedModule.topMarginPx.toFloat()
        val clockH = clock.measuredHeight.toFloat().coerceAtLeast(clock.hourSizeDp * density * (if (clock.isVertical) 2.0f else 1.1f))
        val clockW = clock.measuredWidth.toFloat().coerceAtLeast(CaveArtXposedModule.screenW * 0.65f)
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
            val currentTime = System.currentTimeMillis()
            val date = java.util.Date(currentTime)
            val locale = java.util.Locale.getDefault()

            val dateString = when (dateFormatIndex) {
                0 -> java.text.SimpleDateFormat("EEE, d MMMM", locale).format(date)
                1 -> java.text.SimpleDateFormat("EEE, d MMM", locale).format(date)
                2 -> java.text.SimpleDateFormat("d MMM yyyy", locale).format(date).uppercase(locale)
                3 -> java.text.SimpleDateFormat("EEEE, MMMM d", locale).format(date)
                4 -> java.text.SimpleDateFormat("EEEE • d MMM", locale).format(date)
                else -> java.text.SimpleDateFormat("EEE, d MMMM", locale).format(date)
            }
            text = dateString
        } catch (_: Exception) {}
    }

    fun refreshSettings() {
        val hSize = getSettingFloat(context, "clock_hour_size", 100f)
        val autoDateSize = (hSize * 0.24f).coerceIn(14f, 34f)
        dynamicDateSizeSp = getSettingFloat(context, "date_size", autoDateSize)
        compactDateSizeSp = 18f
        applyCurrentTextSize()

        setTextColor(getSettingInt(context, "clock_color", Color.WHITE))
        dateFormatIndex = getSettingInt(context, "date_format", 0)
        isAttachedToClock = getSettingInt(context, "date_attached", 1) == 1

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
    var hourSizeDp = 100f
    var minuteSizeDp = 75f
    var strokeW = 8f
    var curveRound = 30f
    var stretchEnabled = false
    var isVertical = false
    var collisionMap: FloatArray? = null
    var clockColor = Color.WHITE
    var clockFont = "default"
    var dualToneEnabled = true

    var notificationProgress = 0f
    private var notificationAnimator: ValueAnimator? = null
    var userTranslationX = 0f

    private val screenPos = IntArray(2)
    private var lastCurveRound = -1f
    private var lastStrokeW = -1f

    var customTypeface: Typeface = Typeface.DEFAULT_BOLD

    val penPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL_AND_STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        setShadowLayer(15f, 0f, 5f, Color.argb(160, 0, 0, 0))
    }

    init {
        setTextColor(Color.TRANSPARENT)
        gravity = Gravity.CENTER
    }

    fun reloadTypeface() {
        customTypeface = if (clockFont == "default" || clockFont.isEmpty()) {
            Typeface.create("sans-serif-bold", Typeface.NORMAL)
        } else {
            try {
                val moduleContext = context.createPackageContext("com.android.CaveArt", Context.CONTEXT_IGNORE_SECURITY)
                Typeface.createFromAsset(moduleContext.assets, "fonts/$clockFont")
            } catch (_: Exception) {
                Typeface.create("sans-serif-bold", Typeface.NORMAL)
            }
        }
    }

    fun refreshSettings() {
        hourSizeDp = getSettingFloat(context, "clock_hour_size", 100f)
        minuteSizeDp = getSettingFloat(context, "clock_minute_size", 75f)
        strokeW = getSettingFloat(context, "clock_stroke_width", 8f)
        curveRound = getSettingFloat(context, "clock_roundness", 30f)
        stretchEnabled = getSettingInt(context, "clock_stretch", 0) == 1
        clockColor = getSettingInt(context, "clock_color", Color.WHITE)

        val mapStr = getSettingString(context, "clock_collision_map", "")
        collisionMap = if (mapStr.isNotEmpty()) mapStr.split(",").mapNotNull { v -> v.toFloatOrNull() }.toFloatArray() else null

        clockFont = getSettingString(context, "clock_font", "default")
        dualToneEnabled = getSettingInt(context, "clock_dual_tone", 1) == 1
        isVertical = getSettingInt(context, "clock_layout", 0) == 1

        reloadTypeface()
        updateInterpolatedPositions()
        requestLayout()
        invalidate()

        val autoDate = (hourSizeDp * 0.24f).coerceIn(14f, 34f)
        CaveArtXposedModule.activeDate?.updateSizeFromClock(autoDate)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshSettings()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) {
            invalidate()
        }
    }

    fun setClockState(hasNotifications: Boolean) {
        val targetProgress = if (hasNotifications) 1f else 0f
        if (targetProgress == notificationProgress && notificationAnimator?.isRunning != true) return
        notificationAnimator?.cancel()
        notificationAnimator = ValueAnimator.ofFloat(notificationProgress, targetProgress).apply {
            duration = 350
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener {
                notificationProgress = it.animatedValue as Float
                updateInterpolatedPositions()
                invalidate()
            }
            start()
        }
    }

    fun updateInterpolatedPositions() {
        val density = resources.displayMetrics.density
        val targetYPx = 100f * density
        val userY = CaveArtXposedModule.topMarginPx.toFloat()

        val targetTranslationY = targetYPx - userY
        translationY = targetTranslationY * notificationProgress
        translationX = userTranslationX + (0f - userTranslationX) * notificationProgress
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val maxTextSize = maxOf(hourSizeDp, minuteSizeDp) * density

        val verticalMultiplier = if (isVertical) 2.2f else 1.2f
        val maxExtraPadding = if (stretchEnabled) (maxTextSize * 1.6f) else 0f
        val staticMeasuredH = (maxTextSize * verticalMultiplier + maxExtraPadding).toInt()

        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), staticMeasuredH)
    }

    private fun updatePaintIfNeeded() {
        if (lastCurveRound != curveRound || lastStrokeW != strokeW || penPaint.color != clockColor) {
            penPaint.strokeWidth = strokeW * resources.displayMetrics.density
            penPaint.pathEffect = CornerPathEffect(curveRound * resources.displayMetrics.density)
            lastCurveRound = curveRound
            lastStrokeW = strokeW
        }
    }

    override fun onDraw(canvas: Canvas) {
        val currentTime = System.currentTimeMillis()
        val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
        val pattern = if (is24Hour) "HH:mm" else "hh:mm"
        val timeString = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()).format(java.util.Date(currentTime))
        if (timeString.isEmpty()) return

        updatePaintIfNeeded()

        val targetHourSizeDp = if (hourSizeDp > 85f) 85f else hourSizeDp
        val targetMinSizeDp = if (minuteSizeDp > 72f) 72f else minuteSizeDp

        val currentHourSizeDp = hourSizeDp + (targetHourSizeDp - hourSizeDp) * notificationProgress
        val currentMinSizeDp = minuteSizeDp + (targetMinSizeDp - minuteSizeDp) * notificationProgress

        val hPx = currentHourSizeDp * resources.displayMetrics.density
        val mPx = currentMinSizeDp * resources.displayMetrics.density

        val preferredLayoutProgress = if (isVertical) 1f else 0f
        val layoutProgress = preferredLayoutProgress * (1f - notificationProgress)

        val totalWidth = AdaptiveClockHelper.measureClockWidth(timeString, hPx, mPx, customTypeface, layoutProgress)

        val startX = (width - totalWidth) / 2f
        val startY = 0f

        getLocationOnScreen(screenPos)
        val trueAbsoluteX = screenPos[0].toFloat()
        val trueAbsoluteY = screenPos[1].toFloat()

        val paths = AdaptiveClockHelper.buildPaths(
            timeString = timeString,
            startX = startX,
            startY = startY,
            absoluteClockX = trueAbsoluteX,
            absoluteClockY = trueAbsoluteY,
            hourSizePx = hPx,
            minSizePx = mPx,
            typeface = customTypeface,
            screenW = CaveArtXposedModule.screenW,
            screenH = CaveArtXposedModule.screenH,
            isStretchEnabled = stretchEnabled,
            layoutProgress = layoutProgress,
            collisionMap = collisionMap,
            density = resources.displayMetrics.density,
            strokeWidth = strokeW,
            stretchProgress = 1f - notificationProgress
        )

        penPaint.color = if (dualToneEnabled) Color.WHITE else clockColor
        canvas.drawPath(paths.hours, penPaint)
        canvas.drawPath(paths.colon, penPaint)

        penPaint.color = clockColor
        canvas.drawPath(paths.mins, penPaint)
    }
}
