package com.android.CaveArt

import android.graphics.*
import kotlin.math.max

data class UnifiedProjection(
    val destRect: RectF,
    val origMatrix: Matrix,
    val maskMatrix: Matrix
)

object LayerVisualizerHelper {

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val structurePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val maskXferPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }
    
    const val RUBYLITH_COLOR = 0x8CE53935.toInt()
    
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
        if (originalBmp == null || originalBmp.isRecycled) return

        val projection = computeProjection(screenW, screenH, originalBmp, maskBmp, zoom)
        val isMaskInspecting = layerStack.isMaskInspectorActive || layerStack.subjectLayer.isMaskOverlayActive
        val bgLayer = layerStack.backgroundLayer
        val clockLayer = layerStack.clockLayer
        val subjectLayer = layerStack.subjectLayer
        
        if (bgLayer.isVisible) {
            if (isMaskInspecting) {
                
                scrimPaint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                    setSaturation(0.20f)
                })
                scrimPaint.alpha = 130
                canvas.drawBitmap(originalBmp, projection.origMatrix, scrimPaint)
                canvas.drawColor(Color.argb(90, 0, 0, 0))
            } else {
                
                if (bgLayer.structure != 0f) {
                    val sat = (1f + (bgLayer.structure * 0.5f)).coerceIn(0f, 2f)
                    structurePaint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                        setSaturation(sat)
                    })
                } else {
                    structurePaint.colorFilter = null
                }
                structurePaint.alpha = (bgLayer.opacity * 255).toInt().coerceIn(0, 255)
                canvas.drawBitmap(originalBmp, projection.origMatrix, structurePaint)
            }
        }
        
        val hasMask = maskBmp != null && !maskBmp.isRecycled

        if (clockLayer.zIndex < subjectLayer.zIndex) {
            
            if (clockLayer.isVisible) {
                drawClockAndDate(canvas)
            }
            if (subjectLayer.isVisible && hasMask) {
                drawSubject(
                    canvas = canvas,
                    screenW = screenW,
                    screenH = screenH,
                    originalBmp = originalBmp,
                    maskBmp = maskBmp!!,
                    origMatrix = projection.origMatrix,
                    maskMatrix = projection.maskMatrix,
                    opacity = subjectLayer.opacity
                )
            }
        } else {
            
            if (subjectLayer.isVisible && hasMask) {
                drawSubject(
                    canvas = canvas,
                    screenW = screenW,
                    screenH = screenH,
                    originalBmp = originalBmp,
                    maskBmp = maskBmp!!,
                    origMatrix = projection.origMatrix,
                    maskMatrix = projection.maskMatrix,
                    opacity = subjectLayer.opacity
                )
            }
            if (clockLayer.isVisible) {
                drawClockAndDate(canvas)
            }
        }
        
        if (isMaskInspecting && hasMask) {
            drawRubylithMaskOverlay(
                canvas = canvas,
                screenW = screenW,
                screenH = screenH,
                maskBmp = maskBmp!!,
                maskMatrix = projection.maskMatrix
            )
        }
    }

    private fun drawSubject(
        canvas: Canvas,
        screenW: Float,
        screenH: Float,
        originalBmp: Bitmap,
        maskBmp: Bitmap,
        origMatrix: Matrix,
        maskMatrix: Matrix,
        opacity: Float
    ) {
        val layerId = canvas.saveLayer(0f, 0f, screenW, screenH, null)
        maskPaint.alpha = (opacity * 255).toInt().coerceIn(0, 255)
        
        canvas.drawBitmap(maskBmp, maskMatrix, maskPaint)
        
        canvas.drawBitmap(originalBmp, origMatrix, maskXferPaint)

        canvas.restoreToCount(layerId)
    }

    private fun drawRubylithMaskOverlay(
        canvas: Canvas,
        screenW: Float,
        screenH: Float,
        maskBmp: Bitmap,
        maskMatrix: Matrix
    ) {
        val layerId = canvas.saveLayer(0f, 0f, screenW, screenH, null)
        
        maskPaint.alpha = 255
        canvas.drawBitmap(maskBmp, maskMatrix, maskPaint)
        
        overlayPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        overlayPaint.color = RUBYLITH_COLOR
        canvas.drawRect(0f, 0f, screenW, screenH, overlayPaint)
        overlayPaint.xfermode = null

        canvas.restoreToCount(layerId)
    }
}
