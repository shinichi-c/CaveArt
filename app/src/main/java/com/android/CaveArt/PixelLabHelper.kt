package com.android.CaveArt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

object PixelLabHelper {

    private const val MODEL_NAME = "mobile_bg_removal_mosaic_dm1_w_metadata.f16.tflite"

    fun generateCoarseMask(context: Context, originalBitmap: Bitmap): Bitmap? {
        var interpreter: Interpreter? = null
        try {
            val options = Interpreter.Options()
            val compatList = CompatibilityList()
            if (compatList.isDelegateSupportedOnThisDevice) {
                options.addDelegate(GpuDelegate(compatList.bestOptionsForThisDevice))
            } else {
                options.setNumThreads(4)
            }

            val file = FileUtil.loadMappedFile(context, MODEL_NAME)
            interpreter = Interpreter(file, options)
            val inputTensor = interpreter.getInputTensor(0)
            val inputShape = inputTensor.shape() // [1, H, W, 3]
            val height = inputShape[1]
            val width = inputShape[2]
            val inputBuffer = ByteBuffer.allocateDirect(1 * height * width * 3 * 4).order(ByteOrder.nativeOrder())
            val scaled = Bitmap.createScaledBitmap(originalBitmap, width, height, true)
            val pixels = IntArray(width * height)
            scaled.getPixels(pixels, 0, width, 0, 0, width, height)

            for (pixel in pixels) {
                inputBuffer.putFloat(((pixel shr 16) and 0xFF).toFloat())
                inputBuffer.putFloat(((pixel shr 8) and 0xFF).toFloat())
                inputBuffer.putFloat((pixel and 0xFF).toFloat())
            }
            
            var maskIndex = 0
            for (i in 0 until interpreter.outputTensorCount) {
                val shape = interpreter.getOutputTensor(i).shape()
                if (shape.size == 4 && shape[3] == 1) maskIndex = i
            }
            
            val outputBuffer = ByteBuffer.allocateDirect(1 * height * width * 1 * 4).order(ByteOrder.nativeOrder())
            val outputs = mutableMapOf<Int, Any>()
            outputs[maskIndex] = outputBuffer

            interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)

            outputBuffer.rewind()
            val maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val maskPixels = IntArray(width * height)

            for (i in maskPixels.indices) {
                val prob = outputBuffer.float
                val alpha = if (prob > 0.5f) 255 else 0
                maskPixels[i] = Color.argb(255, alpha, alpha, alpha)
            }
            maskBitmap.setPixels(maskPixels, 0, width, 0, 0, width, height)
            return maskBitmap

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            interpreter?.close()
        }
    }
}