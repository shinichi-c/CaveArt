package com.android.CaveArt

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.tasks.await

object ImageLabelingHelper {
    private const val TAG = "ImageLabelingHelper"
    private const val CONFIDENCE_THRESHOLD = 0.7f

    private val labeler by lazy {
        val options = ImageLabelerOptions.Builder()
            .setConfidenceThreshold(CONFIDENCE_THRESHOLD)
            .build()
        ImageLabeling.getClient(options)
    }
    
    suspend fun getTagsFromBitmap(bitmap: Bitmap): List<String> {
        val image = InputImage.fromBitmap(bitmap, 0)

        return try {
            val result = labeler.process(image).await()
            val tags = result
                .filter { it.confidence >= CONFIDENCE_THRESHOLD }
                .map { it.text }
                .distinct()

            Log.d(TAG, "Generated tags: $tags")
            tags
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate tags: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
}
