package com.android.CaveArt

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.CornerPathEffect
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RenderNode
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.CaveArt.layer.LayerCompositor
import com.android.CaveArt.layer.LayerType
import com.android.CaveArt.layer.LockscreenLayerStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class ActiveLayerTab(val label: String) {
    ALL("All"),
    BACKGROUND("Background"),
    CLOCK("Clock"),
    SUBJECT("Subject")
}

enum class ContextualToolType {
    SLIDER,
    FONT,
    COLOR,
    OPTIONS
}

data class LayerAction(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val toolType: ContextualToolType = ContextualToolType.SLIDER,
    val getVal: () -> Float = { 0f },
    val setVal: (Float) -> Unit = {},
    val range: ClosedFloatingPointRange<Float> = 0f..1f,
    val formatDisplay: (Float) -> String = { "" }
)

class ClockStudioState(viewModel: WallpaperViewModel) {
    val layerStack: LockscreenLayerStack = LockscreenLayerStack().apply {
        setDepthClockActive(viewModel.isClockDepthEnabled && viewModel.clockLayerOrder == 1)
        depthOcclusionLimit = viewModel.clockDepthOcclusion
        backgroundLayer.structure = viewModel.layerBackgroundStructure
        subjectLayer.opacity = viewModel.layerSubjectOpacity
        backgroundLayer.opacity = viewModel.layerBackgroundOpacity
    }

    val clockX: MutableFloatState = mutableFloatStateOf(viewModel.lockscreenClockOffsetX)
    val clockY: MutableFloatState = mutableFloatStateOf(viewModel.lockscreenClockOffsetY)
    val dateX: MutableFloatState = mutableFloatStateOf(viewModel.lockscreenDateOffsetX)
    val dateY: MutableFloatState = mutableFloatStateOf(viewModel.lockscreenDateOffsetY)
    val hourSize: MutableFloatState = mutableFloatStateOf(viewModel.clockHourSize)
    val minSize: MutableFloatState = mutableFloatStateOf(viewModel.clockMinuteSize)
    val strokeWidth: MutableFloatState = mutableFloatStateOf(viewModel.clockStrokeWidth)
    val roundness: MutableFloatState = mutableFloatStateOf(viewModel.clockRoundness)
    val stretchEnabled: MutableState<Boolean> = mutableStateOf(viewModel.isClockStretchEnabled)
    val collisionMap: MutableState<String> = mutableStateOf(viewModel.clockCollisionMap)
    val clockColor: MutableIntState = mutableIntStateOf(viewModel.clockColor)
    val dualTone: MutableState<Boolean> = mutableStateOf(viewModel.isDualToneEnabled)
    val clockLayout: MutableIntState = mutableIntStateOf(viewModel.clockLayout)
    val dateFormat: MutableIntState = mutableIntStateOf(viewModel.dateLayout)
    val dateAttached: MutableState<Boolean> = mutableStateOf(viewModel.isDateAttached)
    val clockFont: MutableState<String> = mutableStateOf(viewModel.clockFont)
    val dateSize: MutableFloatState = mutableFloatStateOf((viewModel.clockHourSize * 0.24f).coerceIn(14f, 34f))

    val udfpsX: MutableFloatState = mutableFloatStateOf(-1f)
    val udfpsY: MutableFloatState = mutableFloatStateOf(-1f)
    val udfpsRadius: MutableFloatState = mutableFloatStateOf(0f)
    val liveWallpaperZoom: MutableFloatState = mutableFloatStateOf(1.0f)
    val notificationShelfTop: MutableFloatState = mutableFloatStateOf(-1f)
}

