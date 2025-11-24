package com.android.CaveArt

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentationResult
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

object Geometric {

    private const val CONFIDENCE_THRESHOLD = 0.5f
    private const val CIRCLE_PADDING_FACTOR = 1.2f
    
    fun calculateCircleBounds(
        result: SubjectSegmentationResult,
        originalBitmap: Bitmap
    ): RectF {
        val foregroundMaskBuffer: FloatBuffer? = result.foregroundConfidenceMask

        if (foregroundMaskBuffer == null) {
            return RectF(0f, 0f, originalBitmap.width.toFloat(), originalBitmap.height.toFloat())
        }

        val maskWidth = originalBitmap.width
        val maskHeight = originalBitmap.height
        foregroundMaskBuffer.rewind()

        var minX = maskWidth
        var minY = maskHeight
        var maxX = 0
        var maxY = 0
        var foundSubject = false
        
        for (y in 0 until maskHeight) {
            for (x in 0 until maskWidth) {
                val confidence = foregroundMaskBuffer.get()
                if (confidence > CONFIDENCE_THRESHOLD) {
                    minX = min(minX, x)
                    minY = min(minY, y)
                    maxX = max(maxX, x)
                    maxY = max(maxY, y)
                    foundSubject = true
                }
            }
        }

        if (foundSubject) {
            val centerX = (minX + maxX).toFloat() / 2f
            val centerY = (minY + maxY).toFloat() / 2f
            val subjectWidth = (maxX - minX).toFloat()
            val subjectHeight = (maxY - minY).toFloat()
            val maxDim = max(subjectWidth, subjectHeight)
            val radius = (maxDim * CIRCLE_PADDING_FACTOR) / 2f
            
            return RectF(
                centerX - radius,
                centerY - radius,
                centerX + radius,
                centerY + radius
            )
        } else {
            
            return RectF(0f, 0f, maskWidth.toFloat(), maskHeight.toFloat())
        }
    }
}
