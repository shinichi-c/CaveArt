package com.android.CaveArt.layer

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.CaveArt.DepthColorMap

enum class DepthBlendStyle(val label: String) {
    FULL_HEATMAP("Direct Map"),
    ATMOSPHERIC_SCREEN("Screen Glow"),
    VOLUMETRIC_OVERLAY("Overlay Fog")
}

class DepthVisionRenderLayer(
    id: String = "layer_depth_vision",
    name: String = "Depth Vision (DA3)",
    initialZIndex: Int = 1
) : BaseRenderLayer(id, LayerType.EFFECT, name, initialVisible = false, initialOpacity = 1.0f, initialZIndex = initialZIndex) {

    var depthHeatMap: Bitmap? by mutableStateOf(null)
    var rawDepthMap: Bitmap? by mutableStateOf(null)
    var selectedColorMap by mutableStateOf(DepthColorMap.INFERNO)
    var blendStyle by mutableStateOf(DepthBlendStyle.FULL_HEATMAP)

    private val depthMatrix = Matrix()

    override fun render(context: LayerRenderContext) {
        val heatMap = depthHeatMap ?: return
        if (heatMap.isRecycled) return

        val canvas = context.canvas
        val paint = context.paintCache.bitmapPaint
        paint.alpha = (opacity.coerceIn(0f, 1f) * 255).toInt().coerceIn(0, 255)

        val proj = context.projection
        depthMatrix.reset()
        val sx = proj.destRect.width() / heatMap.width.toFloat()
        val sy = proj.destRect.height() / heatMap.height.toFloat()
        depthMatrix.postScale(sx, sy)
        depthMatrix.postTranslate(proj.destRect.left, proj.destRect.top)

        canvas.save()
        if (transform.translationX != 0f || transform.translationY != 0f) {
            canvas.translate(transform.translationX, transform.translationY)
        }

        when (blendStyle) {
            DepthBlendStyle.FULL_HEATMAP -> {
                paint.xfermode = null
                canvas.drawBitmap(heatMap, depthMatrix, paint)
            }
            DepthBlendStyle.ATMOSPHERIC_SCREEN -> {
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                canvas.drawBitmap(heatMap, depthMatrix, paint)
                paint.xfermode = null
            }
            DepthBlendStyle.VOLUMETRIC_OVERLAY -> {
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
                canvas.drawBitmap(heatMap, depthMatrix, paint)
                paint.xfermode = null
            }
        }

        canvas.restore()
    }

    override fun getBounds(context: LayerRenderContext): RectF = context.projection.destRect
}
