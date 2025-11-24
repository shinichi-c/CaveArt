package com.android.CaveArt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.tasks.await
import java.nio.FloatBuffer
import kotlin.math.max

object ShapeEffectHelper {

    private val segmenter: SubjectSegmenter by lazy {
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundConfidenceMask()
            .build()
        SubjectSegmentation.getClient(options)
    }

    suspend fun createShapeCropBitmap(
        context: Context,
        originalBitmap: Bitmap,
        shape: MagicShape,
        backgroundColor: Int,
        enable3DPop: Boolean
    ): Bitmap {
        val width = originalBitmap.width
        val height = originalBitmap.height
        val image = InputImage.fromBitmap(originalBitmap, 0)

        val finalBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(finalBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        try {
            val result = segmenter.process(image).await()
            val rawBounds = Geometric.calculateCircleBounds(result, originalBitmap)

            val sideLength = max(rawBounds.width(), rawBounds.height())
            val centerX = rawBounds.centerX()
            val centerY = rawBounds.centerY()
            val halfSide = sideLength / 2f
            
            val shapeBounds = RectF(
                centerX - halfSide,
                centerY - halfSide,
                centerX + halfSide,
                centerY + halfSide
            )
            
            canvas.drawColor(backgroundColor)
            
            val shapePath = ShapePathProvider.getPathForShape(shape, shapeBounds)
            canvas.save()
            canvas.clipPath(shapePath)
            canvas.drawBitmap(originalBitmap, 0f, 0f, paint)
            canvas.restore()
            
            if (enable3DPop) {
                val maskBuffer = result.foregroundConfidenceMask
                if (maskBuffer != null) {
                    val subjectBitmap = createSubjectCutout(originalBitmap, maskBuffer)
                    
                    canvas.save()
                    canvas.clipRect(0f, 0f, width.toFloat(), shapeBounds.bottom)
                    canvas.drawBitmap(subjectBitmap, 0f, 0f, paint)
                    canvas.restore()
                    
                    subjectBitmap.recycle()
                }
            }

            return finalBitmap

        } catch (e: Exception) {
            e.printStackTrace()
            return originalBitmap
        }
    }
    
    private fun createSubjectCutout(original: Bitmap, maskBuffer: FloatBuffer): Bitmap {
        val w = original.width
        val h = original.height
        val cutout = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        
        val origPixels = IntArray(w * h)
        original.getPixels(origPixels, 0, w, 0, 0, w, h)
        
        val newPixels = IntArray(w * h)
        maskBuffer.rewind()
        
        val lowThreshold = 0.40f
        val highThreshold = 0.65f 

        for (i in 0 until w * h) {
            val confidence = maskBuffer.get()
            
            if (confidence < lowThreshold) {
                
                newPixels[i] = 0
            } else if (confidence >= highThreshold) {
                
                newPixels[i] = origPixels[i]
            } else {
                
                val originalPixel = origPixels[i]
                
                val t = (confidence - lowThreshold) / (highThreshold - lowThreshold)
                val alpha = (t * 255).toInt()
                
                newPixels[i] = (alpha shl 24) or (originalPixel and 0x00FFFFFF)
            }
        }
        
        cutout.setPixels(newPixels, 0, w, 0, 0, w, h)
        return cutout
    }
}
