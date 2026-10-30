package com.android.CaveArt

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class LayerType {
    SUBJECT,
    CLOCK,
    BACKGROUND,
    BASE_PHOTO
}

class LockscreenLayer(
    val id: String,
    val type: LayerType,
    val name: String,
    initialVisible: Boolean = true,
    initialOpacity: Float = 1.0f,
    initialZIndex: Int = 0,
    initialStructure: Float = 0f
) {
    var isVisible by mutableStateOf(initialVisible)
    var opacity by mutableFloatStateOf(initialOpacity)
    var zIndex by mutableIntStateOf(initialZIndex)
    var structure by mutableFloatStateOf(initialStructure)
    var isMaskOverlayActive by mutableStateOf(false)
}

class LockscreenLayerStack {
    val subjectLayer = LockscreenLayer(
        id = "layer_subject",
        type = LayerType.SUBJECT,
        name = "Subject",
        initialZIndex = 2
    )

    val clockLayer = LockscreenLayer(
        id = "layer_clock",
        type = LayerType.CLOCK,
        name = "Clock Face",
        initialZIndex = 1
    )

    val backgroundLayer = LockscreenLayer(
        id = "layer_background",
        type = LayerType.BACKGROUND,
        name = "Background",
        initialZIndex = 0
    )

    val basePhotoLayer = LockscreenLayer(
        id = "layer_photo",
        type = LayerType.BASE_PHOTO,
        name = "Photo",
        initialZIndex = -1
    )

    var activeLayerId by mutableStateOf<String?>("layer_subject")
    var isDepthEnabled by mutableStateOf(true)
    var isMaskInspectorActive by mutableStateOf(false)
    var depthOcclusionLimit by mutableFloatStateOf(0.45f)

    fun getOrderedLayers(): List<LockscreenLayer> {
        return listOf(basePhotoLayer, backgroundLayer, clockLayer, subjectLayer)
            .sortedBy { it.zIndex }
    }

    fun setDepthClockActive(active: Boolean) {
        isDepthEnabled = active
        if (active) {
        	
            clockLayer.zIndex = 1
            subjectLayer.zIndex = 2
        } else {
            
            subjectLayer.zIndex = 1
            clockLayer.zIndex = 2
        }
    }

    fun selectLayer(layerId: String) {
        activeLayerId = layerId
        
        if (layerId == "layer_subject") {
            subjectLayer.isMaskOverlayActive = true
            isMaskInspectorActive = true
        } else {
            subjectLayer.isMaskOverlayActive = false
            isMaskInspectorActive = false
        }
    }
}
