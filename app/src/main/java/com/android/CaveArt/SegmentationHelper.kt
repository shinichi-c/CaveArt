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

class SegmentationHelper(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private val MODEL_NAME = "mobile_bg_removal_mosaic_dm1_w_metadata.f16.tflite"
    
    suspend fun generateSoftMask(bitmap: Bitmap): Bitmap? = withContext(MLThread.dispatcher) {
        try {
            ensureInitialized()
            val tflite = interpreter ?: return@withContext null
            
            val inputTensor = tflite.getInputTensor(0)
            val shape = inputTensor.shape() 
            val h = shape[1]
            val w = shape[2]

            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, w, h, true)
            val inputBuffer = ByteBuffer.allocateDirect(1 * h * w * 3 * 4).apply { order(ByteOrder.nativeOrder()) }
            val pixels = IntArray(w * h)
            scaledBitmap.getPixels(pixels, 0, w, 0, 0, w, h)

            for (pixel in pixels) {
                inputBuffer.putFloat(((pixel shr 16) and 0xFF).toFloat())
                inputBuffer.putFloat(((pixel shr 8) and 0xFF).toFloat())
                inputBuffer.putFloat((pixel and 0xFF).toFloat())
            }
            
            val outputBuffer = ByteBuffer.allocateDirect(1 * h * w * 4).apply { order(ByteOrder.nativeOrder()) }
            
            var maskIndex = 0
            if (tflite.outputTensorCount > 0) {
                 val outShape = tflite.getOutputTensor(0).shape()
                 if (outShape.size == 4 && outShape[3] == 1) maskIndex = 0 
                 else if (tflite.outputTensorCount > 1) maskIndex = 1
            }
            val outputs = mutableMapOf<Int, Any>()
            outputs[maskIndex] = outputBuffer

            tflite.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)
            
            outputBuffer.rewind()
            val maskBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val maskPixels = IntArray(w * h)

            for (i in maskPixels.indices) {
                val prob = outputBuffer.float
                
                val alpha = if (prob > 0.5f) 255 else 0
                
                maskPixels[i] = Color.argb(alpha, 255, 255, 255)
            }
            maskBitmap.setPixels(maskPixels, 0, w, 0, 0, w, h)
            
            if (scaledBitmap != bitmap) scaledBitmap.recycle()

            return@withContext maskBitmap

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