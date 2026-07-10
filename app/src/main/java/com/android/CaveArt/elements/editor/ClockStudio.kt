package com.android.CaveArt

import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class ClockStudioState(viewModel: WallpaperViewModel) {
    val clockX = mutableFloatStateOf(viewModel.lockscreenClockOffsetX)
    val clockY = mutableFloatStateOf(viewModel.lockscreenClockOffsetY)
    val dateX = mutableFloatStateOf(viewModel.lockscreenDateOffsetX)
    val dateY = mutableFloatStateOf(viewModel.lockscreenDateOffsetY)
    val hourSize = mutableFloatStateOf(viewModel.clockHourSize)
    val minSize = mutableFloatStateOf(viewModel.clockMinuteSize)
    val strokeWidth = mutableFloatStateOf(viewModel.clockStrokeWidth)
    val roundness = mutableFloatStateOf(viewModel.clockRoundness)
    val stretchEnabled = mutableStateOf(viewModel.isClockStretchEnabled)
    val collisionMap = mutableStateOf(viewModel.clockCollisionMap)
    val clockColor = mutableIntStateOf(viewModel.clockColor)
    val dualTone = mutableStateOf(viewModel.isDualToneEnabled)
    val clockLayout = mutableIntStateOf(viewModel.clockLayout)
    val dateFormat = mutableIntStateOf(viewModel.dateLayout)
    val dateAttached = mutableStateOf(viewModel.isDateAttached)
    val clockFont = mutableStateOf(viewModel.clockFont)
}

