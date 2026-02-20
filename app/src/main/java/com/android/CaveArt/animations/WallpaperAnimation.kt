package com.android.CaveArt.animations

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import com.android.CaveArt.UnifiedGeometry

data class AnimationState(
    val bodyMatrix: Matrix,
    val shapeMatrix: Matrix,
    val popMatrix: Matrix,
    val popAlpha: Int,
    val vShift: Float,
    val progress: Float = 1f, 
    val time: Float = 0f      
)

interface WallpaperAnimation {
    fun update(deltaTime: Float)
    fun onUnlock()
    fun onLock()
    
    fun calculateState(
        geo: UnifiedGeometry,
        screenW: Float,
        screenH: Float,
        imgW: Int,
        imgH: Int,
        is3DPopEnabled: Boolean
    ): AnimationState
    
    fun applyShader(
        paint: Paint,
        bitmap: Bitmap,
        state: AnimationState,
        canvasWidth: Float,
        canvasHeight: Float
    ): Boolean
}