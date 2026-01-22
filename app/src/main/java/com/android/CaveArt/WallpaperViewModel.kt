package com.android.CaveArt

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Color as AndroidColor
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID

private const val WALLPAPER_PREFIX = "wp_"

data class Wallpaper(
    val id: String,
    val resourceId: Int = 0,
    val uri: Uri? = null,
    val title: String,
    val tag: String,
    var mlTags: List<String> = emptyList()
)

class WallpaperViewModel(application: Application) : AndroidViewModel(application) {

    var isLoading by mutableStateOf(true)
        private set

    private val mlProcessingSemaphore = Semaphore(2)

    private val _isHapticsEnabled = mutableStateOf(true)
    val isHapticsEnabled: Boolean by _isHapticsEnabled
    fun setHapticsEnabled(enabled: Boolean) { _isHapticsEnabled.value = enabled }

    private val _isMagicShapeEnabled = mutableStateOf(false)
    val isMagicShapeEnabled: Boolean by _isMagicShapeEnabled
    
    var currentMagicShape by mutableStateOf(MagicShape.SQUIRCLE)
    var currentBackgroundColor by mutableStateOf(AndroidColor.parseColor("#4CAF50"))
    var is3DPopEnabled by mutableStateOf(true)
    var magicScale by mutableFloatStateOf(1.0f)
    
    fun setMagicShapeEnabled(enabled: Boolean) {
        _isMagicShapeEnabled.value = enabled
    }

    fun updateMagicConfig(shape: MagicShape, color: Int) {
        currentMagicShape = shape
        currentBackgroundColor = color
    }
    fun toggle3DPop() { is3DPopEnabled = !is3DPopEnabled }
    fun updateMagicScale(scale: Float) { magicScale = scale }

    private val _isFixedAlignmentEnabled = mutableStateOf(true)
    val isFixedAlignmentEnabled: Boolean by _isFixedAlignmentEnabled
    fun setFixedAlignmentEnabled(enabled: Boolean) { _isFixedAlignmentEnabled.value = enabled }

    var baseWallpapers by mutableStateOf<List<Wallpaper>>(emptyList())
    var allWallpapers by mutableStateOf<List<Wallpaper>>(emptyList())
    var allTags by mutableStateOf<List<String>>(listOf("All"))
    var selectedTag by mutableStateOf("All")
        private set

    var debugResults by mutableStateOf<List<DebugResult>>(emptyList())
    var isRunningDebug by mutableStateOf(false)

    fun selectTag(tag: String) { selectedTag = tag }

    val filteredWallpapers by derivedStateOf {
        if (selectedTag == "All") allWallpapers 
        else baseWallpapers.filter { it.tag == selectedTag || it.mlTags.contains(selectedTag) }
    }

    init {
        viewModelScope.launch {
            isLoading = true
            val initialList = loadBasicWallpaperList(application.applicationContext)
            baseWallpapers = initialList
            allWallpapers = initialList
            updateTags()
            isLoading = false
            scanWallpapersForTags(application.applicationContext)
        }
    }
    
    fun addGalleryWallpaper(uri: Uri) {
        val newWallpaper = Wallpaper(
            id = UUID.randomUUID().toString(),
            uri = uri,
            title = "Gallery Image",
            tag = "Gallery"
        )
        baseWallpapers = listOf(newWallpaper) + baseWallpapers
        allWallpapers = baseWallpapers
        updateTags()
        selectTag("All")
        viewModelScope.launch { scanSingleWallpaper(getApplication(), newWallpaper) }
    }

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8
    
