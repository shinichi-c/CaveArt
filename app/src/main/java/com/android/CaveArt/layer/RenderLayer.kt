package com.android.CaveArt.layer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class LayerPaintCache {
    val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val colorFilterPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val xferPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
}

data class LayerRenderContext(
    val canvas: Canvas,
    val screenW: Float,
    val screenH: Float,
    val originalBitmap: Bitmap?,
    val maskBitmap: Bitmap?,
    val projection: UnifiedProjection,
    val timeNanos: Long = 0L,
    val paintCache: LayerPaintCache = LayerPaintCache()
)

class LayerPhysicsTransform(
    initialTranslationX: Float = 0f,
    initialTranslationY: Float = 0f,
    initialScale: Float = 1.0f,
    initialParallaxFactor: Float = 0.0f
) {
    var translationX by mutableFloatStateOf(initialTranslationX)
    var translationY by mutableFloatStateOf(initialTranslationY)
    var scale by mutableFloatStateOf(initialScale)
    var parallaxFactor by mutableFloatStateOf(initialParallaxFactor)
}

enum class LayerType {
    BASE_PHOTO,
    BACKGROUND,
    CLOCK,
    SUBJECT,
    EFFECT,
    CUSTOM
}

interface RenderLayer {
    val id: String
    val type: LayerType
    val name: String
    var isVisible: Boolean
    var opacity: Float
    var zIndex: Int
    val transform: LayerPhysicsTransform
    var blendMode: PorterDuff.Mode?

    fun render(context: LayerRenderContext)
    fun getBounds(context: LayerRenderContext): RectF? = null
}

abstract class BaseRenderLayer(
    override val id: String,
    override val type: LayerType,
    override val name: String,
    initialVisible: Boolean = true,
    initialOpacity: Float = 1.0f,
    initialZIndex: Int = 0,
    initialBlendMode: PorterDuff.Mode? = null
) : RenderLayer {
    override var isVisible by mutableStateOf(initialVisible)
    override var opacity by mutableFloatStateOf(initialOpacity)
    override var zIndex by mutableIntStateOf(initialZIndex)
    override val transform: LayerPhysicsTransform = LayerPhysicsTransform()
    override var blendMode by mutableStateOf(initialBlendMode)
}
