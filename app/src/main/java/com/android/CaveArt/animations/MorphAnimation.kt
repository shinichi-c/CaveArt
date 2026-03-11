package com.android.CaveArt.animations

import android.graphics.*
import com.android.CaveArt.LiveWallpaperConfig
import com.android.CaveArt.UnifiedGeometry
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.max

class MorphAnimation : WallpaperAnimation {
	
    override fun needsSegmentationMask(): Boolean = false
    override fun supports3DPop(): Boolean = false
    override fun supportsCenter(): Boolean = false

    companion object {
        private val AGSL_SRC = """
            uniform shader image;
            uniform float2 resolution;
            uniform float progress;
            uniform float time;

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / resolution;
                float warp = sin(uv.y * 12.0 + time * 1.5) * cos(uv.x * 12.0 + time * 1.5);
                float2 distortion = float2(warp) * (1.0 - progress) * 0.05;
                return image.eval(fragCoord + distortion * resolution);
            }
        """.trimIndent()
    }

    private var timeSeconds = 0f
    private var currentProgress = 0f 
    private var targetProgress = 0f
    private val TRANSITION_SPEED = 8.5f
    private val FLOAT_AMPLITUDE = 25f
    private val TILT_AMPLITUDE = 1.5f
    private val BREATH_SPEED = 1.2f
    
    private val _bodyMatrix = Matrix()
    private var runtimeShader: RuntimeShader? = null

    override fun update(deltaTime: Float) {
        timeSeconds += deltaTime
        val diff = targetProgress - currentProgress
        if (abs(diff) > 0.0001f) {
            currentProgress += diff * (TRANSITION_SPEED * deltaTime)
        } else {
            currentProgress = targetProgress
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
        val screenW = canvas.width.toFloat()
        val screenH = canvas.height.toFloat()
        val imgW = originalBitmap.width
        val imgH = originalBitmap.height

        val fillScale = max(screenW / imgW, screenH / imgH)
        val zoomEffect = if (targetProgress == 1f) (currentProgress * 0.02f) else 0f
        val currentImgScale = lerp(fillScale, geo.baseScale * config.scale + zoomEffect, currentProgress)
        
        val floatY = (sin(timeSeconds * BREATH_SPEED) * FLOAT_AMPLITUDE) * currentProgress
        val floatX = (cos(timeSeconds * BREATH_SPEED * 0.5f) * (FLOAT_AMPLITUDE * 0.3f)) * currentProgress
        val tiltAngle = (sin(timeSeconds * BREATH_SPEED) * TILT_AMPLITUDE) * currentProgress
        
        val finalAnchorX = imgW / 2f
        val finalAnchorY = imgH / 2f
        
        _bodyMatrix.reset()
        _bodyMatrix.postTranslate(-finalAnchorX, -finalAnchorY)
        _bodyMatrix.postScale(currentImgScale, currentImgScale)
        _bodyMatrix.postRotate(tiltAngle)
        _bodyMatrix.postTranslate(screenW / 2f + floatX, screenH / 2f + floatY)
        
        if (runtimeShader == null) runtimeShader = RuntimeShader(AGSL_SRC)
        
        val bShader = BitmapShader(originalBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        bShader.setLocalMatrix(_bodyMatrix)
        
        runtimeShader?.let { shader ->
            shader.setInputShader("image", bShader)
            shader.setFloatUniform("resolution", screenW, screenH)
            shader.setFloatUniform("progress", currentProgress)
            shader.setFloatUniform("time", timeSeconds)
            paint.shader = shader
        }
        
        canvas.drawColor(config.backgroundColor)
        canvas.drawRect(0f, 0f, screenW, screenH, paint)
        paint.shader = null
    }
    
    private fun lerp(start: Float, end: Float, t: Float): Float = start + (end - start) * t
}
