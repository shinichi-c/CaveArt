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

    private val segmentationHelper = SegmentationHelper(application)
    private val refineHelper = ForegroundEstimationHelper(application)
    private val mattingHelper = DeepMattingHelper(application)

    var isLoading by mutableStateOf(true)
        private set

    private val mlProcessingSemaphore = Semaphore(2)
    
    private val _isHapticsEnabled = mutableStateOf(true)
    val isHapticsEnabled: Boolean by _isHapticsEnabled
    fun setHapticsEnabled(enabled: Boolean) { _isHapticsEnabled.value = enabled }

    private val _isFixedAlignmentEnabled = mutableStateOf(true)
    val isFixedAlignmentEnabled: Boolean by _isFixedAlignmentEnabled
    fun setFixedAlignmentEnabled(enabled: Boolean) { _isFixedAlignmentEnabled.value = enabled }
    
    private val _isMagicShapeEnabled = mutableStateOf(false)
    val isMagicShapeEnabled: Boolean by _isMagicShapeEnabled
    
    var currentMagicShape by mutableStateOf(MagicShape.SQUIRCLE)
    var currentBackgroundColor by mutableStateOf(AndroidColor.parseColor("#4CAF50"))
    var is3DPopEnabled by mutableStateOf(true)
    var magicScale by mutableFloatStateOf(1.0f)
    var isCentered by mutableStateOf(false)

    fun setMagicShapeEnabled(enabled: Boolean) { _isMagicShapeEnabled.value = enabled }
    fun updateMagicConfig(shape: MagicShape, color: Int) {
        currentMagicShape = shape
        currentBackgroundColor = color
    }
    fun toggle3DPop() { is3DPopEnabled = !is3DPopEnabled }
    fun updateMagicScale(scale: Float) { magicScale = scale }
    fun toggleCentered() { isCentered = !isCentered }
    
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
    
    private val cutoutCache = object : LruCache<String, Bitmap>(cacheSize / 4) {
        override fun sizeOf(key: String, bitmap: Bitmap) = bitmap.byteCount / 1024
    }
    
    private suspend fun generateCutoutInternal(originalBitmap: Bitmap): Bitmap? {
        val coarse = segmentationHelper.generateSoftMask(originalBitmap) ?: return null
        val refined = refineHelper.refineMask(originalBitmap, coarse)
        var finalCutout = mattingHelper.run(originalBitmap, refined ?: coarse) ?: coarse
        
        if (refined != null && refined != coarse) refined.recycle()
        if (coarse != finalCutout) coarse.recycle()

        if (finalCutout.width != originalBitmap.width || finalCutout.height != originalBitmap.height) {
            val scaled = Bitmap.createScaledBitmap(finalCutout, originalBitmap.width, originalBitmap.height, true)
            if (scaled != finalCutout) finalCutout.recycle()
            finalCutout = scaled
        }
        return finalCutout
    }
    
    suspend fun getOrCreateProcessedBitmap(context: Context, wallpaper: Wallpaper, allowMagic: Boolean = true): Bitmap? {
        val useMagic = allowMagic && isMagicShapeEnabled
        
        val cacheKey = if(useMagic) {
            "final_${wallpaper.id}_${currentMagicShape}_${currentBackgroundColor}_${is3DPopEnabled}_${magicScale}_${isCentered}"
        } else {
            "preview_${wallpaper.id}"
        }
        
        bitmapCache.get(cacheKey)?.let { return it }
        
        return withContext(Dispatchers.Default) {
            try {
                val originalBitmap = if (wallpaper.uri != null) {
                    BitmapHelper.decodeSampledBitmapFromUri(context, wallpaper.uri, 1024)
                } else {
                    BitmapHelper.decodeSampledBitmapFromResource(context.resources, wallpaper.resourceId, 1024)
                }
                
                var cutout: Bitmap? = null
                if (useMagic) {
                    cutout = cutoutCache.get(wallpaper.id) ?: generateCutoutInternal(originalBitmap)?.also { 
                        cutoutCache.put(wallpaper.id, it) 
                    }
                }

                val resultBitmap = composeFinalImage(context, originalBitmap, cutout, useMagic)
                if (resultBitmap != null) bitmapCache.put(cacheKey, resultBitmap)
                resultBitmap
            } catch (e: Exception) { null }
        }
    }

    /**
     * Static High-Res Rendering.
     */
    suspend fun generateHighQualityFinalBitmap(context: Context, wallpaper: Wallpaper): Bitmap? = withContext(Dispatchers.IO) {
        var originalBitmap: Bitmap? = null
        try {
            originalBitmap = if (wallpaper.uri != null) {
                BitmapHelper.decodeSampledBitmapFromUri(context, wallpaper.uri, 2500)
            } else {
                BitmapFactory.decodeResource(context.resources, wallpaper.resourceId, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
            }
        } catch (e: Exception) { return@withContext null }

        if (originalBitmap == null) return@withContext null

        val cutout = if (isMagicShapeEnabled) generateCutoutInternal(originalBitmap) else null
        val processed = composeFinalImage(context, originalBitmap, cutout, isMagicShapeEnabled)

        if (processed != originalBitmap) originalBitmap.recycle()
        cutout?.recycle()
        
        return@withContext processed
    }

    /**
     * Live Wallpaper Components Export.
     */
    suspend fun getHighQualityComponents(context: Context, wallpaper: Wallpaper): Pair<Bitmap?, Bitmap?> = withContext(Dispatchers.IO) {
        val original = if (wallpaper.uri != null) {
            BitmapHelper.decodeSampledBitmapFromUri(context, wallpaper.uri, 2500)
        } else {
            BitmapHelper.decodeSampledBitmapFromResource(context.resources, wallpaper.resourceId, 2500)
        }
        val cutout = if (isMagicShapeEnabled && original != null) generateCutoutInternal(original) else null
        return@withContext Pair(original, cutout)
    }
    
    private fun composeFinalImage(context: Context, original: Bitmap, cutout: Bitmap?, useMagic: Boolean): Bitmap {
        return if (useMagic && cutout != null) {
            val config = LiveWallpaperConfig(
                shapeName = currentMagicShape.name,
                backgroundColor = currentBackgroundColor,
                is3DPopEnabled = is3DPopEnabled,
                scale = magicScale,
                isCentered = isCentered
            )
            
            ShapeEffectHelper.createShapeCropBitmapWithPreCutout(original, cutout, config)
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
                    val tag = parts.last().replaceFirstChar { it.uppercase() }
                    val title = parts.subList(0, parts.size - 1).joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                    list.add(Wallpaper(id = rawName, resourceId = resId, title = title, tag = tag))
                }
            } catch (e: Exception) { }
        }
        list.sortedBy { it.title }
    }

    private suspend fun scanWallpapersForTags(context: Context) = withContext(Dispatchers.IO) {
        baseWallpapers.toList().forEach { scanSingleWallpaper(context, it) }
    }
    
    private suspend fun scanSingleWallpaper(context: Context, wallpaper: Wallpaper) {
        if (wallpaper.mlTags.isNotEmpty()) return
        val detectedTags = mlProcessingSemaphore.withPermit {
            try {
                val small = if (wallpaper.uri != null) BitmapHelper.decodeSampledBitmapFromUri(context, wallpaper.uri, 300)
                else BitmapHelper.decodeSampledBitmapFromResource(context.resources, wallpaper.resourceId, 300)
                val tags = ImageLabelingHelper.getTagsFromBitmap(small)
                small.recycle()
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

    fun runModelDiagnostics(context: Context, target: Wallpaper?) {
        if (target == null) return
        viewModelScope.launch(Dispatchers.IO) {
            isRunningDebug = true
            try {
                val original = if (target.uri != null) BitmapHelper.decodeSampledBitmapFromUri(context, target.uri, 512)
                else BitmapFactory.decodeResource(context.resources, target.resourceId)
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
        segmentationHelper.close()
        refineHelper.close()
        mattingHelper.close()
    }
}