@Composable
fun rememberClockStudioState(viewModel: WallpaperViewModel) = remember { ClockStudioState(viewModel) }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    val metrics = context.resources.displayMetrics
    val realScreenW = metrics.widthPixels.toFloat()
    val realScreenH = metrics.heightPixels.toFloat()
    val densityVal = metrics.density
    val config = LocalConfiguration.current
    val screenAspectRatio = realScreenW / realScreenH
    val isDarkTheme = isSystemInDarkTheme()
    
    var transitionFinished by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(350)
        transitionFinished = true
    }
    
    val state = rememberClockStudioState(viewModel)
    var previewMask by remember { mutableStateOf<Bitmap?>(null) }
    var isMaskLoading by remember { mutableStateOf(false) } 
    var showCustomizeSheet by remember { mutableStateOf(false) }
    var showDateCustomizeSheet by remember { mutableStateOf(false) }
    var showApplyOptions by remember { mutableStateOf(false) }
    
    var isDraggingClock by remember { mutableStateOf(false) }
    var isDraggingDate by remember { mutableStateOf(false) }
    var isCalculatingMap by remember { mutableStateOf(false) } 
    var extractedColors by remember { mutableStateOf(listOf(android.graphics.Color.WHITE, android.graphics.Color.BLACK)) }

    var timeString by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    val availableFonts = remember { viewModel.getAvailableFonts(context) }
    val customTypeface = remember(state.clockFont.value) { if (state.clockFont.value == "default") Typeface.create("sans-serif-bold", Typeface.NORMAL) else try { Typeface.createFromAsset(context.assets, "fonts/${state.clockFont.value}") } catch (e: Exception) { Typeface.create("sans-serif-bold", Typeface.NORMAL) } }

    LaunchedEffect(wallpaper) {
        delay(350)
        isMaskLoading = true
        previewMask = viewModel.getMaskForClock(context, wallpaper)
        isMaskLoading = false
    }

    LaunchedEffect(state.clockX.floatValue, state.clockY.floatValue, state.dateAttached.value) {
        if (state.dateAttached.value) { state.dateX.floatValue = state.clockX.floatValue; state.dateY.floatValue = state.clockY.floatValue - 35f }
    }
    
    LaunchedEffect(state.dateFormat.intValue) {
        while (true) {
            timeString = java.text.SimpleDateFormat(if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "hh:mm", java.util.Locale.getDefault()).format(java.util.Date())
            val date = java.util.Date()
            val locale = java.util.Locale.getDefault()
            dateText = when (state.dateFormat.intValue) { 0 -> java.text.SimpleDateFormat("EEE, d MMMM", locale).format(date); 1 -> java.text.SimpleDateFormat("EEE, d MMM", locale).format(date); 2 -> java.text.SimpleDateFormat("d MMM yyyy", locale).format(date).uppercase(locale); 3 -> java.text.SimpleDateFormat("EEEE, MMMM d", locale).format(date); 4 -> java.text.SimpleDateFormat("EEEE • d MMM", locale).format(date); else -> java.text.SimpleDateFormat("EEE, d MMMM", locale).format(date) }
            delay(1000L) 
        }
    }

    LaunchedEffect(wallpaper) {
        state.clockColor.intValue = android.graphics.Color.WHITE
        if (!transitionFinished) delay(350)
        withContext(Dispatchers.IO) {
            val bitmap = if(wallpaper.uri != null) BitmapHelper.decodeSampledBitmapFromUri(context, wallpaper.uri, 112) else BitmapHelper.decodeSampledBitmapFromResource(context.resources, wallpaper.resourceId, 112)
            if (bitmap != null) {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                var finalColors = com.materialkolor.score.Score.score(com.materialkolor.quantize.QuantizerCelebi.quantize(pixels, 128)).distinct().take(5)
                
                val hct = com.materialkolor.hct.Hct.fromInt(finalColors.firstOrNull() ?: android.graphics.Color.WHITE)
                val hue = hct.hue
                val tonePrimary = if (isDarkTheme) 85.0 else 40.0
                val toneSecondary = if (isDarkTheme) 80.0 else 30.0
                
                finalColors = listOf(
                    com.materialkolor.hct.Hct.from(hue, maxOf(48.0, hct.chroma), tonePrimary).toInt(),
                    com.materialkolor.hct.Hct.from(hue, 16.0, toneSecondary).toInt(),
                    com.materialkolor.hct.Hct.from(hue + 60.0, 24.0, tonePrimary).toInt(),
                    com.materialkolor.hct.Hct.from(hue, 4.0, if(isDarkTheme) 90.0 else 20.0).toInt(),
                    com.materialkolor.hct.Hct.from(hue + 180.0, maxOf(48.0, hct.chroma), tonePrimary).toInt()
                )
                
                withContext(Dispatchers.Main) { 
                    extractedColors = listOf(android.graphics.Color.WHITE, android.graphics.Color.BLACK) + finalColors
                    if (finalColors.isNotEmpty()) state.clockColor.intValue = finalColors.first() 
                }
                bitmap.recycle()
            }
        }
    }

    val currentMask = previewMask
    LaunchedEffect(currentMask, state.stretchEnabled.value) {
        if (state.stretchEnabled.value && currentMask != null) {
            isCalculatingMap = true 
            withContext(Dispatchers.Default) {
                val mapStr = AdaptiveClockHelper.generateCollisionMap(currentMask, realScreenW, realScreenH)
                withContext(Dispatchers.Main) { state.collisionMap.value = mapStr; isCalculatingMap = false }
            }
        } else state.collisionMap.value = ""
    }

    val collisionMapArray = remember(state.collisionMap.value) { if (state.collisionMap.value.isNotEmpty()) state.collisionMap.value.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray() else null }
    val sharedPathBounds = remember { RectF() }
    val sharedMatrix = remember { android.graphics.Matrix() }
    var currentScale by remember { mutableFloatStateOf(1f) }
    val sharedPath = remember { android.graphics.Path() }
    val cornerEffect = remember(state.roundness.floatValue, densityVal, currentScale) { android.graphics.CornerPathEffect(state.roundness.floatValue * densityVal * currentScale) }
    val vectorPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.FILL_AND_STROKE; strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND; setShadowLayer(15f, 0f, 5f, android.graphics.Color.argb(160, 0, 0, 0)) } }
    val datePaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textAlign = android.graphics.Paint.Align.CENTER; setShadowLayer(10f, 0f, 3f, android.graphics.Color.argb(160, 0, 0, 0)); typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Clock Studio", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            Row(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp, top = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                ExtendedFloatingActionButton(onClick = { showCustomizeSheet = true }, icon = { Icon(Icons.Default.Palette, null) }, text = { Text("Customize") }, containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, shape = MaterialTheme.shapes.extraLarge)
                Spacer(Modifier.width(16.dp))
                ExtendedFloatingActionButton(onClick = { showApplyOptions = true }, icon = { Icon(Icons.Default.Check, null) }, text = { Text("Done") }, containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = MaterialTheme.shapes.extraLarge)
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        	
            val targetElevation = if (!transitionFinished || showCustomizeSheet || showApplyOptions || showDateCustomizeSheet) 0.dp else 24.dp
            val cardElevation by animateDpAsState(targetElevation, label = "cardElevation")

            Card(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(screenAspectRatio, matchHeightConstraintsFirst = true)
                    .then(sharedElementModifier)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .pointerInput(Unit) {
                        var rawClockX = state.clockX.floatValue; var rawClockY = state.clockY.floatValue; var rawDateX = state.dateX.floatValue; var rawDateY = state.dateY.floatValue
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = true)
                            if (down.isConsumed) return@awaitEachGesture
                            val timeStart = System.currentTimeMillis()
                            var dragged = false
                            val scale = minOf(size.width / realScreenW, size.height / realScreenH)
                            val dx = (size.width - realScreenW * scale) / 2f
                            val dy = (size.height - realScreenH * scale) / 2f
                            val scaledDateY = (state.dateY.floatValue * densityVal + (20f * densityVal)) * scale + dy
                            val scaledClockY = (state.clockY.floatValue * densityVal) * scale + dy
                            val distToDate = abs(down.position.y - scaledDateY)
                            val distToClock = abs(down.position.y - scaledClockY)
                            val activeDrag = if (distToDate < distToClock && distToDate < 300f) "DATE" else "CLOCK"
                            rawClockX = state.clockX.floatValue; rawClockY = state.clockY.floatValue; rawDateX = state.dateX.floatValue; rawDateY = state.dateY.floatValue
                            var hasChangedClock = false; var hasChangedDate = false

                            do {
                                val event = awaitPointerEvent()
                                val pan = event.calculatePan(); val zoom = event.calculateZoom()
                                if (pan.getDistance() > 3f || abs(zoom - 1f) > 0.01f) dragged = true
                                if (dragged) {
                                    if (activeDrag == "DATE" && !state.dateAttached.value) {
                                        isDraggingDate = true; hasChangedDate = true
                                        rawDateX += (pan.x / scale) / densityVal; rawDateY += (pan.y / scale) / densityVal
                                        state.dateX.floatValue = rawDateX; state.dateY.floatValue = rawDateY.coerceIn(0f, config.screenHeightDp.toFloat())
                                    } else {
                                        isDraggingClock = true; hasChangedClock = true
                                        state.hourSize.floatValue = (state.hourSize.floatValue * zoom).coerceIn(40f, 200f)
                                        state.minSize.floatValue = (state.minSize.floatValue * zoom).coerceIn(40f, 200f)
                                        rawClockX += (pan.x / scale) / densityVal; rawClockY += (pan.y / scale) / densityVal
                                        state.clockX.floatValue = rawClockX; state.clockY.floatValue = rawClockY.coerceIn(0f, config.screenHeightDp.toFloat())
                                    }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                            
                            if (!dragged && (System.currentTimeMillis() - timeStart) < 300L) { if (activeDrag == "DATE") showDateCustomizeSheet = true else showCustomizeSheet = true }
                            isDraggingClock = false; isDraggingDate = false
                            if (hasChangedClock && abs(state.clockX.floatValue) < 15f) { state.clockX.floatValue = 0f; view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                            if (hasChangedDate && !state.dateAttached.value && abs(state.dateX.floatValue) < 15f) { state.dateX.floatValue = 0f; view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                        }
                    }, 
                shape = MaterialTheme.shapes.extraLarge,
                elevation = CardDefaults.cardElevation(cardElevation)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncWallpaperImage(wallpaper = wallpaper, contentDescription = null, viewModel = viewModel, modifier = Modifier.fillMaxSize(), allowMagic = false)
                    
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (timeString.isEmpty()) return@Canvas
                        val hPx = state.hourSize.floatValue * densityVal; val mPx = state.minSize.floatValue * densityVal
                        val isVert = state.clockLayout.intValue == 1
                        val totalWidth = AdaptiveClockHelper.measureClockWidth(timeString, hPx, mPx, customTypeface, isVert)
                        val realCenterX = (realScreenW / 2f) + (state.clockX.floatValue * densityVal)
                        val realStartX = realCenterX - (totalWidth / 2f); val realStartY = state.clockY.floatValue * densityVal
                        val scale = minOf(size.width / realScreenW, size.height / realScreenH)
                        if (currentScale != scale) currentScale = scale
                        val dx = (size.width - realScreenW * scale) / 2f; val dy = (size.height - realScreenH * scale) / 2f

                        val paths = AdaptiveClockHelper.buildPaths(timeString, realStartX, realStartY, 0f, 0f, hPx, mPx, customTypeface, realScreenW, realScreenH, state.stretchEnabled.value, isVert, collisionMapArray, densityVal, state.strokeWidth.floatValue, 1f)
                        sharedPath.rewind(); sharedPath.addPath(paths.hours); sharedPath.addPath(paths.colon); sharedPath.addPath(paths.mins); sharedPath.computeBounds(sharedPathBounds, true)

                        datePaint.color = state.clockColor.intValue; datePaint.textSize = 20f * densityVal * scale
                        val dateTextWidth = datePaint.measureText(dateText)
                        val scaledDateY = (state.dateY.floatValue * densityVal + (20f * densityVal)) * scale + dy
                        val scaledDateX = (realScreenW / 2f) * scale + dx + (state.dateX.floatValue * densityVal) * scale

                        if (isDraggingClock) {
                            val cL = sharedPathBounds.left * scale + dx - 40f; val cT = sharedPathBounds.top * scale + dy - 40f; val cR = sharedPathBounds.right * scale + dx + 40f; val cB = sharedPathBounds.bottom * scale + dy + 40f
                            val bL = if (state.dateAttached.value) minOf(cL, scaledDateX - dateTextWidth / 2f - 60f) else cL
                            val bT = if (state.dateAttached.value) minOf(cT, scaledDateY - 60f) else cT
                            val bR = if (state.dateAttached.value) maxOf(cR, scaledDateX + dateTextWidth / 2f + 60f) else cR
                            drawRoundRect(color = Color.White.copy(alpha = 0.3f), topLeft = Offset(bL, bT), size = Size(bR - bL, cB - bT), cornerRadius = CornerRadius(32f, 32f), style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)))
                        }

                        if (isDraggingDate && !state.dateAttached.value) {
                            val halfW = (dateTextWidth / 2f) + 60f
                            drawRoundRect(color = Color.White.copy(alpha = 0.3f), topLeft = Offset(scaledDateX - halfW, scaledDateY - 60f), size = Size(halfW * 2f, 100f), cornerRadius = CornerRadius(32f, 32f), style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)))
                        }

                        drawIntoCanvas { canvas ->
                            sharedMatrix.reset(); sharedMatrix.postScale(scale, scale); sharedMatrix.postTranslate(dx, dy)
                            paths.hours.transform(sharedMatrix); paths.colon.transform(sharedMatrix); paths.mins.transform(sharedMatrix)
                            vectorPaint.strokeWidth = state.strokeWidth.floatValue * densityVal * scale; vectorPaint.pathEffect = cornerEffect
                            vectorPaint.color = if (state.dualTone.value) Color.White.toArgb() else state.clockColor.intValue
                            canvas.nativeCanvas.drawPath(paths.hours, vectorPaint); canvas.nativeCanvas.drawPath(paths.colon, vectorPaint)
                            vectorPaint.color = state.clockColor.intValue; canvas.nativeCanvas.drawPath(paths.mins, vectorPaint)
                            canvas.nativeCanvas.drawText(dateText, scaledDateX, scaledDateY, datePaint)
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(visible = (state.stretchEnabled.value && isMaskLoading) || isCalculatingMap, enter = fadeIn(), exit = fadeOut()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            AsyncWallpaperImage(wallpaper = wallpaper, contentDescription = null, viewModel = viewModel, modifier = Modifier.fillMaxSize(), allowMagic = false)
                            ParticleLoadingOverlay(color = Color.White)
                        }
                    }
                }
            }
        }
        
        if (showDateCustomizeSheet) EditorDatePanel(viewModel, wallpaper, state) { showDateCustomizeSheet = false }
        if (showCustomizeSheet) EditorClockPanel(viewModel, wallpaper, state, extractedColors, availableFonts, previewMask, realScreenW, realScreenH, densityVal) { showCustomizeSheet = false }
        if (showApplyOptions) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            AmbientBottomSheet(onDismissRequest = { showApplyOptions = false }, sheetState = sheetState, viewModel = viewModel, currentWallpaper = wallpaper) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).navigationBarsPadding()) {
                    StaggeredRow(0) { Text("Apply Lockscreen", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 24.dp)) }
                    StaggeredRow(1) { 
                        DestinationButton(Icons.Default.ScreenLockPortrait, "Clock Layout Only", "Update clock position and style", false) {
                            viewModel.updateClockLayout(context, state.clockLayout.intValue); viewModel.updateDualTone(context, state.dualTone.value); viewModel.updateClockFont(context, state.clockFont.value); viewModel.updateClockColor(context, state.clockColor.intValue); viewModel.updateDateLayout(context, state.dateFormat.intValue); viewModel.updateDateAttached(context, state.dateAttached.value); viewModel.updateClockStyle(context, state.hourSize.floatValue, state.minSize.floatValue, state.strokeWidth.floatValue, state.roundness.floatValue); viewModel.updateLockscreenClockPosition(context, state.clockX.floatValue, state.clockY.floatValue); viewModel.updateLockscreenDatePosition(context, state.dateX.floatValue, state.dateY.floatValue); viewModel.toggleClockStretch(context, state.stretchEnabled.value, previewMask, realScreenW, realScreenH)
                            onApplyRequested(WallpaperDestinations.FLAG_LOCK_SCREEN)
                            showApplyOptions = false
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    StaggeredRow(2) { 
                        DestinationButton(Icons.Default.Wallpaper, "Clock + Wallpaper", "Update layout and set static wallpaper", false) {
                            viewModel.updateClockLayout(context, state.clockLayout.intValue); viewModel.updateDualTone(context, state.dualTone.value); viewModel.updateClockFont(context, state.clockFont.value); viewModel.updateClockColor(context, state.clockColor.intValue); viewModel.updateDateLayout(context, state.dateFormat.intValue); viewModel.updateDateAttached(context, state.dateAttached.value); viewModel.updateClockStyle(context, state.hourSize.floatValue, state.minSize.floatValue, state.strokeWidth.floatValue, state.roundness.floatValue); viewModel.updateLockscreenClockPosition(context, state.clockX.floatValue, state.clockY.floatValue); viewModel.updateLockscreenDatePosition(context, state.dateX.floatValue, state.dateY.floatValue); viewModel.toggleClockStretch(context, state.stretchEnabled.value, previewMask, realScreenW, realScreenH)
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
