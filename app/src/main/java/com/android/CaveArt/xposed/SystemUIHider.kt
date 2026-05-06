package com.android.CaveArt.xposed

import android.content.Context
import android.view.View
import android.view.ViewGroup

object SystemUIHider {
    var dateSmartspaceId = 0
    var keyguardSliceViewId = 0
    var bcSmartspaceId = 0
    var lockscreenSmartspaceId = 0
    
    fun initIds(context: Context) {
        dateSmartspaceId = context.resources.getIdentifier("date_smartspace_view", "id", context.packageName)
        keyguardSliceViewId = context.resources.getIdentifier("keyguard_slice_view", "id", context.packageName)
        bcSmartspaceId = context.resources.getIdentifier("bc_smartspace_view", "id", context.packageName)
        lockscreenSmartspaceId = context.resources.getIdentifier("lockscreen_smartspace", "id", context.packageName)
    }
    
    private fun hideViewCompletely(view: View?) {
        view?.let {
            it.visibility = View.GONE
            it.alpha = 0f
            if (it is ViewGroup) {
                for (i in 0 until it.childCount) {
                    hideViewCompletely(it.getChildAt(i))
                }
            }
        }
    }
    
    fun forceHideOnPreDraw(rootLayout: ViewGroup) {
        
        val smartIds = listOf(dateSmartspaceId, keyguardSliceViewId, bcSmartspaceId, lockscreenSmartspaceId)
        smartIds.forEach { id ->
            if (id != 0) hideViewCompletely(rootLayout.findViewById(id))
        }
        
        for (i in 0 until rootLayout.childCount) {
            val child = rootLayout.getChildAt(i)
            val className = child.javaClass.simpleName.lowercase()
            if (className.contains("smartspace") || className.contains("slice")) {
                hideViewCompletely(child)
            }
        }
    }
    
    fun hideInConstraintSet(cs: Any, constraintSetClass: Class<*>) {
        try {
            val setVisibility = constraintSetClass.getMethod("setVisibility", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            val setAlpha = try { constraintSetClass.getMethod("setAlpha", Int::class.javaPrimitiveType, Float::class.javaPrimitiveType) } catch (e: Exception) { null }

            val smartIds = listOf(dateSmartspaceId, keyguardSliceViewId, bcSmartspaceId, lockscreenSmartspaceId)
            smartIds.filter { it != 0 }.forEach { viewId ->
                setVisibility.invoke(cs, viewId, 8)
                setAlpha?.invoke(cs, viewId, 0f)
            }
        } catch (e: Exception) {
            
        }
    }
}
