package com.android.CaveArt

import android.content.Context
import android.util.LruCache
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score

object MonetEngine {
    private val paletteCache = LruCache<String, List<Int>>(120)

    fun getDefaultPalette(): List<Int> {
        return listOf(
            0xFF4A80D4.toInt(), 0xFF7090B0.toInt(), 0xFF305080.toInt(),
            0xFF1A1C1E.toInt(), 0xFFF2F4F7.toInt()
        )
    }

    /**
     * Fast Algorithmic Color Extraction:
     * Downscales to a 36x36 pixel grid (< 2ms) and ranks the dominant vibrant colors.
     */
    fun getPalette(context: Context, wallpaper: Wallpaper): List<Int> {
        val cacheKey = wallpaper.id
        paletteCache.get(cacheKey)?.let { return it }

        val thumb = try {
            if (wallpaper.uri != null) {
                BitmapHelper.decodeSampledBitmapFromUri(context, wallpaper.uri, 36)
            } else if (wallpaper.resourceId != 0) {
                BitmapHelper.decodeSampledBitmapFromResource(context.resources, wallpaper.resourceId, 36)
            } else null
        } catch (e: Exception) {
            null
        }

        val palette = if (thumb != null) {
            val w = thumb.width
            val h = thumb.height
            val pixels = IntArray(w * h)
            thumb.getPixels(pixels, 0, w, 0, 0, w, h)
            thumb.recycle()

            val quantized = QuantizerCelebi.quantize(pixels, 128)
            val ranked = Score.score(quantized).distinct()

            val colors = ranked.take(5).toMutableList()
            while (colors.size < 5) {
                colors.add(colors.firstOrNull() ?: 0xFF4A80D4.toInt())
            }
            colors
        } else {
            getDefaultPalette()
        }

        paletteCache.put(cacheKey, palette)
        return palette
    }
}
