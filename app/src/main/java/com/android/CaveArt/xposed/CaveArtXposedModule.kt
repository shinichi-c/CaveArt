package com.android.CaveArt.xposed

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.TextClock
import com.android.CaveArt.AdaptiveClockHelper
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

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
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        super.onPackageLoaded(param)
        
        if (param.packageName == "com.android.systemui") {
            val classLoader = try {
                param.javaClass.getMethod("getDefaultClassLoader").invoke(param) as ClassLoader
            } catch (e: Exception) {
                param.javaClass.getMethod("getClassLoader").invoke(param) as ClassLoader
            }

            var largeId = 0
            var smallId = 0
            try {
                val clockViewIdsClass = classLoader.loadClass("com.android.systemui.plugins.keyguard.ui.clocks.ClockViewIds")
                val clockViewIdsInstance = clockViewIdsClass.getDeclaredField("INSTANCE").get(null)
                largeId = clockViewIdsClass.getMethod("getLOCKSCREEN_CLOCK_VIEW_LARGE").invoke(clockViewIdsInstance) as Int
                smallId = clockViewIdsClass.getMethod("getLOCKSCREEN_CLOCK_VIEW_SMALL").invoke(clockViewIdsInstance) as Int
            } catch (e: Exception) {}

            try {
                val clockSectionClass = classLoader.loadClass("com.android.systemui.keyguard.ui.view.layout.sections.ClockSection")
                val constraintLayoutClass = classLoader.loadClass("androidx.constraintlayout.widget.ConstraintLayout")
                val bindDataMethod = clockSectionClass.getDeclaredMethod("bindData", constraintLayoutClass)

                hook(bindDataMethod).intercept { chain ->
                    val result = chain.proceed()
                    
                    val rootLayout = chain.args[0] as? ViewGroup
                    if (rootLayout != null) {
                        val context = rootLayout.context
                        screenW = context.resources.displayMetrics.widthPixels.toFloat()
                        screenH = context.resources.displayMetrics.heightPixels.toFloat()
                        
                        SystemUIHider.initIds(context)
                        OverlapPreventionHelper.initIds(context)

                        if (rootLayout.findViewById<View>(customClockId) == null) {
                            var initialX = 0f
                            var initialY = 110f
                            var dateX = 0f
                            var dateY = 75f
                            var dateSize = 20f
                            var hSize = 100f
                            var mSize = 75f
                            var sWidth = 8f
                            var cRound = 30f
                            var isStretch = false
                            var collMap = ""
                            var cColor = Color.WHITE
                            
                            try {
                                val cr = context.contentResolver
                                cr.query(Uri.parse("content://com.android.CaveArt.settings/clock_x"), null, null, null, null)?.use { if (it.moveToFirst()) initialX = it.getFloat(0) }
                                cr.query(Uri.parse("content://com.android.CaveArt.settings/clock_y"), null, null, null, null)?.use { if (it.moveToFirst()) initialY = it.getFloat(0) }
                                cr.query(Uri.parse("content://com.android.CaveArt.settings/date_x"), null, null, null, null)?.use { if (it.moveToFirst()) dateX = it.getFloat(0) }
                                cr.query(Uri.parse("content://com.android.CaveArt.settings/date_y"), null, null, null, null)?.use { if (it.moveToFirst()) dateY = it.getFloat(0) }
                                cr.query(Uri.parse("content://com.android.CaveArt.settings/date_size"), null, null, null, null)?.use { if (it.moveToFirst()) dateSize = it.getFloat(0) }
                                cr.query(Uri.parse("content://com.android.CaveArt.settings/clock_hour_size"), null, null, null, null)?.use { if (it.moveToFirst()) hSize = it.getFloat(0) }
                                cr.query(Uri.parse("content://com.android.CaveArt.settings/clock_minute_size"), null, null, null, null)?.use { if (it.moveToFirst()) mSize = it.getFloat(0) }
                                cr.query(Uri.parse("content://com.android.CaveArt.settings/clock_stroke_width"), null, null, null, null)?.use { if (it.moveToFirst()) sWidth = it.getFloat(0) }
                                cr.query(Uri.parse("content://com.android.CaveArt.settings/clock_roundness"), null, null, null, null)?.use { if (it.moveToFirst()) cRound = it.getFloat(0) }
                                cr.query(Uri.parse("content://com.android.CaveArt.settings/clock_stretch"), null, null, null, null)?.use { if (it.moveToFirst()) isStretch = it.getInt(0) == 1 }
                                cr.query(Uri.parse("content://com.android.CaveArt.settings/clock_collision_map"), null, null, null, null)?.use { if (it.moveToFirst()) collMap = it.getString(0) ?: "" }
                                cr.query(Uri.parse("content://com.android.CaveArt.settings/clock_color"), null, null, null, null)?.use { if (it.moveToFirst()) cColor = it.getInt(0) }
                            } catch (e: Exception) {}

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
                                penPaint.color = cColor
                                translationX = initialXPx
                                translationZ = 50f
                                elevation = 50f
                            }
                            activeClock = myCustomClock
                            
                            val myCustomDate = IndependentDateView(context).apply {
                                id = customDateId
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, dateSize)
                                setTextColor(cColor)
                                translationX = dateXPx
                                translationZ = 60f
                                elevation = 60f
                            }
                            activeDate = myCustomDate

                            rootLayout.viewTreeObserver.addOnPreDrawListener {
                                if (largeId != 0) rootLayout.findViewById<View>(largeId)?.apply { if(visibility != View.GONE) visibility = View.GONE; if(alpha != 0f) alpha = 0f; scaleX=0f; scaleY=0f }
                                if (smallId != 0) rootLayout.findViewById<View>(smallId)?.apply { if(visibility != View.GONE) visibility = View.GONE; if(alpha != 0f) alpha = 0f; scaleX=0f; scaleY=0f }
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
                                addAction("com.android.CaveArt.UPDATE_CLOCK_STRETCH")
                                addAction("com.android.CaveArt.UPDATE_COLLISION_MAP")
                                addAction("com.android.CaveArt.UPDATE_CLOCK_COLOR")
                                addAction(Intent.ACTION_USER_PRESENT)
                                addAction(Intent.ACTION_SCREEN_ON) 
                                addAction(Intent.ACTION_TIME_TICK) 
                            }

                            context.registerReceiver(object : BroadcastReceiver() {
                                override fun onReceive(ctx: Context?, intent: Intent?) {
                                    when (intent?.action) {
                                        "com.android.CaveArt.UPDATE_CLOCK_STYLE" -> {
                                            myCustomClock.hourSizeDp = intent.getFloatExtra("clock_hour_size", 100f)
                                            myCustomClock.minuteSizeDp = intent.getFloatExtra("clock_minute_size", 75f)
                                            myCustomClock.strokeW = intent.getFloatExtra("clock_stroke_width", 8f)
                                            myCustomClock.curveRound = intent.getFloatExtra("clock_roundness", 30f)
                                            myCustomClock.requestLayout()
                                            myCustomClock.invalidate()
                                        }
                                        "com.android.CaveArt.UPDATE_DATE_POSITION" -> {
                                            val newX = intent.getFloatExtra("date_x", 0f)
                                            val newY = intent.getFloatExtra("date_y", 75f)
                                            myCustomDate.translationX = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, newX, context.resources.displayMetrics)
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
                                            } catch (e: Exception) {}
                                            
                                            myCustomDate.requestLayout()
                                            myCustomDate.invalidate()
                                        }
                                        "com.android.CaveArt.UPDATE_CLOCK_POSITION" -> {
                                            val newX = intent.getFloatExtra("clock_x", 0f)
                                            val newY = intent.getFloatExtra("clock_y", 110f)
                                            myCustomClock.translationX = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, newX, context.resources.displayMetrics)
                                            topMarginPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, newY, context.resources.displayMetrics).toInt()
                                            
                                            try {
                                                val constraintSetClass = classLoader.loadClass("androidx.constraintlayout.widget.ConstraintSet")
                                                val constraintLayoutClass = classLoader.loadClass("androidx.constraintlayout.widget.ConstraintLayout")
                                                if (constraintLayoutClass.isInstance(rootLayout)) {
                                                    val set = constraintSetClass.getDeclaredConstructor().newInstance()
                                                    constraintSetClass.getMethod("clone", constraintLayoutClass).invoke(set, rootLayout)
                                                    constraintSetClass.getMethod("setMargin", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(set, customClockId, 3, topMarginPx)
                                                    constraintSetClass.getMethod("applyTo", constraintLayoutClass).invoke(set, rootLayout)
                                                }
                                            } catch (e: Exception) {}
                                        }
                                        "com.android.CaveArt.UPDATE_CLOCK_STRETCH" -> {
                                            myCustomClock.stretchEnabled = intent.getBooleanExtra("clock_stretch", false)
                                            myCustomClock.requestLayout()
                                            myCustomClock.invalidate()
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
                                        Intent.ACTION_TIME_TICK, Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_ON -> {
                                            myCustomClock.refreshSettings()
                                            myCustomDate.refreshSettings()
                                        }
                                    }
                                }
                            }, filter, Context.RECEIVER_EXPORTED)
                        }
                    }
                    
                    result
                }
            } catch (e: Exception) {}

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
                        val setAlpha = try { constraintSetClass.getMethod("setAlpha", Int::class.javaPrimitiveType, Float::class.javaPrimitiveType) } catch (e: Exception) { null }
                        val constrainWidth = try { constraintSetClass.getMethod("constrainWidth", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType) } catch (e: Exception) { null }
                        val constrainHeight = try { constraintSetClass.getMethod("constrainHeight", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType) } catch (e: Exception) { null }

                        try {
                            if (largeId != 0) {
                                val getVisibility = constraintSetClass.getMethod("getVisibility", Int::class.javaPrimitiveType)
                                val largeVis = getVisibility.invoke(cs, largeId) as Int
                                val hasNotifications = (largeVis == 8 || largeVis == 4) 
                                activeClock?.setClockState(hasNotifications)
                            }
                        } catch(e: Exception) {}

                        if (largeId != 0) { setVisibility.invoke(cs, largeId, 8); setAlpha?.invoke(cs, largeId, 0f) }
                        if (smallId != 0) { setVisibility.invoke(cs, smallId, 8); setAlpha?.invoke(cs, smallId, 0f) }
                        
                        connect.invoke(cs, customClockId, 3, 0, 3, topMarginPx) 
                        connect.invoke(cs, customClockId, 6, 0, 6, 0) 
                        connect.invoke(cs, customClockId, 7, 0, 7, 0)
                        setVisibility.invoke(cs, customClockId, 0)
                        setAlpha?.invoke(cs, customClockId, 1f)
                        
                        constrainWidth?.invoke(cs, customDateId, -2)
                        constrainHeight?.invoke(cs, customDateId, -2)

                        connect.invoke(cs, customDateId, 3, 0, 3, dateTopMarginPx) 
                        connect.invoke(cs, customDateId, 6, 0, 6, 0) 
                        connect.invoke(cs, customDateId, 7, 0, 7, 0)
                        setVisibility.invoke(cs, customDateId, 0)
                        setAlpha?.invoke(cs, customDateId, 1f)

                        val context = activeClock?.context
                        if (context != null) {
                            try {
                                val density = context.resources.displayMetrics.density
                                val cHeight = (activeClock?.hourSizeDp ?: 100f) * density * 1.2f
                                val dHeight = 20f * density * 1.5f
                                
                                val lowestPoint = Math.max(
                                    topMarginPx + cHeight.toInt(),
                                    dateTopMarginPx + dHeight.toInt()
                                )
                                val targetMargin = lowestPoint + (32f * density).toInt()
                                
                                val nsslId = OverlapPreventionHelper.nsslId
                                if (nsslId != 0) connect.invoke(cs, nsslId, 3, 0, 3, targetMargin)
                                
                                val mediaId = OverlapPreventionHelper.mediaCarouselId
                                if (mediaId != 0) connect.invoke(cs, mediaId, 3, 0, 3, targetMargin)

                                val placeholderId = OverlapPreventionHelper.nsslPlaceholderId
                                if (placeholderId != 0) connect.invoke(cs, placeholderId, 3, 0, 3, targetMargin)

                            } catch (e: Exception) {}
                        }
                        
                        SystemUIHider.hideInConstraintSet(cs, constraintSetClass)
                        
                        result
                    }
                } catch (e: Exception) {}
            }
        }
    }
}

