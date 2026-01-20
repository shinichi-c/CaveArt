package com.android.CaveArt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ForegroundEstimationHelper {

    private const val MODEL_NAME = "foreground_estimator_5680_512_512.tflite"

    fun refineMask(context: Context, original: Bitmap, coarseMask: Bitmap): Bitmap? {
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
            
            val imgTensorIndex = 0
            val maskTensorIndex = 1
            
            val imgShape = interpreter.getInputTensor(imgTensorIndex).shape()
            val height = imgShape[1]
            val width = imgShape[2]

            val imgDataType = interpreter.getInputTensor(imgTensorIndex).dataType()
            val maskDataType = interpreter.getInputTensor(maskTensorIndex).dataType()

            val imgBuffer = ByteBuffer.allocateDirect(1 * height * width * 3 * getTypeSize(imgDataType))
            imgBuffer.order(ByteOrder.nativeOrder())
            
            val maskBuffer = ByteBuffer.allocateDirect(1 * height * width * 1 * getTypeSize(maskDataType))
            maskBuffer.order(ByteOrder.nativeOrder())

            val scaledImg = Bitmap.createScaledBitmap(original, width, height, true)
            val scaledMask = Bitmap.createScaledBitmap(coarseMask, width, height, true)

            val imgPixels = IntArray(width * height)
            val maskPixels = IntArray(width * height)
            scaledImg.getPixels(imgPixels, 0, width, 0, 0, width, height)
            scaledMask.getPixels(maskPixels, 0, width, 0, 0, width, height)
            
            for (pixel in imgPixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                if (imgDataType == DataType.FLOAT32) {
                    imgBuffer.putFloat(r / 255.0f)
                    imgBuffer.putFloat(g / 255.0f)
                    imgBuffer.putFloat(b / 255.0f)
                } else {
                    imgBuffer.put(r.toByte())
                    imgBuffer.put(g.toByte())
                    imgBuffer.put(b.toByte())
                }
            }
            
            for (pixel in maskPixels) {
                val m = (pixel shr 16) and 0xFF
                if (maskDataType == DataType.FLOAT32) {
                    maskBuffer.putFloat(m / 255.0f)
                } else {
                    maskBuffer.put(m.toByte())
                }
            }
            
            val outputTensorIndex = if (interpreter.getOutputTensor(0).shape()[3] == 1) 0 else 1
            val outputDataType = interpreter.getOutputTensor(outputTensorIndex).dataType()
            
            val outBuffer = ByteBuffer.allocateDirect(1 * height * width * 1 * getTypeSize(outputDataType))
            outBuffer.order(ByteOrder.nativeOrder())
            
            val inputs = arrayOf<Any>(imgBuffer, maskBuffer)
            
            val outputs = mutableMapOf<Int, Any>()
            outputs[outputTensorIndex] = outBuffer

            interpreter.runForMultipleInputsOutputs(inputs, outputs)
            
            outBuffer.rewind()
            val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val resultPixels = IntArray(width * height)

            for (i in resultPixels.indices) {
                val prob = if (outputDataType == DataType.FLOAT32) {
                    outBuffer.float
                } else {
                    (outBuffer.get().toInt() and 0xFF) / 255.0f
                }
                
                val valInt = (prob * 255).toInt().coerceIn(0, 255)
                resultPixels[i] = Color.rgb(valInt, valInt, valInt)
            }
            resultBitmap.setPixels(resultPixels, 0, width, 0, 0, width, height)
            return resultBitmap

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            interpreter?.close()
        }
    }

    private fun getTypeSize(type: DataType): Int {
        return if (type == DataType.FLOAT32) 4 else 1
    }
}