@Composable
fun rememberClockStudioState(viewModel: WallpaperViewModel): ClockStudioState =
    remember(viewModel) { ClockStudioState(viewModel) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockStudio(
    wallpaper: Wallpaper,
    viewModel: WallpaperViewModel,
    sharedElementModifier: Modifier = Modifier,
    onBack: () -> Unit,
    onApplyRequested: (Int) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isDarkTheme = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()

    val displayGeometry = remember { NativeDisplayMetrics.getGeometry(context) }
    val realW = displayGeometry.widthPx
    val realH = displayGeometry.heightPx
    val densityVal = displayGeometry.density

    val statusBarDp = displayGeometry.statusBarHeightPx / densityVal
    val navBarDp = displayGeometry.navBarHeightPx / densityVal
    val minSafeYDp = statusBarDp + (displayGeometry.statusBarHeightPx * 0.12f / densityVal)
    val minClockSizeDp = (realW * 0.08f) / densityVal
    val maxClockSizeDp = (realW * 0.55f) / densityVal
    val magneticSnapThresholdDp = (realW * 0.025f) / densityVal

    val state = rememberClockStudioState(viewModel)
    var previewOriginal by remember { mutableStateOf<Bitmap?>(null) }
    var previewMask by remember { mutableStateOf<Bitmap?>(null) }

    var isDraggingClock by remember { mutableStateOf(false) }
    var isDraggingDate by remember { mutableStateOf(false) }

    var showDateCustomizeSheet by remember { mutableStateOf(false) }
    var showLayersSheet by remember { mutableStateOf(false) }
    var showApplyOptions by remember { mutableStateOf(false) }

    var activeLayerTab by remember { mutableStateOf(ActiveLayerTab.CLOCK) }
    var activeActionIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(activeLayerTab) {
        activeActionIndex = 0
    }

    val currentLayerActions = remember(activeLayerTab, state, minClockSizeDp, maxClockSizeDp) {
        when (activeLayerTab) {
            ActiveLayerTab.ALL -> listOf(
                LayerAction(
                    id = "all_occlusion",
                    label = "Depth Occlusion",
                    icon = Icons.Rounded.Layers,
                    toolType = ContextualToolType.SLIDER,
                    getVal = { state.layerStack.depthOcclusionLimit },
                    setVal = { state.layerStack.depthOcclusionLimit = it },
                    range = 0.10f..0.85f,
                    formatDisplay = { "${(it * 100).toInt()}%" }
                ),
                LayerAction(
                    id = "all_zoom",
                    label = "Wallpaper Zoom",
                    icon = Icons.Rounded.ZoomIn,
                    toolType = ContextualToolType.SLIDER,
                    getVal = { state.liveWallpaperZoom.floatValue },
                    setVal = { state.liveWallpaperZoom.floatValue = it },
                    range = 1.0f..1.35f,
                    formatDisplay = { String.format("%.2fx", it) }
                )
            )
            ActiveLayerTab.BACKGROUND -> listOf(
                LayerAction(
                    id = "bg_vibrance",
                    label = "Background Vibrance",
                    icon = Icons.Rounded.AutoFixHigh,
                    toolType = ContextualToolType.SLIDER,
                    getVal = { state.layerStack.backgroundLayer.structure },
                    setVal = { state.layerStack.backgroundLayer.structure = it },
                    range = -1.0f..1.0f,
                    formatDisplay = {
                        val v = (it * 50).toInt()
                        if (v > 0) "+$v" else "$v"
                    }
                ),
                LayerAction(
                    id = "bg_opacity",
                    label = "Background Opacity",
                    icon = Icons.Rounded.Opacity,
                    toolType = ContextualToolType.SLIDER,
                    getVal = { state.layerStack.backgroundLayer.opacity },
                    setVal = { state.layerStack.backgroundLayer.opacity = it },
                    range = 0.10f..1.0f,
                    formatDisplay = { "${(it * 100).toInt()}%" }
                )
            )
            ActiveLayerTab.CLOCK -> listOf(
                LayerAction(
                    id = "clock_thickness",
                    label = "Thickness",
                    icon = Icons.Rounded.LineWeight,
                    toolType = ContextualToolType.SLIDER,
                    getVal = { state.strokeWidth.floatValue },
                    setVal = { state.strokeWidth.floatValue = it },
                    range = 0f..25f,
                    formatDisplay = { String.format("%.1f", it) }
                ),
                LayerAction(
                    id = "clock_roundness",
                    label = "Roundness",
                    icon = Icons.Rounded.RoundedCorner,
                    toolType = ContextualToolType.SLIDER,
                    getVal = { state.roundness.floatValue },
                    setVal = { state.roundness.floatValue = it },
                    range = 0f..80f,
                    formatDisplay = { String.format("%.1f", it) }
                ),
                LayerAction(
                    id = "clock_font",
                    label = "Typography",
                    icon = Icons.Rounded.Title,
                    toolType = ContextualToolType.FONT
                ),
                LayerAction(
                    id = "clock_color",
                    label = "Color Palette",
                    icon = Icons.Rounded.Palette,
                    toolType = ContextualToolType.COLOR
                ),
                LayerAction(
                    id = "clock_options",
                    label = "Layout & Fit",
                    icon = Icons.Rounded.DashboardCustomize,
                    toolType = ContextualToolType.OPTIONS
                )
            )
            ActiveLayerTab.SUBJECT -> listOf(
                LayerAction(
                    id = "subject_opacity",
                    label = "Subject Opacity",
                    icon = Icons.Rounded.Opacity,
                    toolType = ContextualToolType.SLIDER,
                    getVal = { state.layerStack.subjectLayer.opacity },
                    setVal = { state.layerStack.subjectLayer.opacity = it },
                    range = 0.10f..1.0f,
                    formatDisplay = { "${(it * 100).toInt()}%" }
                )
            )
        }
    }

    val activeAction = currentLayerActions.getOrNull(activeActionIndex) ?: currentLayerActions.first()

    var extractedColors by remember { mutableStateOf<List<Int>>(emptyList()) }
    var timeString by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    val availableFonts = remember { viewModel.getAvailableFonts(context) }

    val vectorPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL_AND_STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            setShadowLayer(15f, 0f, 5f, android.graphics.Color.argb(160, 0, 0, 0))
        }
    }
    val datePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            setShadowLayer(10f, 0f, 3f, android.graphics.Color.argb(160, 0, 0, 0))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
    }
    val notificationPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(200, 255, 183, 77)
            strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(20f, 20f), 0f)
        }
    }
    val udfpsPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f)
        }
    }
    val combinedClockPath = remember { Path() }
    val sharedPathBounds = remember { RectF() }

    val hardwareRenderNode = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RenderNode("LockscreenPreviewHardwareNode").apply {
                setHasOverlappingRendering(false)
                clipToBounds = false
            }
        } else null
    }

    LaunchedEffect(state.hourSize.floatValue) {
        val calculatedDateSize = (state.hourSize.floatValue * 0.24f).coerceIn(14f, 34f)
        state.dateSize.floatValue = calculatedDateSize
    }

    LaunchedEffect(
        state.clockX.floatValue,
        state.clockY.floatValue,
        state.dateAttached.value,
        state.hourSize.floatValue,
        state.clockLayout.intValue,
        state.dateSize.floatValue,
        state.stretchEnabled.value,
        dateText
    ) {
        datePaint.textSize = state.dateSize.floatValue * densityVal
        val sampleDate = if (dateText.isNotEmpty()) dateText else "Wednesday, 14 October"
        val dateWidthDp = datePaint.measureText(sampleDate) / densityVal
        val halfScreenDp = realW / densityVal / 2f
        val halfDateWDp = dateWidthDp / 2f
        val sideSafeMarginDp = 18f
        val minSafeX = -halfScreenDp + halfDateWDp + sideSafeMarginDp
        val maxSafeX = halfScreenDp - halfDateWDp - sideSafeMarginDp

        if (state.dateAttached.value) {
            state.dateX.floatValue = if (minSafeX <= maxSafeX) {
                state.clockX.floatValue.coerceIn(minSafeX, maxSafeX)
            } else 0f

            val fontMetrics = datePaint.fontMetrics
            val dateLineHeightDp = (fontMetrics.descent - fontMetrics.ascent) / densityVal
            val dynamicGapDp = (state.strokeWidth.floatValue * 0.4f) + (dateLineHeightDp * 0.35f)
            val compoundDateOffsetDp = dateLineHeightDp + dynamicGapDp

            val idealAboveYDp = state.clockY.floatValue - compoundDateOffsetDp
            val touchesTopBorder = idealAboveYDp < minSafeYDp

            if (state.stretchEnabled.value) {
                state.dateY.floatValue = max(minSafeYDp, idealAboveYDp)
            } else {
                if (touchesTopBorder) {
                    val isVert = state.clockLayout.intValue == 1
                    val clockEstimatedHDp = if (isVert) {
                        (state.hourSize.floatValue * 2.15f) + (state.strokeWidth.floatValue * 0.5f)
                    } else {
                        (state.hourSize.floatValue * 1.15f) + (state.strokeWidth.floatValue * 0.5f)
                    }
                    state.dateY.floatValue = state.clockY.floatValue + clockEstimatedHDp + dynamicGapDp
                } else {
                    state.dateY.floatValue = max(minSafeYDp, idealAboveYDp)
                }
            }
        }
    }

    val datePhysicsSpringY = remember { Animatable(state.dateY.floatValue) }
    LaunchedEffect(
        isDraggingDate,
        isDraggingClock,
        state.dateAttached.value,
        state.clockX.floatValue,
        state.clockY.floatValue,
        state.dateX.floatValue,
        state.hourSize.floatValue,
        state.clockLayout.intValue
    ) {
        if (!state.dateAttached.value && !isDraggingDate && !isDraggingClock) {
            datePaint.textSize = state.dateSize.floatValue * densityVal
            val sampleDate = if (dateText.isNotEmpty()) dateText else "Wednesday, 14 October"
            val dateWDp = datePaint.measureText(sampleDate) / densityVal
            val dateHDp = (datePaint.fontMetrics.descent - datePaint.fontMetrics.ascent) / densityVal

            val nativeDateCenterX = (realW / densityVal / 2f) + state.dateX.floatValue
            val dateLeft = nativeDateCenterX - (dateWDp / 2f)
            val dateRight = nativeDateCenterX + (dateWDp / 2f)
            val dateTop = state.dateY.floatValue
            val dateBottom = dateTop + dateHDp

            val clockLeft = sharedPathBounds.left / densityVal
            val clockRight = sharedPathBounds.right / densityVal
            val clockTop = sharedPathBounds.top / densityVal
            val clockBottom = sharedPathBounds.bottom / densityVal

            val marginDp = 16f
            val xOverlap = max(dateLeft, clockLeft) < min(dateRight, clockRight)
            val yOverlap = (dateBottom + marginDp > clockTop) && (dateTop < clockBottom + marginDp)

            if (xOverlap && yOverlap && sharedPathBounds.width() > 0 && sharedPathBounds.height() > 0) {
                val clockCenterY = (clockTop + clockBottom) / 2f
                val dateCenterY = (dateTop + dateBottom) / 2f

                val maxDateSafeYDp = (realH / densityVal) - navBarDp - (state.dateSize.floatValue * 1.5f)

                val targetY = if (dateCenterY < clockCenterY) {
                    val candidateAbove = clockTop - dateHDp - marginDp
                    if (candidateAbove >= minSafeYDp) candidateAbove else clockBottom + marginDp
                } else {
                    val candidateBelow = clockBottom + marginDp
                    if (candidateBelow <= maxDateSafeYDp) candidateBelow else clockTop - dateHDp - marginDp
                }

                val safeClampedY = targetY.coerceIn(minSafeYDp, maxDateSafeYDp)
                datePhysicsSpringY.snapTo(state.dateY.floatValue)
                datePhysicsSpringY.animateTo(
                    targetValue = safeClampedY,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) {
                    state.dateY.floatValue = value
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        state.liveWallpaperZoom.floatValue = displayGeometry.wallpaperMaxScale

        withContext(Dispatchers.IO) {
            val coords = RootUtils.detectUdfpsLocation(context)
            if (coords != null) {
                withContext(Dispatchers.Main) {
                    state.udfpsX.floatValue = coords.first
                    state.udfpsY.floatValue = coords.second
                    state.udfpsRadius.floatValue = coords.third
                }
            }

            val prefs = context.getSharedPreferences("cave_art_clock_prefs", Context.MODE_PRIVATE)
            val zoom = prefs.getFloat("telemetry_zoom", displayGeometry.wallpaperMaxScale)
            val nssl = prefs.getFloat("telemetry_nssl_top", -1f)
            val ux = prefs.getFloat("udfps_x", -1f)
            val uy = prefs.getFloat("udfps_y", -1f)
            val ur = prefs.getFloat("udfps_radius", 0f)

            val depthEnabled = prefs.getBoolean("clock_depth_enabled", true)
            val layerOrder = prefs.getInt("clock_layer_order", 1)
            val depthOcclusion = prefs.getFloat("clock_depth_occlusion", 0.45f)
            val layerStructure = prefs.getFloat("layer_structure", 0f)
            val subOpacity = prefs.getFloat("layer_subject_opacity", 1.0f)
            val bgOpacity = prefs.getFloat("layer_bg_opacity", 1.0f)

            withContext(Dispatchers.Main) {
                state.liveWallpaperZoom.floatValue = zoom
                state.notificationShelfTop.floatValue = nssl
                state.layerStack.setDepthClockActive(depthEnabled && layerOrder == 1)
                state.layerStack.depthOcclusionLimit = depthOcclusion
                state.layerStack.backgroundLayer.structure = layerStructure
                state.layerStack.subjectLayer.opacity = subOpacity
                state.layerStack.backgroundLayer.opacity = bgOpacity

                if (ur > 0f) {
                    state.udfpsX.floatValue = ux
                    state.udfpsY.floatValue = uy
                    state.udfpsRadius.floatValue = ur
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val filter = IntentFilter().apply {
            addAction("com.android.CaveArt.UPDATE_TELEMETRY")
            addAction("com.android.CaveArt.UPDATE_UDFPS_LOCATION")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent == null) return
                if (intent.hasExtra("telemetry_zoom")) {
                    state.liveWallpaperZoom.floatValue = intent.getFloatExtra("telemetry_zoom", displayGeometry.wallpaperMaxScale)
                }
                if (intent.hasExtra("telemetry_nssl_top")) {
                    state.notificationShelfTop.floatValue = intent.getFloatExtra("telemetry_nssl_top", -1f)
                }
                if (intent.hasExtra("udfps_radius")) {
                    state.udfpsX.floatValue = intent.getFloatExtra("udfps_x", -1f)
                    state.udfpsY.floatValue = intent.getFloatExtra("udfps_y", -1f)
                    state.udfpsRadius.floatValue = intent.getFloatExtra("udfps_radius", 0f)
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    LaunchedEffect(wallpaper) {
        val components = viewModel.getHighQualityComponents(context, wallpaper)
        previewOriginal = components.first
        previewMask = components.second ?: viewModel.getMaskForClock(context, wallpaper)
    }

    val currentMask = previewMask
    val currentZoom = state.liveWallpaperZoom.floatValue
    LaunchedEffect(currentMask, state.stretchEnabled.value, currentZoom) {
        if (state.stretchEnabled.value && currentMask != null) {
            withContext(Dispatchers.Default) {
                val mapStr = AdaptiveClockHelper.generateCollisionMap(
                    currentMask, realW, realH, currentZoom
                )
                withContext(Dispatchers.Main) {
                    state.collisionMap.value = mapStr
                }
            }
        } else if (!state.stretchEnabled.value) {
            state.collisionMap.value = ""
        }
    }

    LaunchedEffect(state.dateFormat.intValue) {
        while (true) {
            val is24 = android.text.format.DateFormat.is24HourFormat(context)
            val date = java.util.Date()
            val locale = java.util.Locale.getDefault()
            timeString = java.text.SimpleDateFormat(if (is24) "HH:mm" else "hh:mm", locale).format(date)
            dateText = when (state.dateFormat.intValue) {
                0 -> java.text.SimpleDateFormat("EEE, d MMMM", locale).format(date)
                1 -> java.text.SimpleDateFormat("EEE, d MMM", locale).format(date)
                2 -> java.text.SimpleDateFormat("d MMM yyyy", locale).format(date).uppercase(locale)
                3 -> java.text.SimpleDateFormat("EEEE, MMMM d", locale).format(date)
                else -> java.text.SimpleDateFormat("EEE, d MMMM", locale).format(date)
            }
            delay(1000L)
        }
    }

    LaunchedEffect(wallpaper, isDarkTheme) {
        withContext(Dispatchers.IO) {
            val dynamicTheme = MonetEngine.getThemePalette(context, wallpaper, isDarkTheme)
            withContext(Dispatchers.Main) {
                extractedColors = dynamicTheme.allColors
                if (state.clockColor.intValue == 0 || isDefaultColor(state.clockColor.intValue)) {
                    state.clockColor.intValue = dynamicTheme.tonalSpot.firstOrNull() ?: dynamicTheme.seedColor
                }
            }
        }
    }

    val customTypeface = remember(state.clockFont.value) {
        if (state.clockFont.value == "default" || state.clockFont.value.isEmpty()) {
            Typeface.create("sans-serif-bold", Typeface.NORMAL)
        } else {
            try {
                Typeface.createFromAsset(context.assets, "fonts/${state.clockFont.value}")
            } catch (_: Exception) {
                Typeface.create("sans-serif-bold", Typeface.NORMAL)
            }
        }
    }

    val collisionMapArray = remember(state.collisionMap.value) {
        if (state.collisionMap.value.isNotEmpty()) {
            state.collisionMap.value.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray()
        } else null
    }

    val cardCornerRadiusDp = (displayGeometry.physicalCornerRadiusPx / densityVal).coerceAtLeast(0f)

    val hoursString = if (timeString.contains(":")) timeString.substringBefore(":") else "07"
    val minsString = if (timeString.contains(":")) timeString.substringAfter(":") else "44"

    val focusedLayerType = remember(activeLayerTab) {
        when (activeLayerTab) {
            ActiveLayerTab.BACKGROUND -> LayerType.BACKGROUND
            ActiveLayerTab.SUBJECT -> LayerType.SUBJECT
            else -> null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (viewModel.isAmbientBlurEnabled) {
            AsyncWallpaperImage(
                wallpaper = wallpaper,
                contentDescription = null,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize().blur(85.dp),
                contentScale = ContentScale.Crop,
                allowMagic = false
            )
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)))
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Lock Screen",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                state.layerStack.isMaskInspectorActive = !state.layerStack.isMaskInspectorActive
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Visibility,
                                contentDescription = "Inspect Mask Overlay",
                                tint = if (state.layerStack.isMaskInspectorActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            bottomBar = {
                if (!isLandscape) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(top = 8.dp, bottom = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .padding(horizontal = 18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = activeAction.toolType,
                                transitionSpec = {
                                    (fadeIn(tween(180)) + slideInVertically { it / 3 }) togetherWith
                                    (fadeOut(tween(140)) + slideOutVertically { -it / 3 })
                                },
                                label = "dynamicToolSlot"
                            ) { tool ->
                                when (tool) {
                                    ContextualToolType.SLIDER -> {
                                        SlimRulerSlider(
                                            value = activeAction.getVal(),
                                            onValueChange = activeAction.setVal,
                                            valueRange = activeAction.range,
                                            icon = activeAction.icon,
                                            label = activeAction.label,
                                            valueDisplay = activeAction.formatDisplay(activeAction.getVal()),
                                            accentColor = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    ContextualToolType.FONT -> {
                                        CompactFontSelectorStrip(
                                            availableFonts = availableFonts,
                                            selectedFont = state.clockFont.value,
                                            onFontSelected = { state.clockFont.value = it },
                                            hours = hoursString,
                                            mins = minsString,
                                            clockColor = state.clockColor.intValue,
                                            isDualTone = state.dualTone.value,
                                            isVertical = state.clockLayout.intValue == 1
                                        )
                                    }
                                    ContextualToolType.COLOR -> {
                                        CompactColorSelectorStrip(
                                            extractedColors = extractedColors,
                                            selectedColor = state.clockColor.intValue,
                                            onColorSelected = { state.clockColor.intValue = it }
                                        )
                                    }
                                    ContextualToolType.OPTIONS -> {
                                        CompactClockOptionsStrip(
                                            isVertical = state.clockLayout.intValue == 1,
                                            onToggleLayout = {
                                                state.clockLayout.intValue = if (state.clockLayout.intValue == 0) 1 else 0
                                            },
                                            isDualTone = state.dualTone.value,
                                            onToggleDualTone = { state.dualTone.value = !state.dualTone.value },
                                            isDepthEnabled = state.layerStack.isDepthEnabled,
                                            onToggleDepth = {
                                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                                state.layerStack.setDepthClockActive(!state.layerStack.isDepthEnabled)
                                            },
                                            isAdaptive = state.stretchEnabled.value,
                                            onToggleAdaptive = { state.stretchEnabled.value = !state.stretchEnabled.value },
                                            onAutoFit = {
                                                val mask = previewMask
                                                if (mask != null) scope.launch(Dispatchers.Default) {
                                                    val result = AdaptiveClockHelper.calculateAutoFit(
                                                        mask, realW, realH,
                                                        state.hourSize.floatValue, state.minSize.floatValue,
                                                        densityVal, viewModel.isMagicShapeEnabled,
                                                        viewModel.magicScale, viewModel.isCentered
                                                    )
                                                    if (result != null) withContext(Dispatchers.Main) {
                                                        state.hourSize.floatValue = result.hourSize
                                                        state.minSize.floatValue = result.minSize
                                                        state.clockX.floatValue = 0f
                                                        state.clockY.floatValue = result.yDp
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                            shadowElevation = 8.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(58.dp)
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val isClockActive = activeLayerTab == ActiveLayerTab.CLOCK
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isClockActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .height(44.dp)
                                            .clickable {
                                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                                activeLayerTab = ActiveLayerTab.CLOCK
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Schedule,
                                                contentDescription = "Clock",
                                                modifier = Modifier.size(17.dp),
                                                tint = if (isClockActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "Clock",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = if (isClockActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    val isNonClockActive = activeLayerTab != ActiveLayerTab.CLOCK
                                    Surface(
                                        shape = CircleShape,
                                        color = if (showLayersSheet || isNonClockActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .height(44.dp)
                                            .clickable {
                                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                                showLayersSheet = true
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Layers,
                                                contentDescription = "Layers",
                                                modifier = Modifier.size(17.dp),
                                                tint = if (showLayersSheet || isNonClockActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = if (isNonClockActive) activeLayerTab.label else "Layers",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = if (showLayersSheet || isNonClockActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        showApplyOptions = true
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier.height(44.dp),
                                    contentPadding = PaddingValues(horizontal = 20.dp)
                                ) {
                                    Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Apply", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black))
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(top = 4.dp, bottom = 14.dp, start = 20.dp, end = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(displayGeometry.aspectRatio, matchHeightConstraintsFirst = true)
                        .then(sharedElementModifier)
                        .shadow(28.dp, RoundedCornerShape(cardCornerRadiusDp.dp))
                        .clip(RoundedCornerShape(cardCornerRadiusDp.dp))
                        .pointerInput(realW, realH) {
                            var rawClockX = state.clockX.floatValue
                            var rawClockY = state.clockY.floatValue
                            var rawDateX = state.dateX.floatValue
                            var rawDateY = state.dateY.floatValue

                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = true)
                                if (down.isConsumed) return@awaitEachGesture
                                val timeStart = System.currentTimeMillis()
                                var dragged = false

                                val previewScale = size.width / realW
                                val scaledDateY = (state.dateY.floatValue * densityVal) * previewScale
                                val scaledClockY = (state.clockY.floatValue * densityVal) * previewScale
                                val distToDate = abs(down.position.y - scaledDateY)
                                val distToClock = abs(down.position.y - scaledClockY)

                                val dateHitThresholdPx = (state.dateSize.floatValue * densityVal * 1.5f) * previewScale
                                val activeDrag = if (distToDate < distToClock && distToDate < dateHitThresholdPx) "DATE" else "CLOCK"

                                rawClockX = state.clockX.floatValue
                                rawClockY = state.clockY.floatValue
                                rawDateX = state.dateX.floatValue
                                rawDateY = state.dateY.floatValue

                                do {
                                    val event = awaitPointerEvent()
                                    val pan = event.calculatePan()
                                    val zoom = event.calculateZoom()

                                    if (pan.getDistance() > 2f || abs(zoom - 1f) > 0.01f) dragged = true

                                    if (dragged) {
                                        val deltaNativeDpX = (pan.x / previewScale) / densityVal
                                        val deltaNativeDpY = (pan.y / previewScale) / densityVal

                                        val maxClockSafeYDp = (realH / densityVal) - navBarDp - (state.hourSize.floatValue * 0.75f)
                                        val maxDateSafeYDp = (realH / densityVal) - navBarDp - (state.dateSize.floatValue * 1.5f)

                                        val sampleDate = if (dateText.isNotEmpty()) dateText else "Wednesday, 14 October"
                                        val dateWidthDp = datePaint.measureText(sampleDate) / densityVal
                                        val halfScreenDp = realW / densityVal / 2f
                                        val halfDateWDp = dateWidthDp / 2f
                                        val sideSafeMarginDp = 18f
                                        val minSafeX = -halfScreenDp + halfDateWDp + sideSafeMarginDp
                                        val maxSafeX = halfScreenDp - halfDateWDp - sideSafeMarginDp

                                        if (activeDrag == "DATE" && !state.dateAttached.value) {
                                            isDraggingDate = true
                                            rawDateX += deltaNativeDpX
                                            rawDateY += deltaNativeDpY
                                            state.dateX.floatValue = rawDateX.coerceIn(minSafeX, maxSafeX)
                                            state.dateY.floatValue = rawDateY.coerceIn(minSafeYDp, maxDateSafeYDp)
                                        } else {
                                            isDraggingClock = true
                                            state.hourSize.floatValue = (state.hourSize.floatValue * zoom).coerceIn(minClockSizeDp, maxClockSizeDp)
                                            state.minSize.floatValue = (state.minSize.floatValue * zoom).coerceIn(minClockSizeDp, maxClockSizeDp)
                                            rawClockX += deltaNativeDpX
                                            rawClockY += deltaNativeDpY
                                            val fontMetrics = datePaint.fontMetrics
                                            val dateLineHeightDp = (fontMetrics.descent - fontMetrics.ascent) / densityVal
                                            val dynamicGapDp = (state.strokeWidth.floatValue * 0.4f) + (dateLineHeightDp * 0.35f)
                                            val compoundDateOffsetDp = dateLineHeightDp + dynamicGapDp

                                            val effectiveMinClockYDp = if (state.dateAttached.value && state.stretchEnabled.value) {
                                                minSafeYDp + compoundDateOffsetDp
                                            } else {
                                                minSafeYDp
                                            }

                                            state.clockX.floatValue = rawClockX
                                            state.clockY.floatValue = rawClockY.coerceIn(effectiveMinClockYDp, maxClockSafeYDp)
                                        }
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })

                                if (!dragged && (System.currentTimeMillis() - timeStart) < 300L) {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    if (activeDrag == "DATE") {
                                        showDateCustomizeSheet = true
                                    } else {
                                        activeLayerTab = ActiveLayerTab.CLOCK
                                    }
                                }

                                isDraggingClock = false
                                isDraggingDate = false

                                if (abs(state.clockX.floatValue) < magneticSnapThresholdDp) state.clockX.floatValue = 0f
                                if (!state.dateAttached.value && abs(state.dateX.floatValue) < magneticSnapThresholdDp) state.dateX.floatValue = 0f
                            }
                        },
                    shape = RoundedCornerShape(cardCornerRadiusDp.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val previewScale = size.width / realW

                            drawIntoCanvas { canvas ->
                                val nativeCanvas = canvas.nativeCanvas

                                val drawClockAndDateRoutine: (android.graphics.Canvas) -> Unit = { targetCanvas ->
                                    if (dateText.isNotEmpty()) {
                                        datePaint.color = state.clockColor.intValue
                                        datePaint.textSize = state.dateSize.floatValue * densityVal
                                        val nativeDateX = (realW / 2f) + (state.dateX.floatValue * densityVal)
                                        val nativeDateY = state.dateY.floatValue * densityVal
                                        val drawBaselineY = nativeDateY - datePaint.fontMetrics.top
                                        targetCanvas.drawText(dateText, nativeDateX, drawBaselineY, datePaint)
                                    }

                                    if (timeString.isNotEmpty()) {
                                        val hPx = state.hourSize.floatValue * densityVal
                                        val mPx = state.minSize.floatValue * densityVal
                                        val isVert = state.clockLayout.intValue == 1

                                        val totalWidth = AdaptiveClockHelper.measureClockWidth(timeString, hPx, mPx, customTypeface, isVert)
                                        val nativeClockCenterX = (realW / 2f) + (state.clockX.floatValue * densityVal)
                                        val startX = nativeClockCenterX - (totalWidth / 2f)
                                        val startY = state.clockY.floatValue * densityVal

                                        val paths = AdaptiveClockHelper.buildPaths(
                                            timeString = timeString,
                                            startX = startX,
                                            startY = startY,
                                            absoluteClockX = 0f,
                                            absoluteClockY = 0f,
                                            hourSizePx = hPx,
                                            minSizePx = mPx,
                                            typeface = customTypeface,
                                            screenW = realW,
                                            screenH = realH,
                                            isStretchEnabled = state.stretchEnabled.value,
                                            isVertical = isVert,
                                            collisionMap = collisionMapArray,
                                            density = densityVal,
                                            strokeWidth = state.strokeWidth.floatValue,
                                            stretchProgress = 1f
                                        )

                                        combinedClockPath.rewind()
                                        combinedClockPath.addPath(paths.hours)
                                        combinedClockPath.addPath(paths.colon)
                                        combinedClockPath.addPath(paths.mins)
                                        combinedClockPath.computeBounds(sharedPathBounds, true)

                                        if (state.layerStack.isDepthEnabled && previewMask != null) {
                                            val ratio = AdaptiveClockHelper.calculateOcclusionRatio(
                                                clockPath = combinedClockPath,
                                                mask = previewMask,
                                                screenW = realW,
                                                screenH = realH,
                                                clockAbsoluteX = 0f,
                                                clockAbsoluteY = 0f,
                                                zoomFactor = state.liveWallpaperZoom.floatValue
                                            )
                                            val isOccludedTooMuch = ratio > state.layerStack.depthOcclusionLimit
                                            state.layerStack.clockLayer.zIndex = if (isOccludedTooMuch) 3 else 1
                                        }

                                        vectorPaint.strokeWidth = state.strokeWidth.floatValue * densityVal
                                        vectorPaint.pathEffect = CornerPathEffect(state.roundness.floatValue * densityVal)

                                        vectorPaint.color = if (state.dualTone.value) android.graphics.Color.WHITE else state.clockColor.intValue
                                        targetCanvas.drawPath(paths.hours, vectorPaint)
                                        targetCanvas.drawPath(paths.colon, vectorPaint)

                                        vectorPaint.color = state.clockColor.intValue
                                        targetCanvas.drawPath(paths.mins, vectorPaint)
                                    }
                                }

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hardwareRenderNode != null) {
                                    hardwareRenderNode.setPosition(0, 0, realW.toInt(), realH.toInt())
                                    hardwareRenderNode.pivotX = 0f
                                    hardwareRenderNode.pivotY = 0f
                                    hardwareRenderNode.scaleX = previewScale
                                    hardwareRenderNode.scaleY = previewScale

                                    val recordingCanvas = hardwareRenderNode.beginRecording(realW.toInt(), realH.toInt())
                                    try {
                                        previewOriginal?.let { bmp ->
                                            LayerCompositor.composite(
                                                canvas = recordingCanvas,
                                                screenW = realW,
                                                screenH = realH,
                                                originalBmp = bmp,
                                                maskBmp = previewMask,
                                                zoom = state.liveWallpaperZoom.floatValue,
                                                stack = state.layerStack,
                                                focusedLayerType = focusedLayerType,
                                                drawClockAndDate = drawClockAndDateRoutine
                                            )
                                        }

                                        val nsslTop = state.notificationShelfTop.floatValue
                                        if (nsslTop > 0f && isDraggingClock && sharedPathBounds.bottom > nsslTop) {
                                            recordingCanvas.drawLine(
                                                16f * densityVal, nsslTop,
                                                realW - (16f * densityVal), nsslTop,
                                                notificationPaint
                                            )
                                        }

                                        val sensorR = state.udfpsRadius.floatValue
                                        if (sensorR > 0f && state.udfpsX.floatValue > 0f) {
                                            val cx = state.udfpsX.floatValue
                                            val cy = state.udfpsY.floatValue
                                            val sensorBufferPx = sensorR * 0.15f

                                            val isOverlapping = (cx in (sharedPathBounds.left - sensorBufferPx)..(sharedPathBounds.right + sensorBufferPx)) &&
                                                    (cy in (sharedPathBounds.top - sensorBufferPx)..(sharedPathBounds.bottom + sensorBufferPx))

                                            udfpsPaint.color = if (isOverlapping) {
                                                android.graphics.Color.argb(230, 255, 82, 82)
                                            } else {
                                                android.graphics.Color.argb(90, 255, 255, 255)
                                            }
                                            udfpsPaint.strokeWidth = (if (isOverlapping) 2.5f else 1.5f) * densityVal

                                            recordingCanvas.drawCircle(cx, cy, sensorR, udfpsPaint)
                                        }
                                    } finally {
                                        hardwareRenderNode.endRecording()
                                    }

                                    nativeCanvas.drawRenderNode(hardwareRenderNode)
                                } else {
                                    nativeCanvas.save()
                                    nativeCanvas.scale(previewScale, previewScale)
                                    previewOriginal?.let { bmp ->
                                        LayerCompositor.composite(
                                            canvas = nativeCanvas,
                                            screenW = realW,
                                            screenH = realH,
                                            originalBmp = bmp,
                                            maskBmp = previewMask,
                                            zoom = state.liveWallpaperZoom.floatValue,
                                            stack = state.layerStack,
                                            focusedLayerType = focusedLayerType,
                                            drawClockAndDate = drawClockAndDateRoutine
                                        )
                                    }
                                    nativeCanvas.restore()
                                }
                            }

                            if (isDraggingClock) {
                                val scale = size.width / realW
                                val padXPx = (state.strokeWidth.floatValue * densityVal + 8f * densityVal) * scale
                                val padYPx = (state.strokeWidth.floatValue * densityVal * 0.5f + 6f * densityVal) * scale

                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.6f),
                                    topLeft = Offset(
                                        sharedPathBounds.left * scale - padXPx,
                                        sharedPathBounds.top * scale - padYPx
                                    ),
                                    size = Size(
                                        sharedPathBounds.width() * scale + (padXPx * 2f),
                                        sharedPathBounds.height() * scale + (padYPx * 2f)
                                    ),
                                    cornerRadius = CornerRadius(16.dp.toPx()),
                                    style = Stroke(
                                        width = 2.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                                    )
                                )
                            }
                        }
                        
                        if (currentLayerActions.size > 1) {
                            VerticalLayerActionSwitcher(
                                actions = currentLayerActions,
                                selectedIndex = activeActionIndex,
                                onActionSelected = { activeActionIndex = it },
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 12.dp)
                            )
                        }
                    }
                }
            }

            if (showLayersSheet) {
                EditorLayersPanel(
                    layerStack = state.layerStack,
                    previewOriginal = previewOriginal,
                    previewMask = previewMask,
                    activeLayerTab = activeLayerTab,
                    onSelectLayerTab = { activeLayerTab = it },
                    onDismiss = { showLayersSheet = false }
                )
            }

            if (showDateCustomizeSheet) {
                EditorDatePanel(
                    viewModel = viewModel,
                    wallpaper = wallpaper,
                    state = state,
                    onDismiss = { showDateCustomizeSheet = false }
                )
            }

            if (showApplyOptions) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                AmbientBottomSheet(
                    onDismissRequest = { showApplyOptions = false },
                    sheetState = sheetState,
                    viewModel = viewModel,
                    currentWallpaper = wallpaper
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                            .navigationBarsPadding()
                    ) {
                        StaggeredRow(0) {
                            Text(
                                "Apply Lock Screen",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                                modifier = Modifier.padding(bottom = 20.dp)
                            )
                        }
                        StaggeredRow(1) {
                            DestinationButton(
                                Icons.Default.Lock,
                                "Clock Face Only",
                                "Update lock screen clock layout, depth & style",
                                false
                            ) {
                                saveAndBroadcastClockSettings(context, state, viewModel, previewMask, realW, realH)
                                onApplyRequested(WallpaperDestinations.FLAG_LOCK_SCREEN)
                                showApplyOptions = false
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        StaggeredRow(2) {
                            DestinationButton(
                                Icons.Default.PhotoLibrary,
                                "Clock + Wallpaper",
                                "Apply depth clock style and set static background",
                                false
                            ) {
                                saveAndBroadcastClockSettings(context, state, viewModel, previewMask, realW, realH)
                                onApplyRequested(WallpaperDestinations.FLAG_BOTH)
                                showApplyOptions = false
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * Clean 5-tool vertical switcher without the extra Size or hanging Depth button.
 */
@Composable
private fun VerticalLayerActionSwitcher(
    actions: List<LayerAction>,
    selectedIndex: Int,
    onActionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Surface(
        modifier = modifier
            .wrapContentWidth()
            .shadow(12.dp, CircleShape)
            .clip(CircleShape)
            .pointerInput(actions.size, selectedIndex) {
                detectVerticalDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulator += dragAmount
                        val thresholdPx = 32f
                        if (abs(dragAccumulator) > thresholdPx) {
                            val step = if (dragAccumulator > 0) 1 else -1
                            val newIdx = (selectedIndex + step).coerceIn(0, actions.size - 1)
                            if (newIdx != selectedIndex) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onActionSelected(newIdx)
                            }
                            dragAccumulator = 0f
                        }
                    }
                )
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 5.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            actions.forEachIndexed { index, action ->
                val isSelected = index == selectedIndex
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.05f else 0.88f,
                    animationSpec = spring(stiffness = 500f, dampingRatio = 0.75f),
                    label = "actionScale"
                )

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                            else Color.Transparent
                        )
                        .clickable {
                            if (!isSelected) {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onActionSelected(index)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = action.label,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactFontSelectorStrip(
    availableFonts: List<String>,
    selectedFont: String,
    onFontSelected: (String) -> Unit,
    hours: String,
    mins: String,
    clockColor: Int,
    isDualTone: Boolean,
    isVertical: Boolean
) {
    val context = LocalContext.current
    val view = LocalView.current

    LazyRow(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(availableFonts) { fontFile ->
            val isSelected = selectedFont == fontFile
            val androidTypeface = remember(fontFile) {
                if (fontFile == "default" || fontFile.isEmpty()) {
                    Typeface.create("sans-serif-bold", Typeface.NORMAL)
                } else {
                    try {
                        Typeface.createFromAsset(context.assets, "fonts/$fontFile")
                    } catch (_: Exception) {
                        Typeface.create("sans-serif-bold", Typeface.NORMAL)
                    }
                }
            }
            val composeFont = remember(androidTypeface) { FontFamily(androidTypeface) }

            Surface(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onFontSelected(fontFile)
                    },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$hours:$mins",
                        fontFamily = composeFont,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = 15.sp),
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Compact Color Selector with Android 17 EyeDropper API and fallback.
 */
@Composable
private fun CompactColorSelectorStrip(
    extractedColors: List<Int>,
    selectedColor: Int,
    onColorSelected: (Int) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    var showCustomPicker by remember { mutableStateOf(false) }
    
    val eyeDropperLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val colorInt = result.data?.getIntExtra("android.intent.extra.COLOR", selectedColor)
                ?: result.data?.getIntExtra(Intent.EXTRA_COLOR, selectedColor)
                ?: selectedColor
            onColorSelected(colorInt)
        }
    }

    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    val intent = Intent("android.intent.action.OPEN_EYE_DROPPER")
                    try {
                        eyeDropperLauncher.launch(intent)
                    } catch (_: Exception) {
                    	
                        showCustomPicker = true
                    }
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Colorize,
                    contentDescription = "Pick Custom Color",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(extractedColors) { colorInt ->
                val isSelected = colorInt == selectedColor
                val composeColor = safeColor(colorInt)
                val isBright = composeColor.luminance() > 0.5f

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable {
                            if (!isSelected) {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                onColorSelected(colorInt)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize(if (isSelected) 0.8f else 0.95f)
                            .clip(CircleShape)
                            .background(composeColor),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = if (isBright) Color.Black else Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCustomPicker) {
        ExpressiveCustomColorPickerDialog(
            initialColor = selectedColor,
            onDismiss = { showCustomPicker = false },
            onColorConfirmed = { chosen ->
                onColorSelected(chosen)
                showCustomPicker = false
            }
        )
    }
}

/**
 * Options Strip with the Depth toggle moved alongside the Adaptive chip.
 */
@Composable
private fun CompactClockOptionsStrip(
    isVertical: Boolean,
    onToggleLayout: () -> Unit,
    isDualTone: Boolean,
    onToggleDualTone: () -> Unit,
    isDepthEnabled: Boolean,
    onToggleDepth: () -> Unit,
    isAdaptive: Boolean,
    onToggleAdaptive: () -> Unit,
    onAutoFit: () -> Unit
) {
    val view = LocalView.current

    LazyRow(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            FilterChip(
                selected = isVertical,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onToggleLayout()
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (isVertical) Icons.Rounded.SwapVert else Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text(if (isVertical) "Vertical" else "Horizontal", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                shape = CircleShape
            )
        }

        item {
            FilterChip(
                selected = isDualTone,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onToggleDualTone()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Contrast,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Dual Tone", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                shape = CircleShape
            )
        }
        
        item {
            FilterChip(
                selected = isDepthEnabled,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onToggleDepth()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Layers,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Depth", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                shape = CircleShape
            )
        }

        item {
            FilterChip(
                selected = isAdaptive,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onToggleAdaptive()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Transform,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Adaptive", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                shape = CircleShape
            )
        }

        item {
            FilterChip(
                selected = false,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onAutoFit()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.AutoFixHigh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Auto Fit", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                shape = CircleShape
            )
        }
    }
}

@Composable
private fun SlimRulerSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    icon: ImageVector,
    label: String,
    valueDisplay: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val view = LocalView.current
    var lastHapticTickIndex by remember { mutableIntStateOf(-1) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = accentColor
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = valueDisplay,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 12.5.sp
                ),
                color = accentColor
            )
        }

        Spacer(Modifier.height(3.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(valueRange) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val widthPx = size.width.toFloat()
                        val newProgress = (change.position.x / widthPx).coerceIn(0f, 1f)
                        val newValue = valueRange.start + newProgress * (valueRange.endInclusive - valueRange.start)
                        onValueChange(newValue)

                        val tickSpacingPx = 5f * view.resources.displayMetrics.density
                        val totalTicks = (widthPx / tickSpacingPx).toInt().coerceAtLeast(1)
                        val currentTick = (newProgress * totalTicks).toInt()

                        if (currentTick != lastHapticTickIndex) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            } else {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            }
                            lastHapticTickIndex = currentTick
                        }
                    }
                }
                .pointerInput(valueRange) {
                    detectTapGestures { offset ->
                        val newProgress = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val newValue = valueRange.start + newProgress * (valueRange.endInclusive - valueRange.start)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onValueChange(newValue)
                    }
                }
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            val progress = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
            val indicatorXPx = progress * widthPx

            val normalTickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
            val majorTickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

            Canvas(modifier = Modifier.fillMaxSize()) {
                val tickSpacing = 5.dp.toPx()
                val totalTicks = (size.width / tickSpacing).toInt()
                val centerY = size.height / 2f

                for (i in 0..totalTicks) {
                    val x = i * tickSpacing
                    val isMajor = (i % 5 == 0)
                    val tickHeight = if (isMajor) 11.dp.toPx() else 6.dp.toPx()
                    val tickColor = if (isMajor) majorTickColor else normalTickColor

                    drawLine(
                        color = tickColor,
                        start = Offset(x, centerY - tickHeight / 2f),
                        end = Offset(x, centerY + tickHeight / 2f),
                        strokeWidth = if (isMajor) 1.5.dp.toPx() else 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                val thumbWidth = 4.dp.toPx()
                val thumbHeight = 15.dp.toPx()
                drawRoundRect(
                    color = accentColor,
                    topLeft = Offset(indicatorXPx - thumbWidth / 2f, centerY - thumbHeight / 2f),
                    size = Size(thumbWidth, thumbHeight),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )

                val underlineWidth = 24.dp.toPx()
                drawLine(
                    color = accentColor.copy(alpha = 0.8f),
                    start = Offset((indicatorXPx - underlineWidth / 2f).coerceAtLeast(0f), size.height - 1.dp.toPx()),
                    end = Offset((indicatorXPx + underlineWidth / 2f).coerceAtMost(size.width), size.height - 1.dp.toPx()),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

private fun saveAndBroadcastClockSettings(
    context: Context,
    state: ClockStudioState,
    viewModel: WallpaperViewModel,
    previewMask: Bitmap?,
    realW: Float,
    realH: Float
) {
    viewModel.updateClockLayout(context, state.clockLayout.intValue)
    viewModel.updateDualTone(context, state.dualTone.value)
    viewModel.updateClockFont(context, state.clockFont.value)
    viewModel.updateClockColor(context, state.clockColor.intValue)
    viewModel.updateDateLayout(context, state.dateFormat.intValue)
    viewModel.updateDateAttached(context, state.dateAttached.value)
    viewModel.updateClockStyle(
        context, state.hourSize.floatValue, state.minSize.floatValue,
        state.strokeWidth.floatValue, state.roundness.floatValue,
        state.dateSize.floatValue
    )
    viewModel.updateLockscreenClockPosition(context, state.clockX.floatValue, state.clockY.floatValue)
    viewModel.updateLockscreenDatePosition(context, state.dateX.floatValue, state.dateY.floatValue)
    viewModel.toggleClockStretch(context, state.stretchEnabled.value, previewMask, realW, realH)

    viewModel.saveLayerStackSettings(context, state.layerStack)
}
