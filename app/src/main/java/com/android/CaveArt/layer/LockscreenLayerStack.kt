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

    private val customLayers = mutableListOf<RenderLayer>()

    var activeLayerId by mutableStateOf<String?>("layer_subject")
    var isDepthEnabled by mutableStateOf(true)
    var isMaskInspectorActive by mutableStateOf(false)
    var depthOcclusionLimit by mutableFloatStateOf(0.45f)

    fun addLayer(layer: RenderLayer) {
        if (customLayers.none { it.id == layer.id }) {
            customLayers.add(layer)
        }
    }

    fun removeLayer(layerId: String) {
        customLayers.removeAll { it.id == layerId }
    }

    fun getOrderedLayers(): List<RenderLayer> {
        val list = mutableListOf<RenderLayer>(basePhotoLayer, backgroundLayer, clockLayer, subjectLayer)
        list.addAll(customLayers)
        return list.sortedBy { it.zIndex }
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
        val isSubject = (layerId == subjectLayer.id)
        subjectLayer.isMaskOverlayActive = isSubject
        isMaskInspectorActive = isSubject
    }
}
