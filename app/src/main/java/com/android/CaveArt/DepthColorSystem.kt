package com.android.CaveArt

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class DepthColorMap(val label: String) {
    INFERNO("Inferno Gold"),
    NEON_CYBER("Neon Cyberpunk"),
    TURBO("Turbo Spectrum"),
    MONO_GLASS("Frosted Glass")
}

object DepthColorSystem {

    suspend fun colorizeRawDepth(
        rawDepth: Bitmap,
        colorMap: DepthColorMap
    ): Bitmap = withContext(Dispatchers.Default) {
        val w = rawDepth.width
        val h = rawDepth.height
        val total = w * h
        val inPixels = IntArray(total)
        rawDepth.getPixels(inPixels, 0, w, 0, 0, w, h)

        val outBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val outPixels = IntArray(total)

        for (i in 0 until total) {
            val norm = ((inPixels[i] shr 16) and 0xFF) / 255.0f
            outPixels[i] = when (colorMap) {
                DepthColorMap.INFERNO -> evaluateInferno(norm)
                DepthColorMap.NEON_CYBER -> evaluateNeonCyber(norm)
                DepthColorMap.TURBO -> evaluateTurbo(norm)
                DepthColorMap.MONO_GLASS -> evaluateMonoGlass(norm)
            }
        }

        outBitmap.setPixels(outPixels, 0, w, 0, 0, w, h)
        outBitmap
    }

    private fun evaluateInferno(t: Float): Int {
        val c = t.coerceIn(0f, 1f)
        val r: Float; val g: Float; val b: Float
        when {
            c < 0.25f -> {
                val f = c / 0.25f
                r = f * 0.35f; g = 0f; b = f * 0.55f
            }
            c < 0.50f -> {
                val f = (c - 0.25f) / 0.25f
                r = 0.35f + f * 0.50f; g = f * 0.15f; b = 0.55f * (1f - f)
            }
            c < 0.75f -> {
                val f = (c - 0.50f) / 0.25f
                r = 0.85f + f * 0.15f; g = 0.15f + f * 0.45f; b = 0f
            }
            else -> {
                val f = (c - 0.75f) / 0.25f
                r = 1.0f; g = 0.60f + f * 0.40f; b = f * 0.75f
            }
        }
        return Color.argb(255, (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
    }
    
    private fun evaluateNeonCyber(t: Float): Int {
        val c = t.coerceIn(0f, 1f)
        val r: Float; val g: Float; val b: Float
        when {
            c < 0.33f -> {
                val f = c / 0.33f
                r = f * 0.45f; g = 0.85f * (1f - f * 0.5f); b = 0.95f
            }
            c < 0.66f -> {
                val f = (c - 0.33f) / 0.33f
                r = 0.45f + f * 0.55f; g = 0.42f * (1f - f); b = 0.95f * (1f - f * 0.2f)
            }
            else -> {
                val f = (c - 0.66f) / 0.34f
                r = 1.0f; g = f * 0.85f; b = 0.76f * (1f - f * 0.6f)
            }
        }
        return Color.argb(255, (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
    }
    
    private fun evaluateTurbo(t: Float): Int {
        val c = t.coerceIn(0f, 1f)
        val r = (0.1357f + c * (4.5874f + c * (-42.2474f + c * (111.8514f + c * (-100.6763f + c * 31.5783f))))).coerceIn(0f, 1f)
        val g = (0.0914f + c * (2.1941f + c * (8.0917f + c * (-16.9230f + c * (8.6312f + c * 0.0526f))))).coerceIn(0f, 1f)
        val b = (0.1067f + c * (12.5732f + c * (-78.3521f + c * (197.9488f + c * (-183.3377f + c * 52.8568f))))).coerceIn(0f, 1f)
        return Color.argb(255, (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
    }
    
    private fun evaluateMonoGlass(t: Float): Int {
        val c = t.coerceIn(0f, 1f)
        val v = (c * 255f).toInt()
        return Color.argb(255, v, v, v)
    }
}