class IndependentDateView(context: Context) : TextClock(context) {
    init {
        format12Hour = "EEE, d MMMM"
        format24Hour = "EEE, d MMMM"
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setShadowLayer(10f, 0f, 3f, Color.argb(160, 0, 0, 0))
        isSingleLine = true
    }

    fun refreshSettings() {
        try {
            val cr = context.contentResolver
            cr.query(Uri.parse("content://com.android.CaveArt.settings/date_size"), null, null, null, null)?.use { 
                if (it.moveToFirst()) setTextSize(TypedValue.COMPLEX_UNIT_SP, it.getFloat(0)) 
            }
            cr.query(Uri.parse("content://com.android.CaveArt.settings/clock_color"), null, null, null, null)?.use { 
                if (it.moveToFirst()) setTextColor(it.getInt(0)) 
            }
            requestLayout()
            invalidate()
        } catch (e: Exception) {}
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshSettings() 
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (measuredWidth == 0 || measuredHeight == 0) {
            val fallbackW = MeasureSpec.makeMeasureSpec(800, MeasureSpec.AT_MOST)
            val fallbackH = MeasureSpec.makeMeasureSpec(200, MeasureSpec.AT_MOST)
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
    var collisionMap: FloatArray? = null
    var clockColor = Color.WHITE
    var stretchProgress = 1f
    private var animator: ValueAnimator? = null
    
    private val cachedPath = Path()
    private val screenPos = IntArray(2)
    private var lastCurveRound = -1f
    private var lastStrokeW = -1f

    val penPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE 
        strokeCap = Paint.Cap.ROUND 
        strokeJoin = Paint.Join.ROUND 
        setShadowLayer(15f, 0f, 5f, Color.argb(160, 0, 0, 0))
    }

    init {
        setTextColor(Color.TRANSPARENT) 
        gravity = Gravity.CENTER
    }

    fun refreshSettings() {
        try {
            val cr = context.contentResolver
            cr.query(Uri.parse("content://com.android.CaveArt.settings/clock_hour_size"), null, null, null, null)?.use { if (it.moveToFirst()) hourSizeDp = it.getFloat(0) }
            cr.query(Uri.parse("content://com.android.CaveArt.settings/clock_minute_size"), null, null, null, null)?.use { if (it.moveToFirst()) minuteSizeDp = it.getFloat(0) }
            cr.query(Uri.parse("content://com.android.CaveArt.settings/clock_stroke_width"), null, null, null, null)?.use { if (it.moveToFirst()) strokeW = it.getFloat(0) }
            cr.query(Uri.parse("content://com.android.CaveArt.settings/clock_roundness"), null, null, null, null)?.use { if (it.moveToFirst()) curveRound = it.getFloat(0) }
            cr.query(Uri.parse("content://com.android.CaveArt.settings/clock_stretch"), null, null, null, null)?.use { if (it.moveToFirst()) stretchEnabled = it.getInt(0) == 1 }
            cr.query(Uri.parse("content://com.android.CaveArt.settings/clock_color"), null, null, null, null)?.use { if (it.moveToFirst()) clockColor = it.getInt(0) }
            cr.query(Uri.parse("content://com.android.CaveArt.settings/clock_collision_map"), null, null, null, null)?.use { 
                if (it.moveToFirst()) {
                    val mapStr = it.getString(0) ?: ""
                    collisionMap = if (mapStr.isNotEmpty()) mapStr.split(",").mapNotNull { v -> v.toFloatOrNull() }.toFloatArray() else null
                }
            }
            requestLayout()
            invalidate()
        } catch (e: Exception) {}
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshSettings()
    }

    fun setClockState(hasNotifications: Boolean) {
        val targetProgress = if (hasNotifications) 0f else 1f
        if (targetProgress == stretchProgress && animator?.isRunning != true) return
        animator?.cancel()
        animator = ValueAnimator.ofFloat(stretchProgress, targetProgress).apply {
            duration = 450
            interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            addUpdateListener { 
                stretchProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val maxTextSize = maxOf(hourSizeDp, minuteSizeDp) * density
        
        val maxExtraPadding = if (stretchEnabled) (maxTextSize * 1.6f) else 0f
        val staticMeasuredH = (maxTextSize * 1.2f + maxExtraPadding).toInt()
        
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), staticMeasuredH)
    }

    private fun updatePaintIfNeeded() {
        if (lastCurveRound != curveRound || lastStrokeW != strokeW || penPaint.color != clockColor) {
            penPaint.color = clockColor
            penPaint.strokeWidth = strokeW * resources.displayMetrics.density
            penPaint.pathEffect = CornerPathEffect(curveRound * resources.displayMetrics.density)
            lastCurveRound = curveRound
            lastStrokeW = strokeW
        }
    }

    override fun onDraw(canvas: Canvas) {
        val timeString = text.toString()
        if (timeString.isEmpty()) return

        updatePaintIfNeeded()
        
        val hPx = hourSizeDp * resources.displayMetrics.density
        val mPx = minuteSizeDp * resources.displayMetrics.density
        
        val hourW = hPx * 0.55f
        val minW = mPx * 0.55f
        val gap = hPx * 0.15f
        
        val colonIdx = timeString.indexOf(':')
        val hCount = if (colonIdx != -1) colonIdx else timeString.length
        val mCount = if (colonIdx != -1) timeString.length - colonIdx - 1 else 0
        val totalWidth = (hCount * hourW) + (mCount * minW) + (gap * (hCount + mCount))

        val startX = (width - totalWidth) / 2f
        val startY = 0f 

        getLocationOnScreen(screenPos)
        val trueAbsoluteX = screenPos[0].toFloat()
        val trueAbsoluteY = screenPos[1].toFloat()

        AdaptiveClockHelper.buildPath(
            timeString = timeString,
            startX = startX,
            startY = startY,
            absoluteClockX = trueAbsoluteX,
            absoluteClockY = trueAbsoluteY,
            hourH = hPx,
            minH = mPx,
            screenW = CaveArtXposedModule.screenW,
            screenH = CaveArtXposedModule.screenH,
            isStretchEnabled = stretchEnabled,
            collisionMap = collisionMap,
            density = resources.displayMetrics.density,
            strokeWidth = strokeW,
            stretchProgress = stretchProgress,
            path = cachedPath
        )
        
        canvas.drawPath(cachedPath, penPaint)
    }
}
