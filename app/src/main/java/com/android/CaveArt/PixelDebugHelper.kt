package com.android.CaveArt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

data class DebugResult(
    val testName: String,
    val inputType: String,
    val outputShape: String,
    val minOutput: Float,
    val maxOutput: Float,
    val previewBitmap: Bitmap?,
    val error: String? = null
)

object PixelDebugHelper {
	
    suspend fun runFullPipelineDiagnostic(context: Context, original: Bitmap): List<DebugResult> {
        val results = mutableListOf<DebugResult>()
        var coarseMask: Bitmap? = null
        val segHelper = SegmentationHelper(context)
        
        try {
            val start = System.currentTimeMillis()
            coarseMask = segHelper.generateSoftMask(original)
            val duration = System.currentTimeMillis() - start

            if (coarseMask != null) {
                val stats = analyzeBitmapStats(coarseMask)
                results.add(DebugResult(
                    testName = "1. Segmentation (Soft)",
                    inputType = "Original Bitmap (${original.width}x${original.height})",
                    outputShape = "${coarseMask.width}x${coarseMask.height} (${duration}ms)",
                    minOutput = stats.first,
                    maxOutput = stats.second,
                    previewBitmap = coarseMask
                ))
            } else {
                results.add(createErrorResult("1. Segmentation", "Helper returned null"))
            }
        } catch (e: Exception) {
            results.add(createErrorResult("1. Segmentation", e.message))
        } finally {
            segHelper.close()
        }
        
        if (coarseMask == null) return results
        
        var fgMask: Bitmap? = null
        val refineHelper = ForegroundEstimationHelper(context)

        try {
            val start = System.currentTimeMillis()
            fgMask = refineHelper.refineMask(original, coarseMask)
            val duration = System.currentTimeMillis() - start

            if (fgMask != null) {
                val stats = analyzeBitmapStats(fgMask)
                
                val tintedPreview = tintBitmapGreen(fgMask)

                results.add(DebugResult(
                    testName = "2. Foreground Est (Refine)",
                    inputType = "Original + Coarse Mask",
                    outputShape = "${fgMask.width}x${fgMask.height} (${duration}ms)",
                    minOutput = stats.first,
                    maxOutput = stats.second,
                    previewBitmap = tintedPreview
                ))
            } else {
                results.add(createErrorResult("2. Foreground Est", "Helper returned null"))
            }
        } catch (e: Exception) {
            results.add(createErrorResult("2. Foreground Est", e.message))
        } finally {
            refineHelper.close()
        }
        
        val maskForMatting = fgMask ?: coarseMask
        
        val mattingHelper = DeepMattingHelper(context)

        try {
            val start = System.currentTimeMillis()
            val finalResult = mattingHelper.run(original, maskForMatting)
            val duration = System.currentTimeMillis() - start

            if (finalResult != null) {
                val stats = analyzeBitmapStats(finalResult)
                results.add(DebugResult(
                    testName = "3. Deep Matting",
                    inputType = "Original + Refined Mask",
                    outputShape = "${finalResult.width}x${finalResult.height} (${duration}ms)",
                    minOutput = stats.first,
                    maxOutput = stats.second,
                    previewBitmap = finalResult
                ))
            } else {
                results.add(createErrorResult("3. Deep Matting", "Helper returned null"))
            }
        } catch (e: Exception) {
            results.add(createErrorResult("3. Deep Matting", e.message))
        } finally {
            mattingHelper.close()
        }

        return results
    }
    
    private fun analyzeBitmapStats(bitmap: Bitmap): Pair<Float, Float> {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var minVal = 1.0f
        var maxVal = 0.0f

        for (pixel in pixels) {
            val alpha = (pixel shr 24) and 0xFF
            val r = (pixel shr 16) and 0xFF
            
            val normalized: Float
            if (alpha < 255) {
                normalized = alpha / 255f
            } else {
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val brightness = (r + g + b) / 3f
                normalized = brightness / 255f
            }

            if (normalized < minVal) minVal = normalized
            if (normalized > maxVal) maxVal = normalized
        }
        
        if (minVal > maxVal) return Pair(0f, 0f)
        return Pair(minVal, maxVal)
    }

    private fun createErrorResult(name: String, errorMsg: String?): DebugResult {
        return DebugResult(name, "Error", "-", 0f, 0f, null, errorMsg ?: "Unknown error")
    }
    
    private fun tintBitmapGreen(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val dest = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val srcPixels = IntArray(w * h)
        val destPixels = IntArray(w * h)
        
        src.getPixels(srcPixels, 0, w, 0, 0, w, h)
        
        for (i in srcPixels.indices) {
            val p = srcPixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val brightness = (r + g + b) / 3
            
            destPixels[i] = Color.rgb(0, brightness, 0)
        }
        dest.setPixels(destPixels, 0, w, 0, 0, w, h)
        return dest
    }
}