package com.android.CaveArt.animations

import android.graphics.Matrix
import com.android.CaveArt.UnifiedGeometry
import kotlin.math.*

class NanoAssemblyAnimation : WallpaperAnimation {

    private var currentProgress = 0f    
    private var velocity = 0f           
    private var targetProgress = 0f     
    private var timeSeconds = 0f
    
    private val SPRING_TENSION = 340f  
    private val SPRING_FRICTION = 32f  
    
    private val LEVITATION_SPEED = 0.8f
    private val LEVITATION_AMP = 10f

    private val _bodyMatrix = Matrix()
    private val _shapeMatrix = Matrix()
    private val _popMatrix = Matrix()

    override fun update(deltaTime: Float) {
        timeSeconds += deltaTime

        val displacement = currentProgress - targetProgress
        val force = -SPRING_TENSION * displacement - SPRING_FRICTION * velocity
        
        velocity += force * deltaTime
        currentProgress += velocity * deltaTime

        if (abs(displacement) < 0.0001f && abs(velocity) < 0.0001f) {
            currentProgress = targetProgress
            velocity = 0f
        }
    }

    override fun onUnlock() { 
        
        if (targetProgress == 0f || currentProgress < 0.1f) {
            velocity = 9.0f 
        }
        targetProgress = 1f 
    }

    override fun onLock() { 
        targetProgress = 0f 
        
        if (velocity > -2.0f) velocity = -4.0f
    }

    override fun calculateState(
        geo: UnifiedGeometry,
        screenW: Float,
        screenH: Float,
        imgW: Int,
        imgH: Int,
        is3DPopEnabled: Boolean
    ): AnimationState {
        
        val fillScale = max(screenW / imgW, screenH / imgH)
        val currentImgScale = lerp(fillScale * 0.2f, geo.baseScale, currentProgress.coerceIn(0f, 1f))

        val anchorX = if (currentProgress > 0.05f && (geo.shiftX != 0f || geo.shiftY != 0f)) geo.subjectCenterX else imgW / 2f
        val anchorY = if (currentProgress > 0.05f && (geo.shiftX != 0f || geo.shiftY != 0f)) geo.subjectCenterY else imgH / 2f
        
        val finalAnchorX = lerp(imgW / 2f, anchorX, currentProgress.coerceIn(0f, 1f))
        val finalAnchorY = lerp(imgH / 2f, anchorY, currentProgress.coerceIn(0f, 1f))

        val idleY = sin(timeSeconds * LEVITATION_SPEED) * LEVITATION_AMP * currentProgress.coerceIn(0f, 1f)
        val rotation = (1f - currentProgress) * 10f 

        _bodyMatrix.reset()
        _bodyMatrix.postTranslate(-finalAnchorX, -finalAnchorY)
        _bodyMatrix.postScale(currentImgScale, currentImgScale)
        _bodyMatrix.postRotate(rotation)
        _bodyMatrix.postTranslate(screenW / 2f, (screenH / 2f) + idleY)

        _popMatrix.set(_bodyMatrix)
        if (is3DPopEnabled) {
            val popLag = (1f - currentProgress.coerceIn(0f, 1.2f)) * 200f 
            _popMatrix.postTranslate(0f, -popLag)
        }

        _shapeMatrix.set(_bodyMatrix)
        val expansion = lerp(6.0f, 1.0f, currentProgress.coerceIn(0f, 1f))
        _shapeMatrix.postScale(expansion, expansion, screenW / 2f, (screenH / 2f) + idleY)

        val scaledShapeHeight = geo.shapeBoundsRel.height() * geo.baseScale
        val vShift = if (is3DPopEnabled) {
            scaledShapeHeight * 0.1f * currentProgress.coerceIn(0f, 1f)
        } else {
            0f
        }

        val popAlpha = (currentProgress * 255).toInt().coerceIn(0, 255)

        return AnimationState(
            bodyMatrix = _bodyMatrix,
            shapeMatrix = _shapeMatrix,
            popMatrix = _popMatrix,
            popAlpha = popAlpha,
            vShift = vShift
        )
    }

    private fun lerp(start: Float, end: Float, t: Float): Float = start + (end - start) * t
}