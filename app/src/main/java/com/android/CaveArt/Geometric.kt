package com.android.CaveArt

import android.graphics.Bitmap
import android.graphics.RectF

object Geometric {

    fun getSmartBounds(cutout: Bitmap, originalWidth: Int, originalHeight: Int): RectF {
        val w = cutout.width
        val h = cutout.height
        val pixels = IntArray(w * h)
        cutout.getPixels(pixels, 0, w, 0, 0, w, h)

        var sumX = 0.0
        var sumY = 0.0
        var totalWeight = 0.0
        
        var minX = w
        var maxX = 0
        var minY = h
        var maxY = 0
        
        for (y in 0 until h) {
            for (x in 0 until w) {
                val alpha = (pixels[y * w + x] shr 24) and 0xFF
                
                if (alpha > 40) {
                    val weight = alpha.toDouble()
                    sumX += x * weight
                    sumY += y * weight
                    totalWeight += weight

                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        
        if (totalWeight < 1000) {
            val cx = originalWidth / 2f
            val cy = originalHeight / 2f
            val size = originalWidth / 2f
            return RectF(cx - size, cy - size, cx + size, cy + size)
        }
        
        val comX = (sumX / totalWeight).toFloat()
        val comY = (sumY / totalWeight).toFloat()
        
        val rawWidth = (maxX - minX).toFloat()
        val rawHeight = (maxY - minY).toFloat()
        
        val scaleX = originalWidth.toFloat() / w
        val scaleY = originalHeight.toFloat() / h

        val finalCenterX = comX * scaleX
        val finalCenterY = comY * scaleY
        
        val finalWidth = rawWidth * scaleX
        val finalHeight = rawHeight * scaleY
        
        return RectF(
            finalCenterX - (finalWidth / 2f),
            finalCenterY - (finalHeight / 2f),
            finalCenterX + (finalWidth / 2f),
            finalCenterY + (finalHeight / 2f)
        )
    }
}