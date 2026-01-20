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
import kotlin.math.max

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

    private const val MODEL_RAID = "mobile_bg_removal_mosaic_dm1_w_metadata.f16.tflite"
    private const val MODEL_FG = "foreground_estimator_5680_512_512.tflite"
    private const val MODEL_MATTING = "deep_matting.tflite"

    fun runFullPipelineDiagnostic(context: Context, original: Bitmap): List<DebugResult> {
        val results = mutableListOf<DebugResult>()
        
        // STAGE OnE COARSE 
        var coarseMask: Bitmap? = null
        val raidResult = runModelGeneric(
            context, "1. Coarse (Raid)", MODEL_RAID, original, null,
            normalizeInput = false 
        ) { outBuffer, _ ->
            val prob = outBuffer.float
            val alpha = if (prob > 0.5f) 255 else 0
            Color.argb(255, alpha, alpha, alpha) // White Mask
        }
        results.add(raidResult)
        coarseMask = raidResult.previewBitmap

        if (coarseMask == null) {
            results.add(DebugResult("Pipeline Stopped", "-", "-", 0f, 0f, null, "Coarse mask failed"))
            return results
        }

        // STAGE TwO FOREGROUND ESTIMATION
        var fgMask: Bitmap? = null
        val fgResult = runModelGeneric(
            context, "2. Foreground Est", MODEL_FG, original, coarseMask,
            normalizeInput = true 
        ) { outBuffer, channels ->
            var fgProb = 0f
            if (channels >= 2) {
                val c0 = outBuffer.float
                val c1 = outBuffer.float // We want Channel 1
                for (k in 2 until channels) { outBuffer.float }
                fgProb = c1
            } else {
                fgProb = outBuffer.float
            }
            
            val c = (fgProb * 255).toInt().coerceIn(0, 255)
            Color.argb(255, 0, c, 0) // Green Gradient Mask
        }
        results.add(fgResult)
        fgMask = fgResult.previewBitmap
        
        val maskForMatting = fgMask ?: coarseMask

        // STAGE ThREE DEEP MATTING
        val mattingResult = runModelGeneric(
            context, "3. Deep Matting", MODEL_MATTING, original, maskForMatting,
            normalizeInput = true
        ) { outBuffer, _ ->
            val alpha = outBuffer.float
            val a = (alpha * 255).toInt().coerceIn(0, 255)
            Color.argb(255, a, a, a) // Final Alpha
        }
        results.add(mattingResult)

        return results
    }

    private fun runModelGeneric(
        context: Context,
        testName: String,
        modelName: String,
        original: Bitmap,
        priorMask: Bitmap?,
        normalizeInput: Boolean,
        visualization: (ByteBuffer, Int) -> Int
    ): DebugResult {
        var interpreter: Interpreter? = null
        try {
            val options = Interpreter.Options()
            val compatList = CompatibilityList()
            if (compatList.isDelegateSupportedOnThisDevice) {
                options.addDelegate(GpuDelegate(compatList.bestOptionsForThisDevice))
            } else {
                options.setNumThreads(4)
            }

            val file = FileUtil.loadMappedFile(context, modelName)
            interpreter = Interpreter(file, options)
            val imgTensorIndex = 0
            val imgShape = interpreter.getInputTensor(imgTensorIndex).shape() 
            val inH = imgShape[1]
            val inW = imgShape[2]
            val imgDataType = interpreter.getInputTensor(imgTensorIndex).dataType()
            val scaledImg = Bitmap.createScaledBitmap(original, inW, inH, true)
            val scaledMask = if (priorMask != null) Bitmap.createScaledBitmap(priorMask, inW, inH, true) else null
            
            val imgPixels = IntArray(inW * inH)
            val maskPixels = IntArray(inW * inH)
            scaledImg.getPixels(imgPixels, 0, inW, 0, 0, inW, inH)
            scaledMask?.getPixels(maskPixels, 0, inW, 0, 0, inW, inH)

            // Fill Image Buffer
            val imgBuffer = ByteBuffer.allocateDirect(1 * inH * inW * 3 * getTypeSize(imgDataType))
            imgBuffer.order(ByteOrder.nativeOrder())

            for (pixel in imgPixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                if (imgDataType == DataType.FLOAT32) {
                    if (normalizeInput) {
                        imgBuffer.putFloat(r / 255.0f)
                        imgBuffer.putFloat(g / 255.0f)
                        imgBuffer.putFloat(b / 255.0f)
                    } else {
                        imgBuffer.putFloat(r.toFloat())
                        imgBuffer.putFloat(g.toFloat())
                        imgBuffer.putFloat(b.toFloat())
                    }
                } else {
                    imgBuffer.put(r.toByte())
                    imgBuffer.put(g.toByte())
                    imgBuffer.put(b.toByte())
                }
            }
            imgBuffer.rewind()
            
            var maskBuffer: ByteBuffer? = null
            if (priorMask != null) {
                val maskTensorIndex = 1 
                val maskDataType = interpreter.getInputTensor(maskTensorIndex).dataType()
                
                maskBuffer = ByteBuffer.allocateDirect(1 * inH * inW * 1 * getTypeSize(maskDataType))
                maskBuffer.order(ByteOrder.nativeOrder())

                for (pixel in maskPixels) {
                    
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    val maxVal = max(r, max(g, b))
                    
                    if (maskDataType == DataType.FLOAT32) {
                        maskBuffer.putFloat(maxVal / 255.0f)
                    } else {
                        maskBuffer.put(maxVal.toByte())
                    }
                }
                maskBuffer.rewind()
            }
            
            var outputIndex = 0
            for (i in 0 until interpreter.outputTensorCount) {
                val s = interpreter.getOutputTensor(i).shape()
                if (s.size == 4 && s[3] == 1) outputIndex = i
            }
            
            val outTensor = interpreter.getOutputTensor(outputIndex)
            val outShape = outTensor.shape()
            val outH = outShape[1]
            val outW = outShape[2]
            val outChannels = if (outShape.size == 4) outShape[3] else 1
            val outDataType = outTensor.dataType()
            
            val outSize = 1 * outH * outW * outChannels * getTypeSize(outDataType)
            val outBuffer = ByteBuffer.allocateDirect(outSize).order(ByteOrder.nativeOrder())
            
            val inputs = if (maskBuffer != null) arrayOf(imgBuffer, maskBuffer) else arrayOf(imgBuffer)
            val outputs = mutableMapOf<Int, Any>()
            outputs[outputIndex] = outBuffer

            interpreter.runForMultipleInputsOutputs(inputs, outputs)
            
            outBuffer.rewind()
            
            val resultBitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val resultPixels = IntArray(outW * outH)
            
            var minVal = Float.MAX_VALUE
            var maxVal = Float.MIN_VALUE

            for (i in 0 until outW * outH) {
                val pixelColor = visualization(outBuffer, outChannels)
                resultPixels[i] = pixelColor
                
                val brightness = Color.green(pixelColor) / 255f
                if (brightness < minVal) minVal = brightness
                if (brightness > maxVal) maxVal = brightness
            }
            resultBitmap.setPixels(resultPixels, 0, outW, 0, 0, outW, outH)

            return DebugResult(
                testName = testName,
                inputType = if(normalizeInput) "Norm: 0..1" else "Norm: RAW",
                outputShape = outShape.contentToString(),
                minOutput = minVal,
                maxOutput = maxVal,
                previewBitmap = resultBitmap
            )

        } catch (e: Exception) {
            e.printStackTrace()
            return DebugResult(testName, "Error", "N/A", 0f, 0f, null, e.message)
        } finally {
            interpreter?.close()
        }
    }

    private fun getTypeSize(type: DataType): Int {
        return if (type == DataType.FLOAT32) 4 else 1
    }
}