    private val bitmapCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap) = bitmap.byteCount / 1024
    }
    
    private var cachedCutout: Pair<String, Bitmap>? = null

    suspend fun getOrCreateProcessedBitmap(context: Context, wallpaper: Wallpaper, allowMagic: Boolean = true): Bitmap? {
        val useMagic = allowMagic && isMagicShapeEnabled
        val cacheKey = when {
            useMagic -> "shape_${wallpaper.id}_${currentMagicShape.name}_${currentBackgroundColor}_${is3DPopEnabled}_$magicScale"
            else -> "preview_${wallpaper.id}" 
        }
        
        bitmapCache.get(cacheKey)?.let { return it }
        
        return withContext(Dispatchers.IO) {
            try {
                
                val originalBitmap = if (wallpaper.uri != null) {
                    BitmapHelper.decodeSampledBitmapFromUri(context, wallpaper.uri, 1080)
                } else {
                    BitmapHelper.decodeSampledBitmapFromResource(context.resources, wallpaper.resourceId, 1080)
                }
                
                var cutout: Bitmap? = null
                
                if (useMagic) {
                    
                    if (cachedCutout?.first == wallpaper.id && cachedCutout?.second != null) {
                        cutout = cachedCutout!!.second
                        
                        if (cutout!!.width != originalBitmap.width || cutout!!.height != originalBitmap.height) {
                             cutout = Bitmap.createScaledBitmap(cutout!!, originalBitmap.width, originalBitmap.height, true)
                        }
                    } else {
                        
                        cutout = generateCutout(context, originalBitmap)
                        
                        if (cutout != null) {
                            cachedCutout = Pair(wallpaper.id, cutout)
                        }
                    }
                }

                
                val resultBitmap = composeFinalImage(context, originalBitmap, cutout, useMagic)
                
                if (resultBitmap != originalBitmap && resultBitmap != null) {
                    originalBitmap.recycle()
                }

                if (resultBitmap != null) {
                    bitmapCache.put(cacheKey, resultBitmap)
                }
                resultBitmap
            } catch (e: Exception) { 
                e.printStackTrace()
                null 
            }
        }
    }

    suspend fun generateHighQualityFinalBitmap(context: Context, wallpaper: Wallpaper): Bitmap? = withContext(Dispatchers.IO) {
        var originalBitmap: Bitmap? = null
        try {
            
            if (wallpaper.uri != null) {
                originalBitmap = BitmapHelper.decodeSampledBitmapFromUri(context, wallpaper.uri, 2500)
            } else {
                val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888; inMutable = true }
                originalBitmap = BitmapFactory.decodeResource(context.resources, wallpaper.resourceId, options)
            }
        } catch (e: OutOfMemoryError) {
            System.gc()
            try { 
                if (wallpaper.uri != null) {
                     originalBitmap = BitmapHelper.decodeSampledBitmapFromUri(context, wallpaper.uri, 1500)
                } else {
                    originalBitmap = BitmapHelper.decodeSampledBitmapFromResource(context.resources, wallpaper.resourceId, 2500) 
                }
            } catch (e2: Exception) { return@withContext null }
        } catch (e: Exception) { return@withContext null }

        if (originalBitmap == null) return@withContext null

        try {
            
            val useMagic = isMagicShapeEnabled
            
            val cutout = if(useMagic) generateCutout(context, originalBitmap) else null
            
            val processedBitmap = composeFinalImage(context, originalBitmap, cutout, useMagic)

            if (processedBitmap != originalBitmap) originalBitmap.recycle()
            
            if (cutout != null && cutout != originalBitmap) cutout.recycle()
            
            return@withContext processedBitmap
        } catch (e: Exception) { return@withContext originalBitmap }
    }
    
    private suspend fun generateCutout(context: Context, original: Bitmap): Bitmap? {
        val coarseMask = PixelLabHelper.generateCoarseMask(context, original) ?: return null
        val refinedMask = ForegroundEstimationHelper.refineMask(context, original, coarseMask) ?: coarseMask
        val mattingResult = DeepMattingHelper.runDeepMatting(context, original, refinedMask)
        
        return if (mattingResult != null && !isBitmapEmpty(mattingResult)) {
            applyHighQualityCutout(original, mattingResult, isAlphaMask = true)
        } else {
            
            applyHighQualityCutout(original, refinedMask, isAlphaMask = false)
        }
    }
    
    private fun composeFinalImage(
        context: Context, 
        original: Bitmap, 
        cutout: Bitmap?, 
        useMagic: Boolean
    ): Bitmap {
        return if (useMagic && cutout != null) {
            ShapeEffectHelper.createShapeCropBitmapWithPreCutout(
                context, original, cutout, 
                currentMagicShape, currentBackgroundColor, is3DPopEnabled, magicScale
            )
        } else {
            original
        }
    }

    private suspend fun loadBasicWallpaperList(context: Context): List<Wallpaper> = withContext(Dispatchers.IO) {
        val drawableFields = R.drawable::class.java.fields
        val list = mutableListOf<Wallpaper>()
        drawableFields.filter { it.name.startsWith(WALLPAPER_PREFIX) }.forEach { field ->
            try {
                val resId = field.getInt(null)
                val rawName = field.name
                val parts = rawName.substring(WALLPAPER_PREFIX.length).split("_")
                if (parts.size >= 2) {
                    val tag = parts.last().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    val title = parts.subList(0, parts.size - 1).joinToString(" ") { word ->
                        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    }
                    list.add(Wallpaper(id = rawName, resourceId = resId, title = title, tag = tag))
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
        list.sortedBy { it.title }
    }

    private suspend fun scanWallpapersForTags(context: Context) = withContext(Dispatchers.IO) {
        val currentList = baseWallpapers.toList()
        currentList.forEach { wallpaper -> scanSingleWallpaper(context, wallpaper) }
    }
    
    private suspend fun scanSingleWallpaper(context: Context, wallpaper: Wallpaper) {
        if (wallpaper.mlTags.isNotEmpty()) return
        val detectedTags = mlProcessingSemaphore.withPermit {
            try {
                val smallBitmap = if (wallpaper.uri != null) {
                    BitmapHelper.decodeSampledBitmapFromUri(context, wallpaper.uri, 500)
                } else {
                    BitmapHelper.decodeSampledBitmapFromResource(context.resources, wallpaper.resourceId, 500)
                }
                val tags = ImageLabelingHelper.getTagsFromBitmap(smallBitmap)
                smallBitmap.recycle()
                tags
            } catch (e: Exception) { emptyList<String>() }
        }
        if (detectedTags.isNotEmpty()) {
            wallpaper.mlTags = detectedTags
            withContext(Dispatchers.Main) { updateTags() }
        }
    }

    private fun updateTags() {
        val originalTags = baseWallpapers.map { it.tag }
        val mlKitTags = baseWallpapers.flatMap { it.mlTags }
        allTags = listOf("All") + (originalTags + mlKitTags).distinct().sorted()
    }

    private fun isBitmapEmpty(bitmap: Bitmap?): Boolean {
        if (bitmap == null) return true
        val w = bitmap.width
        val h = bitmap.height
        val pointsToCheck = listOf(
            android.graphics.Point(w / 2, h / 2),
            android.graphics.Point(w / 3, h / 3),
            android.graphics.Point(w - 10, h / 2),
            android.graphics.Point(w / 2, h - 10)
        )
        var totalAlpha = 0
        try {
            for (p in pointsToCheck) {
                if (p.x < w && p.y < h) {
                    totalAlpha += AndroidColor.alpha(bitmap.getPixel(p.x, p.y))
                }
            }
        } catch (e: Exception) { return true }
        return totalAlpha < 10
    }
    
    private fun applyHighQualityCutout(original: Bitmap, maskOrCutout: Bitmap, isAlphaMask: Boolean): Bitmap {
        val w = original.width
        val h = original.height
        val upscaledMask = Bitmap.createScaledBitmap(maskOrCutout, w, h, true)

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val origPixels = IntArray(w * h)
        val maskPixels = IntArray(w * h)
        val resultPixels = IntArray(w * h)

        original.getPixels(origPixels, 0, w, 0, 0, w, h)
        upscaledMask.getPixels(maskPixels, 0, w, 0, 0, w, h)

        for (i in origPixels.indices) {
            val alpha = if (isAlphaMask) {
                
                (maskPixels[i] shr 24) and 0xFF
            } else {
                
                (maskPixels[i] shr 16) and 0xFF
            }
            
            val r = (origPixels[i] shr 16) and 0xFF
            val g = (origPixels[i] shr 8) and 0xFF
            val b = origPixels[i] and 0xFF
            
            resultPixels[i] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
        }

        result.setPixels(resultPixels, 0, w, 0, 0, w, h)
        
        if (upscaledMask != maskOrCutout) {
            upscaledMask.recycle()
        }

        return result
    }

    fun runModelDiagnostics(context: Context, targetWallpaper: Wallpaper?) {
        if (targetWallpaper == null) return
        viewModelScope.launch(Dispatchers.IO) {
            isRunningDebug = true
            try {
                val original = if (targetWallpaper.uri != null) {
                    BitmapHelper.decodeSampledBitmapFromUri(context, targetWallpaper.uri, 512)
                } else {
                    BitmapFactory.decodeResource(context.resources, targetWallpaper.resourceId)
                }
                if (original != null) {
                    val res = PixelDebugHelper.runFullPipelineDiagnostic(context, original)
                    withContext(Dispatchers.Main) { 
                        debugResults = res
                        isRunningDebug = false 
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { isRunningDebug = false }
            }
        }
    }

    override fun onCleared() { 
        super.onCleared()
        bitmapCache.evictAll()
        cachedCutout?.second?.recycle()
        cachedCutout = null
    }
}