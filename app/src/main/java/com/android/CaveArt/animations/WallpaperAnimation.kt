package com.android.CaveArt.animations

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.android.CaveArt.LiveWallpaperConfig
import com.android.CaveArt.UnifiedGeometry

sealed class AnimSetting {
    abstract val id: String
    abstract val title: String
    
    data class Slider(override val id: String, override val title: String, val minValue: Float, val maxValue: Float, val defaultValue: Float) : AnimSetting()
    data class Toggle(override val id: String, override val title: String, val defaultValue: Boolean) : AnimSetting()
}

interface WallpaperAnimation {
    fun needsSegmentationMask(): Boolean = false
    fun supports3DPop(): Boolean = true
    fun supportsCenter(): Boolean = true
    fun supportsScale(): Boolean = true
    
    fun getCustomSettings(): List<AnimSetting> = emptyList()
    
    fun getPreviewScaleTarget(): Float = 1.05f
    fun getPreviewRotationTarget(): Float = 0f
    fun getPreviewDuration(): Int = 3000

    fun update(deltaTime: Float)
    fun onUnlock()
    fun onLock()
    
    fun draw(
        canvas: Canvas,
        originalBitmap: Bitmap,
        maskBitmap: Bitmap?,
        geo: UnifiedGeometry,
        config: LiveWallpaperConfig,
        paint: Paint,
        maskXferPaint: Paint,
        clipPath: Path,
        screenShapeRect: RectF
    )
}
