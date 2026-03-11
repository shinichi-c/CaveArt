package com.android.CaveArt.animations

import android.graphics.*
import com.android.CaveArt.LiveWallpaperConfig
import com.android.CaveArt.UnifiedGeometry
import kotlin.math.*

class OrganicBlobAnimation : WallpaperAnimation {

    override fun needsSegmentationMask(): Boolean = true
    
    override fun getCustomSettings(): List<AnimSetting> = listOf(
        AnimSetting.Slider("blob_wobble_size", "Blob Wobble Size", 0.01f, 0.15f, 0.05f)
    )
    
    override fun getPreviewScaleTarget(): Float = 1.06f
    override fun getPreviewDuration(): Int = 4000

    private var timeSeconds = 0f
    private var currentProgress = 0f
    private var targetProgress = 0f
    private var velocity = 0f

    private val SPRING_TENSION = 200f
    private val SPRING_FRICTION = 20f

    private val _bodyMatrix = Matrix()
    private val _blobPath = Path()
    private val _shapeRect = RectF()
    private val _fadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private var _lastFadeStart = -1f
    private var _lastFadeEnd = -1f

    override fun update(deltaTime: Float) {
        timeSeconds += deltaTime
        val steps = 4
        val dt = deltaTime / steps
        for (i in 0 until steps) {
            val displacement = currentProgress - targetProgress
            val force = -SPRING_TENSION * displacement - SPRING_FRICTION * velocity
            velocity += force * dt
            currentProgress += velocity * dt
        }

        if (abs(currentProgress - targetProgress) < 0.001f && abs(velocity) < 0.001f) {
            currentProgress = targetProgress
            velocity = 0f
        }
    }

    override fun onUnlock() { targetProgress = 1f }
    override fun onLock() { targetProgress = 0f }

    override fun draw(
        canvas: Canvas,
        originalBitmap: Bitmap,
        maskBitmap: Bitmap?,
        geo: UnifiedGeometry,
        config: LiveWallpaperConfig,
        paint: Paint,
        maskXferPaint: Paint,
        clipPath: Path,
        screenShapeRect: RectF
    ) {
        
        val wobbleSize = config.animParams["blob_wobble_size"] ?: 0.05f

        val screenW = canvas.width.toFloat()
        val screenH = canvas.height.toFloat()
        val imgW = originalBitmap.width
        val imgH = originalBitmap.height

        val safeProgress = currentProgress.coerceAtLeast(0.01f)
        val popScale = lerp(0.3f, 1.0f, safeProgress) 
        
        val currentImgScale = geo.baseScale * config.scale * popScale

        val anchorX = if (config.isCentered) geo.subjectCenterX else imgW / 2f
        val anchorY = if (config.isCentered) geo.subjectCenterY else imgH / 2f

        _bodyMatrix.reset()
        _bodyMatrix.postTranslate(-anchorX, -anchorY)
        _bodyMatrix.postScale(currentImgScale, currentImgScale)
        _bodyMatrix.postTranslate(screenW / 2f, screenH / 2f)
        
        _shapeRect.set(geo.shapeBoundsRel)
        _bodyMatrix.mapRect(_shapeRect)
        
        val vShift = if (config.is3DPopEnabled) _shapeRect.height() * (0.12f + wobbleSize * 1.5f) else 0f
        
        val centerX = _shapeRect.centerX()
        val centerY = _shapeRect.centerY() + vShift
        val baseRadius = min(_shapeRect.width(), _shapeRect.height()) / 2f

        _blobPath.rewind()
        val numPoints = 120 
        
        for (i in 0..numPoints) {
            val angle = (i.toFloat() / numPoints) * (PI.toFloat() * 2f)
            
            val offset1 = sin(angle * 4f + timeSeconds * 2.2f) * (baseRadius * wobbleSize)
            val offset2 = cos(angle * 3f - timeSeconds * 1.5f) * (baseRadius * wobbleSize * 0.8f)
            val offset3 = sin(angle * 6f + timeSeconds * 1.0f) * (baseRadius * wobbleSize * 0.5f)
            
            val r = baseRadius + offset1 + offset2 + offset3
            val x = centerX + r * cos(angle)
            val y = centerY + r * sin(angle)
            
            if (i == 0) _blobPath.moveTo(x, y) else _blobPath.lineTo(x, y)
        }
        _blobPath.close()

        canvas.drawColor(config.backgroundColor)

        canvas.save()
        canvas.clipPath(_blobPath)
        
        paint.alpha = (safeProgress * 255).toInt().coerceIn(0, 255)
        canvas.drawBitmap(originalBitmap, _bodyMatrix, paint)
        paint.alpha = 255 
        canvas.restore()
        
        if (config.is3DPopEnabled && maskBitmap != null) {
            val popMatrix = Matrix(_bodyMatrix)
            
            val layerId = canvas.saveLayer(0f, 0f, screenW, screenH, null)
            paint.alpha = (safeProgress * 255).toInt().coerceIn(0, 255)
            
            canvas.drawBitmap(maskBitmap, popMatrix, paint)
            canvas.drawBitmap(originalBitmap, popMatrix, maskXferPaint)
            
            val fadeStart = _shapeRect.bottom - _shapeRect.height() * 0.35f
            val fadeEnd = _shapeRect.bottom - _shapeRect.height() * 0.05f
            
            if (fadeStart != _lastFadeStart || fadeEnd != _lastFadeEnd) {
                _fadePaint.shader = LinearGradient(
                    0f, fadeStart, 
                    0f, fadeEnd, 
                    Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP
                )
                _lastFadeStart = fadeStart
                _lastFadeEnd = fadeEnd
            }
            canvas.drawRect(0f, 0f, screenW, screenH, _fadePaint)
            
            paint.alpha = 255 
            canvas.restoreToCount(layerId)
        }
    }

    private fun lerp(start: Float, end: Float, t: Float): Float = start + (end - start) * t
}
