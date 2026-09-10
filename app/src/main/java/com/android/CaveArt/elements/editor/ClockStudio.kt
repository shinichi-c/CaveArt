package com.android.CaveArt

import android.content.res.Configuration
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
}

@Composable
fun rememberClockStudioState(viewModel: WallpaperViewModel): ClockStudioState =
    remember(viewModel) { ClockStudioState(viewModel) }

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
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isDarkTheme = isSystemInDarkTheme()

    var transitionFinished by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300)
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

    val customTypeface = remember(state.clockFont.value) {
        if (state.clockFont.value == "default" || state.clockFont.value.isEmpty()) {
            Typeface.create("sans-serif-bold", Typeface.NORMAL)
        } else {
            try {
                Typeface.createFromAsset(context.assets, "fonts/${state.clockFont.value}")
            } catch (e: Exception) {
                Typeface.create("sans-serif-bold", Typeface.NORMAL)
            }
        }
    }

    LaunchedEffect(wallpaper) {
        delay(300)
        isMaskLoading = true
        previewMask = viewModel.getMaskForClock(context, wallpaper)
        isMaskLoading = false
    }
    
    LaunchedEffect(state.clockX.floatValue, state.clockY.floatValue, state.dateAttached.value, state.hourSize.floatValue, state.clockLayout.intValue) {
        if (state.dateAttached.value) {
            state.dateX.floatValue = state.clockX.floatValue
            val topOffset = if (state.clockLayout.intValue == 1) {
                (state.hourSize.floatValue * 0.3f) + 30f
            } else {
                (state.hourSize.floatValue * 0.45f) + 24f
            }
            state.dateY.floatValue = max(18f, state.clockY.floatValue - topOffset)
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
                4 -> java.text.SimpleDateFormat("EEEE • d MMM", locale).format(date)
                else -> java.text.SimpleDateFormat("EEE, d MMMM", locale).format(date)
            }
            delay(1000L)
        }
    }

    LaunchedEffect(wallpaper) {
        state.clockColor.intValue = android.graphics.Color.WHITE
        withContext(Dispatchers.IO) {
            val bitmap = if (wallpaper.uri != null) {
                BitmapHelper.decodeSampledBitmapFromUri(context, wallpaper.uri, 112)
            } else {
                BitmapHelper.decodeSampledBitmapFromResource(context.resources, wallpaper.resourceId, 112)
            }
            if (bitmap != null) {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                var finalColors = com.materialkolor.score.Score.score(
                    com.materialkolor.quantize.QuantizerCelebi.quantize(pixels, 128)
                ).distinct().take(5)

                val hct = com.materialkolor.hct.Hct.fromInt(finalColors.firstOrNull() ?: android.graphics.Color.WHITE)
                val hue = hct.hue
                val tonePrimary = if (isDarkTheme) 85.0 else 40.0
                val toneSecondary = if (isDarkTheme) 80.0 else 30.0

                finalColors = listOf(
                    com.materialkolor.hct.Hct.from(hue, maxOf(48.0, hct.chroma), tonePrimary).toInt(),
                    com.materialkolor.hct.Hct.from(hue, 16.0, toneSecondary).toInt(),
                    com.materialkolor.hct.Hct.from(hue + 60.0, 24.0, tonePrimary).toInt(),
                    com.materialkolor.hct.Hct.from(hue, 4.0, if (isDarkTheme) 90.0 else 20.0).toInt(),
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
                withContext(Dispatchers.Main) {
                    state.collisionMap.value = mapStr
                    isCalculatingMap = false
                }
            }
        } else {
            state.collisionMap.value = ""
        }
    }

    val collisionMapArray = remember(state.collisionMap.value) {
        if (state.collisionMap.value.isNotEmpty()) {
            state.collisionMap.value.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray()
        } else null
    }

    val sharedPathBounds = remember { RectF() }
    val sharedMatrix = remember { android.graphics.Matrix() }
    var currentScale by remember { mutableFloatStateOf(1f) }
    val sharedPath = remember { android.graphics.Path() }
    val cornerEffect = remember(state.roundness.floatValue, densityVal, currentScale) {
        android.graphics.CornerPathEffect(state.roundness.floatValue * densityVal * currentScale)
    }
    val vectorPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL_AND_STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
            setShadowLayer(15f, 0f, 5f, android.graphics.Color.argb(160, 0, 0, 0))
        }
    }
    val datePaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER
            setShadowLayer(10f, 0f, 3f, android.graphics.Color.argb(160, 0, 0, 0))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
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
                            "Lock screen",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.padding(start = 6.dp)
                        ) {
                            Icon(Icons.Rounded.ArrowBack, "Back", modifier = Modifier.size(24.dp))
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
            },
            bottomBar = {
                if (!isLandscape) {
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val btnScale by animateFloatAsState(if (isPressed) 0.94f else 1f, spring(), label = "btnSquish")

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
                                .graphicsLayer { scaleX = btnScale; scaleY = btnScale }
                                .shadow(14.dp, CircleShape, ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 24.dp)
                        ) {
                            Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Set Clock Face",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.5.sp
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            val targetElevation = if (!transitionFinished || showCustomizeSheet || showApplyOptions || showDateCustomizeSheet) 0.dp else 28.dp
            val cardElevation by animateDpAsState(targetElevation, label = "cardElevation")

            if (isLandscape) {
                
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .displayCutoutPadding()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(9f / 19.5f, matchHeightConstraintsFirst = true)
                                .then(sharedElementModifier)
                                .clip(MaterialTheme.shapes.extraLarge),
                            shape = MaterialTheme.shapes.extraLarge,
                            elevation = CardDefaults.cardElevation(cardElevation)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncWallpaperImage(
                                    wallpaper = wallpaper,
                                    contentDescription = null,
                                    viewModel = viewModel,
                                    modifier = Modifier.fillMaxSize(),
                                    allowMagic = false
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = { showCustomizeSheet = true },
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.filledTonalButtonColors()
                        ) {
                            Icon(Icons.Default.Tune, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Customize Clock Face", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                showApplyOptions = true
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Rounded.CheckCircle, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Set Clock Face", fontWeight = FontWeight.Black)
                        }
                    }
                }
            } else {
                
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
                            .aspectRatio(9f / 19.5f, matchHeightConstraintsFirst = true)
                            .then(sharedElementModifier)
                            .clip(MaterialTheme.shapes.extraLarge)
                            .pointerInput(Unit) {
                                var rawClockX = state.clockX.floatValue
                                var rawClockY = state.clockY.floatValue
                                var rawDateX = state.dateX.floatValue
                                var rawDateY = state.dateY.floatValue

                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = true)
                                    if (down.isConsumed) return@awaitEachGesture
                                    val timeStart = System.currentTimeMillis()
                                    var dragged = false
                                    val scale = minOf(size.width / realScreenW, size.height / realScreenH)
                                    val dx = (size.width - realScreenW * scale) / 2f
                                    val dy = (size.height - realScreenH * scale) / 2f
                                    val scaledDateY = (state.dateY.floatValue * densityVal) * scale + dy
                                    val scaledClockY = (state.clockY.floatValue * densityVal) * scale + dy
                                    val distToDate = abs(down.position.y - scaledDateY)
                                    val distToClock = abs(down.position.y - scaledClockY)
                                    val activeDrag = if (distToDate < distToClock && distToDate < 240f) "DATE" else "CLOCK"
                                    rawClockX = state.clockX.floatValue
                                    rawClockY = state.clockY.floatValue
                                    rawDateX = state.dateX.floatValue
                                    rawDateY = state.dateY.floatValue
                                    var hasChangedClock = false
                                    var hasChangedDate = false

                                    do {
                                        val event = awaitPointerEvent()
                                        val pan = event.calculatePan()
                                        val zoom = event.calculateZoom()
                                        if (pan.getDistance() > 3f || abs(zoom - 1f) > 0.01f) dragged = true
                                        if (dragged) {
                                            if (activeDrag == "DATE" && !state.dateAttached.value) {
                                                isDraggingDate = true
                                                hasChangedDate = true
                                                rawDateX += (pan.x / scale) / densityVal
                                                rawDateY += (pan.y / scale) / densityVal
                                                state.dateX.floatValue = rawDateX
                                                state.dateY.floatValue = rawDateY.coerceIn(0f, config.screenHeightDp.toFloat())
                                            } else {
                                                isDraggingClock = true
                                                hasChangedClock = true
                                                state.hourSize.floatValue = (state.hourSize.floatValue * zoom).coerceIn(40f, 200f)
                                                state.minSize.floatValue = (state.minSize.floatValue * zoom).coerceIn(40f, 200f)
                                                rawClockX += (pan.x / scale) / densityVal
                                                rawClockY += (pan.y / scale) / densityVal
                                                state.clockX.floatValue = rawClockX
                                                state.clockY.floatValue = rawClockY.coerceIn(0f, config.screenHeightDp.toFloat())
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
                                    if (hasChangedClock && abs(state.clockX.floatValue) < 15f) {
                                        state.clockX.floatValue = 0f
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                    if (hasChangedDate && !state.dateAttached.value && abs(state.dateX.floatValue) < 15f) {
                                        state.dateX.floatValue = 0f
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                }
                            },
                        shape = MaterialTheme.shapes.extraLarge,
                        elevation = CardDefaults.cardElevation(cardElevation)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncWallpaperImage(
                                wallpaper = wallpaper,
                                contentDescription = null,
                                viewModel = viewModel,
                                modifier = Modifier.fillMaxSize(),
                                allowMagic = false
                            )

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                if (timeString.isEmpty()) return@Canvas
                                val hPx = state.hourSize.floatValue * densityVal
                                val mPx = state.minSize.floatValue * densityVal
                                val isVert = state.clockLayout.intValue == 1
                                val totalWidth = AdaptiveClockHelper.measureClockWidth(timeString, hPx, mPx, customTypeface, isVert)
                                val realCenterX = (realScreenW / 2f) + (state.clockX.floatValue * densityVal)
                                val realStartX = realCenterX - (totalWidth / 2f)
                                val realStartY = state.clockY.floatValue * densityVal
                                val scale = minOf(size.width / realScreenW, size.height / realScreenH)
                                if (currentScale != scale) currentScale = scale
                                val dx = (size.width - realScreenW * scale) / 2f
                                val dy = (size.height - realScreenH * scale) / 2f

                                val paths = AdaptiveClockHelper.buildPaths(
                                    timeString, realStartX, realStartY, 0f, 0f,
                                    hPx, mPx, customTypeface, realScreenW, realScreenH,
                                    state.stretchEnabled.value, isVert, collisionMapArray,
                                    densityVal, state.strokeWidth.floatValue, 1f
                                )
                                sharedPath.rewind()
                                sharedPath.addPath(paths.hours)
                                sharedPath.addPath(paths.colon)
                                sharedPath.addPath(paths.mins)
                                sharedPath.computeBounds(sharedPathBounds, true)

                                datePaint.color = state.clockColor.intValue
                                datePaint.textSize = 17f * densityVal * scale
                                val dateTextWidth = datePaint.measureText(dateText)
                                val scaledDateY = (state.dateY.floatValue * densityVal) * scale + dy
                                val scaledDateX = (realScreenW / 2f) * scale + dx + (state.dateX.floatValue * densityVal) * scale
                                
                                val halfW = (dateTextWidth / 2f) + 36f
                                val dateAlpha = if (isDraggingDate) 0.65f else 0.28f
                                val dateStroke = if (isDraggingDate) 3.5f else 2f
                                drawRoundRect(
                                    color = Color.White.copy(alpha = dateAlpha),
                                    topLeft = Offset(scaledDateX - halfW, scaledDateY - 46f),
                                    size = Size(halfW * 2f, 68f),
                                    cornerRadius = CornerRadius(22f, 22f),
                                    style = Stroke(width = dateStroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 16f), 0f))
                                )
                                
                                val cL = sharedPathBounds.left * scale + dx - 30f
                                val cT = sharedPathBounds.top * scale + dy - 24f
                                val cR = sharedPathBounds.right * scale + dx + 30f
                                val cB = sharedPathBounds.bottom * scale + dy + 24f
                                val clockAlpha = if (isDraggingClock) 0.65f else 0.28f
                                val clockStroke = if (isDraggingClock) 3.5f else 2f
                                drawRoundRect(
                                    color = Color.White.copy(alpha = clockAlpha),
                                    topLeft = Offset(cL, cT),
                                    size = Size(cR - cL, cB - cT),
                                    cornerRadius = CornerRadius(30f, 30f),
                                    style = Stroke(width = clockStroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f))
                                )

                                drawIntoCanvas { canvas ->
                                    sharedMatrix.reset()
                                    sharedMatrix.postScale(scale, scale)
                                    sharedMatrix.postTranslate(dx, dy)
                                    paths.hours.transform(sharedMatrix)
                                    paths.colon.transform(sharedMatrix)
                                    paths.mins.transform(sharedMatrix)
                                    vectorPaint.strokeWidth = state.strokeWidth.floatValue * densityVal * scale
                                    vectorPaint.pathEffect = cornerEffect
                                    vectorPaint.color = if (state.dualTone.value) Color.White.toArgb() else state.clockColor.intValue
                                    canvas.nativeCanvas.drawPath(paths.hours, vectorPaint)
                                    canvas.nativeCanvas.drawPath(paths.colon, vectorPaint)
                                    vectorPaint.color = state.clockColor.intValue
                                    canvas.nativeCanvas.drawPath(paths.mins, vectorPaint)
                                    canvas.nativeCanvas.drawText(dateText, scaledDateX, scaledDateY, datePaint)
                                }
                            }
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = (state.stretchEnabled.value && isMaskLoading) || isCalculatingMap,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                AsyncWallpaperImage(
                                    wallpaper = wallpaper,
                                    contentDescription = null,
                                    viewModel = viewModel,
                                    modifier = Modifier.fillMaxSize(),
                                    allowMagic = false
                                )
                                ParticleLoadingOverlay(color = Color.White)
                            }
                        }
                    }
                }
            }

            if (showDateCustomizeSheet) {
                EditorDatePanel(viewModel, wallpaper, state) { showDateCustomizeSheet = false }
            }
            if (showCustomizeSheet) {
                EditorClockPanel(
                    viewModel, wallpaper, state, extractedColors,
                    availableFonts, previewMask, realScreenW, realScreenH, densityVal
                ) { showCustomizeSheet = false }
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
                                viewModel.toggleClockStretch(context, state.stretchEnabled.value, previewMask, realScreenW, realScreenH)
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
                                viewModel.toggleClockStretch(context, state.stretchEnabled.value, previewMask, realScreenW, realScreenH)
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
