package com.android.CaveArt.animations

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.android.CaveArt.LiveWallpaperConfig
import com.android.CaveArt.UnifiedGeometry

interface WallpaperAnimation {
    fun needsSegmentationMask(): Boolean = false

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
