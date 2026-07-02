package com.android.CaveArt

import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.Typeface
import android.text.format.DateFormat
import android.view.HapticFeedbackConstants
import android.view.TextureView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.CaveArt.animations.AnimationFactory
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score
import com.materialkolor.hct.Hct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class LockscreenEditorState(viewModel: WallpaperViewModel) {
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
fun rememberLockscreenEditorState(viewModel: WallpaperViewModel) = remember { LockscreenEditorState(viewModel) }

@Composable
fun LockscreenEditor(wallpaper: Wallpaper, viewModel: WallpaperViewModel, modifier: Modifier = Modifier, onApplyClockAndWallpaperClick: () -> Unit = {}) {
    val context = LocalContext.current
    var previewMask by remember { mutableStateOf<Bitmap?>(null) }
    var isMaskLoading by remember { mutableStateOf(false) } 
    LaunchedEffect(wallpaper) { isMaskLoading = true; previewMask = viewModel.getMaskForClock(context, wallpaper); isMaskLoading = false }
    if (viewModel.isLockscreenClockPreviewVisible) ClockEditorPreview(wallpaper, viewModel, previewMask, isMaskLoading, modifier, onApplyClockAndWallpaperClick)
    else AnimatedWallpaperEngine(wallpaper, viewModel, previewMask, modifier)
}

@Composable
fun ClockEditorPreview(wallpaper: Wallpaper, viewModel: WallpaperViewModel, previewMask: Bitmap?, isMaskLoading: Boolean, modifier: Modifier = Modifier, onApplyClockAndWallpaperClick: () -> Unit = {}) {
    val context = LocalContext.current
    val view = LocalView.current
    val metrics = context.resources.displayMetrics
    val realScreenW = metrics.widthPixels.toFloat()
    val realScreenH = metrics.heightPixels.toFloat()
    val densityVal = metrics.density
    val config = LocalConfiguration.current
    
    val state = rememberLockscreenEditorState(viewModel)
    
    var showCustomizeSheet by remember { mutableStateOf(false) }
    var showDateCustomizeSheet by remember { mutableStateOf(false) }
    var showApplyOptions by remember { mutableStateOf(false) }
    var isDraggingClock by remember { mutableStateOf(false) }
    var isDraggingDate by remember { mutableStateOf(false) }
    var isCalculatingMap by remember { mutableStateOf(false) } 
    var extractedColors by remember { mutableStateOf(listOf(android.graphics.Color.WHITE, android.graphics.Color.BLACK)) }

    var timeString by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    val screenAspectRatio = realScreenW / realScreenH
    val availableFonts = remember { viewModel.getAvailableFonts(context) }

    val customTypeface = remember(state.clockFont.value) { if (state.clockFont.value == "default") Typeface.create("sans-serif-bold", Typeface.NORMAL) else try { Typeface.createFromAsset(context.assets, "fonts/${state.clockFont.value}") } catch (e: Exception) { Typeface.create("sans-serif-bold", Typeface.NORMAL) } }

    LaunchedEffect(state.clockX.floatValue, state.clockY.floatValue, state.dateAttached.value) {
        if (state.dateAttached.value) { state.dateX.floatValue = state.clockX.floatValue; state.dateY.floatValue = state.clockY.floatValue - 35f }
    }
    
    LaunchedEffect(state.dateFormat.intValue) {
        while (true) {
            timeString = java.text.SimpleDateFormat(if (DateFormat.is24HourFormat(context)) "HH:mm" else "hh:mm", java.util.Locale.getDefault()).format(java.util.Date())
            val date = java.util.Date()
            val locale = java.util.Locale.getDefault()
            dateText = when (state.dateFormat.intValue) { 0 -> java.text.SimpleDateFormat("EEE, d MMMM", locale).format(date); 1 -> java.text.SimpleDateFormat("EEE, d MMM", locale).format(date); 2 -> java.text.SimpleDateFormat("d MMM yyyy", locale).format(date).uppercase(locale); 3 -> java.text.SimpleDateFormat("EEEE, MMMM d", locale).format(date); 4 -> java.text.SimpleDateFormat("EEEE • d MMM", locale).format(date); else -> java.text.SimpleDateFormat("EEE, d MMMM", locale).format(date) }
            delay(1000L) 
        }
    }

    LaunchedEffect(wallpaper) {
        state.clockColor.intValue = android.graphics.Color.WHITE
        withContext(Dispatchers.IO) {
            val bitmap = if(wallpaper.uri != null) BitmapHelper.decodeSampledBitmapFromUri(context, wallpaper.uri, 112) else BitmapHelper.decodeSampledBitmapFromResource(context.resources, wallpaper.resourceId, 112)
            if (bitmap != null) {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                var finalColors = Score.score(QuantizerCelebi.quantize(pixels, 128)).distinct().take(5)
                if (finalColors.size < 5 && finalColors.isNotEmpty()) {
                    val hct = Hct.fromInt(finalColors.first())
                    finalColors = (finalColors + listOf(Hct.from(hct.hue + 60.0, hct.chroma, hct.tone).toInt(), Hct.from(hct.hue - 60.0, hct.chroma, hct.tone).toInt(), Hct.from(hct.hue + 180.0, hct.chroma, hct.tone).toInt(), Hct.from(hct.hue, hct.chroma, 30.0).toInt(), Hct.from(hct.hue, hct.chroma, 80.0).toInt())).distinct().take(5)
                }
                withContext(Dispatchers.Main) { extractedColors = listOf(android.graphics.Color.WHITE, android.graphics.Color.BLACK) + finalColors; if (finalColors.isNotEmpty()) state.clockColor.intValue = finalColors.first() }
                bitmap.recycle()
            }
        }
    }

    LaunchedEffect(previewMask, state.stretchEnabled.value) {
        if (state.stretchEnabled.value && previewMask != null) {
            isCalculatingMap = true 
            withContext(Dispatchers.Default) {
                val mapStr = AdaptiveClockHelper.generateCollisionMap(previewMask, realScreenW, realScreenH)
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

    Column(modifier = modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.statusBarsPadding().height(16.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Card(modifier = Modifier.fillMaxHeight(0.85f).aspectRatio(screenAspectRatio).pointerInput(Unit) {
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
            }, shape = RoundedCornerShape(36.dp), elevation = CardDefaults.cardElevation(12.dp)) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncWallpaperImage(wallpaper = wallpaper, contentDescription = null, viewModel = viewModel, modifier = Modifier.fillMaxSize(), allowMagic = false)
                    Box(modifier = Modifier.fillMaxWidth().height(300.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=0.6f), Color.Transparent))))
                    
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
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
                    androidx.compose.animation.AnimatedVisibility(visible = (state.stretchEnabled.value && isMaskLoading) || isCalculatingMap, enter = fadeIn(tween(300)), exit = fadeOut(tween(400)), modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            AsyncWallpaperImage(wallpaper = wallpaper, contentDescription = null, viewModel = viewModel, modifier = Modifier.fillMaxSize().blur(25.dp), allowMagic = false)
                            Spacer(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.4f)))
                            ParticleLoadingOverlay(color = Color.White)
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp, top = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            ExtendedFloatingActionButton(onClick = { showCustomizeSheet = true }, icon = { Icon(Icons.Default.Palette, null) }, text = { Text("Customize") }, containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, shape = RoundedCornerShape(24.dp))
            Spacer(Modifier.width(16.dp))
            ExtendedFloatingActionButton(onClick = { showApplyOptions = true }, icon = { Icon(Icons.Default.Check, null) }, text = { Text("Done") }, containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = RoundedCornerShape(24.dp))
        }
        if (showDateCustomizeSheet) EditorDatePanel(viewModel, wallpaper, state) { showDateCustomizeSheet = false }
        if (showCustomizeSheet) EditorClockPanel(viewModel, wallpaper, state, extractedColors, availableFonts, previewMask, realScreenW, realScreenH, densityVal) { showCustomizeSheet = false }
        if (showApplyOptions) EditorApplyPanel(viewModel, wallpaper, state, previewMask, realScreenW, realScreenH, context, onApplyClockAndWallpaperClick) { showApplyOptions = false }
    }
}

