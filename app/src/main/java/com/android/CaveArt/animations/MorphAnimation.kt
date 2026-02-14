package com.android.CaveArt.animations

import android.graphics.Matrix
import android.graphics.RectF
import com.android.CaveArt.UnifiedGeometry
import kotlin.math.sin
import kotlin.math.abs
import kotlin.math.max

class MorphAnimation : WallpaperAnimation {

    private var timeSeconds = 0f
    private var currentProgress = 0f 
    private var targetProgress = 0f
    
    private val _bodyMatrix = Matrix()
    private val _shapeMatrix = Matrix()
    private val _popMatrix = Matrix()

    override fun update(deltaTime: Float) {
        timeSeconds += deltaTime
        val speed = 5.0f
        val diff = targetProgress - currentProgress
        if (abs(diff) > 0.001f) currentProgress += diff * (speed * deltaTime)
        else currentProgress = targetProgress
    }

    override fun onUnlock() { targetProgress = 1f }
    override fun onLock() { targetProgress = 0f }

    override fun calculateState(geo: UnifiedGeometry, screenW: Float, screenH: Float, imgW: Int, imgH: Int, is3DPopEnabled: Boolean): AnimationState {
    	
        val fillScale = max(screenW / imgW, screenH / imgH)
        val currentImgScale = lerp(fillScale, geo.baseScale, currentProgress)
        
        val floatY = (sin(timeSeconds * 0.8f) * 20f) * currentProgress
        
        val anchorX = if (currentProgress > 0.5f && (geo.shiftX != 0f || geo.shiftY != 0f)) geo.subjectCenterX else imgW / 2f
        val anchorY = if (currentProgress > 0.5f && (geo.shiftX != 0f || geo.shiftY != 0f)) geo.subjectCenterY else imgH / 2f
        
        val finalAnchorX = lerp(imgW / 2f, anchorX, currentProgress)
        val finalAnchorY = lerp(imgH / 2f, anchorY, currentProgress)
        
        _bodyMatrix.reset()
        _bodyMatrix.postTranslate(-finalAnchorX, -finalAnchorY)
        _bodyMatrix.postScale(currentImgScale, currentImgScale)
        _bodyMatrix.postTranslate(screenW / 2f, screenH / 2f + floatY)
        
        _shapeMatrix.reset()
        _shapeMatrix.postTranslate(-finalAnchorX, -finalAnchorY)
        _shapeMatrix.postScale(currentImgScale, currentImgScale)
        _shapeMatrix.postTranslate(screenW / 2f, screenH / 2f + floatY)
        
        val expansion = lerp(50f, 1f, currentProgress)
        _shapeMatrix.postScale(expansion, expansion, screenW / 2f, screenH / 2f + floatY)
        
        _popMatrix.set(_bodyMatrix)
        if (is3DPopEnabled) {
            _popMatrix.postTranslate(sin(timeSeconds * 0.8f) * 6f * currentProgress, 0f)
        }

        val popAlpha = (currentProgress * 255).toInt().coerceIn(0, 255)
        val vShift = if (is3DPopEnabled) geo.shapeBoundsRel.height() * 0.1f * currentProgress else 0f

        return AnimationState(_bodyMatrix, _shapeMatrix, _popMatrix, popAlpha, vShift)
    }
    
    private fun lerp(start: Float, end: Float, t: Float): Float = start + (end - start) * t
}