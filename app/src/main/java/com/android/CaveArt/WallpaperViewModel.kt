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
import com.android.CaveArt.DebugSegmentationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Color as AndroidColor

private const val WALLPAPER_PREFIX = "wp_"

data class Wallpaper(
    val id: String,
    val resourceId: Int,
    val title: String,
    val tag: String,
    val mlTags: List<String> = emptyList()
)

class WallpaperViewModel(application: Application) : AndroidViewModel(application) {

    var isLoading by mutableStateOf(true)
        private set

    private suspend fun loadWallpapersDynamically(context: Context): List<Wallpaper> {
        val drawableFields = R.drawable::class.java.fields
        val wallpaperList = mutableListOf<Wallpaper>()
        withContext(Dispatchers.IO) {
            drawableFields.filter { it.name.startsWith(WALLPAPER_PREFIX) }.forEach { field ->
                try {
                    val resourceId = field.getInt(null)
                    val rawName = field.name
                    val parts = rawName.substring(WALLPAPER_PREFIX.length).split("_")
                    if (parts.size >= 2) {
                        val tag = parts.last().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                        val title = parts.subList(0, parts.size - 1).joinToString(" ") { word ->
                                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                            }
                        val bitmap = BitmapFactory.decodeResource(context.resources, resourceId)
                        val mlTags = ImageLabelingHelper.getTagsFromBitmap(bitmap)
                        bitmap.recycle()
                        wallpaperList.add(Wallpaper(id = rawName, resourceId = resourceId, title = title, tag = tag, mlTags = mlTags))
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
        return wallpaperList.sortedBy { it.title }
    }

    var baseWallpapers by mutableStateOf<List<Wallpaper>>(emptyList())
    var allWallpapers by mutableStateOf<List<Wallpaper>>(emptyList())
    var allTags by mutableStateOf<List<String>>(listOf("All"))

    init {
        viewModelScope.launch {
            isLoading = true
            val loadedWallpapers = loadWallpapersDynamically(application.applicationContext)
            baseWallpapers = loadedWallpapers
            allWallpapers = loadedWallpapers
            val originalTags = baseWallpapers.map { it.tag }
            val mlKitTags = baseWallpapers.flatMap { it.mlTags }
            allTags = listOf("All") + (originalTags + mlKitTags).distinct().sorted()
            isLoading = false
        }
    }

    var selectedTag by mutableStateOf("All")
        private set
        
    private val _isHapticsEnabled = mutableStateOf(true)
    val isHapticsEnabled: Boolean by _isHapticsEnabled
    fun setHapticsEnabled(enabled: Boolean) { _isHapticsEnabled.value = enabled }

    private val _isDebugMaskEnabled = mutableStateOf(false)
    val isDebugMaskEnabled: Boolean by _isDebugMaskEnabled
    fun setDebugMaskEnabled(enabled: Boolean) { 
        _isDebugMaskEnabled.value = enabled
        if(enabled) _isMagicShapeEnabled.value = false
        clearBitmapCache() 
    }
    
    private val _isMagicShapeEnabled = mutableStateOf(false)
    val isMagicShapeEnabled: Boolean by _isMagicShapeEnabled
    
    var currentMagicShape by mutableStateOf(MagicShape.SQUIRCLE)
    var currentBackgroundColor by mutableStateOf(AndroidColor.parseColor("#4CAF50"))
    
    var is3DPopEnabled by mutableStateOf(false)

    fun setMagicShapeEnabled(enabled: Boolean) {
        _isMagicShapeEnabled.value = enabled
        if(enabled) {
            _isDebugMaskEnabled.value = false
        }
        clearBitmapCache()
    }

    fun updateMagicConfig(shape: MagicShape, color: Int) {
        if (currentMagicShape != shape || currentBackgroundColor != color) {
            currentMagicShape = shape
            currentBackgroundColor = color
            clearBitmapCache()
        }
    }
    
    fun toggle3DPop() {
        is3DPopEnabled = !is3DPopEnabled
        clearBitmapCache()
    }

    private val _isFixedAlignmentEnabled = mutableStateOf(true)
    val isFixedAlignmentEnabled: Boolean by _isFixedAlignmentEnabled
    fun setFixedAlignmentEnabled(enabled: Boolean) { _isFixedAlignmentEnabled.value = enabled }

    val filteredWallpapers by derivedStateOf {
        if (selectedTag == "All") allWallpapers else baseWallpapers.filter { it.tag == selectedTag || it.mlTags.contains(selectedTag) }
    }

    fun selectTag(tag: String) { selectedTag = tag }
    
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8
    private val bitmapCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap) = bitmap.byteCount / 1024
    }
    
    private fun clearBitmapCache() { bitmapCache.evictAll() }
    private fun getCachedBitmap(key: String): Bitmap? = bitmapCache.get(key)
    private fun cacheBitmap(key: String, bitmap: Bitmap) { if (getCachedBitmap(key) == null) bitmapCache.put(key, bitmap) }
    
    suspend fun getOrCreateProcessedBitmap(context: Context, resourceId: Int): Bitmap? {
        if (!isDebugMaskEnabled && !isMagicShapeEnabled) {
            return null
        }

        val cacheKey = when {
            isDebugMaskEnabled -> "debug_$resourceId"
            isMagicShapeEnabled -> "shape_${resourceId}_${currentMagicShape.name}_${currentBackgroundColor}_$is3DPopEnabled"
            else -> return null
        }
        
        getCachedBitmap(cacheKey)?.let { return it }
        
        return withContext(Dispatchers.IO) {
            try {
                val originalBitmap = BitmapFactory.decodeResource(context.resources, resourceId)
                val processedBitmap = when {
                    isDebugMaskEnabled -> DebugSegmentationHelper.createDebugMaskBitmap(context, originalBitmap)
                    isMagicShapeEnabled -> ShapeEffectHelper.createShapeCropBitmap(
                        context, 
                        originalBitmap, 
                        currentMagicShape, 
                        currentBackgroundColor,
                        is3DPopEnabled 
                    )
                    else -> null
                }
                originalBitmap.recycle()
                if (processedBitmap != null) {
                    cacheBitmap(cacheKey, processedBitmap)
                }
                processedBitmap
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
