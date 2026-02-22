package com.android.CaveArt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ForegroundEstimationHelper(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private val MODEL_NAME = "foreground_estimator_5680_512_512.tflite"

    suspend fun refineMask(original: Bitmap, coarseMask: Bitmap): Bitmap? = withContext(MLThread.dispatcher) {
        try {
            ensureInitialized()
            val tflite = interpreter ?: return@withContext coarseMask

            val inputShape = tflite.getInputTensor(0).shape()
            val h = inputShape[1]
            val w = inputShape[2]

            val scaledImg = Bitmap.createScaledBitmap(original, w, h, true)
            val scaledMask = Bitmap.createScaledBitmap(coarseMask, w, h, true)

            val imgBuffer = ByteBuffer.allocateDirect(1 * h * w * 3 * 4).apply { order(ByteOrder.nativeOrder()) }
            val maskBuffer = ByteBuffer.allocateDirect(1 * h * w * 1 * 4).apply { order(ByteOrder.nativeOrder()) }

            val imgPixels = IntArray(w * h)
            val maskPixels = IntArray(w * h)
            scaledImg.getPixels(imgPixels, 0, w, 0, 0, w, h)
            scaledMask.getPixels(maskPixels, 0, w, 0, 0, w, h)

            for (p in imgPixels) {
                imgBuffer.putFloat(((p shr 16) and 0xFF) / 255.0f)
                imgBuffer.putFloat(((p shr 8) and 0xFF) / 255.0f)
                imgBuffer.putFloat((p and 0xFF) / 255.0f)
            }
            
            for (p in maskPixels) {
                val alpha = (p shr 24) and 0xFF
                maskBuffer.putFloat(alpha / 255.0f)
            }

            val outputBuffer = ByteBuffer.allocateDirect(1 * h * w * 1 * 4).apply { order(ByteOrder.nativeOrder()) }
            val outputs = mutableMapOf<Int, Any>()
            
            var outputIndex = 0
            if (tflite.outputTensorCount > 0) {
                 val outShape = tflite.getOutputTensor(0).shape()
                 if (outShape.size == 4 && outShape[3] == 1) outputIndex = 0 
                 else if (tflite.outputTensorCount > 1) outputIndex = 1
            }
            outputs[outputIndex] = outputBuffer

            tflite.runForMultipleInputsOutputs(arrayOf(imgBuffer, maskBuffer), outputs)

            outputBuffer.rewind()
            val resultBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val resultPixels = IntArray(w * h)

            for (i in resultPixels.indices) {
                val prob = outputBuffer.float
                val valInt = (prob * 255).toInt().coerceIn(0, 255)
                
                resultPixels[i] = Color.argb(255, valInt, valInt, valInt)
            }
            resultBitmap.setPixels(resultPixels, 0, w, 0, 0, w, h)

            scaledImg.recycle()
            scaledMask.recycle()
            
            return@withContext resultBitmap

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    private fun ensureInitialized() {
        if (interpreter != null) return
        val options = Interpreter.Options()
        val compatList = CompatibilityList()

        try {
            if (compatList.isDelegateSupportedOnThisDevice) {
                gpuDelegate = GpuDelegate(compatList.bestOptionsForThisDevice)
                options.addDelegate(gpuDelegate)
            } else {
                options.setNumThreads(4)
            }
            val file = FileUtil.loadMappedFile(context, MODEL_NAME)
            interpreter = Interpreter(file, options)
        } catch (e: Exception) {
            options.setNumThreads(4)
            try {
                val file = FileUtil.loadMappedFile(context, MODEL_NAME)
                interpreter = Interpreter(file, options)
            } catch (e2: Exception) {}
        }
    }
    
    fun warmUp() {
        ensureInitialized()
    }

    fun close() {
        interpreter?.close()
        gpuDelegate?.close()
        interpreter = null
        gpuDelegate = null
    }
}