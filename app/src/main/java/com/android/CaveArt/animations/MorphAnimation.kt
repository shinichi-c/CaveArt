package com.android.CaveArt.animations

import android.graphics.Matrix
import com.android.CaveArt.UnifiedGeometry
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.max

class MorphAnimation : WallpaperAnimation {

    private var timeSeconds = 0f
    private var currentProgress = 0f 
    private var targetProgress = 0f
    private val TRANSITION_SPEED = 8.5f
    private val FLOAT_AMPLITUDE = 25f
    private val TILT_AMPLITUDE = 1.5f
    private val BREATH_SPEED = 1.2f
    private val _bodyMatrix = Matrix()
    private val _shapeMatrix = Matrix()
    private val _popMatrix = Matrix()

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

    override fun calculateState(
        geo: UnifiedGeometry, 
        screenW: Float, 
        screenH: Float, 
        imgW: Int, 
        imgH: Int, 
        is3DPopEnabled: Boolean
    ): AnimationState {
    	
        val fillScale = max(screenW / imgW, screenH / imgH)
        val zoomEffect = if (targetProgress == 1f) (currentProgress * 0.02f) else 0f
        val currentImgScale = lerp(fillScale, geo.baseScale + zoomEffect, currentProgress)
        
        val floatY = (sin(timeSeconds * BREATH_SPEED) * FLOAT_AMPLITUDE) * currentProgress
        val floatX = (cos(timeSeconds * BREATH_SPEED * 0.5f) * (FLOAT_AMPLITUDE * 0.3f)) * currentProgress
        
        val tiltAngle = (sin(timeSeconds * BREATH_SPEED) * TILT_AMPLITUDE) * currentProgress
        
        val anchorX = if (currentProgress > 0.1f && (geo.shiftX != 0f || geo.shiftY != 0f)) geo.subjectCenterX else imgW / 2f
        val anchorY = if (currentProgress > 0.1f && (geo.shiftX != 0f || geo.shiftY != 0f)) geo.subjectCenterY else imgH / 2f
        
        val finalAnchorX = lerp(imgW / 2f, anchorX, currentProgress)
        val finalAnchorY = lerp(imgH / 2f, anchorY, currentProgress)
        
        _bodyMatrix.reset()
        _bodyMatrix.postTranslate(-finalAnchorX, -finalAnchorY)
        _bodyMatrix.postScale(currentImgScale, currentImgScale)
        _bodyMatrix.postRotate(tiltAngle)
        
        _bodyMatrix.postTranslate(screenW / 2f + floatX, screenH / 2f + floatY)
        
        _shapeMatrix.set(_bodyMatrix)
        val expansion = lerp(6.0f, 1.0f, currentProgress)
        _shapeMatrix.postScale(expansion, expansion, screenW / 2f + floatX, screenH / 2f + floatY)
        
        _popMatrix.set(_bodyMatrix)
        if (is3DPopEnabled) {
            
            val popParallax = cos(timeSeconds * 0.8f) * 8f * currentProgress
            _popMatrix.postTranslate(popParallax, -floatY * 0.2f) 
        }

        val popAlpha = (currentProgress * 255).toInt().coerceIn(0, 255)
        
        val vShift = if (is3DPopEnabled) geo.shapeBoundsRel.height() * 0.12f * currentProgress else 0f

        return AnimationState(_bodyMatrix, _shapeMatrix, _popMatrix, popAlpha, vShift)
    }
    
    private fun lerp(start: Float, end: Float, t: Float): Float = start + (end - start) * t
}