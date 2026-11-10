package com.android.CaveArt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DepthResult(
    val rawDepthMap: Bitmap,
    val heatMapBitmap: Bitmap
)

class SpatialDepthHelper(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null

    companion object {
        private const val MODEL_NAME = "spatialdepth.tflite"
        private const val FALLBACK_MODEL_NAME = "depth_anything_v3.tflite"
        private const val LEGACY_FALLBACK_MODEL = "midas.tflite"
    }

    suspend fun runInference(bitmap: Bitmap): DepthResult? = withContext(MLThread.dispatcher) {
        try {
            ensureInitialized()
            val tflite = interpreter ?: run {
                Log.e("SpatialDepthHelper", "Interpreter failed to initialize")
                return@withContext null
            }

            val inTensor = tflite.getInputTensor(0)
            val shape = inTensor.shape()

            val isNCHW = shape.size == 4 && shape[1] == 3
            val targetH = if (isNCHW) shape[2] else shape[1]
            val targetW = if (isNCHW) shape[3] else shape[2]

            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            val pixels = IntArray(targetW * targetH)
            scaledBitmap.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)

            val inputBuffer = ByteBuffer.allocateDirect(1 * targetH * targetW * 3 * 4)
                .apply { order(ByteOrder.nativeOrder()) }
                
            val meanR = 0.485f; val meanG = 0.456f; val meanB = 0.406f
            val stdR = 0.229f;  val stdG = 0.224f;  val stdB = 0.225f

            if (isNCHW) {
                for (p in pixels) inputBuffer.putFloat((((p shr 16 and 0xFF) / 255f) - meanR) / stdR)
                for (p in pixels) inputBuffer.putFloat((((p shr 8 and 0xFF) / 255f) - meanG) / stdG)
                for (p in pixels) inputBuffer.putFloat((((p and 0xFF) / 255f) - meanB) / stdB)
            } else {
                for (p in pixels) {
                    inputBuffer.putFloat((((p shr 16 and 0xFF) / 255f) - meanR) / stdR)
                    inputBuffer.putFloat((((p shr 8 and 0xFF) / 255f) - meanG) / stdG)
                    inputBuffer.putFloat((((p and 0xFF) / 255f) - meanB) / stdB)
                }
            }

            val outCount = targetH * targetW
            val outputBuffer = ByteBuffer.allocateDirect(outCount * 4)
                .apply { order(ByteOrder.nativeOrder()) }

            tflite.run(inputBuffer, outputBuffer)
            outputBuffer.rewind()

            val rawDepth = FloatArray(outCount)
            var minVal = Float.MAX_VALUE
            var maxVal = Float.MIN_VALUE

            var topSkySum = 0f
            var bottomGroundSum = 0f
            val sampleBand = (targetH * 0.12f).toInt() * targetW

            for (i in 0 until outCount) {
                val d = outputBuffer.float
                rawDepth[i] = d
                if (d < minVal) minVal = d
                if (d > maxVal) maxVal = d

                if (i < sampleBand) topSkySum += d
                if (i >= outCount - sampleBand) bottomGroundSum += d
            }

            val range = (maxVal - minVal).coerceAtLeast(1e-5f)
            val isDirectDepth = (topSkySum / sampleBand) > (bottomGroundSum / sampleBand)

            val grayBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            val heatBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            val grayPixels = IntArray(outCount)
            val heatPixels = IntArray(outCount)

            for (i in 0 until outCount) {
                var norm = ((rawDepth[i] - minVal) / range).coerceIn(0f, 1f)
                if (isDirectDepth) {
                    norm = 1.0f - norm
                }

                val gray = (norm * 255f).toInt()
                grayPixels[i] = Color.argb(255, gray, gray, gray)
                heatPixels[i] = applyInfernoColormap(norm)
            }

            grayBitmap.setPixels(grayPixels, 0, targetW, 0, 0, targetW, targetH)
            heatBitmap.setPixels(heatPixels, 0, targetW, 0, 0, targetW, targetH)

            if (scaledBitmap != bitmap) scaledBitmap.recycle()

            return@withContext DepthResult(rawDepthMap = grayBitmap, heatMapBitmap = heatBitmap)
        } catch (e: Exception) {
            Log.e("SpatialDepthHelper", "Inference error", e)
            return@withContext null
        }
    }

    private fun applyInfernoColormap(t: Float): Int {
        val clamped = t.coerceIn(0f, 1f)
        val r: Float; val g: Float; val b: Float
        when {
            clamped < 0.25f -> {
                val f = clamped / 0.25f
                r = f * 0.35f; g = 0f; b = f * 0.55f
            }
            clamped < 0.50f -> {
                val f = (clamped - 0.25f) / 0.25f
                r = 0.35f + f * 0.50f; g = f * 0.15f; b = 0.55f * (1f - f)
            }
            clamped < 0.75f -> {
                val f = (clamped - 0.50f) / 0.25f
                r = 0.85f + f * 0.15f; g = 0.15f + f * 0.45f; b = 0f
            }
            else -> {
                val f = (clamped - 0.75f) / 0.25f
                r = 1.0f; g = 0.60f + f * 0.40f; b = f * 0.75f
            }
        }
        return Color.argb(
            255,
            (r * 255f).toInt().coerceIn(0, 255),
            (g * 255f).toInt().coerceIn(0, 255),
            (b * 255f).toInt().coerceIn(0, 255)
        )
    }

    private fun ensureInitialized() {
        if (interpreter != null) return
        val options = Interpreter.Options()
        val compatList = CompatibilityList()

        if (compatList.isDelegateSupportedOnThisDevice) {
            gpuDelegate = GpuDelegate(compatList.bestOptionsForThisDevice)
            options.addDelegate(gpuDelegate)
        } else {
            options.setNumThreads(4)
        }

        val candidates = listOf(MODEL_NAME, FALLBACK_MODEL_NAME, LEGACY_FALLBACK_MODEL)
        for (candidate in candidates) {
            try {
                val file = FileUtil.loadMappedFile(context, candidate)
                interpreter = Interpreter(file, options)
                Log.i("SpatialDepthHelper", "Loaded active depth model: $candidate")
                return
            } catch (_: Exception) {}
        }
        Log.e("SpatialDepthHelper", "Failed to load any depth model from assets")
    }

    fun close() {
        interpreter?.close()
        gpuDelegate?.close()
        interpreter = null
        gpuDelegate = null
    }
}
