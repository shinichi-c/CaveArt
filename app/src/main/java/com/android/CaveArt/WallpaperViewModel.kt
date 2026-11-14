package com.android.CaveArt

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.CaveArt.animations.AnimationFactory
import com.android.CaveArt.animations.AnimationStyle
import com.android.CaveArt.animations.AnimSetting
import com.android.CaveArt.clockkit.ClockKitConfig
import com.android.CaveArt.clockkit.ClockKitConfigStore
import com.android.CaveArt.layer.LockscreenLayerStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
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
    private val depthHelper = SpatialDepthHelper(application)

    private val safeContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        application.createDeviceProtectedStorageContext()
    } else {
        application
    }

    private val prefs = safeContext.getSharedPreferences("cave_art_settings", Context.MODE_PRIVATE)
    private val clockPrefs = safeContext.getSharedPreferences("cave_art_clock_prefs", Context.MODE_PRIVATE)

    var isLoading by mutableStateOf(true)
        private set

    private val mlProcessingSemaphore = Semaphore(2)

    private val _showFastScrollGuide = mutableStateOf(!prefs.getBoolean("has_seen_fast_scroll_guide", false))
    val showFastScrollGuide: Boolean by _showFastScrollGuide
    fun dismissFastScrollGuide() {
        if (_showFastScrollGuide.value) {
            _showFastScrollGuide.value = false
            prefs.edit().putBoolean("has_seen_fast_scroll_guide", true).apply()
        }
    }

    private val _isHapticsEnabled = mutableStateOf(prefs.getBoolean("haptics_enabled", true))
    val isHapticsEnabled: Boolean by _isHapticsEnabled
    fun setHapticsEnabled(enabled: Boolean) {
        _isHapticsEnabled.value = enabled
        prefs.edit().putBoolean("haptics_enabled", enabled).apply()
    }

    private val _isFixedAlignmentEnabled = mutableStateOf(prefs.getBoolean("fixed_alignment", true))
    val isFixedAlignmentEnabled: Boolean by _isFixedAlignmentEnabled
    fun setFixedAlignmentEnabled(enabled: Boolean) {
        _isFixedAlignmentEnabled.value = enabled
        prefs.edit().putBoolean("fixed_alignment", enabled).apply()
    }

    private val _isAmbientBlurEnabled = mutableStateOf(prefs.getBoolean("ambient_blur", true))
    val isAmbientBlurEnabled: Boolean by _isAmbientBlurEnabled
    fun setAmbientBlurEnabled(enabled: Boolean) {
        _isAmbientBlurEnabled.value = enabled
        prefs.edit().putBoolean("ambient_blur", enabled).apply()
    }

    private val _isFloatingDockEnabled = mutableStateOf(prefs.getBoolean("floating_dock", true))
    val isFloatingDockEnabled: Boolean by _isFloatingDockEnabled
    fun setFloatingDockEnabled(enabled: Boolean) {
        _isFloatingDockEnabled.value = enabled
        prefs.edit().putBoolean("floating_dock", enabled).apply()
    }

    var scrollStyle by mutableIntStateOf(clockPrefs.getInt("scroll_style", 0))
    fun updateScrollStyle(style: Int) {
        scrollStyle = style
        clockPrefs.edit().putInt("scroll_style", style).apply()
    }

    var isLockscreenClockPreviewVisible by mutableStateOf(false)
    
    var clockKitConfig by mutableStateOf(ClockKitConfigStore.loadFromPrefs(clockPrefs))

    var lockscreenClockOffsetX by mutableFloatStateOf(clockKitConfig.clockX)
    var lockscreenClockOffsetY by mutableFloatStateOf(clockKitConfig.clockY)

    var lockscreenDateOffsetX by mutableFloatStateOf(clockKitConfig.dateX)
    var lockscreenDateOffsetY by mutableFloatStateOf(clockKitConfig.dateY)

    var clockHourSize by mutableFloatStateOf(clockKitConfig.getFloat("adaptive_vector", "hour_size", 100f))
    var clockMinuteSize by mutableFloatStateOf(clockKitConfig.getFloat("adaptive_vector", "min_size", 75f))
    var clockStrokeWidth by mutableFloatStateOf(clockKitConfig.getFloat("adaptive_vector", "stroke_width", 8f))
    var clockRoundness by mutableFloatStateOf(clockKitConfig.getFloat("adaptive_vector", "roundness", 30f))

    var clockColor by mutableIntStateOf(clockKitConfig.color)
    var clockFont by mutableStateOf(clockKitConfig.font)
    var isDualToneEnabled by mutableStateOf(clockKitConfig.getBool("adaptive_vector", "dual_tone", true))
    var clockLayout by mutableIntStateOf(if (clockKitConfig.getString("adaptive_vector", "layout", "horizontal") == "vertical") 1 else 0)

    var dateLayout by mutableIntStateOf(clockKitConfig.dateFormat)
    var isDateAttached by mutableStateOf(clockKitConfig.dateAttached)

    var isClockStretchEnabled by mutableStateOf(clockKitConfig.getBool("adaptive_vector", "stretch", false))
    var clockCollisionMap by mutableStateOf(clockPrefs.getString("clock_collision_map", "") ?: "")

    var isClockDepthEnabled by mutableStateOf(clockKitConfig.isDepthEnabled)
    var clockLayerOrder by mutableIntStateOf(if (clockKitConfig.isDepthEnabled) 1 else 0)
    var clockDepthOcclusion by mutableFloatStateOf(clockKitConfig.depthOcclusionLimit)

    var isClockGravityEnabled by mutableStateOf(clockKitConfig.isGravityEnabled)
    var clockGravityIntensity by mutableFloatStateOf(clockKitConfig.gravityIntensity)

    var layerBackgroundStructure by mutableFloatStateOf(clockPrefs.getFloat("layer_structure", 0f))
    var layerSubjectOpacity by mutableFloatStateOf(clockPrefs.getFloat("layer_subject_opacity", 1.0f))
    var layerBackgroundOpacity by mutableFloatStateOf(clockPrefs.getFloat("layer_bg_opacity", 1.0f))

    private val wallpaperColorMap = mutableMapOf<String, Int>()

    fun getColorForWallpaper(wallpaperId: String): Int? = wallpaperColorMap[wallpaperId]

    fun saveColorForWallpaper(wallpaperId: String, color: Int) {
        wallpaperColorMap[wallpaperId] = color
        currentBackgroundColor = color
    }

    fun getAvailableFonts(context: Context): List<String> {
        return try {
            val fontFiles = context.assets.list("fonts")?.filter { it.endsWith(".ttf") || it.endsWith(".otf") } ?: emptyList()
            listOf("default") + fontFiles
        } catch (_: Exception) {
            listOf("default")
        }
    }

    fun updateClockKitConfig(
        context: Context,
        newConfig: ClockKitConfig,
        collisionMap: String? = null
    ) {
        clockKitConfig = newConfig

        lockscreenClockOffsetX = newConfig.clockX
        lockscreenClockOffsetY = newConfig.clockY
        lockscreenDateOffsetX = newConfig.dateX
        lockscreenDateOffsetY = newConfig.dateY
        clockColor = newConfig.color
        clockFont = newConfig.font
        dateLayout = newConfig.dateFormat
        isDateAttached = newConfig.dateAttached
        isClockDepthEnabled = newConfig.isDepthEnabled
        clockLayerOrder = if (newConfig.isDepthEnabled) 1 else 0
        clockDepthOcclusion = newConfig.depthOcclusionLimit
        isClockGravityEnabled = newConfig.isGravityEnabled
        clockGravityIntensity = newConfig.gravityIntensity

        clockHourSize = newConfig.getFloat("adaptive_vector", "hour_size", clockHourSize)
        clockMinuteSize = newConfig.getFloat("adaptive_vector", "min_size", clockMinuteSize)
        clockStrokeWidth = newConfig.getFloat("adaptive_vector", "stroke_width", clockStrokeWidth)
        clockRoundness = newConfig.getFloat("adaptive_vector", "roundness", clockRoundness)
        isDualToneEnabled = newConfig.getBool("adaptive_vector", "dual_tone", isDualToneEnabled)
        clockLayout = if (newConfig.getString("adaptive_vector", "layout", "horizontal") == "vertical") 1 else 0
        isClockStretchEnabled = newConfig.getBool("adaptive_vector", "stretch", isClockStretchEnabled)

        if (collisionMap != null) {
            clockCollisionMap = collisionMap
        }

        ClockKitConfigStore.saveAndBroadcast(context, newConfig, collisionMap)
    }

    fun updateClockFont(context: Context, fontFilename: String) {
        updateClockKitConfig(context, clockKitConfig.copy(font = fontFilename))
    }

    fun updateDualTone(context: Context, enabled: Boolean) {
        updateClockKitConfig(context, clockKitConfig.withBool("adaptive_vector", "dual_tone", enabled))
    }

    fun updateClockLayout(context: Context, layout: Int) {
        updateClockKitConfig(
            context,
            clockKitConfig.withString("adaptive_vector", "layout", if (layout == 1) "vertical" else "horizontal")
        )
    }

    fun updateDateLayout(context: Context, formatIndex: Int) {
        updateClockKitConfig(context, clockKitConfig.copy(dateFormat = formatIndex))
    }

    fun updateDateAttached(context: Context, attached: Boolean) {
        updateClockKitConfig(context, clockKitConfig.copy(dateAttached = attached))
    }

    fun updateLockscreenClockPosition(context: Context, x: Float, y: Float) {
        updateClockKitConfig(context, clockKitConfig.copy(clockX = x, clockY = y))
    }

    fun updateLockscreenDatePosition(context: Context, x: Float, y: Float) {
        updateClockKitConfig(context, clockKitConfig.copy(dateX = x, dateY = y))
    }

    fun updateClockStyle(
        context: Context,
        hourSize: Float,
        minuteSize: Float,
        strokeWidth: Float,
        roundness: Float,
        dateSize: Float = (hourSize * 0.24f).coerceIn(14f, 34f)
    ) {
        val updated = clockKitConfig
            .withFloat("adaptive_vector", "hour_size", hourSize)
            .withFloat("adaptive_vector", "min_size", minuteSize)
            .withFloat("adaptive_vector", "stroke_width", strokeWidth)
            .withFloat("adaptive_vector", "roundness", roundness)
        updateClockKitConfig(context, updated)
    }

    fun updateClockColor(context: Context, color: Int) {
        updateClockKitConfig(context, clockKitConfig.copy(color = color))
    }

    fun toggleClockStretch(context: Context, enabled: Boolean, mask: Bitmap?, screenW: Float, screenH: Float) {
        updateClockKitConfig(context, clockKitConfig.withBool("adaptive_vector", "stretch", enabled))
        if (enabled && mask != null) generateCollisionMap(context, mask, screenW, screenH)
    }

    fun updateClockGravity(context: Context, enabled: Boolean, intensity: Float) {
        updateClockKitConfig(
            context,
            clockKitConfig.copy(isGravityEnabled = enabled, gravityIntensity = intensity)
        )
    }

    fun generateCollisionMap(context: Context, mask: Bitmap, screenW: Float, screenH: Float) {
        viewModelScope.launch(Dispatchers.Default) {
            val mapString = AdaptiveClockHelper.generateCollisionMap(mask, screenW, screenH, 1.10f)
            withContext(Dispatchers.Main) {
                clockCollisionMap = mapString
                clockPrefs.edit().putString("clock_collision_map", mapString).apply()
                context.sendBroadcast(android.content.Intent("com.android.CaveArt.UPDATE_COLLISION_MAP").apply {
                    putExtra("collision_map", mapString)
                })
            }
        }
    }

    fun autoFitClockToSubject(context: Context, mask: Bitmap?, screenW: Float, screenH: Float) {
        if (mask == null) return
        viewModelScope.launch(Dispatchers.Default) {
            val density = context.resources.displayMetrics.density
            val result = AdaptiveClockHelper.calculateAutoFit(
                mask, screenW, screenH, clockHourSize, clockMinuteSize, density,
                isMagicShapeEnabled, magicScale, isCentered
            ) ?: return@launch

            withContext(Dispatchers.Main) {
                val updated = clockKitConfig
                    .withFloat("adaptive_vector", "hour_size", result.hourSize)
                    .withFloat("adaptive_vector", "min_size", result.minSize)
                    .copy(clockX = 0f, clockY = result.yDp)
                updateClockKitConfig(context, updated)
            }
        }
    }

    fun saveLayerStackSettings(context: Context, stack: LockscreenLayerStack) {
        val isDepth = stack.isDepthEnabled
        val order = if (isDepth) 1 else 0
        val occlusion = stack.depthOcclusionLimit
        val structure = stack.backgroundLayer.structure
        val subjectOpacity = stack.subjectLayer.opacity
        val bgOpacity = stack.backgroundLayer.opacity

        isClockDepthEnabled = isDepth
        clockLayerOrder = order
        clockDepthOcclusion = occlusion
        layerBackgroundStructure = structure
        layerSubjectOpacity = subjectOpacity
        layerBackgroundOpacity = bgOpacity

        clockKitConfig = clockKitConfig.copy(
            isDepthEnabled = isDepth,
            depthOcclusionLimit = occlusion
        )

        clockPrefs.edit()
            .putString(ClockKitConfigStore.KEY_JSON, ClockKitConfigStore.toJson(clockKitConfig))
            .putBoolean("clock_depth_enabled", isDepth)
            .putInt("clock_layer_order", order)
            .putFloat("clock_depth_occlusion", occlusion)
            .putFloat("layer_structure", structure)
            .putFloat("layer_subject_opacity", subjectOpacity)
            .putFloat("layer_bg_opacity", bgOpacity)
            .apply()

        context.sendBroadcast(android.content.Intent("com.android.CaveArt.UPDATE_LAYER_STACK").apply {
            putExtra("clock_depth_enabled", isDepth)
            putExtra("clock_layer_order", order)
            putExtra("clock_depth_occlusion", occlusion)
            putExtra("layer_structure", structure)
            putExtra("layer_subject_opacity", subjectOpacity)
            putExtra("layer_bg_opacity", bgOpacity)
        })
    }

    private val _isMagicShapeEnabled = mutableStateOf(false)
    val isMagicShapeEnabled: Boolean by _isMagicShapeEnabled

    private val _isAnimationEnabled = mutableStateOf(false)
    val isAnimationEnabled: Boolean by _isAnimationEnabled

    private val _isFilamentEnabled = mutableStateOf(false)
    val isFilamentEnabled: Boolean by _isFilamentEnabled

    var currentMagicShape by mutableStateOf(MagicShape.SQUIRCLE)
    var currentBackgroundColor by mutableStateOf(0xFF1E2022.toInt())
    var is3DPopEnabled by mutableStateOf(true)
    var magicScale by mutableFloatStateOf(1.0f)
    var isCentered by mutableStateOf(false)
    var currentAnimationStyle by mutableStateOf(AnimationStyle.EXPRESSIVE_HORIZON)
    var currentAnimParams by mutableStateOf<Map<String, Float>>(emptyMap())

    fun setMagicShapeEnabled(enabled: Boolean) {
        _isMagicShapeEnabled.value = enabled
        if (enabled) {
            _isAnimationEnabled.value = false
            _isFilamentEnabled.value = false
        }
    }

    fun setAnimationEnabled(enabled: Boolean) {
        _isAnimationEnabled.value = enabled
        if (enabled) {
            _isMagicShapeEnabled.value = false
            _isFilamentEnabled.value = false
        }
    }

    fun setFilamentEnabled(enabled: Boolean) {
        _isFilamentEnabled.value = enabled
        if (enabled) {
            _isMagicShapeEnabled.value = false
            _isAnimationEnabled.value = false
        }
    }

    fun updateMagicConfig(shape: MagicShape, color: Int) {
        currentMagicShape = shape
        currentBackgroundColor = color
    }

    fun toggle3DPop() { is3DPopEnabled = !is3DPopEnabled }
    fun updateMagicScale(scale: Float) { magicScale = scale }
    fun toggleCentered() { isCentered = !isCentered }

    fun updateAnimationStyle(style: AnimationStyle) {
        currentAnimationStyle = style
        val anim = AnimationFactory.getAnimation(style)
        val defaults = mutableMapOf<String, Float>()
        anim.getCustomSettings().forEach { setting ->
            when (setting) {
                is AnimSetting.Slider -> defaults[setting.id] = setting.defaultValue
                is AnimSetting.Toggle -> defaults[setting.id] = if (setting.defaultValue) 1f else 0f
            }
        }
        currentAnimParams = defaults
    }

    fun updateAnimParam(key: String, value: Float) {
        currentAnimParams = currentAnimParams.toMutableMap().apply { put(key, value) }
    }

    var allWallpapers by mutableStateOf<List<Wallpaper>>(emptyList())

    init {
        viewModelScope.launch {
            isLoading = true
            launch(MLThread.dispatcher) {
                try {
                    segmentationHelper.warmUp()
                    refineHelper.warmUp()
                    mattingHelper.warmUp()
                } catch (_: Exception) {}
            }
            val initialList = loadBasicWallpaperList(application.applicationContext)
            allWallpapers = initialList

            autoSyncActiveWallpaper(application.applicationContext)

            isLoading = false
            scanWallpapersForTags(application.applicationContext)
            updateAnimationStyle(currentAnimationStyle)
        }
    }

    suspend fun autoSyncActiveWallpaper(context: Context) {
        withContext(Dispatchers.IO) {
            if (RootUtils.isRootAvailable()) {
                val extractedFile = RootUtils.copyActiveLockscreenWallpaper(context)
                if (extractedFile != null && extractedFile.exists() && extractedFile.length() > 0) {
                    val sysWp = Wallpaper(
                        id = "active_system_lockscreen",
                        uri = Uri.fromFile(extractedFile),
                        title = "Current Lockscreen",
                        tag = "Active"
                    )
                    withContext(Dispatchers.Main) {
                        allWallpapers = listOf(sysWp) + allWallpapers.filter { it.id != "active_system_lockscreen" }
                    }
                }
            }
        }
    }

    fun addGalleryWallpaper(uri: Uri) {
        val newWallpaper = Wallpaper(id = UUID.randomUUID().toString(), uri = uri, title = "Gallery Image", tag = "Gallery")
        allWallpapers = listOf(newWallpaper) + allWallpapers
        viewModelScope.launch { scanSingleWallpaper(getApplication(), newWallpaper) }
    }

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val bitmapCache = object : LruCache<String, Bitmap>(maxMemory / 8) {
        override fun sizeOf(key: String, bitmap: Bitmap) = bitmap.byteCount / 1024
    }
    private val cutoutCache = object : LruCache<String, Bitmap>(maxMemory / 32) {
        override fun sizeOf(key: String, bitmap: Bitmap) = bitmap.byteCount / 1024
    }
    private val originalCache = object : LruCache<String, Bitmap>(maxMemory / 32) {
        override fun sizeOf(key: String, bitmap: Bitmap) = bitmap.byteCount / 1024
    }
    private val depthCache = object : LruCache<String, DepthResult>(maxMemory / 32) {
        override fun sizeOf(key: String, value: DepthResult) =
            (value.rawDepthMap.byteCount + value.heatMapBitmap.byteCount) / 1024
    }

    private suspend fun getCachedOriginal(context: Context, wallpaper: Wallpaper, maxDim: Int = 1024): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "orig_${wallpaper.id}_$maxDim"
        originalCache.get(cacheKey)?.let { return@withContext it }
        try {
            val bitmap = if (wallpaper.uri != null) {
                BitmapHelper.decodeSampledBitmapFromUri(context, wallpaper.uri, maxDim)
            } else {
                BitmapHelper.decodeSampledBitmapFromResource(context.resources, wallpaper.resourceId, maxDim)
            }
            originalCache.put(cacheKey, bitmap)
            return@withContext bitmap
        } catch (_: Exception) {
            return@withContext null
        }
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

    suspend fun getPreviewAnimationComponents(context: Context, wallpaper: Wallpaper): Pair<Bitmap?, Bitmap?> = withContext(Dispatchers.Default) {
        try {
            val original = getCachedOriginal(context, wallpaper, 1024)
            var cutout: Bitmap? = null
            if (isAnimationEnabled && AnimationFactory.getAnimation(currentAnimationStyle).needsSegmentationMask() && original != null) {
                cutout = cutoutCache.get(wallpaper.id) ?: generateCutoutInternal(original)?.also { cutoutCache.put(wallpaper.id, it) }
            }
            Pair(original, cutout)
        } catch (_: Exception) {
            Pair(null, null)
        }
    }

    suspend fun getMaskForClock(context: Context, wallpaper: Wallpaper): Bitmap? = withContext(Dispatchers.Default) {
        try {
            val cached = cutoutCache.get(wallpaper.id)
            if (cached != null && !cached.isRecycled) {
                return@withContext cached
            }

            val original = getCachedOriginal(context, wallpaper, 1024) ?: return@withContext null
            val generated = generateCutoutInternal(original)
            if (generated != null) {
                cutoutCache.put(wallpaper.id, generated)
                saveDepthMaskToDisk(context, generated)
            }
            generated
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getOrGenerateDepth(context: Context, wallpaper: Wallpaper): DepthResult? = withContext(Dispatchers.Default) {
        try {
            depthCache.get(wallpaper.id)?.let { return@withContext it }

            val original = getCachedOriginal(context, wallpaper, 1024) ?: return@withContext null
            val result = depthHelper.runInference(original)
            if (result != null) {
                depthCache.put(wallpaper.id, result)
                saveRawDepthToDisk(context, result.rawDepthMap)
            }
            result
        } catch (_: Exception) {
            null
        }
    }

    fun saveDepthMaskToDisk(context: Context, mask: Bitmap) {
        try {
            val safeCtx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.createDeviceProtectedStorageContext()
            } else context

            val file = File(safeCtx.filesDir, "lock_depth_mask.png")
            FileOutputStream(file).use { out ->
                mask.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val backupFile = File(safeCtx.noBackupFilesDir, "boot_cutout.png")
            FileOutputStream(backupFile).use { out ->
                mask.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (_: Exception) {}
    }

    fun saveRawDepthToDisk(context: Context, depthMap: Bitmap) {
        try {
            val safeCtx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.createDeviceProtectedStorageContext()
            } else context

            val file = File(safeCtx.filesDir, "lock_raw_depth.png")
            FileOutputStream(file).use { out ->
                depthMap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file.setReadable(true, false)
        } catch (_: Exception) {}
    }

    suspend fun getOrCreateProcessedBitmap(context: Context, wallpaper: Wallpaper, allowMagic: Boolean = true, maxDim: Int = 1024): Bitmap? {
        val needsCutout = allowMagic && (isMagicShapeEnabled || (isAnimationEnabled && AnimationFactory.getAnimation(currentAnimationStyle).needsSegmentationMask()))
        val paramKey = currentAnimParams.entries.joinToString { "${it.key}=${it.value}" }
        val cacheKey = if (needsCutout) {
            "final_${wallpaper.id}_${currentMagicShape}_${currentBackgroundColor}_${is3DPopEnabled}_${magicScale}_${isCentered}_${currentAnimationStyle.name}_$paramKey"
        } else {
            "preview_${wallpaper.id}_$maxDim"
        }

        bitmapCache.get(cacheKey)?.let { return it }

        return withContext(Dispatchers.Default) {
            try {
                val originalBitmap = getCachedOriginal(context, wallpaper, maxDim) ?: return@withContext null
                var cutout: Bitmap? = null
                if (needsCutout) {
                    cutout = cutoutCache.get(wallpaper.id) ?: generateCutoutInternal(originalBitmap)?.also { cutoutCache.put(wallpaper.id, it) }
                }

                val resultBitmap = if (allowMagic && isMagicShapeEnabled && cutout != null) {
                    ShapeEffectHelper.createShapeCropBitmapWithPreCutout(
                        originalBitmap,
                        cutout,
                        LiveWallpaperConfig(
                            shapeName = currentMagicShape.name,
                            backgroundColor = currentBackgroundColor,
                            is3DPopEnabled = is3DPopEnabled,
                            scale = magicScale,
                            isCentered = isCentered,
                            isMagicShapeEnabled = true,
                            animParams = currentAnimParams
                        )
                    )
                } else {
                    originalBitmap
                }
                bitmapCache.put(cacheKey, resultBitmap)
                resultBitmap
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun getHighQualityComponents(context: Context, wallpaper: Wallpaper): Pair<Bitmap?, Bitmap?> = withContext(Dispatchers.IO) {
        val original = try {
            if (wallpaper.uri != null) BitmapHelper.decodeSampledBitmapFromUri(context, wallpaper.uri, 2500)
            else BitmapHelper.decodeSampledBitmapFromResource(context.resources, wallpaper.resourceId, 2500)
        } catch (_: Exception) { null }

        val cutout = if (original != null) {
            getMaskForClock(context, wallpaper)
        } else null

        Pair(original, cutout)
    }

    private suspend fun loadBasicWallpaperList(context: Context): List<Wallpaper> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Wallpaper>()
        R.drawable::class.java.fields.filter { it.name.startsWith(WALLPAPER_PREFIX) }.forEach { field ->
            try {
                val parts = field.name.substring(WALLPAPER_PREFIX.length).split("_")
                if (parts.size >= 2) list.add(Wallpaper(id = field.name, resourceId = field.getInt(null), title = parts.subList(0, parts.size - 1).joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }, tag = parts.last().replaceFirstChar { it.uppercase() }))
            } catch (_: Exception) {}
        }
        list.sortedBy { it.title }
    }

    private suspend fun scanWallpapersForTags(context: Context) = withContext(Dispatchers.IO) {
        allWallpapers.toList().forEach { scanSingleWallpaper(context, it) }
    }

    private suspend fun scanSingleWallpaper(context: Context, wallpaper: Wallpaper) {
        if (wallpaper.mlTags.isNotEmpty()) return
        val detectedTags = mlProcessingSemaphore.withPermit {
            try {
                val small = if (wallpaper.uri != null) {
                    BitmapHelper.decodeSampledBitmapFromUri(context, wallpaper.uri, 300)
                } else {
                    BitmapHelper.decodeSampledBitmapFromResource(context.resources, wallpaper.resourceId, 300)
                }
                val tags = ImageLabelingHelper.getTagsFromBitmap(small)
                small.recycle()
                tags
            } catch (_: Exception) {
                emptyList<String>()
            }
        }
        if (detectedTags.isNotEmpty()) {
            wallpaper.mlTags = detectedTags
        }
    }

    var debugResults by mutableStateOf<List<DebugResult>>(emptyList())
    var isRunningDebug by mutableStateOf(false)

    fun runModelDiagnostics(context: Context, target: Wallpaper?) {
        if (target == null) return
        viewModelScope.launch(Dispatchers.IO) {
            isRunningDebug = true
            try {
                val original = try {
                    if (target.uri != null) BitmapHelper.decodeSampledBitmapFromUri(context, target.uri, 512)
                    else BitmapHelper.decodeSampledBitmapFromResource(context.resources, target.resourceId, 512)
                } catch (_: Exception) { null }

                if (original != null) {
                    val res = ModelDebugHelper.runFullPipelineDiagnostic(context, original)
                    withContext(Dispatchers.Main) {
                        debugResults = res
                        isRunningDebug = false
                    }
                } else {
                    withContext(Dispatchers.Main) { isRunningDebug = false }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { isRunningDebug = false }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        segmentationHelper.close()
        refineHelper.close()
        mattingHelper.close()
        depthHelper.close()
    }
}
