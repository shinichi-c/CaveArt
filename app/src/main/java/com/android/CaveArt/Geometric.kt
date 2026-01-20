package com.android.CaveArt

import android.graphics.Bitmap
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

object Geometric {

    fun calculateCircleBounds(
        cutout: Bitmap,
        originalWidth: Int,
        originalHeight: Int,
        paddingFactor: Float
    ): RectF {
        val w = cutout.width
        val h = cutout.height
        val pixels = IntArray(w * h)
        cutout.getPixels(pixels, 0, w, 0, 0, w, h)

        var minX = w
        var maxX = 0
        var minY = h
        var maxY = 0
        var foundSubject = false
        
        for (y in 0 until h) {
            for (x in 0 until w) {
                
                val alpha = (pixels[y * w + x] shr 24) and 0xFF
                if (alpha > 40) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                    foundSubject = true
                }
            }
        }

        if (foundSubject) {
            val scaleX = originalWidth.toFloat() / w
            val scaleY = originalHeight.toFloat() / h

            val finalMinX = minX * scaleX
            val finalMaxX = maxX * scaleX
            val finalMinY = minY * scaleY
            val finalMaxY = maxY * scaleY

            val centerX = (finalMinX + finalMaxX) / 2f
            val centerY = (finalMinY + finalMaxY) / 2f
            
            val subjectWidth = finalMaxX - finalMinX
            val subjectHeight = finalMaxY - finalMinY
            
            val maxDim = max(subjectWidth, subjectHeight)
            val radius = (maxDim * paddingFactor) / 2f

            return RectF(
                centerX - radius,
                centerY - radius,
                centerX + radius,
                centerY + radius
            )
        } else {
            val cx = originalWidth / 2f
            val cy = originalHeight / 2f
            val r = min(originalWidth, originalHeight) / 2f * paddingFactor
            return RectF(cx - r, cy - r, cx + r, cy + r)
        }
    }
}