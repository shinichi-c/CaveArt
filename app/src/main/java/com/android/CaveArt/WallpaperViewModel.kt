package com.android.CaveArt

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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

private const val WALLPAPER_PREFIX = "wp_"

data class Wallpaper(
    val id: String,
    val resourceId: Int,
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

    private val _isDebugMaskEnabled = mutableStateOf(false)
    val isDebugMaskEnabled: Boolean by _isDebugMaskEnabled
    fun setDebugMaskEnabled(enabled: Boolean) { 
        _isDebugMaskEnabled.value = enabled
        if(enabled) _isMagicShapeEnabled.value = false
    }
    
    private val _isMagicShapeEnabled = mutableStateOf(false)
    val isMagicShapeEnabled: Boolean by _isMagicShapeEnabled
    var currentMagicShape by mutableStateOf(MagicShape.SQUIRCLE)
    var currentBackgroundColor by mutableStateOf(AndroidColor.parseColor("#4CAF50"))
    var is3DPopEnabled by mutableStateOf(false)
    
    fun setMagicShapeEnabled(enabled: Boolean) {
        _isMagicShapeEnabled.value = enabled
        if(enabled) _isDebugMaskEnabled.value = false
    }

    fun updateMagicConfig(shape: MagicShape, color: Int) {
        currentMagicShape = shape
        currentBackgroundColor = color
    }
    
    fun toggle3DPop() { is3DPopEnabled = !is3DPopEnabled }

    private val _isFixedAlignmentEnabled = mutableStateOf(true)
    val isFixedAlignmentEnabled: Boolean by _isFixedAlignmentEnabled
    fun setFixedAlignmentEnabled(enabled: Boolean) { _isFixedAlignmentEnabled.value = enabled }

    var baseWallpapers by mutableStateOf<List<Wallpaper>>(emptyList())
    var allWallpapers by mutableStateOf<List<Wallpaper>>(emptyList())
    var allTags by mutableStateOf<List<String>>(listOf("All"))

    var selectedTag by mutableStateOf("All")
        private set

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
        currentList.forEach { wallpaper ->
            val detectedTags = mlProcessingSemaphore.withPermit {
                try {
                    val smallBitmap = BitmapHelper.decodeSampledBitmapFromResource(context.resources, wallpaper.resourceId, 500)
                    val tags = ImageLabelingHelper.getTagsFromBitmap(smallBitmap)
                    smallBitmap.recycle()
                    tags
                } catch (e: Exception) {
                    emptyList<String>()
                }
            }
            if (detectedTags.isNotEmpty()) {
                wallpaper.mlTags = detectedTags
                withContext(Dispatchers.Main) { updateTags() }
            }
        }
    }

    private fun updateTags() {
        val originalTags = baseWallpapers.map { it.tag }
        val mlKitTags = baseWallpapers.flatMap { it.mlTags }
        allTags = listOf("All") + (originalTags + mlKitTags).distinct().sorted()
    }

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8
    private val bitmapCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap) = bitmap.byteCount / 1024
    }
    
    suspend fun getOrCreateProcessedBitmap(context: Context, resourceId: Int, allowMagic: Boolean = true): Bitmap? {
        
        val useMagic = allowMagic && isMagicShapeEnabled

        val cacheKey = when {
            isDebugMaskEnabled -> "debug_$resourceId"
            useMagic -> "shape_${resourceId}_${currentMagicShape.name}_${currentBackgroundColor}_$is3DPopEnabled"
            else -> "preview_$resourceId" 
        }
        
        bitmapCache.get(cacheKey)?.let { return it }
        
        return withContext(Dispatchers.IO) {
            try {
                val originalBitmap = BitmapHelper.decodeSampledBitmapFromResource(context.resources, resourceId, 1080)
                
                val resultBitmap = when {
                    isDebugMaskEnabled -> DebugSegmentationHelper.createDebugMaskBitmap(context, originalBitmap)
                    useMagic -> ShapeEffectHelper.createShapeCropBitmap(context, originalBitmap, currentMagicShape, currentBackgroundColor, is3DPopEnabled)
                    else -> originalBitmap 
                }
                
                if (resultBitmap != originalBitmap && resultBitmap != null) {
                    originalBitmap.recycle()
                }

                if (resultBitmap != null) {
                    bitmapCache.put(cacheKey, resultBitmap)
                }
                resultBitmap
            } catch (e: Exception) { null }
        }
    }

    suspend fun generateHighQualityFinalBitmap(context: Context, resourceId: Int): Bitmap? = withContext(Dispatchers.IO) {
        var originalBitmap: Bitmap? = null
        try {
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888; inMutable = true }
            originalBitmap = BitmapFactory.decodeResource(context.resources, resourceId, options)
        } catch (e: OutOfMemoryError) {
            System.gc()
            try { originalBitmap = BitmapHelper.decodeSampledBitmapFromResource(context.resources, resourceId, 2500) } catch (e2: Exception) { return@withContext null }
        }

        if (originalBitmap == null) return@withContext null

        try {
            val processedBitmap = when {
                isDebugMaskEnabled -> DebugSegmentationHelper.createDebugMaskBitmap(context, originalBitmap)
                isMagicShapeEnabled -> ShapeEffectHelper.createShapeCropBitmap(context, originalBitmap, currentMagicShape, currentBackgroundColor, is3DPopEnabled)
                else -> originalBitmap
            }

            if (processedBitmap != originalBitmap) originalBitmap.recycle()
            return@withContext processedBitmap
        } catch (e: Exception) { return@withContext originalBitmap }
    }
    
    override fun onCleared() { super.onCleared(); bitmapCache.evictAll() }
}