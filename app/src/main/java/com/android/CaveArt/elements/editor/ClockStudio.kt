package com.android.CaveArt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RenderNode
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

class ClockStudioState(viewModel: WallpaperViewModel) {
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
    val dateSize: MutableFloatState = mutableFloatStateOf(viewModel.clockMinuteSize * 0.27f)
    
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
    
    val displayGeometry = remember { NativeDisplayMetrics.getGeometry(context) }
    val realW = displayGeometry.widthPx
    val realH = displayGeometry.heightPx
    val densityVal = displayGeometry.density
    
    val statusBarDp = displayGeometry.statusBarHeightPx / densityVal
    val navBarDp = displayGeometry.navBarHeightPx / densityVal
    val minSafeYDp = statusBarDp + (displayGeometry.statusBarHeightPx * 0.1f / densityVal)
    val minClockSizeDp = (realW * 0.08f) / densityVal
    val maxClockSizeDp = (realW * 0.55f) / densityVal
    val magneticSnapThresholdDp = (realW * 0.025f) / densityVal

    val state = rememberClockStudioState(viewModel)
    var previewOriginal by remember { mutableStateOf<Bitmap?>(null) }
    var previewMask by remember { mutableStateOf<Bitmap?>(null) }

    var isDraggingClock by remember { mutableStateOf(false) }
    var isDraggingDate by remember { mutableStateOf(false) }
    var showCustomizeSheet by remember { mutableStateOf(false) }
    var showDateCustomizeSheet by remember { mutableStateOf(false) }
    var showApplyOptions by remember { mutableStateOf(false) }
    var extractedColors by remember { mutableStateOf<List<Int>>(emptyList()) }

    var timeString by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    val availableFonts = remember { viewModel.getAvailableFonts(context) }
    
