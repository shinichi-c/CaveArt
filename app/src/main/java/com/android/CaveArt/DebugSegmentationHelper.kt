package com.android.CaveArt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.tasks.await
import java.nio.FloatBuffer

object DebugSegmentationHelper {

    private val segmenter: SubjectSegmenter by lazy {
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundConfidenceMask()
            .build()
        SubjectSegmentation.getClient(options)
    }
    
    suspend fun createDebugMaskBitmap(
        context: Context,
        bitmap: Bitmap
    ): Bitmap {
        val image = InputImage.fromBitmap(bitmap, 0)

        return try {
            val result = segmenter.process(image).await()
            val foregroundMaskBuffer: FloatBuffer? = result.foregroundConfidenceMask

            if (foregroundMaskBuffer == null) {
                val fallbackBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                Canvas(fallbackBitmap).drawColor(Color.BLACK)
                return fallbackBitmap
            }

            val maskWidth = bitmap.width
            val maskHeight = bitmap.height
            val maskBitmap = Bitmap.createBitmap(maskWidth, maskHeight, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(maskWidth * maskHeight)
            foregroundMaskBuffer.rewind()
            val threshold = 0.5f

            for (i in 0 until pixels.size) {
                val confidence = foregroundMaskBuffer.get()
                pixels[i] = if (confidence > threshold) Color.WHITE else Color.BLACK
            }
            maskBitmap.setPixels(pixels, 0, maskWidth, 0, 0, maskWidth, maskHeight)

            maskBitmap

        } catch (e: Exception) {
            e.printStackTrace()
            val errorBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Canvas(errorBitmap).drawColor(Color.RED)
            errorBitmap
        }
    }
}
