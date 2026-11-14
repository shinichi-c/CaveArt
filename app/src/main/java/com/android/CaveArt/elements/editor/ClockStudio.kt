package com.android.CaveArt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.CaveArt.clockkit.*
import com.android.CaveArt.layer.LayerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    val state = rememberClockStudioState(viewModel)
    var previewOriginal by remember { mutableStateOf<Bitmap?>(null) }
    var previewMask by remember { mutableStateOf<Bitmap?>(null) }
    var isMlLoading by remember(wallpaper.id) { mutableStateOf(true) }

    var showDateCustomizeSheet by remember { mutableStateOf(false) }
    var isStyleBrowseMode by remember { mutableStateOf(false) }
    var showLayersSheet by remember { mutableStateOf(false) }
    var showApplyOptions by remember { mutableStateOf(false) }

    var activeLayerTab by remember { mutableStateOf(ActiveLayerTab.CLOCK) }
    var activeActionIndex by remember { mutableIntStateOf(0) }

    val activeStyle = remember(state.clockKitConfig.styleId) {
        ClockKitEngine.getStyle(state.clockKitConfig.styleId)
    }

    var focusedCarouselIndex by remember(activeStyle.id) {
        mutableIntStateOf(ClockKitEngine.styles.indexOfFirst { it.id == activeStyle.id }.coerceAtLeast(0))
    }
    var focusedCarouselStyle by remember(activeStyle.id) {
        mutableStateOf(activeStyle)
    }

    BackHandler(enabled = isStyleBrowseMode) {
        isStyleBrowseMode = false
    }

    LaunchedEffect(activeLayerTab) {
        activeActionIndex = 0
    }

    LaunchedEffect(state.layerStack.isDepthEnabled, state.layerStack.depthOcclusionLimit) {
        if (state.clockKitConfig.isDepthEnabled != state.layerStack.isDepthEnabled ||
            state.clockKitConfig.depthOcclusionLimit != state.layerStack.depthOcclusionLimit
        ) {
            state.updateConfig {
                it.copy(
                    isDepthEnabled = state.layerStack.isDepthEnabled,
                    depthOcclusionLimit = state.layerStack.depthOcclusionLimit
                )
            }
        }
    }

    val currentLayerActions = remember(
        activeLayerTab,
        activeStyle.id,
        state.clockKitConfig.isGravityEnabled,
        state
    ) {
        when (activeLayerTab) {
            ActiveLayerTab.ALL -> listOf(
                LayerAction(
                    id = "all_occlusion",
                    label = "Depth Occlusion",
                    icon = Icons.Rounded.Layers,
                    toolType = ContextualToolType.SLIDER,
                    getVal = { state.layerStack.depthOcclusionLimit },
                    setVal = {
                        state.layerStack.depthOcclusionLimit = it
                        state.updateConfig { cfg -> cfg.copy(depthOcclusionLimit = it) }
                    },
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
            ActiveLayerTab.CLOCK -> buildList {
                activeStyle.describeControls(state.clockKitConfig).forEach { ctrl ->
                    val toolType = when (ctrl) {
                        is ClockControl.Slider -> ContextualToolType.SLIDER
                        is ClockControl.Toggle -> ContextualToolType.TOGGLE
                        is ClockControl.Choice -> ContextualToolType.CHOICE
                        is ClockControl.FontPicker -> ContextualToolType.FONT
                        is ClockControl.ColorPicker -> ContextualToolType.COLOR
                        is ClockControl.QuickChips -> ContextualToolType.QUICK_CHIPS
                    }
                    if (ctrl is ClockControl.Slider) {
                        add(
                            LayerAction(
                                id = "ck_${activeStyle.id}_${ctrl.id}",
                                label = ctrl.label,
                                icon = clockIconToImageVector(ctrl.icon),
                                toolType = toolType,
                                getVal = { state.clockKitConfig.getFloat(activeStyle.id, ctrl.id, ctrl.defaultValue) },
                                setVal = { v -> state.updateConfig { it.withFloat(activeStyle.id, ctrl.id, v) } },
                                range = ctrl.min..ctrl.max,
                                formatDisplay = ctrl.formatter,
                                clockControl = ctrl
                            )
                        )
                    } else {
                        add(
                            LayerAction(
                                id = "ck_${activeStyle.id}_${ctrl.id}",
                                label = ctrl.label,
                                icon = clockIconToImageVector(ctrl.icon),
                                toolType = toolType,
                                clockControl = ctrl
                            )
                        )
                    }
                }
                if (state.clockKitConfig.isGravityEnabled) {
                    add(
                        LayerAction(
                            id = "clock_gravity_intensity",
                            label = "Gravity Slant",
                            icon = Icons.Rounded.Explore,
                            toolType = ContextualToolType.SLIDER,
                            getVal = { state.clockKitConfig.gravityIntensity },
                            setVal = { v -> state.updateConfig { it.copy(gravityIntensity = v) } },
                            range = 0.2f..2.5f,
                            formatDisplay = { String.format("%.1fx", it) }
                        )
                    )
                }
            }
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
            ActiveLayerTab.DEPTH -> listOf(
                LayerAction(
                    id = "depth_opacity",
                    label = "Heatmap Opacity",
                    icon = Icons.Rounded.Visibility,
                    toolType = ContextualToolType.SLIDER,
                    getVal = { state.layerStack.depthVisionLayer.opacity },
                    setVal = {
                        state.layerStack.depthVisionLayer.opacity = it
                        state.layerStack.depthVisionLayer.isVisible = it > 0.05f
                    },
                    range = 0.0f..1.0f,
                    formatDisplay = { "${(it * 100).toInt()}%" }
                ),
                LayerAction(
                    id = "depth_occlusion",
                    label = "Occlusion Threshold",
                    icon = Icons.Rounded.Layers,
                    toolType = ContextualToolType.SLIDER,
                    getVal = { state.layerStack.depthOcclusionLimit },
                    setVal = {
                        state.layerStack.depthOcclusionLimit = it
                        state.updateConfig { cfg -> cfg.copy(depthOcclusionLimit = it) }
                    },
                    range = 0.10f..0.85f,
                    formatDisplay = { "${(it * 100).toInt()}%" }
                )
            )
        }
    }

    LaunchedEffect(currentLayerActions.size) {
        if (activeActionIndex >= currentLayerActions.size) {
            activeActionIndex = 0
        }
    }

    val safeActionIndex = activeActionIndex.coerceIn(0, (currentLayerActions.size - 1).coerceAtLeast(0))
    val activeAction = currentLayerActions.getOrNull(safeActionIndex) ?: currentLayerActions.first()

    var extractedColors by remember { mutableStateOf<List<Int>>(emptyList()) }
    var timeString by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    val availableFonts = remember { viewModel.getAvailableFonts(context) }

    val activeHourSize = state.clockKitConfig.getFloat("adaptive_vector", "hour_size", 100f)
    LaunchedEffect(activeHourSize, state.clockKitConfig.styleId) {
        state.dateSize.floatValue = if (state.clockKitConfig.styleId == "adaptive_vector") {
            (activeHourSize * 0.24f).coerceIn(14f, 34f)
        } else {
            (20f * state.clockKitConfig.scale).coerceIn(14f, 32f)
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
            val safeCtx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.createDeviceProtectedStorageContext()
            } else context
            val prefs = safeCtx.getSharedPreferences(ClockKitConfigStore.PREFS_NAME, Context.MODE_PRIVATE)
            val zoom = prefs.getFloat("telemetry_zoom", displayGeometry.wallpaperMaxScale)
            val nssl = prefs.getFloat("telemetry_nssl_top", -1f)
            val ux = prefs.getFloat("udfps_x", -1f)
            val uy = prefs.getFloat("udfps_y", -1f)
            val ur = prefs.getFloat("udfps_radius", 0f)

            withContext(Dispatchers.Main) {
                state.liveWallpaperZoom.floatValue = zoom
                state.notificationShelfTop.floatValue = nssl
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
        onDispose { try { context.unregisterReceiver(receiver) } catch (_: Exception) {} }
    }

    LaunchedEffect(wallpaper.id) {
        isMlLoading = true
        val components = viewModel.getHighQualityComponents(context, wallpaper)
        previewOriginal = components.first
        previewMask = components.second ?: viewModel.getMaskForClock(context, wallpaper)

        scope.launch(Dispatchers.IO) {
            val depthResult = viewModel.getOrGenerateDepth(context, wallpaper)
            withContext(Dispatchers.Main) {
                state.layerStack.depthVisionLayer.depthHeatMap = depthResult?.heatMapBitmap
                state.layerStack.depthVisionLayer.rawDepthMap = depthResult?.rawDepthMap
            }
        }

        isMlLoading = false
    }

    val currentMask = previewMask
    val currentZoom = state.liveWallpaperZoom.floatValue
    val needsCollisionMap = activeStyle.needsCollisionMap(state.clockKitConfig)
    LaunchedEffect(currentMask, needsCollisionMap, currentZoom) {
        if (needsCollisionMap && currentMask != null) {
            withContext(Dispatchers.Default) {
                val mapStr = AdaptiveClockHelper.generateCollisionMap(currentMask, realW, realH, currentZoom)
                withContext(Dispatchers.Main) { state.collisionMap.value = mapStr }
            }
        } else if (!needsCollisionMap) {
            state.collisionMap.value = ""
        }
    }

    LaunchedEffect(state.clockKitConfig.dateFormat) {
        while (true) {
            val now = System.currentTimeMillis()
            val is24 = android.text.format.DateFormat.is24HourFormat(context)
            timeString = java.text.SimpleDateFormat(
                if (is24) "HH:mm" else "hh:mm",
                java.util.Locale.getDefault()
            ).format(java.util.Date(now))
            dateText = ClockTypefaceResolver.formatDate(now, state.clockKitConfig.dateFormat)
            delay(1000L)
        }
    }

    LaunchedEffect(wallpaper, isDarkTheme) {
        withContext(Dispatchers.IO) {
            val dynamicTheme = MonetEngine.getThemePalette(context, wallpaper, isDarkTheme)
            withContext(Dispatchers.Main) {
                extractedColors = dynamicTheme.allColors
                if (state.clockKitConfig.color == 0 || isDefaultColor(state.clockKitConfig.color)) {
                    val picked = dynamicTheme.tonalSpot.firstOrNull() ?: dynamicTheme.seedColor
                    state.updateConfig { it.copy(color = picked) }
                }
            }
        }
    }

    val customTypeface = remember(state.clockKitConfig.font) {
        ClockTypefaceResolver.resolve(context, state.clockKitConfig.font)
    }

    val collisionMapArray = remember(state.collisionMap.value) {
        if (state.collisionMap.value.isNotEmpty()) {
            state.collisionMap.value.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray()
        } else null
    }

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
                            text = if (isStyleBrowseMode) "Choose Style" else "Lock Screen",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (isStyleBrowseMode) isStyleBrowseMode = false else onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        if (!isStyleBrowseMode) {
                            IconButton(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    val willBeVisible = !state.layerStack.depthVisionLayer.isVisible
                                    state.layerStack.depthVisionLayer.isVisible = willBeVisible
                                    if (willBeVisible) {
                                        state.layerStack.depthVisionLayer.opacity = 1.0f
                                        activeLayerTab = ActiveLayerTab.DEPTH
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ViewInAr,
                                    contentDescription = "Toggle 3D Depth Vision",
                                    tint = if (state.layerStack.depthVisionLayer.isVisible) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

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
                            val slotKey: Any = if (isStyleBrowseMode) "STYLE_BROWSE" else activeAction.toolType
                            AnimatedContent(
                                targetState = slotKey,
                                transitionSpec = {
                                    (fadeIn(tween(180)) + slideInVertically { it / 3 }) togetherWith
                                    (fadeOut(tween(140)) + slideOutVertically { -it / 3 })
                                },
                                label = "dynamicToolSlot"
                            ) { key ->
                                if (key == "STYLE_BROWSE") {
                                    MinimalStyleBrowseFooter(
                                        style = focusedCarouselStyle,
                                        currentIndex = focusedCarouselIndex,
                                        totalCount = ClockKitEngine.styles.size
                                    )
                                } else {
                                    when (key as ContextualToolType) {
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
                                        ContextualToolType.CHOICE -> {
                                            val ctrl = activeAction.clockControl as? ClockControl.Choice
                                            if (ctrl != null) {
                                                CompactChoiceStrip(
                                                    options = ctrl.options,
                                                    selectedKey = state.clockKitConfig.getString(activeStyle.id, ctrl.id, ctrl.defaultKey),
                                                    onSelected = { selectedKey ->
                                                        state.updateConfig { it.withString(activeStyle.id, ctrl.id, selectedKey) }
                                                    }
                                                )
                                            }
                                        }
                                        ContextualToolType.QUICK_CHIPS, ContextualToolType.OPTIONS -> {
                                            val ctrl = activeAction.clockControl as? ClockControl.QuickChips
                                            if (ctrl != null) {
                                                CompactQuickChipsStrip(
                                                    items = ctrl.items,
                                                    styleId = activeStyle.id,
                                                    config = state.clockKitConfig,
                                                    onConfigChange = { next -> state.updateConfig { next } },
                                                    onCustomAction = { actionId ->
                                                        scope.launch(Dispatchers.Default) {
                                                            val updated = activeStyle.onAction(
                                                                actionId = actionId,
                                                                config = state.clockKitConfig,
                                                                mask = previewMask,
                                                                screenW = realW,
                                                                screenH = realH,
                                                                density = densityVal
                                                            )
                                                            withContext(Dispatchers.Main) {
                                                                state.updateConfig { updated }
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                        ContextualToolType.TOGGLE -> {
                                            val ctrl = activeAction.clockControl as? ClockControl.Toggle
                                            if (ctrl != null) {
                                                CompactSingleToggleBar(
                                                    label = ctrl.label,
                                                    icon = activeAction.icon,
                                                    checked = state.clockKitConfig.getBool(activeStyle.id, ctrl.id, ctrl.defaultValue),
                                                    onCheckedChange = { checked ->
                                                        state.updateConfig { it.withBool(activeStyle.id, ctrl.id, checked) }
                                                    }
                                                )
                                            }
                                        }
                                        ContextualToolType.FONT -> {
                                            CompactFontSelectorStrip(
                                                availableFonts = availableFonts,
                                                selectedFont = state.clockKitConfig.font,
                                                onFontSelected = { fontName ->
                                                    state.updateConfig { it.copy(font = fontName) }
                                                },
                                                hours = hoursString,
                                                mins = minsString
                                            )
                                        }
                                        ContextualToolType.COLOR -> {
                                            CompactColorSelectorStrip(
                                                extractedColors = extractedColors,
                                                selectedColor = state.clockKitConfig.color,
                                                onColorSelected = { col ->
                                                    state.updateConfig { it.copy(color = col) }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                            shadowElevation = 8.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(58.dp)
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    val isClockActive = !isStyleBrowseMode && activeLayerTab == ActiveLayerTab.CLOCK
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isClockActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .height(44.dp)
                                            .clickable {
                                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                                isStyleBrowseMode = false
                                                activeLayerTab = ActiveLayerTab.CLOCK
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Schedule,
                                                contentDescription = "Clock",
                                                modifier = Modifier.size(16.dp),
                                                tint = if (isClockActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "Clock",
                                                maxLines = 1,
                                                softWrap = false,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.5.sp),
                                                color = if (isClockActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = CircleShape,
                                        color = if (isStyleBrowseMode) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .height(44.dp)
                                            .clickable {
                                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                                isStyleBrowseMode = !isStyleBrowseMode
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.AutoAwesome,
                                                contentDescription = "Style",
                                                modifier = Modifier.size(16.dp),
                                                tint = if (isStyleBrowseMode) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "Style",
                                                maxLines = 1,
                                                softWrap = false,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.5.sp),
                                                color = if (isStyleBrowseMode) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    val isNonClockActive = !isStyleBrowseMode && activeLayerTab != ActiveLayerTab.CLOCK
                                    Surface(
                                        shape = CircleShape,
                                        color = if (showLayersSheet || isNonClockActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .height(44.dp)
                                            .clickable {
                                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                                isStyleBrowseMode = false
                                                showLayersSheet = true
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Layers,
                                                contentDescription = "Layers",
                                                modifier = Modifier.size(16.dp),
                                                tint = if (showLayersSheet || isNonClockActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "Layers",
                                                maxLines = 1,
                                                softWrap = false,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.5.sp),
                                                color = if (showLayersSheet || isNonClockActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        isStyleBrowseMode = false
                                        showApplyOptions = true
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier.height(44.dp),
                                    contentPadding = PaddingValues(horizontal = 15.dp)
                                ) {
                                    Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = "Apply",
                                        maxLines = 1,
                                        softWrap = false,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black, fontSize = 12.sp)
                                    )
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
                    .padding(
                        top = 4.dp,
                        bottom = 14.dp,
                        start = if (isStyleBrowseMode) 0.dp else 20.dp,
                        end = if (isStyleBrowseMode) 0.dp else 20.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = isStyleBrowseMode && !isMlLoading,
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(initialScale = 0.94f, animationSpec = tween(240))) togetherWith
                        (fadeOut(tween(160)) + scaleOut(targetScale = 0.94f, animationSpec = tween(180)))
                    },
                    label = "previewCarouselSwitch"
                ) { browsingStyles ->
                    if (browsingStyles) {
                        ClockStyleCarousel(
                            currentConfig = state.clockKitConfig,
                            displayGeometry = displayGeometry,
                            previewOriginal = previewOriginal,
                            previewMask = previewMask,
                            rawDepthMap = state.layerStack.depthVisionLayer.rawDepthMap,
                            liveWallpaperZoom = state.liveWallpaperZoom.floatValue,
                            dateSizeDp = state.dateSize.floatValue,
                            timeString = timeString,
                            dateText = dateText,
                            customTypeface = customTypeface,
                            collisionMapArray = collisionMapArray,
                            onStyleChanged = { settled ->
                                state.updateConfig { it.copy(styleId = settled.id) }
                            },
                            onConfirmAndExit = { chosen ->
                                state.updateConfig { it.copy(styleId = chosen.id) }
                                activeLayerTab = ActiveLayerTab.CLOCK
                                isStyleBrowseMode = false
                            },
                            onFocusedPageChanged = { idx, style ->
                                focusedCarouselIndex = idx
                                focusedCarouselStyle = style
                            }
                        )
                    } else {
                        ClockStudioPreviewCard(
                            state = state,
                            wallpaper = wallpaper,
                            viewModel = viewModel,
                            isMlLoading = isMlLoading,
                            displayGeometry = displayGeometry,
                            previewOriginal = previewOriginal,
                            previewMask = previewMask,
                            timeString = timeString,
                            dateText = dateText,
                            customTypeface = customTypeface,
                            collisionMapArray = collisionMapArray,
                            focusedLayerType = focusedLayerType,
                            currentLayerActions = currentLayerActions,
                            activeActionIndex = safeActionIndex,
                            onActionSelected = { activeActionIndex = it },
                            sharedElementModifier = sharedElementModifier,
                            onDateClick = { showDateCustomizeSheet = true },
                            onClockClick = { activeLayerTab = ActiveLayerTab.CLOCK }
                        )
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