@Composable
fun AnimatedWallpaperEngine(wallpaper: Wallpaper, viewModel: WallpaperViewModel, previewMask: Bitmap?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewOriginal by remember { mutableStateOf<Bitmap?>(null) }
    var activeWallpaperId by remember { mutableStateOf(wallpaper.id) }

    if (activeWallpaperId != wallpaper.id) { currentBitmap = null; previewOriginal = null; activeWallpaperId = wallpaper.id }
    LaunchedEffect(wallpaper, viewModel.isAnimationEnabled, viewModel.isMagicShapeEnabled, viewModel.isFilamentEnabled, viewModel.currentMagicShape, viewModel.currentBackgroundColor, viewModel.is3DPopEnabled, viewModel.magicScale, viewModel.isCentered, viewModel.currentAnimationStyle) {
        if (viewModel.isFilamentEnabled) currentBitmap = null
        else if (viewModel.isAnimationEnabled) { val components = viewModel.getPreviewAnimationComponents(context, wallpaper); previewOriginal = components.first; if (components.first != null) currentBitmap = components.first } 
        else { val useMagic = viewModel.isMagicShapeEnabled; val newBitmap = viewModel.getOrCreateProcessedBitmap(context, wallpaper, allowMagic = useMagic); if (newBitmap != null) currentBitmap = newBitmap }
    }

    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (currentBitmap == null && viewModel.isFilamentEnabled) AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx -> TextureView(ctx).apply { FilamentTextureController(this) } })
        else if (currentBitmap == null) { AsyncWallpaperImage(wallpaper, null, viewModel, Modifier.fillMaxSize().blur(25.dp), allowMagic = false); Spacer(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.4f))); ParticleLoadingOverlay(Color.White) }
        if (currentBitmap != null) {
            if (viewModel.isAnimationEnabled && previewOriginal != null) {
                val currentAnim = remember(viewModel.currentAnimationStyle) { AnimationFactory.getAnimation(viewModel.currentAnimationStyle).apply { onUnlock() } }
                var frameTimeNanos by remember { mutableLongStateOf(0L) }
                LaunchedEffect(currentAnim) { var lastTime = withFrameNanos { it }; currentAnim.onUnlock(); while (true) { frameTimeNanos = withFrameNanos { it }; val dt = (frameTimeNanos - lastTime) / 1_000_000_000f; lastTime = frameTimeNanos; currentAnim.update(dt.coerceAtMost(0.1f)) } }
                val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG) }
                val maskXferPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN) } }
                val clipPath = remember { android.graphics.Path() }; val screenShapeRect = remember { android.graphics.RectF() }
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().pointerInput(currentAnim) { detectTapGestures(onPress = { currentAnim.onLock(); tryAwaitRelease(); currentAnim.onUnlock() }) }) {
                    frameTimeNanos.let {} 
                    val config = LiveWallpaperConfig(shapeName = viewModel.currentMagicShape.name, backgroundColor = viewModel.currentBackgroundColor, is3DPopEnabled = viewModel.is3DPopEnabled, scale = viewModel.magicScale, isCentered = viewModel.isCentered, animationStyle = viewModel.currentAnimationStyle.name, isMagicShapeEnabled = false, isAnimationEnabled = true, isFilamentEnabled = false, animParams = viewModel.currentAnimParams)
                    val geo = ShapeEffectHelper.getUnifiedGeometry(previewOriginal!!.width, previewOriginal!!.height, size.width, size.height, previewMask, config)
                    drawIntoCanvas { canvas -> currentAnim.draw(canvas.nativeCanvas, previewOriginal!!, previewMask, geo, config, paint, maskXferPaint, clipPath, screenShapeRect) }
                }
            } else {
                val infiniteTransition = rememberInfiniteTransition(label = "LivePreview")
                val finalScale by infiniteTransition.animateFloat(initialValue = 1f, targetValue = 1.03f, animationSpec = infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "animScale")
                AnimatedContent(targetState = currentBitmap, label = "LiveDepthAnim", transitionSpec = { (fadeIn(animationSpec = tween(500)) + scaleIn(initialScale = 0.95f, animationSpec = tween(500, easing = LinearOutSlowInEasing))).togetherWith(fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 1.15f, animationSpec = tween(400, easing = FastOutSlowInEasing))) }) { bitmap ->
                    if (bitmap != null) androidx.compose.foundation.Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize().scale(finalScale), contentScale = ContentScale.Crop)
                }
            }
        }
    }
}

