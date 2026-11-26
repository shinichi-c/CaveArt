package com.android.CaveArt.xposed

import android.content.Context
import android.view.View
import android.view.ViewGroup

object SystemUIHider {
    var nativeNotificationStackId = 0
    var nativeAodIconsId = 0

    @Volatile
    private var didWeHideNssl = false

    @Volatile
    private var savedNsslVisibility = View.VISIBLE

    @Volatile
    private var didWeHideAodIcons = false

    @Volatile
    private var savedAodVisibility = View.VISIBLE

    fun initIds(context: Context) {
        val pkg = context.packageName
        nativeNotificationStackId = context.resources.getIdentifier("notification_stack_scroller", "id", pkg)
        nativeAodIconsId = context.resources.getIdentifier("aod_notification_icon_container", "id", pkg)
    }

    private fun hideSmartspaceView(view: View?) {
        view?.let {
            if (it.alpha != 0f) it.alpha = 0f
            if (it.scaleX != 0f) it.scaleX = 0f
            if (it.scaleY != 0f) it.scaleY = 0f
            it.isClickable = false
        }
    }

    private fun findInRootOrWindow(rootLayout: ViewGroup, viewId: Int): View? {
        if (viewId == 0) return null
        return rootLayout.findViewById<View>(viewId)
            ?: (rootLayout.rootView as? ViewGroup)?.findViewById(viewId)
    }

    fun syncKeyguardNotificationVisibility(rootLayout: ViewGroup, hideStockNotifs: Boolean) {
        val nssl = findInRootOrWindow(rootLayout, nativeNotificationStackId)
        if (nssl != null) {
            if (hideStockNotifs) {
                if (!didWeHideNssl) {
                    savedNsslVisibility = nssl.visibility
                }
                if (nssl.alpha != 0f) nssl.alpha = 0f
                if (nssl.visibility == View.VISIBLE) nssl.visibility = View.INVISIBLE
                didWeHideNssl = true
            } else if (didWeHideNssl) {
                if (nssl.visibility == View.INVISIBLE && savedNsslVisibility != View.INVISIBLE) {
                    nssl.visibility = savedNsslVisibility
                }
                if (nssl.alpha < 1f) nssl.alpha = 1f
                didWeHideNssl = false
            }
        }

        val aodIcons = findInRootOrWindow(rootLayout, nativeAodIconsId)
        if (aodIcons != null) {
            if (hideStockNotifs) {
                if (!didWeHideAodIcons) {
                    savedAodVisibility = aodIcons.visibility
                }
                if (aodIcons.alpha != 0f) aodIcons.alpha = 0f
                if (aodIcons.visibility == View.VISIBLE) aodIcons.visibility = View.INVISIBLE
                didWeHideAodIcons = true
            } else if (didWeHideAodIcons) {
                if (aodIcons.visibility == View.INVISIBLE && savedAodVisibility != View.INVISIBLE) {
                    aodIcons.visibility = savedAodVisibility
                }
                if (aodIcons.alpha < 1f) aodIcons.alpha = 1f
                didWeHideAodIcons = false
            }
        }
    }

    fun forceHideOnPreDraw(rootLayout: ViewGroup, hideStockNotifs: Boolean = false) {
        for (i in 0 until rootLayout.childCount) {
            val child = rootLayout.getChildAt(i)
            val className = child.javaClass.simpleName.lowercase()
            if (className.contains("smartspace") || className.contains("slice")) {
                hideSmartspaceView(child)
            }
        }
        syncKeyguardNotificationVisibility(rootLayout, hideStockNotifs)
    }

    fun hideInConstraintSet(cs: Any, constraintSetClass: Class<*>, hideStockNotifs: Boolean = false) {
        if (!hideStockNotifs) return
        try {
            val setVisibility = constraintSetClass.getMethod("setVisibility", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            val setAlpha = try {
                constraintSetClass.getMethod("setAlpha", Int::class.javaPrimitiveType, Float::class.javaPrimitiveType)
            } catch (_: Exception) { null }

            if (nativeNotificationStackId != 0) {
                setVisibility.invoke(cs, nativeNotificationStackId, View.INVISIBLE)
                setAlpha?.invoke(cs, nativeNotificationStackId, 0f)
            }
            if (nativeAodIconsId != 0) {
                setVisibility.invoke(cs, nativeAodIconsId, View.INVISIBLE)
                setAlpha?.invoke(cs, nativeAodIconsId, 0f)
            }
        } catch (_: Exception) {}
    }
}
