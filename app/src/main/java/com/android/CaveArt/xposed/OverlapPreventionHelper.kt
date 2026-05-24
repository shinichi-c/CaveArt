package com.android.CaveArt.xposed

import android.content.Context
import android.view.View
import android.view.ViewGroup

object OverlapPreventionHelper {
    var nsslId = 0
    var nsslPlaceholderId = 0
    var mediaCarouselId = 0
    var keyguardStatusId = 0
    
    fun initIds(context: Context) {
        val res = context.resources
        val pkg = context.packageName
        
        nsslId = res.getIdentifier("notification_stack_scroller", "id", pkg)
        nsslPlaceholderId = res.getIdentifier("nssl_placeholder", "id", pkg)
        mediaCarouselId = res.getIdentifier("keyguard_media_carousel", "id", pkg)
        keyguardStatusId = res.getIdentifier("keyguard_status_view", "id", pkg)
    }
    
    fun forceElementsBelow(rootLayout: ViewGroup, clock: View, date: View) {
        
    }
}