class FilamentTextureController(val textureView: android.view.TextureView) : android.view.TextureView.SurfaceTextureListener, android.view.Choreographer.FrameCallback {
    private var filamentEngine: com.google.android.filament.Engine? = null; private var renderer: com.google.android.filament.Renderer? = null; private var scene: com.google.android.filament.Scene? = null; private var camera: com.google.android.filament.Camera? = null; private var view: com.google.android.filament.View? = null; private var swapChain: com.google.android.filament.SwapChain? = null; private var assetLoader: com.google.android.filament.gltfio.AssetLoader? = null; private var resourceLoader: com.google.android.filament.gltfio.ResourceLoader? = null; private var filamentAsset: com.google.android.filament.gltfio.FilamentAsset? = null; private var light: Int = 0; private val choreographer = android.view.Choreographer.getInstance(); private var androidSurface: android.view.Surface? = null; private var rotationAngle = 0f
    init { runCatching { com.google.android.filament.Filament.init() }; runCatching { com.google.android.filament.gltfio.Gltfio.init() }; runCatching { System.loadLibrary("gltfio-jni") }; textureView.surfaceTextureListener = this; textureView.isOpaque = false }
    override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) { val engine = com.google.android.filament.Engine.create().also { filamentEngine = it }; renderer = engine.createRenderer(); scene = engine.createScene(); camera = engine.createCamera(engine.entityManager.create()); view = engine.createView(); view?.scene = scene; view?.camera = camera; scene?.skybox = com.google.android.filament.Skybox.Builder().color(0.1f, 0.12f, 0.15f, 1.0f).build(engine); light = com.google.android.filament.EntityManager.get().create(); com.google.android.filament.LightManager.Builder(com.google.android.filament.LightManager.Type.DIRECTIONAL).color(1.0f, 1.0f, 0.95f).intensity(50000.0f).direction(-1.0f, -1.0f, -1.0f).castShadows(true).build(engine, light); scene?.addEntity(light); assetLoader = com.google.android.filament.gltfio.AssetLoader(engine, com.google.android.filament.gltfio.UbershaderProvider(engine), com.google.android.filament.EntityManager.get()); resourceLoader = com.google.android.filament.gltfio.ResourceLoader(engine); try { val safeContext = if (android.os.Build.VERSION.SDK_INT >= 24) textureView.context.createDeviceProtectedStorageContext() else textureView.context; val customFile = java.io.File(safeContext.filesDir, "custom_model.glb"); if (customFile.exists()) { val bytes = customFile.readBytes(); val buffer = java.nio.ByteBuffer.allocateDirect(bytes.size); buffer.put(bytes); buffer.rewind(); filamentAsset = assetLoader?.createAsset(buffer); filamentAsset?.let { asset -> resourceLoader?.loadResources(asset); asset.releaseSourceData(); scene?.addEntities(asset.entities) } } } catch (e: Exception) { }; androidSurface = android.view.Surface(surface); swapChain = engine.createSwapChain(androidSurface!!); view?.viewport = com.google.android.filament.Viewport(0, 0, width, height); camera?.setProjection(45.0, width.toDouble() / height.toDouble(), 0.1, 100.0, com.google.android.filament.Camera.Fov.VERTICAL); camera?.lookAt(0.0, 0.0, 5.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0); choreographer.postFrameCallback(this) }
    override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) { view?.viewport = com.google.android.filament.Viewport(0, 0, width, height); camera?.setProjection(45.0, width.toDouble() / height.toDouble(), 0.1, 100.0, com.google.android.filament.Camera.Fov.VERTICAL) }
    override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean { choreographer.removeFrameCallback(this); filamentEngine?.let { engine -> filamentAsset?.let { assetLoader?.destroyAsset(it) }; assetLoader?.destroy(); resourceLoader?.destroy(); engine.destroyEntity(light); swapChain?.let { engine.destroySwapChain(it) }; renderer?.let { engine.destroyRenderer(it) }; view?.let { engine.destroyView(it) }; scene?.let { engine.destroyScene(it) }; camera?.let { engine.destroyCameraComponent(it.entity) }; engine.destroy() }; filamentEngine = null; androidSurface?.release(); return true }
    override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
    override fun doFrame(frameTimeNanos: Long) { if (filamentEngine != null && swapChain != null) { choreographer.postFrameCallback(this); filamentAsset?.let { asset -> val tm = filamentEngine?.transformManager; val instance = tm?.getInstance(asset.root); if (instance != null && instance != 0) { val halfExtent = asset.boundingBox.halfExtent; val maxExtent = kotlin.math.max(halfExtent[0], kotlin.math.max(halfExtent[1], halfExtent[2])); val scaleFactor = if (maxExtent > 0f) 2.0f / maxExtent else 1.0f; val transform = FloatArray(16); android.opengl.Matrix.setIdentityM(transform, 0); android.opengl.Matrix.rotateM(transform, 0, rotationAngle, 0f, 1f, 0f); android.opengl.Matrix.scaleM(transform, 0, scaleFactor, scaleFactor, scaleFactor); android.opengl.Matrix.translateM(transform, 0, -asset.boundingBox.center[0], -asset.boundingBox.center[1], -asset.boundingBox.center[2]); tm.setTransform(instance, transform); rotationAngle += 0.3f } }; if (renderer?.beginFrame(swapChain!!, frameTimeNanos) == true) { renderer?.render(view!!); renderer?.endFrame() } } }
}
