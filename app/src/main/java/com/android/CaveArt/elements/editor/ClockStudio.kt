package com.android.CaveArt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
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
    var showLayersSheet by remember { mutableStateOf(false) }
    var showApplyOptions by remember { mutableStateOf(false) }

    var activeLayerTab by remember { mutableStateOf(ActiveLayerTab.CLOCK) }
    var activeActionIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(activeLayerTab) {
        activeActionIndex = 0
    }

    val minClockSizeDp = (realW * 0.08f) / densityVal
    val maxClockSizeDp = (realW * 0.55f) / densityVal

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

    LaunchedEffect(state.hourSize.floatValue) {
        state.dateSize.floatValue = (state.hourSize.floatValue * 0.24f).coerceIn(14f, 34f)
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
        isMlLoading = false
    }

    val currentMask = previewMask
    val currentZoom = state.liveWallpaperZoom.floatValue
    LaunchedEffect(currentMask, state.stretchEnabled.value, currentZoom) {
        if (state.stretchEnabled.value && currentMask != null) {
            withContext(Dispatchers.Default) {
                val mapStr = AdaptiveClockHelper.generateCollisionMap(currentMask, realW, realH, currentZoom)
                withContext(Dispatchers.Main) { state.collisionMap.value = mapStr }
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
                                            mins = minsString
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
                    activeActionIndex = activeActionIndex,
                    onActionSelected = { activeActionIndex = it },
                    sharedElementModifier = sharedElementModifier,
                    onDateClick = { showDateCustomizeSheet = true },
                    onClockClick = { activeLayerTab = ActiveLayerTab.CLOCK }
                )
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
