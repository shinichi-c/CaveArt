package com.android.CaveArt

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import com.materialkolor.hct.Hct
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score

data class DynamicThemePalette(
    val seedColor: Int,
    val tonalSpot: List<Int>,
    val vibrant: List<Int>,
    val expressive: List<Int>,
    val neutral: List<Int>,
    val allColors: List<Int>
)

object MonetEngine {
    private val paletteCache = LruCache<String, DynamicThemePalette>(120)

    fun getDefaultPalette(): List<Int> {
        return listOf(
            0xFF4A80D4.toInt(), 0xFF7090B0.toInt(), 0xFF305080.toInt(),
            0xFF1A1C1E.toInt(), 0xFFF2F4F7.toInt()
        )
    }

    /**
     * Synchronous cache hit check to prevent 1-frame latency or stale colors when switching wallpapers.
     */
    fun getCachedPalette(wallpaperId: String, isDarkTheme: Boolean = true): List<Int>? {
        val cacheKey = "${wallpaperId}_dark=$isDarkTheme"
        return paletteCache.get(cacheKey)?.allColors
    }

    /**
     * Extracts an Android 17-grade Material You dynamic theme from a wallpaper image.
     */
    fun getThemePalette(context: Context, wallpaper: Wallpaper, isDarkTheme: Boolean = true): DynamicThemePalette {
        val cacheKey = "${wallpaper.id}_dark=$isDarkTheme"
        paletteCache.get(cacheKey)?.let { return it }

        val thumb = try {
            if (wallpaper.uri != null) {
                BitmapHelper.decodeSampledBitmapFromUri(context, wallpaper.uri, 112)
            } else if (wallpaper.resourceId != 0) {
                BitmapHelper.decodeSampledBitmapFromResource(context.resources, wallpaper.resourceId, 112)
            } else null
        } catch (e: Exception) {
            null
        }

        val palette = if (thumb != null) {
            extractPaletteFromBitmap(thumb, isDarkTheme).also { thumb.recycle() }
        } else {
            generatePaletteFromSeed(0xFF4A80D4.toInt(), isDarkTheme)
        }

        paletteCache.put(cacheKey, palette)
        return palette
    }

    fun getPalette(context: Context, wallpaper: Wallpaper): List<Int> {
        return getThemePalette(context, wallpaper).allColors
    }

    fun extractPaletteFromBitmap(bitmap: Bitmap, isDarkTheme: Boolean): DynamicThemePalette {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val quantized = QuantizerCelebi.quantize(pixels, 128)
        val ranked = Score.score(quantized).distinct()
        val seed = ranked.firstOrNull() ?: 0xFF4A80D4.toInt()

        return generatePaletteFromSeed(seed, isDarkTheme)
    }

    fun generatePaletteFromSeed(seedColorInt: Int, isDarkTheme: Boolean): DynamicThemePalette {
        val hct = Hct.fromInt(seedColorInt)
        val hue = hct.hue
        val chroma = hct.chroma

        val primaryTone = if (isDarkTheme) 80.0 else 40.0
        val secondaryTone = if (isDarkTheme) 70.0 else 50.0
        val neutralTone = if (isDarkTheme) 90.0 else 25.0
        
        val tonalSpot = listOf(
            Hct.from(hue, chroma.coerceAtLeast(36.0), primaryTone).toInt(),
            Hct.from(hue, 16.0, secondaryTone).toInt(),
            Hct.from(hue + 60.0, 24.0, primaryTone).toInt()
        )
        
        val vibrant = listOf(
            Hct.from(hue, chroma.coerceAtLeast(54.0), primaryTone).toInt(),
            Hct.from(hue + 20.0, 40.0, secondaryTone).toInt(),
            Hct.from(hue + 180.0, chroma.coerceAtLeast(48.0), primaryTone).toInt()
        )
        
        val expressive = listOf(
            Hct.from(hue + 120.0, 32.0, primaryTone).toInt(),
            Hct.from(hue + 240.0, 28.0, secondaryTone).toInt(),
            Hct.from(hue + 45.0, 36.0, primaryTone).toInt()
        )
        
        val neutral = listOf(
            Hct.from(hue, 6.0, neutralTone).toInt(),
            Hct.from(hue, 12.0, secondaryTone).toInt()
        )

        val combined = (listOf(seedColorInt) + tonalSpot + vibrant + expressive + neutral).distinct()

        return DynamicThemePalette(
            seedColor = seedColorInt,
            tonalSpot = tonalSpot,
            vibrant = vibrant,
            expressive = expressive,
            neutral = neutral,
            allColors = combined
        )
    }
}