    val wallpaperPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val wallpaperMatrix = remember { Matrix() }
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
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 20f), 0f)
        }
    }
    val udfpsPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(15f, 15f), 0f)
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
    
    LaunchedEffect(
        state.clockX.floatValue,
        state.clockY.floatValue,
        state.dateAttached.value,
        state.hourSize.floatValue,
        state.clockLayout.intValue,
        state.dateSize.floatValue
    ) {
        if (state.dateAttached.value) {
            state.dateX.floatValue = state.clockX.floatValue

            datePaint.textSize = state.dateSize.floatValue * densityVal
            val fontMetrics = datePaint.fontMetrics
            val dateLineHeightDp = (fontMetrics.descent - fontMetrics.ascent) / densityVal
            val dynamicGapDp = (state.strokeWidth.floatValue * 0.4f) + (dateLineHeightDp * 0.3f)
            val dynamicOffsetDp = dateLineHeightDp + dynamicGapDp

            state.dateY.floatValue = max(minSafeYDp, state.clockY.floatValue - dynamicOffsetDp)
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
                            "Lock Screen Preview",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Rounded.ArrowBack, "Back")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
            },
            bottomBar = {
                if (!isLandscape) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                showApplyOptions = true
                            },
                            modifier = Modifier
                                .fillMaxWidth(0.88f)
                                .height(58.dp)
                                .shadow(14.dp, CircleShape, ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Set Lock Screen", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black))
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp, vertical = 6.dp),
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

                                        if (activeDrag == "DATE" && !state.dateAttached.value) {
                                            isDraggingDate = true
                                            rawDateX += deltaNativeDpX
                                            rawDateY += deltaNativeDpY
                                            state.dateX.floatValue = rawDateX
                                            state.dateY.floatValue = rawDateY.coerceIn(minSafeYDp, maxDateSafeYDp)
                                        } else {
                                            isDraggingClock = true
                                            state.hourSize.floatValue = (state.hourSize.floatValue * zoom).coerceIn(minClockSizeDp, maxClockSizeDp)
                                            state.minSize.floatValue = (state.minSize.floatValue * zoom).coerceIn(minClockSizeDp, maxClockSizeDp)
                                            rawClockX += deltaNativeDpX
                                            rawClockY += deltaNativeDpY
                                            state.clockX.floatValue = rawClockX
                                            state.clockY.floatValue = rawClockY.coerceIn(minSafeYDp, maxClockSafeYDp)
                                        }
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })

                                if (!dragged && (System.currentTimeMillis() - timeStart) < 300L) {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    if (activeDrag == "DATE") showDateCustomizeSheet = true else showCustomizeSheet = true
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
                    
                	Canvas(modifier = Modifier.fillMaxSize()) {
                        val previewScale = size.width / realW

                        drawIntoCanvas { canvas ->
                            val nativeCanvas = canvas.nativeCanvas

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hardwareRenderNode != null) {
                                hardwareRenderNode.setPosition(0, 0, realW.toInt(), realH.toInt())
                                hardwareRenderNode.pivotX = 0f
                                hardwareRenderNode.pivotY = 0f
                                hardwareRenderNode.scaleX = previewScale
                                hardwareRenderNode.scaleY = previewScale
                                
                                val recordingCanvas = hardwareRenderNode.beginRecording(realW.toInt(), realH.toInt())
                                try {
                                    
                                    previewOriginal?.let { bmp ->
                                        val zoom = state.liveWallpaperZoom.floatValue
                                        val baseScale = max(realW / bmp.width.toFloat(), realH / bmp.height.toFloat()) * zoom
                                        val tx = (realW - bmp.width * baseScale) / 2f
                                        val ty = (realH - bmp.height * baseScale) / 2f

                                        wallpaperMatrix.reset()
                                        wallpaperMatrix.postScale(baseScale, baseScale)
                                        wallpaperMatrix.postTranslate(tx, ty)
                                        recordingCanvas.drawBitmap(bmp, wallpaperMatrix, wallpaperPaint)
                                    }
                                    
                                    if (dateText.isNotEmpty()) {
                                        datePaint.color = state.clockColor.intValue
                                        datePaint.textSize = state.dateSize.floatValue * densityVal
                                        val nativeDateX = (realW / 2f) + (state.dateX.floatValue * densityVal)
                                        val nativeDateY = state.dateY.floatValue * densityVal
                                        val drawBaselineY = nativeDateY - datePaint.fontMetrics.top
                                        recordingCanvas.drawText(dateText, nativeDateX, drawBaselineY, datePaint)
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

                                        vectorPaint.strokeWidth = state.strokeWidth.floatValue * densityVal
                                        vectorPaint.pathEffect = android.graphics.CornerPathEffect(state.roundness.floatValue * densityVal)

                                        vectorPaint.color = if (state.dualTone.value) android.graphics.Color.WHITE else state.clockColor.intValue
                                        recordingCanvas.drawPath(paths.hours, vectorPaint)
                                        recordingCanvas.drawPath(paths.colon, vectorPaint)

                                        vectorPaint.color = state.clockColor.intValue
                                        recordingCanvas.drawPath(paths.mins, vectorPaint)

                                        combinedClockPath.rewind()
                                        combinedClockPath.addPath(paths.hours)
                                        combinedClockPath.addPath(paths.colon)
                                        combinedClockPath.addPath(paths.mins)
                                        combinedClockPath.computeBounds(sharedPathBounds, true)
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
                                    val zoom = state.liveWallpaperZoom.floatValue
                                    val baseScale = max(realW / bmp.width.toFloat(), realH / bmp.height.toFloat()) * zoom
                                    val tx = (realW - bmp.width * baseScale) / 2f
                                    val ty = (realH - bmp.height * baseScale) / 2f
                                    wallpaperMatrix.reset()
                                    wallpaperMatrix.postScale(baseScale, baseScale)
                                    wallpaperMatrix.postTranslate(tx, ty)
                                    nativeCanvas.drawBitmap(bmp, wallpaperMatrix, wallpaperPaint)
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
                }
            }

            if (showDateCustomizeSheet) {
                EditorDatePanel(
                    viewModel = viewModel,
                    wallpaper = wallpaper,
                    state = state,
                    onDismiss = { showDateCustomizeSheet = false }
                )
            }
            if (showCustomizeSheet) {
                EditorClockPanel(
                    viewModel = viewModel,
                    wallpaper = wallpaper,
                    state = state,
                    extractedColors = extractedColors,
                    availableFonts = availableFonts,
                    previewMask = previewMask,
                    realScreenW = realW,
                    realScreenH = realH,
                    densityVal = densityVal,
                    onDismiss = { showCustomizeSheet = false }
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
                                "Update lock screen clock layout & style",
                                false
                            ) {
                                viewModel.updateClockLayout(context, state.clockLayout.intValue)
                                viewModel.updateDualTone(context, state.dualTone.value)
                                viewModel.updateClockFont(context, state.clockFont.value)
                                viewModel.updateClockColor(context, state.clockColor.intValue)
                                viewModel.updateDateLayout(context, state.dateFormat.intValue)
                                viewModel.updateDateAttached(context, state.dateAttached.value)
                                viewModel.updateClockStyle(
                                    context, state.hourSize.floatValue, state.minSize.floatValue,
                                    state.strokeWidth.floatValue, state.roundness.floatValue
                                )
                                viewModel.updateLockscreenClockPosition(context, state.clockX.floatValue, state.clockY.floatValue)
                                viewModel.updateLockscreenDatePosition(context, state.dateX.floatValue, state.dateY.floatValue)
                                viewModel.toggleClockStretch(context, state.stretchEnabled.value, previewMask, realW, realH)
                                onApplyRequested(WallpaperDestinations.FLAG_LOCK_SCREEN)
                                showApplyOptions = false
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        StaggeredRow(2) {
                            DestinationButton(
                                Icons.Default.PhotoLibrary,
                                "Clock + Wallpaper",
                                "Apply clock style and set static background",
                                false
                            ) {
                                viewModel.updateClockLayout(context, state.clockLayout.intValue)
                                viewModel.updateDualTone(context, state.dualTone.value)
                                viewModel.updateClockFont(context, state.clockFont.value)
                                viewModel.updateClockColor(context, state.clockColor.intValue)
                                viewModel.updateDateLayout(context, state.dateFormat.intValue)
                                viewModel.updateDateAttached(context, state.dateAttached.value)
                                viewModel.updateClockStyle(
                                    context, state.hourSize.floatValue, state.minSize.floatValue,
                                    state.strokeWidth.floatValue, state.roundness.floatValue
                                )
                                viewModel.updateLockscreenClockPosition(context, state.clockX.floatValue, state.clockY.floatValue)
                                viewModel.updateLockscreenDatePosition(context, state.dateX.floatValue, state.dateY.floatValue)
                                viewModel.toggleClockStretch(context, state.stretchEnabled.value, previewMask, realW, realH)
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
