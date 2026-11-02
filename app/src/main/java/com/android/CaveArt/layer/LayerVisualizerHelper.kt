package com.android.CaveArt

import android.graphics.Bitmap
import android.graphics.Canvas
import com.android.CaveArt.layer.LayerCompositor
import com.android.CaveArt.layer.LockscreenLayerStack

typealias LayerType = com.android.CaveArt.layer.LayerType
typealias LockscreenLayer = com.android.CaveArt.layer.RenderLayer
typealias LockscreenLayerStack = com.android.CaveArt.layer.LockscreenLayerStack

object LayerVisualizerHelper {
    const val RUBYLITH_COLOR = 0x8CE53935.toInt()

    fun drawLayerStack(
        canvas: Canvas,
        screenW: Float,
        screenH: Float,
        originalBmp: Bitmap?,
        maskBmp: Bitmap?,
        zoom: Float,
        layerStack: LockscreenLayerStack,
        drawClockAndDate: (Canvas) -> Unit
    ) {
        LayerCompositor.composite(
            canvas = canvas,
            screenW = screenW,
            screenH = screenH,
            originalBmp = originalBmp,
            maskBmp = maskBmp,
            zoom = zoom,
            stack = layerStack,
            drawClockAndDate = drawClockAndDate
        )
    }
}
