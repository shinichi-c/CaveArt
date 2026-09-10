package com.android.CaveArt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp

class ForegroundEstimationHelper(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private val MODEL_NAME = "foreground_estimator_5680_512_512.tflite"

    var lastError: String? = null
        private set

    var modelSignature: String? = null
        private set

    private data class TensorDim(val h: Int, val w: Int, val c: Int, val isNCHW: Boolean)

    private fun parseTensorDim(shape: IntArray): TensorDim {
        return when (shape.size) {
            4 -> {
                if (shape[1] in 1..4 && shape[2] > 4 && shape[3] > 4) {
                    TensorDim(h = shape[2], w = shape[3], c = shape[1], isNCHW = true)
                } else {
                    TensorDim(h = shape[1], w = shape[2], c = shape[3], isNCHW = false)
                }
            }
            3 -> TensorDim(h = shape[1], w = shape[2], c = 1, isNCHW = false)
            2 -> TensorDim(h = shape[0], w = shape[1], c = 1, isNCHW = false)
            else -> TensorDim(512, 512, 1, false)
        }
    }

    suspend fun refineMask(original: Bitmap, coarseMask: Bitmap): Bitmap? = withContext(MLThread.dispatcher) {
        lastError = null
        try {
            ensureInitialized()
            val tflite = interpreter ?: run {
                lastError = "Interpreter failed to initialize"
                return@withContext null
            }

            try {
                return@withContext executeInference(tflite, original, coarseMask)
            } catch (runtimeGpuEx: Exception) {
                Log.w("ForegroundEst", "Inference failed on delegate. Retrying on CPU...", runtimeGpuEx)
                close()
                val cpuOptions = Interpreter.Options().apply { setNumThreads(4) }
                val file = FileUtil.loadMappedFile(context, MODEL_NAME)
                interpreter = Interpreter(file, cpuOptions)
                return@withContext executeInference(interpreter!!, original, coarseMask)
            }
        } catch (e: Exception) {
            Log.e("ForegroundEst", "Fatal execution error", e)
            lastError = "${e::class.java.simpleName}: ${e.message}"
            return@withContext null
        }
    }

    private fun executeInference(tflite: Interpreter, original: Bitmap, coarseMask: Bitmap): Bitmap {
        val inputCount = tflite.inputTensorCount
        val outputCount = tflite.outputTensorCount

        val inShapes = (0 until inputCount).map { tflite.getInputTensor(it).shape().contentToString() }
        val outShapes = (0 until outputCount).map { tflite.getOutputTensor(it).shape().contentToString() }
        modelSignature = "In: ${inShapes.joinToString()} | Out: ${outShapes.joinToString()}"
        Log.i("ForegroundEst", modelSignature!!)

        val targetH: Int
        val targetW: Int
        val inputs: Array<Any>

        val dim0 = parseTensorDim(tflite.getInputTensor(0).shape())
        val dim1 = if (inputCount > 1) parseTensorDim(tflite.getInputTensor(1).shape()) else dim0

        val (imgIdx, maskIdx) = if (dim0.c >= 3) Pair(0, 1) else Pair(1, 0)
        val imgDim = if (imgIdx == 0) dim0 else dim1
        val maskDim = if (maskIdx == 1) dim1 else dim0

        targetH = imgDim.h
        targetW = imgDim.w

        val scaledImg = Bitmap.createScaledBitmap(original, targetW, targetH, true)
        val scaledMask = Bitmap.createScaledBitmap(coarseMask, targetW, targetH, true)

        val imgPixels = IntArray(targetW * targetH)
        val maskPixels = IntArray(targetW * targetH)
        scaledImg.getPixels(imgPixels, 0, targetW, 0, 0, targetW, targetH)
        scaledMask.getPixels(maskPixels, 0, targetW, 0, 0, targetW, targetH)

        if (inputCount == 1) {
            val tensor = tflite.getInputTensor(0)
            val isFloat = tensor.dataType() == DataType.FLOAT32
            val bytesPerElem = if (isFloat) 4 else 1
            val buffer = ByteBuffer.allocateDirect(1 * targetH * targetW * imgDim.c * bytesPerElem)
                .apply { order(ByteOrder.nativeOrder()) }

            if (imgDim.c == 4) {
                if (imgDim.isNCHW) {
                    for (p in imgPixels) if (isFloat) buffer.putFloat(((p shr 16) and 0xFF) / 255f) else buffer.put(((p shr 16) and 0xFF).toByte())
                    for (p in imgPixels) if (isFloat) buffer.putFloat(((p shr 8) and 0xFF) / 255f) else buffer.put(((p shr 8) and 0xFF).toByte())
                    for (p in imgPixels) if (isFloat) buffer.putFloat((p and 0xFF) / 255f) else buffer.put((p and 0xFF).toByte())
                    for (p in maskPixels) {
                        val mVal = maxOf((p shr 24) and 0xFF, (p shr 16) and 0xFF)
                        if (isFloat) buffer.putFloat(mVal / 255f) else buffer.put(mVal.toByte())
                    }
                } else {
                    for (i in imgPixels.indices) {
                        val p = imgPixels[i]
                        val mVal = maxOf((maskPixels[i] shr 24) and 0xFF, (maskPixels[i] shr 16) and 0xFF)
                        if (isFloat) {
                            buffer.putFloat(((p shr 16) and 0xFF) / 255f)
                            buffer.putFloat(((p shr 8) and 0xFF) / 255f)
                            buffer.putFloat((p and 0xFF) / 255f)
                            buffer.putFloat(mVal / 255f)
                        } else {
                            buffer.put(((p shr 16) and 0xFF).toByte())
                            buffer.put(((p shr 8) and 0xFF).toByte())
                            buffer.put((p and 0xFF).toByte())
                            buffer.put(mVal.toByte())
                        }
                    }
                }
            } else {
                for (p in imgPixels) {
                    if (isFloat) {
                        buffer.putFloat(((p shr 16) and 0xFF) / 255f)
                        buffer.putFloat(((p shr 8) and 0xFF) / 255f)
                        buffer.putFloat((p and 0xFF) / 255f)
                    } else {
                        buffer.put(((p shr 16) and 0xFF).toByte())
                        buffer.put(((p shr 8) and 0xFF).toByte())
                        buffer.put((p and 0xFF).toByte())
                    }
                }
            }

            inputs = arrayOf(buffer)
        } else {
            val imgTensor = tflite.getInputTensor(imgIdx)
            val maskTensor = tflite.getInputTensor(maskIdx)

            val isImgFloat = imgTensor.dataType() == DataType.FLOAT32
            val isMaskFloat = maskTensor.dataType() == DataType.FLOAT32

            val imgBuffer = ByteBuffer.allocateDirect(1 * targetH * targetW * imgDim.c * (if (isImgFloat) 4 else 1))
                .apply { order(ByteOrder.nativeOrder()) }
            val maskBuffer = ByteBuffer.allocateDirect(1 * targetH * targetW * maskDim.c * (if (isMaskFloat) 4 else 1))
                .apply { order(ByteOrder.nativeOrder()) }

            if (imgDim.isNCHW) {
                for (p in imgPixels) if (isImgFloat) imgBuffer.putFloat(((p shr 16) and 0xFF) / 255f) else imgBuffer.put(((p shr 16) and 0xFF).toByte())
                for (p in imgPixels) if (isImgFloat) imgBuffer.putFloat(((p shr 8) and 0xFF) / 255f) else imgBuffer.put(((p shr 8) and 0xFF).toByte())
                for (p in imgPixels) if (isImgFloat) imgBuffer.putFloat((p and 0xFF) / 255f) else imgBuffer.put((p and 0xFF).toByte())
            } else {
                for (p in imgPixels) {
                    if (isImgFloat) {
                        imgBuffer.putFloat(((p shr 16) and 0xFF) / 255f)
                        imgBuffer.putFloat(((p shr 8) and 0xFF) / 255f)
                        imgBuffer.putFloat((p and 0xFF) / 255f)
                    } else {
                        imgBuffer.put(((p shr 16) and 0xFF).toByte())
                        imgBuffer.put(((p shr 8) and 0xFF).toByte())
                        imgBuffer.put((p and 0xFF).toByte())
                    }
                }
            }

            for (p in maskPixels) {
                val mVal = maxOf((p shr 24) and 0xFF, (p shr 16) and 0xFF)
                if (isMaskFloat) maskBuffer.putFloat(mVal / 255f) else maskBuffer.put(mVal.toByte())
            }

            val tempInputs = arrayOfNulls<Any>(2)
            tempInputs[imgIdx] = imgBuffer
            tempInputs[maskIdx] = maskBuffer
            inputs = tempInputs.filterNotNull().toTypedArray()
        }
        
        val outputs = mutableMapOf<Int, Any>()
        val outputDims = mutableMapOf<Int, TensorDim>()

        for (i in 0 until outputCount) {
            val outTensor = tflite.getOutputTensor(i)
            val shape = outTensor.shape()
            val dim = parseTensorDim(shape)
            outputDims[i] = dim

            var count = 1
            for (s in shape) count *= s
            val bytes = if (outTensor.dataType() == DataType.FLOAT32) count * 4 else count
            val outBuf = ByteBuffer.allocateDirect(bytes).apply { order(ByteOrder.nativeOrder()) }
            outputs[i] = outBuf
        }

        tflite.runForMultipleInputsOutputs(inputs, outputs)

        val selectedOutputIdx = outputDims.entries.firstOrNull { it.value.c == 1 }?.key
            ?: outputDims.entries.firstOrNull { it.value.c == 2 }?.key
            ?: 0

        val selectedBuffer = outputs[selectedOutputIdx] as ByteBuffer
        val selectedDim = outputDims[selectedOutputIdx]!!
        val isOutFloat = tflite.getOutputTensor(selectedOutputIdx).dataType() == DataType.FLOAT32

        selectedBuffer.rewind()
        val resultBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val resultPixels = IntArray(targetW * targetH)
        val numPixels = targetW * targetH

        fun sigmoid(x: Float): Float = 1.0f / (1.0f + exp(-x))

        when (selectedDim.c) {
            1 -> {
                for (i in 0 until numPixels) {
                    val raw = if (isOutFloat) selectedBuffer.float else (selectedBuffer.get().toInt() and 0xFF) / 255f
                    val prob = if (raw < 0f || raw > 1f) sigmoid(raw) else raw
                    val a = (prob * 255f).toInt().coerceIn(0, 255)
                    resultPixels[i] = Color.argb(a, a, a, a)
                }
            }
            2 -> {
                if (selectedDim.isNCHW) {
                    selectedBuffer.position(numPixels * (if (isOutFloat) 4 else 1))
                    for (i in 0 until numPixels) {
                        val raw = if (isOutFloat) selectedBuffer.float else (selectedBuffer.get().toInt() and 0xFF) / 255f
                        val prob = if (raw < 0f || raw > 1f) sigmoid(raw) else raw
                        val a = (prob * 255f).toInt().coerceIn(0, 255)
                        resultPixels[i] = Color.argb(a, a, a, a)
                    }
                } else {
                    for (i in 0 until numPixels) {
                        val bg = if (isOutFloat) selectedBuffer.float else (selectedBuffer.get().toInt() and 0xFF) / 255f
                        val fg = if (isOutFloat) selectedBuffer.float else (selectedBuffer.get().toInt() and 0xFF) / 255f
                        val prob = sigmoid(fg - bg)
                        val a = (prob * 255f).toInt().coerceIn(0, 255)
                        resultPixels[i] = Color.argb(a, a, a, a)
                    }
                }
            }
            3 -> {
                
                for (i in 0 until numPixels) {
                    val r = if (isOutFloat) selectedBuffer.float else (selectedBuffer.get().toInt() and 0xFF) / 255f
                    val g = if (isOutFloat) selectedBuffer.float else (selectedBuffer.get().toInt() and 0xFF) / 255f
                    val b = if (isOutFloat) selectedBuffer.float else (selectedBuffer.get().toInt() and 0xFF) / 255f
                    
                    val inAlpha = maxOf((maskPixels[i] shr 24) and 0xFF, (maskPixels[i] shr 16) and 0xFF)
                    
                    val a = inAlpha
                    resultPixels[i] = Color.argb(a, a, a, a)
                }
            }
            4 -> {
                if (selectedDim.isNCHW) {
                    selectedBuffer.position(numPixels * 3 * (if (isOutFloat) 4 else 1))
                    for (i in 0 until numPixels) {
                        val raw = if (isOutFloat) selectedBuffer.float else (selectedBuffer.get().toInt() and 0xFF) / 255f
                        val prob = if (raw < 0f || raw > 1f) sigmoid(raw) else raw
                        val a = (prob * 255f).toInt().coerceIn(0, 255)
                        resultPixels[i] = Color.argb(a, a, a, a)
                    }
                } else {
                    for (i in 0 until numPixels) {
                        val r = if (isOutFloat) selectedBuffer.float else selectedBuffer.get()
                        val g = if (isOutFloat) selectedBuffer.float else selectedBuffer.get()
                        val b = if (isOutFloat) selectedBuffer.float else selectedBuffer.get()
                        val raw = if (isOutFloat) selectedBuffer.float else (selectedBuffer.get().toInt() and 0xFF) / 255f
                        val prob = if (raw < 0f || raw > 1f) sigmoid(raw) else raw
                        val a = (prob * 255f).toInt().coerceIn(0, 255)
                        resultPixels[i] = Color.argb(a, a, a, a)
                    }
                }
            }
        }

        resultBitmap.setPixels(resultPixels, 0, targetW, 0, 0, targetW, targetH)

        if (scaledImg != original) scaledImg.recycle()
        if (scaledMask != coarseMask) scaledMask.recycle()

        return resultBitmap
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
            gpuDelegate?.close()
            gpuDelegate = null
            val file = FileUtil.loadMappedFile(context, MODEL_NAME)
            interpreter = Interpreter(file, options)
        }
    }

    fun warmUp() {
        try { ensureInitialized() } catch (e: Exception) { Log.e("ForegroundEst", "Warmup failed", e) }
    }

    fun close() {
        interpreter?.close()
        gpuDelegate?.close()
        interpreter = null
        gpuDelegate = null
    }
}
