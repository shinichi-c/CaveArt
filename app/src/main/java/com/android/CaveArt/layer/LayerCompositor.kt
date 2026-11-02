package com.android.CaveArt.layer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max

data class UnifiedProjection(
    val destRect: RectF,
    val origMatrix: Matrix,
    val maskMatrix: Matrix
)

object LayerCompositor {

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0.20f) })
        alpha = 130
    }

    fun computeProjection(
        screenW: Float,
        screenH: Float,
        originalBmp: Bitmap,
        maskBmp: Bitmap?,
        zoom: Float
    ): UnifiedProjection {
        val oW = originalBmp.width.toFloat()
        val oH = originalBmp.height.toFloat()

        val baseScale = max(screenW / oW, screenH / oH) * zoom
        val destW = oW * baseScale
        val destH = oH * baseScale
        val tx = (screenW - destW) / 2f
        val ty = (screenH - destH) / 2f

        val destRect = RectF(tx, ty, tx + destW, ty + destH)

        val origMatrix = Matrix().apply {
            postScale(baseScale, baseScale)
            postTranslate(tx, ty)
        }

        val maskMatrix = Matrix()
        if (maskBmp != null && !maskBmp.isRecycled) {
            val mW = maskBmp.width.toFloat()
            val mH = maskBmp.height.toFloat()
            val scaleX = destW / mW
            val scaleY = destH / mH
            maskMatrix.postScale(scaleX, scaleY)
            maskMatrix.postTranslate(tx, ty)
        }

        return UnifiedProjection(destRect, origMatrix, maskMatrix)
    }

    /**
     * Composites all visible layers sorted by ascending zIndex.
     */
    fun composite(
        canvas: Canvas,
        screenW: Float,
        screenH: Float,
        originalBmp: Bitmap?,
        maskBmp: Bitmap?,
        zoom: Float,
        stack: LockscreenLayerStack,
        drawClockAndDate: ((Canvas) -> Unit)? = null
    ) {
        if (originalBmp == null || originalBmp.isRecycled) return

        val projection = computeProjection(screenW, screenH, originalBmp, maskBmp, zoom)
        val context = LayerRenderContext(
            canvas = canvas,
            screenW = screenW,
            screenH = screenH,
            originalBitmap = originalBmp,
            maskBitmap = maskBmp,
            projection = projection,
            timeNanos = System.nanoTime()
        )

        stack.clockLayer.drawRoutine = drawClockAndDate
        
        if (stack.isMaskInspectorActive && stack.backgroundLayer.isVisible) {
            canvas.drawBitmap(originalBmp, projection.origMatrix, scrimPaint)
            canvas.drawColor(Color.argb(90, 0, 0, 0))
        }

        val orderedLayers = stack.getOrderedLayers()
        for (layer in orderedLayers) {
            if (!layer.isVisible) continue
            if (stack.isMaskInspectorActive && layer.type == LayerType.BACKGROUND) continue
            layer.render(context)
        }
    }
}
