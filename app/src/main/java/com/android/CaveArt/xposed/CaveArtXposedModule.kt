package com.android.CaveArt.xposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextClock
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class CaveArtXposedModule : XposedModule() {

    companion object {
        val customClockId = View.generateViewId()
        var dateSmartspaceId = 0
        var keyguardSliceViewId = 0
        var bcSmartspaceId = 0
        var topMarginPx = 300
        var bottomMarginPx = 30
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
                        
                        dateSmartspaceId = context.resources.getIdentifier("date_smartspace_view", "id", context.packageName)
                        keyguardSliceViewId = context.resources.getIdentifier("keyguard_slice_view", "id", context.packageName)
                        bcSmartspaceId = context.resources.getIdentifier("bc_smartspace_view", "id", context.packageName)
                        
                        topMarginPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 110f, context.resources.displayMetrics).toInt()
                        bottomMarginPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, context.resources.displayMetrics).toInt()

                        if (rootLayout.findViewById<View>(customClockId) == null) {
                        	
                            var initialSize = 95f
                            try {
                                val uri = Uri.parse("content://com.android.CaveArt.settings/clock_size")
                                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                    if (cursor.moveToFirst()) initialSize = cursor.getFloat(0)
                                }
                            } catch (e: Exception) {}

                            val myCustomClock = TextClock(context).apply {
                                id = customClockId 
                                tag = "CAVE_ART_CLOCK"
                                format12Hour = "hh:mm"
                                format24Hour = "HH:mm"
                                
                                setTextSize(TypedValue.COMPLEX_UNIT_DIP, initialSize) 
                                setTextColor(Color.WHITE)
                                gravity = Gravity.CENTER
                                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                                setShadowLayer(15f, 0f, 5f, Color.argb(160, 0, 0, 0))
                            }
                            
                            myCustomClock.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                                var receiver: BroadcastReceiver? = null
                                
                                override fun onViewAttachedToWindow(v: View) {
                                    receiver = object : BroadcastReceiver() {
                                        override fun onReceive(ctx: Context?, intent: Intent?) {
                                            if (intent?.action == "com.android.CaveArt.UPDATE_CLOCK_SIZE") {
                                                val newSize = intent.getFloatExtra("clock_size", 95f)
                                                (v as TextClock).setTextSize(TypedValue.COMPLEX_UNIT_DIP, newSize)
                                            }
                                        }
                                    }
                                    
                                    context.registerReceiver(receiver, IntentFilter("com.android.CaveArt.UPDATE_CLOCK_SIZE"), Context.RECEIVER_EXPORTED)
                                }

                                override fun onViewDetachedFromWindow(v: View) {
                                    receiver?.let { context.unregisterReceiver(it) }
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
                            connectMethod.invoke(constraintSet, dateSmartspaceId, 3, customClockId, 4, bottomMarginPx) 
                        }

                        if (keyguardSliceViewId != 0) {
                            clearAnchorMethod.invoke(constraintSet, keyguardSliceViewId, 3) 
                            connectMethod.invoke(constraintSet, keyguardSliceViewId, 3, customClockId, 4, bottomMarginPx) 
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
