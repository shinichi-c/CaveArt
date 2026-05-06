package com.android.CaveArt.xposed

import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.math.max

object OverlapPreventionHelper {
    private var nsslId = 0
    private var nsslPlaceholderId = 0
    private var mediaCarouselId = 0
    private var keyguardStatusId = 0
    
    fun initIds(context: Context) {
        val res = context.resources
        val pkg = context.packageName
        
        nsslId = res.getIdentifier("notification_stack_scroller", "id", pkg)
        nsslPlaceholderId = res.getIdentifier("nssl_placeholder", "id", pkg)
        mediaCarouselId = res.getIdentifier("keyguard_media_carousel", "id", pkg)
        keyguardStatusId = res.getIdentifier("keyguard_status_view", "id", pkg)
    }
    
    fun forceElementsBelow(rootLayout: ViewGroup, vararg customViews: View) {
        val density = rootLayout.context.resources.displayMetrics.density
        val gapPx = (32f * density).toInt()

        rootLayout.viewTreeObserver.addOnPreDrawListener {
            val nssl = if (nsslId != 0) rootLayout.findViewById<View>(nsslId) else null
            val nsslPlaceholder = if (nsslPlaceholderId != 0) rootLayout.findViewById<View>(nsslPlaceholderId) else null
            val media = if (mediaCarouselId != 0) rootLayout.findViewById<View>(mediaCarouselId) else null
            val statusView = if (keyguardStatusId != 0) rootLayout.findViewById<View>(keyguardStatusId) else null

            var lowestVisualPoint = 0f
            
            for (view in customViews) {
                if (view.visibility == View.VISIBLE) {
                    val bottom = view.y + view.height
                    lowestVisualPoint = max(lowestVisualPoint, bottom)
                }
            }

            val targetMargin = lowestVisualPoint.toInt() + gapPx
            
            if (targetMargin > gapPx) {
                nssl?.let { updateTopMargin(it, targetMargin) }
                nsslPlaceholder?.let { updateTopMargin(it, targetMargin) }
                media?.let { updateTopMargin(it, targetMargin) }
                statusView?.let { updateTopMargin(it, targetMargin) }
            }

            true
        }
    }

    private fun updateTopMargin(view: View, targetMargin: Int) {
        val lp = view.layoutParams as? ViewGroup.MarginLayoutParams
        
        if (lp != null) {
            var needsUpdate = false

            if (lp.topMargin != targetMargin) {
                lp.topMargin = targetMargin
                needsUpdate = true
            }
            
            try {
                val topToTopField = lp.javaClass.getField("topToTop")
                val topToBottomField = lp.javaClass.getField("topToBottom")

                if (topToTopField.getInt(lp) != 0) {
                    topToTopField.setInt(lp, 0)
                    needsUpdate = true
                }
                if (topToBottomField.getInt(lp) != -1) {
                    topToBottomField.setInt(lp, -1)
                    needsUpdate = true
                }
            } catch (e: Exception) {
                
            }

            if (needsUpdate) {
                view.layoutParams = lp
            }
        }
    }
}
