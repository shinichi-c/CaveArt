package com.android.CaveArt.animations

import android.graphics.Matrix
import com.android.CaveArt.UnifiedGeometry

data class AnimationState(
    val bodyMatrix: Matrix,
    val shapeMatrix: Matrix,
    val popMatrix: Matrix,
    val popAlpha: Int,
    val vShift: Float
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
}