package com.android.CaveArt.layer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class LockscreenLayerStack {
    val backgroundLayer = BackgroundRenderLayer()
    val subjectLayer = SubjectRenderLayer()
    val clockLayer = ClockRenderLayer()
    val basePhotoLayer = BasePhotoRenderLayer()
    
    // NEW: Real-time MiDaS depth vision layer
    val depthVisionLayer = DepthVisionRenderLayer()

    private val customLayers = mutableListOf<RenderLayer>()

    var activeLayerId by mutableStateOf<String?>("layer_clock")
    var isDepthEnabled by mutableStateOf(true)
    var isMaskInspectorActive by mutableStateOf(false)
    var depthOcclusionLimit by mutableFloatStateOf(0.45f)

    fun getOrderedLayers(): List<RenderLayer> {
        val list = mutableListOf<RenderLayer>(
            basePhotoLayer, 
            backgroundLayer, 
            depthVisionLayer, // Depth map layer sits under the clock
            clockLayer, 
            subjectLayer
        )
        list.addAll(customLayers)
        return list.sortedBy { it.zIndex }
    }

    fun setDepthClockActive(active: Boolean) {
        isDepthEnabled = active
        if (active) {
            clockLayer.zIndex = 2
            subjectLayer.zIndex = 3
        } else {
            subjectLayer.zIndex = 2
            clockLayer.zIndex = 3
        }
    }

    fun selectLayer(layerId: String) {
        activeLayerId = layerId
        val isSubject = (layerId == subjectLayer.id)
        val isDepth = (layerId == depthVisionLayer.id)
        subjectLayer.isMaskOverlayActive = isSubject
        isMaskInspectorActive = isSubject || isDepth
    }
}