package com.android.CaveArt.xposed

import android.content.Context
import android.view.View
import android.view.ViewGroup

object SystemUIHider {
    var nativeNotificationStackId = 0
    var nativeMediaCarouselId = 0
    
    fun initIds(context: Context) {
        nativeNotificationStackId = context.resources.getIdentifier("notification_stack_scroller", "id", context.packageName)
        nativeMediaCarouselId = context.resources.getIdentifier("keyguard_media_carousel", "id", context.packageName)
    }
    
    private fun hideViewCompletely(view: View?) {
        view?.let {
            if (it.alpha != 0f) it.alpha = 0f
            if (it.scaleX != 0f) it.scaleX = 0f
            if (it.scaleY != 0f) it.scaleY = 0f
            it.isClickable = false
        }
    }
    
    fun forceHideOnPreDraw(rootLayout: ViewGroup) {
        if (nativeNotificationStackId != 0) hideViewCompletely(rootLayout.findViewById(nativeNotificationStackId))
        if (nativeMediaCarouselId != 0) hideViewCompletely(rootLayout.findViewById(nativeMediaCarouselId))
        
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
            if (nativeNotificationStackId != 0) setVisibility.invoke(cs, nativeNotificationStackId, 8)
            if (nativeMediaCarouselId != 0) setVisibility.invoke(cs, nativeMediaCarouselId, 8)
        } catch (e: Exception) {}
    }
}
