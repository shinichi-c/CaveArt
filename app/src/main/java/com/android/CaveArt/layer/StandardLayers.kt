package com.android.CaveArt.layer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class BackgroundRenderLayer(
    id: String = "layer_background",
    name: String = "Background",
    initialZIndex: Int = 0
) : BaseRenderLayer(id, LayerType.BACKGROUND, name, initialZIndex = initialZIndex) {

    var structure by mutableFloatStateOf(0f)
    private val colorMatrix = ColorMatrix()

    override fun render(context: LayerRenderContext) {
        val original = context.originalBitmap ?: return
        if (original.isRecycled) return

        val paint = context.paintCache.colorFilterPaint
        if (structure != 0f) {
            val sat = (1f + (structure * 0.5f)).coerceIn(0f, 2f)
            colorMatrix.setSaturation(sat)
            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        } else {
            paint.colorFilter = null
        }
        
        val effectiveAlpha = (opacity.coerceIn(0f, 1f) * 255).toInt().coerceIn(0, 255)
        paint.alpha = effectiveAlpha

        val canvas = context.canvas
        canvas.save()
        if (transform.translationX != 0f || transform.translationY != 0f) {
            canvas.translate(transform.translationX, transform.translationY)
        }
        canvas.drawBitmap(original, context.projection.origMatrix, paint)
        canvas.restore()
    }

    override fun getBounds(context: LayerRenderContext): RectF = context.projection.destRect
}

class SubjectRenderLayer(
    id: String = "layer_subject",
    name: String = "Subject",
    initialZIndex: Int = 2
) : BaseRenderLayer(id, LayerType.SUBJECT, name, initialZIndex = initialZIndex) {

    var isMaskOverlayActive by mutableStateOf(false)

    override fun render(context: LayerRenderContext) {
        val original = context.originalBitmap ?: return
        val mask = context.maskBitmap ?: return
        if (original.isRecycled || mask.isRecycled) return

        val canvas = context.canvas
        val layerId = canvas.saveLayer(0f, 0f, context.screenW, context.screenH, null)

        canvas.save()
        if (transform.translationX != 0f || transform.translationY != 0f) {
            canvas.translate(transform.translationX, transform.translationY)
        }

        val maskPaint = context.paintCache.bitmapPaint
        maskPaint.alpha = (opacity.coerceIn(0f, 1f) * 255).toInt().coerceIn(0, 255)

        canvas.drawBitmap(mask, context.projection.maskMatrix, maskPaint)

        val xferPaint = context.paintCache.xferPaint
        xferPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(original, context.projection.origMatrix, xferPaint)
        xferPaint.xfermode = null

        canvas.restore()
        canvas.restoreToCount(layerId)
    }

    override fun getBounds(context: LayerRenderContext): RectF = context.projection.destRect
}

class ClockRenderLayer(
    id: String = "layer_clock",
    name: String = "Clock Face",
    initialZIndex: Int = 1,
    var drawRoutine: ((Canvas) -> Unit)? = null
) : BaseRenderLayer(id, LayerType.CLOCK, name, initialZIndex = initialZIndex) {

    override fun render(context: LayerRenderContext) {
        drawRoutine?.invoke(context.canvas)
    }
}

class BasePhotoRenderLayer(
    id: String = "layer_photo",
    name: String = "Photo",
    initialZIndex: Int = -1
) : BaseRenderLayer(id, LayerType.BASE_PHOTO, name, initialZIndex = initialZIndex) {
    override fun render(context: LayerRenderContext) {}
}

class CustomEffectLayer(
    id: String,
    name: String,
    initialZIndex: Int = 3,
    private val onRender: (LayerRenderContext) -> Unit
) : BaseRenderLayer(id, LayerType.EFFECT, name, initialZIndex = initialZIndex) {

    override fun render(context: LayerRenderContext) {
        onRender(context)
    }
}
