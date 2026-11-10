package com.android.CaveArt

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF

enum class GravityMode(val label: String) {
    SURFACE_PLANE("Surface Plane"),
    STEPPED_DIGITS("Stepped 3D"),
    VANISHING_STREET("Vanishing Alley")
}

data class AutoPerspectiveResult(
    val transformMatrix: Matrix,
    val yawY: Float,
    val pitchX: Float,
    val avgDepth: Float,
    val leftScale: Float,
    val rightScale: Float
)

object PerspectiveFitHelper {

    fun calculateAutoPerspective(
        rawDepthMap: Bitmap?,
        clockRect: RectF,
        sampleRectOnScreen: RectF,
        screenW: Float,
        screenH: Float,
        intensity: Float = 1.0f,
        mode: GravityMode = GravityMode.SURFACE_PLANE
    ): AutoPerspectiveResult {
        val identity = AutoPerspectiveResult(Matrix(), 0f, 0f, 0.5f, 1f, 1f)
        if (rawDepthMap == null || rawDepthMap.isRecycled || clockRect.width() <= 0 || clockRect.height() <= 0) {
            return identity
        }

        val dW = rawDepthMap.width
        val dH = rawDepthMap.height
        
        fun sample(screenX: Float, screenY: Float): Float {
            val nx = (screenX / screenW).coerceIn(0f, 1f)
            val ny = (screenY / screenH).coerceIn(0f, 1f)
            val px = (nx * (dW - 1)).toInt().coerceIn(0, dW - 1)
            val py = (ny * (dH - 1)).toInt().coerceIn(0, dH - 1)
            return ((rawDepthMap.getPixel(px, py) shr 16) and 0xFF) / 255.0f
        }

        val xPoints = floatArrayOf(
            sampleRectOnScreen.left,
            sampleRectOnScreen.left + sampleRectOnScreen.width() * 0.25f,
            sampleRectOnScreen.centerX(),
            sampleRectOnScreen.left + sampleRectOnScreen.width() * 0.75f,
            sampleRectOnScreen.right
        )
        val yPoints = floatArrayOf(
            sampleRectOnScreen.top,
            sampleRectOnScreen.centerY(),
            sampleRectOnScreen.bottom
        )

        var leftSum = 0f; var rightSum = 0f
        var topSum = 0f; var bottomSum = 0f
        var totalSum = 0f

        for (x in xPoints) {
            for (y in yPoints) {
                val d = sample(x, y)
                totalSum += d
                if (x <= sampleRectOnScreen.centerX()) leftSum += d else rightSum += d
                if (y <= sampleRectOnScreen.centerY()) topSum += d else bottomSum += d
            }
        }

        val leftColAvg = leftSum / 9f
        val rightColAvg = rightSum / 6f
        val topRowAvg = topSum / 10f
        val bottomRowAvg = bottomSum / 5f
        val avgDepth = totalSum / 15f
        val rawDeltaX = (rightColAvg - leftColAvg) * intensity
        val rawDeltaY = (bottomRowAvg - topRowAvg) * intensity
        val deltaX = rawDeltaX.coerceIn(-0.45f, 0.45f)
        val deltaY = rawDeltaY.coerceIn(-0.40f, 0.40f)

        val yawY = deltaX * 38f
        val pitchX = -deltaY * 26f
        val leftScale = (1.0f - (deltaX * 0.32f)).coerceIn(0.82f, 1.18f)
        val rightScale = (1.0f + (deltaX * 0.32f)).coerceIn(0.82f, 1.18f)

        val vShift = deltaY * (clockRect.height() * 0.08f)
        val cy = clockRect.centerY()
        val halfH = clockRect.height() / 2f

        val srcPts = floatArrayOf(
            clockRect.left, clockRect.top,
            clockRect.right, clockRect.top,
            clockRect.right, clockRect.bottom,
            clockRect.left, clockRect.bottom
        )

        val dstPts = floatArrayOf(
            clockRect.left, (cy - (halfH * leftScale)) - vShift,
            clockRect.right, (cy - (halfH * rightScale)) - vShift,
            clockRect.right, (cy + (halfH * rightScale)) + vShift,
            clockRect.left, (cy + (halfH * leftScale)) + vShift
        )

        val matrix = Matrix()
        matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)

        return AutoPerspectiveResult(
            transformMatrix = matrix,
            yawY = yawY,
            pitchX = pitchX,
            avgDepth = avgDepth,
            leftScale = leftScale,
            rightScale = rightScale
        )
    }
}
