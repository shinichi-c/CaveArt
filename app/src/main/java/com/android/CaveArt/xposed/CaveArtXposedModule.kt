package com.android.CaveArt.xposed

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
import android.widget.TextClock
import com.android.CaveArt.AdaptiveClockHelper
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class CaveArtXposedModule : XposedModule() {

    companion object {
        val customClockId = View.generateViewId()
        var dateSmartspaceId = 0
        var keyguardSliceViewId = 0
        var bcSmartspaceId = 0
        var topMarginPx = 300
        var gapPx = 20
        var screenW = 1080f
        var screenH = 2400f
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        super.onPackageLoaded(param)
        
        if (param.packageName == "com.android.systemui") {
            val classLoader = try {
                param.javaClass.getMethod("getDefaultClassLoader").invoke(param) as ClassLoader
            } catch (e: Exception) {
                try {
                    param.javaClass.getMethod("getClassLoader").invoke(param) as ClassLoader
                } catch (e2: Exception) {
                    Thread.currentThread().contextClassLoader!!
                }
            }

            try {
                val clockSectionClass = classLoader.loadClass("com.android.systemui.keyguard.ui.view.layout.sections.ClockSection")
                val constraintLayoutClass = classLoader.loadClass("androidx.constraintlayout.widget.ConstraintLayout")
                val bindDataMethod = clockSectionClass.getDeclaredMethod("bindData", constraintLayoutClass)

                hook(bindDataMethod).intercept { chain ->
                    chain.proceed() 
                    
                    val rootLayout = chain.args[0] as? ViewGroup
                    if (rootLayout != null) {
                        val context = rootLayout.context
                        
                        screenW = context.resources.displayMetrics.widthPixels.toFloat()
                        screenH = context.resources.displayMetrics.heightPixels.toFloat()

                        dateSmartspaceId = context.resources.getIdentifier("date_smartspace_view", "id", context.packageName)
                        keyguardSliceViewId = context.resources.getIdentifier("keyguard_slice_view", "id", context.packageName)
                        bcSmartspaceId = context.resources.getIdentifier("bc_smartspace_view", "id", context.packageName)
                        
                        gapPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, context.resources.displayMetrics).toInt()

                        if (rootLayout.findViewById<View>(customClockId) == null) {
                            
                            var initialX = 0f
                            var initialY = 110f
                            var hSize = 100f
                            var mSize = 75f
                            var sWidth = 8f
                            var cRound = 30f
                            var isStretch = false
                            var collMap = ""
                            
                            try {
                                val cr = context.contentResolver
                                cr.query(Uri.parse("content://com.android.CaveArt.settings/clock_x"), null, null, null, null)?.use { if (it.moveToFirst()) initialX = it.getFloat(0) }
                                cr.query(Uri.parse("content://com.android.CaveArt.settings/clock_y"), null, null, null, null)?.use { if (it.moveToFirst()) initialY = it.getFloat(0) }
                                cr.query(Uri.parse("content://com.android.CaveArt.settings/clock_hour_size"), null, null, null, null)?.use { if (it.moveToFirst()) hSize = it.getFloat(0) }
                                cr.query(Uri.parse("content://com.android.CaveArt.settings/clock_minute_size"), null, null, null, null)?.use { if (it.moveToFirst()) mSize = it.getFloat(0) }
                                cr.query(Uri.parse("content://com.android.CaveArt.settings/clock_stroke_width"), null, null, null, null)?.use { if (it.moveToFirst()) sWidth = it.getFloat(0) }
                                cr.query(Uri.parse("content://com.android.CaveArt.settings/clock_roundness"), null, null, null, null)?.use { if (it.moveToFirst()) cRound = it.getFloat(0) }
                                cr.query(Uri.parse("content://com.android.CaveArt.settings/clock_stretch"), null, null, null, null)?.use { if (it.moveToFirst()) isStretch = it.getInt(0) == 1 }
                                cr.query(Uri.parse("content://com.android.CaveArt.settings/clock_collision_map"), null, null, null, null)?.use { if (it.moveToFirst()) collMap = it.getString(0) ?: "" }
                            } catch (e: Exception) {}

                            topMarginPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, initialY, context.resources.displayMetrics).toInt()
                            val initialXPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, initialX, context.resources.displayMetrics)

                            val myCustomClock = VectorTextClock(context).apply {
                                id = customClockId 
                                tag = "CAVE_ART_CLOCK"
                                format12Hour = "hh:mm"
                                format24Hour = "HH:mm"
                                
                                hourSizeDp = hSize
                                minuteSizeDp = mSize
                                strokeW = sWidth
                                curveRound = cRound
                                stretchEnabled = isStretch
                                collisionMap = if (collMap.isNotEmpty()) collMap.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray() else null
                                
                                translationX = initialXPx
                            }

                            myCustomClock.viewTreeObserver.addOnPreDrawListener {
                                val root = myCustomClock.parent as? ViewGroup
                                if (root != null) {
                                    val currentX = myCustomClock.translationX
                                    val currentY = myCustomClock.translationY
                                    if (dateSmartspaceId != 0) root.findViewById<View>(dateSmartspaceId)?.apply { translationX = currentX; translationY = currentY }
                                    if (keyguardSliceViewId != 0) root.findViewById<View>(keyguardSliceViewId)?.apply { translationX = currentX; translationY = currentY }
                                    if (bcSmartspaceId != 0) root.findViewById<View>(bcSmartspaceId)?.apply { translationX = currentX; translationY = currentY }
                                }
                                true
                            }

                            myCustomClock.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                                var receiverStyle: BroadcastReceiver? = null
                                var receiverPos: BroadcastReceiver? = null
                                var receiverML: BroadcastReceiver? = null
                                
                                override fun onViewAttachedToWindow(v: View) {
                                    receiverStyle = object : BroadcastReceiver() {
                                        override fun onReceive(ctx: Context?, intent: Intent?) {
                                            if (intent?.action == "com.android.CaveArt.UPDATE_CLOCK_STYLE") {
                                                val clock = v as VectorTextClock
                                                clock.hourSizeDp = intent.getFloatExtra("clock_hour_size", 100f)
                                                clock.minuteSizeDp = intent.getFloatExtra("clock_minute_size", 75f)
                                                clock.strokeW = intent.getFloatExtra("clock_stroke_width", 8f)
                                                clock.curveRound = intent.getFloatExtra("clock_roundness", 30f)
                                                clock.requestLayout(); clock.invalidate()    
                                            }
                                        }
                                    }
                                    
                                    receiverPos = object : BroadcastReceiver() {
                                        override fun onReceive(ctx: Context?, intent: Intent?) {
                                            val newX = intent?.getFloatExtra("clock_x", 0f) ?: 0f
                                            val newY = intent?.getFloatExtra("clock_y", 110f) ?: 110f
                                            v.translationX = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, newX, context.resources.displayMetrics)
                                            topMarginPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, newY, context.resources.displayMetrics).toInt()
                                            
                                            val root = v.parent as? ViewGroup
                                            if (root != null) {
                                                try {
                                                    val constraintSetClass = classLoader.loadClass("androidx.constraintlayout.widget.ConstraintSet")
                                                    val constraintLayoutClass = classLoader.loadClass("androidx.constraintlayout.widget.ConstraintLayout")
                                                    if (constraintLayoutClass.isInstance(root)) {
                                                        val set = constraintSetClass.getDeclaredConstructor().newInstance()
                                                        constraintSetClass.getMethod("clone", constraintLayoutClass).invoke(set, root)
                                                        constraintSetClass.getMethod("setMargin", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(set, customClockId, 3, topMarginPx)
                                                        constraintSetClass.getMethod("applyTo", constraintLayoutClass).invoke(set, root)
                                                    }
                                                } catch (e: Exception) {}
                                            }
                                        }
                                    }

                                    receiverML = object : BroadcastReceiver() {
                                        override fun onReceive(ctx: Context?, intent: Intent?) {
                                            val clock = v as VectorTextClock
                                            if (intent?.action == "com.android.CaveArt.UPDATE_CLOCK_STRETCH") {
                                                clock.stretchEnabled = intent.getBooleanExtra("clock_stretch", false)
                                            } else if (intent?.action == "com.android.CaveArt.UPDATE_COLLISION_MAP") {
                                                val mapStr = intent.getStringExtra("collision_map") ?: ""
                                                clock.collisionMap = if (mapStr.isNotEmpty()) mapStr.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray() else null
                                            }
                                            clock.requestLayout(); clock.invalidate()
                                        }
                                    }

                                    context.registerReceiver(receiverStyle, IntentFilter("com.android.CaveArt.UPDATE_CLOCK_STYLE"), Context.RECEIVER_EXPORTED)
                                    context.registerReceiver(receiverPos, IntentFilter("com.android.CaveArt.UPDATE_CLOCK_POSITION"), Context.RECEIVER_EXPORTED)
                                    context.registerReceiver(receiverML, IntentFilter("com.android.CaveArt.UPDATE_CLOCK_STRETCH").apply { addAction("com.android.CaveArt.UPDATE_COLLISION_MAP") }, Context.RECEIVER_EXPORTED)
                                }

                                override fun onViewDetachedFromWindow(v: View) {
                                    receiverStyle?.let { context.unregisterReceiver(it) }
                                    receiverPos?.let { context.unregisterReceiver(it) }
                                    receiverML?.let { context.unregisterReceiver(it) }
                                }
                            })

                            val lpClass = classLoader.loadClass("androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams")
                            val lpConstructor = lpClass.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                            rootLayout.addView(myCustomClock, lpConstructor.newInstance(-2, -2) as ViewGroup.LayoutParams)
                        }
                    }
                }
            } catch (e: Exception) {}

            val constraintSetClass = classLoader.loadClass("androidx.constraintlayout.widget.ConstraintSet")

            try {
                val clockSectionClass = classLoader.loadClass("com.android.systemui.keyguard.ui.view.layout.sections.ClockSection")
                val applyConstraintsClock = clockSectionClass.getDeclaredMethod("applyConstraints", constraintSetClass)
                
                hook(applyConstraintsClock).intercept { chain ->
                    chain.proceed()
                    val constraintSet = chain.args[0]
                    try {
                        val setVisibilityMethod = constraintSetClass.getMethod("setVisibility", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                        val constrainWidthMethod = constraintSetClass.getMethod("constrainWidth", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                        val constrainHeightMethod = constraintSetClass.getMethod("constrainHeight", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                        val setAlphaMethod = constraintSetClass.getMethod("setAlpha", Int::class.javaPrimitiveType, Float::class.javaPrimitiveType)
                        val connectMethod = constraintSetClass.getMethod("connect", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)

                        val clockViewIdsClass = classLoader.loadClass("com.android.systemui.plugins.keyguard.ui.clocks.ClockViewIds")
                        val clockViewIdsInstance = clockViewIdsClass.getDeclaredField("INSTANCE").get(null)
                        val largeId = clockViewIdsClass.getMethod("getLOCKSCREEN_CLOCK_VIEW_LARGE").invoke(clockViewIdsInstance) as Int
                        val smallId = clockViewIdsClass.getMethod("getLOCKSCREEN_CLOCK_VIEW_SMALL").invoke(clockViewIdsInstance) as Int

                        setVisibilityMethod.invoke(constraintSet, largeId, 4) 
                        setAlphaMethod.invoke(constraintSet, largeId, 0f)
                        constrainWidthMethod.invoke(constraintSet, largeId, 1)
                        constrainHeightMethod.invoke(constraintSet, largeId, 1)

                        setVisibilityMethod.invoke(constraintSet, smallId, 4) 
                        setAlphaMethod.invoke(constraintSet, smallId, 0f)
                        constrainWidthMethod.invoke(constraintSet, smallId, 1)
                        constrainHeightMethod.invoke(constraintSet, smallId, 1)

                        connectMethod.invoke(constraintSet, customClockId, 3, 0, 3, topMarginPx) 
                        connectMethod.invoke(constraintSet, customClockId, 6, 0, 6, 0) 
                        connectMethod.invoke(constraintSet, customClockId, 7, 0, 7, 0) 
                        constrainWidthMethod.invoke(constraintSet, customClockId, -2) 
                        constrainHeightMethod.invoke(constraintSet, customClockId, -2) 
                        setVisibilityMethod.invoke(constraintSet, customClockId, 0) 
                        setAlphaMethod.invoke(constraintSet, customClockId, 1.0f) 

                    } catch (e: Exception) {}
                }
            } catch (e: Exception) {}

            try {
                val smartspaceSectionClass = classLoader.loadClass("com.android.systemui.keyguard.ui.view.layout.sections.SmartspaceSection")
                val applyConstraintsSmartspace = smartspaceSectionClass.getDeclaredMethod("applyConstraints", constraintSetClass)

                hook(applyConstraintsSmartspace).intercept { chain ->
                    chain.proceed()
                    val constraintSet = chain.args[0]
                    try {
                        val clearAnchorMethod = constraintSetClass.getMethod("clear", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                        val connectMethod = constraintSetClass.getMethod("connect", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)

                        if (dateSmartspaceId != 0) {
                            clearAnchorMethod.invoke(constraintSet, dateSmartspaceId, 3) 
                            connectMethod.invoke(constraintSet, dateSmartspaceId, 3, customClockId, 4, gapPx) 
                        }
                        if (keyguardSliceViewId != 0) {
                            clearAnchorMethod.invoke(constraintSet, keyguardSliceViewId, 3) 
                            connectMethod.invoke(constraintSet, keyguardSliceViewId, 3, customClockId, 4, gapPx) 
                        }
                        if (bcSmartspaceId != 0) {
                            clearAnchorMethod.invoke(constraintSet, bcSmartspaceId, 3) 
                            if (dateSmartspaceId != 0) {
                                connectMethod.invoke(constraintSet, bcSmartspaceId, 3, dateSmartspaceId, 4, 0)
                            } else if (keyguardSliceViewId != 0) {
                                connectMethod.invoke(constraintSet, bcSmartspaceId, 3, keyguardSliceViewId, 4, 0)
                            }
                        }
                    } catch (e: Exception) {}
                }
            } catch (e: Exception) {}
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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxSize = maxOf(hourSizeDp, minuteSizeDp)
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, maxSize)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        
        if (stretchEnabled) {
            setMeasuredDimension(measuredWidth, CaveArtXposedModule.screenH.toInt())
        }
    }

    override fun onDraw(canvas: Canvas) {
        val timeString = text.toString()
        if (timeString.isEmpty()) return

        penPaint.strokeWidth = strokeW * resources.displayMetrics.density
        penPaint.pathEffect = CornerPathEffect(curveRound * resources.displayMetrics.density)
        
        val hPx = hourSizeDp * resources.displayMetrics.density
        val mPx = minuteSizeDp * resources.displayMetrics.density
        
        val hourW = hPx * 0.55f
        val minW = mPx * 0.55f
        val gap = hPx * 0.15f

        val parts = timeString.split(":")
        val hCount = (parts.getOrNull(0) ?: "00").length
        val mCount = (parts.getOrNull(1) ?: "00").length
        val totalWidth = (hCount * hourW) + (mCount * minW) + (gap * (hCount + mCount))

        val startX = (width - totalWidth) / 2f
        val startY = 0f 

        val screenPos = IntArray(2)
        getLocationOnScreen(screenPos)
        val trueAbsoluteX = screenPos[0].toFloat()
        val trueAbsoluteY = screenPos[1].toFloat()

        val path = AdaptiveClockHelper.buildPath(
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
            strokeWidth = strokeW
        )
        
        canvas.drawPath(path, penPaint)
    }
}
