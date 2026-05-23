package com.android.CaveArt

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.text.format.DateFormat
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.android.CaveArt.animations.AnimationFactory
import com.android.CaveArt.animations.AnimationStyle
import com.android.CaveArt.animations.AnimSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.pow
import coil3.compose.rememberAsyncImagePainter
import kotlin.random.Random
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score
import com.materialkolor.hct.Hct
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.google.android.filament.Camera
import com.google.android.filament.Engine as FilamentEngineCore
import com.google.android.filament.Filament
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.View as FilamentView
import com.google.android.filament.Viewport

private data class Particle(
    val initialX: Float,
    val initialY: Float,
    val radius: Float,
    val speed: Float,
    val swaySpeed: Float,
    val initialAlpha: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffectsControlsSheet(
    wallpaper: Wallpaper,
    viewModel: WallpaperViewModel,
    previewMask: Bitmap? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val config = LocalConfiguration.current
    val density = LocalDensity.current.density
    
    var localMask by remember { mutableStateOf(previewMask) }
    LaunchedEffect(wallpaper, previewMask) {
        if (previewMask != null) localMask = previewMask
        else localMask = viewModel.getMaskForClock(context, wallpaper)
    }

    val modelPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val safeContext = if (android.os.Build.VERSION.SDK_INT >= 24) context.createDeviceProtectedStorageContext() else context
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        java.io.File(safeContext.filesDir, "custom_model.glb").outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        viewModel.setFilamentEnabled(false)
                        delay(100)
                        viewModel.setFilamentEnabled(true)
                        android.widget.Toast.makeText(context, "3D Model Imported!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    var extractedColors by remember { mutableStateOf(listOf<Int>()) }
    var sliderPosition by remember { mutableFloatStateOf(viewModel.magicScale) }
    
    val currentAnim = remember(viewModel.currentAnimationStyle) { AnimationFactory.getAnimation(viewModel.currentAnimationStyle) }
    
    val tabs = remember(viewModel.isMagicShapeEnabled, viewModel.isAnimationEnabled, viewModel.isFilamentEnabled, currentAnim) {
        val list = mutableListOf<String>()
        if (viewModel.isMagicShapeEnabled) { list.add("Shape"); list.add("Style") }
        if (viewModel.isAnimationEnabled) {
            list.add("Animation")
            if (currentAnim.supports3DPop() || currentAnim.supportsCenter() || currentAnim.supportsScale() || currentAnim.hasCustomUI()) list.add("Style")
        }
        if (viewModel.isFilamentEnabled) list.add("3D Engine")
        list
    }

    var selectedTab by remember { mutableStateOf("") }
    LaunchedEffect(tabs) { if (!tabs.contains(selectedTab) && tabs.isNotEmpty()) selectedTab = tabs.first() }

    LaunchedEffect(wallpaper) {
        withContext(Dispatchers.IO) {
            val bitmap = if(wallpaper.uri != null) BitmapHelper.decodeSampledBitmapFromUri(context, wallpaper.uri, 500) else BitmapHelper.decodeSampledBitmapFromResource(context.resources, wallpaper.resourceId, 500)
            if (bitmap != null) {
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 112, 112, true)
                val pixels = IntArray(scaledBitmap.width * scaledBitmap.height)
                scaledBitmap.getPixels(pixels, 0, scaledBitmap.width, 0, 0, scaledBitmap.width, scaledBitmap.height)
                val quantizerResult = QuantizerCelebi.quantize(pixels, 128)
                val rankedColors = Score.score(quantizerResult)
                
                var finalColors = rankedColors.distinct().take(5)
                if (finalColors.size < 5 && finalColors.isNotEmpty()) {
                    val hct = Hct.fromInt(finalColors.first())
                    val generated = listOf(
                        Hct.from(hct.hue + 60.0, hct.chroma, hct.tone).toInt(),
                        Hct.from(hct.hue - 60.0, hct.chroma, hct.tone).toInt(),
                        Hct.from(hct.hue + 180.0, hct.chroma, hct.tone).toInt(),
                        Hct.from(hct.hue, hct.chroma, 30.0).toInt(),
                        Hct.from(hct.hue, hct.chroma, 80.0).toInt()
                    )
                    finalColors = (finalColors + generated).distinct().take(5)
                } else if (finalColors.isEmpty()) {
                    finalColors = listOf(android.graphics.Color.parseColor("#4CAF50"), android.graphics.Color.parseColor("#2196F3"), android.graphics.Color.parseColor("#FF9800"), android.graphics.Color.parseColor("#E91E63"), android.graphics.Color.parseColor("#9C27B0"))
                }
                
                withContext(Dispatchers.Main) {
                    extractedColors = finalColors
                    if (finalColors.isNotEmpty() && !finalColors.contains(viewModel.currentBackgroundColor)) {
                        viewModel.updateMagicConfig(viewModel.currentMagicShape, finalColors.first())
                    }
                }
                scaledBitmap.recycle()
                bitmap.recycle()
            }
        }
    }
    
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        
        if (tabs.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(48.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background), 
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Box(modifier = Modifier.padding(vertical = 28.dp).animateContentSize()) {
                    when (selectedTab) {
                        "Shape" -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                                ) {
                                    MagicShape.values().take(5).forEach { shape ->
                                        val isSelected = viewModel.currentMagicShape == shape
                                        Box(
                                            modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { viewModel.updateMagicConfig(shape, viewModel.currentBackgroundColor) },
                                            contentAlignment = Alignment.Center
                                        ) { ShapeIcon(shape = shape, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxSize(0.45f)) }
                                    }
                                }
                                Spacer(Modifier.height(28.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                                ) {
                                    extractedColors.forEach { colorInt ->
                                        val isSelected = viewModel.currentBackgroundColor == colorInt
                                        Box(modifier = Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                                            if (isSelected) Box(modifier = Modifier.fillMaxSize().border(3.dp, Color(colorInt), CircleShape))
                                            Box(modifier = Modifier.fillMaxSize(if (isSelected) 0.65f else 1f).clip(CircleShape).background(Color(colorInt)).clickable { viewModel.updateMagicConfig(viewModel.currentMagicShape, colorInt) })
                                        }
                                    }
                                }
                            }
                        }
                        "Animation" -> {
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Animation Style", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(16.dp))
                                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    AnimationStyle.values().forEach { style ->
                                        val isSelected = viewModel.currentAnimationStyle == style
                                        FilterChip(
                                            selected = isSelected, onClick = { viewModel.updateAnimationStyle(style) },
                                            label = { Text(style.label, fontWeight = FontWeight.Bold) }, shape = RoundedCornerShape(16.dp),
                                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer),
                                            border = FilterChipDefaults.filterChipBorder(enabled = true, selected = isSelected, borderColor = Color.Transparent)
                                        )
                                    }
                                }
                            }
                        }
                        "3D Engine" -> {
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Filament Engine Active", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(8.dp))
                                Text("Applies a physical 3D scene to your background. Import a .glb file to render it.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = { modelPickerLauncher.launch("*/*") }, shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = "Import")
                                    Spacer(Modifier.width(8.dp))
                                    Text("Import .GLB Model", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        "Style" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 24.dp)
                            ) {
                                val showPop = viewModel.isMagicShapeEnabled || (viewModel.isAnimationEnabled && currentAnim.supports3DPop())
                                val showCenter = viewModel.isMagicShapeEnabled || (viewModel.isAnimationEnabled && currentAnim.supportsCenter())
                                val showScale = viewModel.isMagicShapeEnabled || (viewModel.isAnimationEnabled && currentAnim.supportsScale())
                                
                                if (showPop || showCenter) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        if (showPop) FilterChip(selected = viewModel.is3DPopEnabled, onClick = { viewModel.toggle3DPop() }, label = { Text("3D Pop", fontWeight = FontWeight.Bold) }, leadingIcon = { Icon(Icons.Default.Layers, null, Modifier.size(18.dp)) }, shape = RoundedCornerShape(16.dp), border = null)
                                        if (showCenter) FilterChip(selected = viewModel.isCentered, onClick = { viewModel.toggleCentered() }, label = { Text("Center", fontWeight = FontWeight.Bold) }, leadingIcon = { Icon(Icons.Default.FilterCenterFocus, null, Modifier.size(18.dp)) }, shape = RoundedCornerShape(16.dp), border = null)
                                    }
                                }
                                
                                if (showScale) {
                                    if (showPop || showCenter) Spacer(Modifier.height(24.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Effect Size", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(if (sliderPosition < 1.0f) "Tight" else "Wide", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Slider(value = sliderPosition, onValueChange = { sliderPosition = it }, onValueChangeFinished = { viewModel.updateMagicScale(sliderPosition) }, valueRange = 0.5f..1.5f, steps = 5)
                                }
                                
                                if (viewModel.isAnimationEnabled && currentAnim.hasCustomUI()) {
                                    if (showPop || showCenter || showScale) {
                                        Spacer(Modifier.height(16.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        Spacer(Modifier.height(16.dp))
                                    }
                                    currentAnim.CustomUI(params = viewModel.currentAnimParams, onUpdateParam = { id, value -> viewModel.updateAnimParam(id, value) })
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (tabs.isNotEmpty()) {
            Surface(
                modifier = Modifier.navigationBarsPadding().padding(bottom = 12.dp).height(64.dp), shape = CircleShape,
                color = MaterialTheme.colorScheme.background, shadowElevation = 0.dp
            ) {
                Row(modifier = Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    tabs.forEach { tab ->
                        val isSelected = selectedTab == tab
                        val tabIcon = when (tab) {
                            "Shape" -> Icons.Default.Category
                            "Animation" -> Icons.Default.Animation
                            "3D Engine" -> Icons.Default.ViewInAr
                            else -> Icons.Default.Palette
                        }
                        Box(
                            modifier = Modifier.height(48.dp).clip(CircleShape).background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent).clickable { selectedTab = tab }.padding(horizontal = if (isSelected) 20.dp else 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (isSelected) Icon(imageVector = tabIcon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text(text = tab, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShapeIcon(shape: MagicShape, color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val rect = RectF(0f, 0f, size.width, size.height)
        drawPath(path = ShapePathProvider.getPathForShape(shape, rect).asComposePath(), color = color)
    }
}

@Composable
fun LiveEffectImage(
    wallpaper: Wallpaper,
    viewModel: WallpaperViewModel,
    modifier: Modifier = Modifier,
    onApplyClockAndWallpaperClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var previewMask by remember { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(wallpaper) {
        previewMask = viewModel.getMaskForClock(context, wallpaper)
    }

    if (viewModel.isLockscreenClockPreviewVisible) {
        ClockEditorPreview(wallpaper, viewModel, previewMask, modifier, onApplyClockAndWallpaperClick)
    } else {
        AnimatedWallpaperEngine(wallpaper, viewModel, previewMask, modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockEditorPreview(
    wallpaper: Wallpaper,
    viewModel: WallpaperViewModel,
    previewMask: Bitmap?,
    modifier: Modifier = Modifier,
    onApplyClockAndWallpaperClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val metrics = context.resources.displayMetrics
    val realScreenW = metrics.widthPixels.toFloat()
    val realScreenH = metrics.heightPixels.toFloat()
    val densityVal = metrics.density
    val config = LocalConfiguration.current
    
    var showCustomizeSheet by remember { mutableStateOf(false) }
    var showApplyOptions by remember { mutableStateOf(false) }
    
    var isDraggingClock by remember { mutableStateOf(false) }
    var isDraggingDate by remember { mutableStateOf(false) }

    var previewClockX by remember { mutableFloatStateOf(viewModel.lockscreenClockOffsetX) }
    var previewClockY by remember { mutableFloatStateOf(viewModel.lockscreenClockOffsetY) }
    var previewDateX by remember { mutableFloatStateOf(viewModel.lockscreenDateOffsetX) }
    var previewDateY by remember { mutableFloatStateOf(viewModel.lockscreenDateOffsetY) }
    var previewHourSize by remember { mutableFloatStateOf(viewModel.clockHourSize) }
    var previewMinSize by remember { mutableFloatStateOf(viewModel.clockMinuteSize) }
    var previewStrokeWidth by remember { mutableFloatStateOf(viewModel.clockStrokeWidth) }
    var previewRoundness by remember { mutableFloatStateOf(viewModel.clockRoundness) }
    var previewStretchEnabled by remember { mutableStateOf(viewModel.isClockStretchEnabled) }
    var previewCollisionMap by remember { mutableStateOf(viewModel.clockCollisionMap) }
    
    var timeString by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        while (true) {
            val is24Hour = DateFormat.is24HourFormat(context)
            val timePattern = if (is24Hour) "HH:mm" else "hh:mm"
            timeString = java.text.SimpleDateFormat(timePattern, java.util.Locale.getDefault()).format(java.util.Date())
            dateText = java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault()).format(java.util.Date())
            delay(1000L)
        }
    }

    LaunchedEffect(previewMask, previewStretchEnabled) {
        if (previewStretchEnabled && previewMask != null) {
            withContext(Dispatchers.Default) {
                val mapStr = AdaptiveClockHelper.generateCollisionMap(previewMask, realScreenW, realScreenH)
                withContext(Dispatchers.Main) {
                    previewCollisionMap = mapStr
                }
            }
        } else {
            previewCollisionMap = ""
        }
    }
    
    val collisionMapArray = remember(previewCollisionMap) {
        if (previewCollisionMap.isNotEmpty()) previewCollisionMap.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray() else null
    }
    
    val sharedPath = remember { android.graphics.Path() }
    val sharedPathBounds = remember { android.graphics.RectF() }
    val sharedMatrix = remember { android.graphics.Matrix() }
    var currentScale by remember { mutableFloatStateOf(1f) }
    
    val cornerEffect = remember(previewRoundness, densityVal, currentScale) {
        android.graphics.CornerPathEffect(previewRoundness * densityVal * currentScale)
    }

    val vectorPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
            setShadowLayer(15f, 0f, 5f, android.graphics.Color.argb(160, 0, 0, 0))
        }
    }

    val datePaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = android.graphics.Paint.Align.CENTER
            setShadowLayer(10f, 0f, 3f, android.graphics.Color.argb(160, 0, 0, 0))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncWallpaperImage(wallpaper = wallpaper, contentDescription = null, viewModel = viewModel, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, allowMagic = false)
            
            Box(modifier = Modifier.fillMaxWidth().height(300.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=0.6f), Color.Transparent))))

            Box(modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var rawClockX = previewClockX
                    var rawClockY = previewClockY
                    var rawDateX = previewDateX
                    var rawDateY = previewDateY

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true)
                        if (down.isConsumed) return@awaitEachGesture
                        
                        var dragged = false
                        val scale = maxOf(size.width / realScreenW, size.height / realScreenH)
                        val dy = (size.height - realScreenH * scale) / 2f
                        
                        val scaledDateY = (previewDateY * densityVal + (20f * densityVal)) * scale + dy
                        val scaledClockY = (previewClockY * densityVal) * scale + dy
                        
                        val distToDate = abs(down.position.y - scaledDateY)
                        val distToClock = abs(down.position.y - scaledClockY)
                        
                        val activeDrag = if (distToDate < distToClock && distToDate < 300f) "DATE" else "CLOCK"
                        
                        rawClockX = previewClockX
                        rawClockY = previewClockY
                        rawDateX = previewDateX
                        rawDateY = previewDateY

                        var hasChangedClock = false
                        var hasChangedDate = false

                        do {
                            val event = awaitPointerEvent()
                            val pan = event.calculatePan()
                            val zoom = event.calculateZoom()
                            
                            if (pan.getDistance() > 3f || abs(zoom - 1f) > 0.01f) {
                                dragged = true
                            }
                            
                            if (dragged) {
                                if (activeDrag == "DATE") {
                                    isDraggingDate = true
                                    hasChangedDate = true
                                    rawDateX += (pan.x / scale) / densityVal
                                    rawDateY += (pan.y / scale) / densityVal
                                    
                                    previewDateX = rawDateX
                                    previewDateY = rawDateY.coerceIn(0f, config.screenHeightDp.toFloat())
                                } else {
                                    isDraggingClock = true
                                    hasChangedClock = true
                                    val newHour = (previewHourSize * zoom).coerceIn(40f, 200f)
                                    val newMin = (previewMinSize * zoom).coerceIn(40f, 200f)
                                    
                                    rawClockX += (pan.x / scale) / densityVal
                                    rawClockY += (pan.y / scale) / densityVal
                                    
                                    previewHourSize = newHour
                                    previewMinSize = newMin
                                    previewClockX = rawClockX
                                    previewClockY = rawClockY.coerceIn(0f, config.screenHeightDp.toFloat())
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                        
                        isDraggingClock = false
                        isDraggingDate = false
                        
                        if (hasChangedClock) {
                            if (abs(previewClockX) < 15f) {
                                previewClockX = 0f
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                        if (hasChangedDate) {
                            if (abs(previewDateX) < 15f) {
                                previewDateX = 0f
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                    }
                }
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    if (timeString.isEmpty()) return@Canvas

                    val hPx = previewHourSize * densityVal
                    val mPx = previewMinSize * densityVal
                    
                    val hourW = hPx * 0.55f
                    val minW = mPx * 0.55f
                    val gap = hPx * 0.15f
                    val colonIdx = timeString.indexOf(':')
                    val hCount = if (colonIdx != -1) colonIdx else timeString.length
                    val mCount = if (colonIdx != -1) timeString.length - colonIdx - 1 else 0
                    val totalWidth = (hCount * hourW) + (mCount * minW) + (gap * (hCount + mCount))

                    val realCenterX = (realScreenW / 2f) + (previewClockX * densityVal)
                    val realStartX = realCenterX - (totalWidth / 2f)
                    val realStartY = previewClockY * densityVal

                    val scale = maxOf(size.width / realScreenW, size.height / realScreenH)
                    if (currentScale != scale) currentScale = scale

                    val dx = (size.width - realScreenW * scale) / 2f
                    val dy = (size.height - realScreenH * scale) / 2f

                    AdaptiveClockHelper.buildPath(
                        timeString = timeString,
                        startX = realStartX,
                        startY = realStartY,
                        absoluteClockX = 0f, 
                        absoluteClockY = 0f, 
                        hourH = hPx,
                        minH = mPx,
                        screenW = realScreenW,
                        screenH = realScreenH,
                        isStretchEnabled = previewStretchEnabled,
                        collisionMap = collisionMapArray,
                        density = densityVal,
                        strokeWidth = previewStrokeWidth,
                        path = sharedPath 
                    )

                    sharedPath.computeBounds(sharedPathBounds, true)

                    if (isDraggingClock) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.3f),
                            topLeft = Offset(sharedPathBounds.left * scale + dx - 40f, sharedPathBounds.top * scale + dy - 40f),
                            size = Size(sharedPathBounds.width() * scale + 80f, sharedPathBounds.height() * scale + 80f),
                            cornerRadius = CornerRadius(32f, 32f),
                            style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f))
                        )
                    }

                    val scaledDateY = (previewDateY * densityVal + (20f * densityVal)) * scale + dy
                    val scaledDateX = (realScreenW / 2f) * scale + dx + (previewDateX * densityVal) * scale

                    if (isDraggingDate) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.3f),
                            topLeft = Offset(scaledDateX - 250f, scaledDateY - 60f),
                            size = Size(500f, 100f),
                            cornerRadius = CornerRadius(32f, 32f),
                            style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f))
                        )
                    }

                    drawIntoCanvas { canvas ->
                        sharedMatrix.reset()
                        sharedMatrix.postScale(scale, scale)
                        sharedMatrix.postTranslate(dx, dy)
                        sharedPath.transform(sharedMatrix) 

                        vectorPaint.strokeWidth = previewStrokeWidth * densityVal * scale
                        vectorPaint.pathEffect = cornerEffect

                        canvas.nativeCanvas.drawPath(sharedPath, vectorPaint)

                        datePaint.textSize = 20f * densityVal * scale
                        canvas.nativeCanvas.drawText(
                            dateText,
                            scaledDateX,
                            scaledDateY,
                            datePaint
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExtendedFloatingActionButton(
                onClick = { showCustomizeSheet = true },
                icon = { Icon(Icons.Default.Palette, null) },
                text = { Text("Customize") },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            ExtendedFloatingActionButton(
                onClick = { showApplyOptions = true },
                icon = { Icon(Icons.Default.Check, null) },
                text = { Text("Done") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(24.dp)
            )
        }

        if (showCustomizeSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            AmbientBottomSheet(
                onDismissRequest = { showCustomizeSheet = false },
                sheetState = sheetState,
                viewModel = viewModel,
                currentWallpaper = wallpaper
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text("Clock Style", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(24.dp))

                    Text("Thickness", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LineWeight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Slider(
                            value = previewStrokeWidth,
                            onValueChange = { previewStrokeWidth = it },
                            valueRange = 1f..25f,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Text("Corner Roundness", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.RoundedCorner, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Slider(
                            value = previewRoundness,
                            onValueChange = { previewRoundness = it },
                            valueRange = 0f..80f,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(32.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(
                            onClick = { 
                                if (previewMask != null) {
                                    scope.launch(Dispatchers.Default) {
                                        val result = AdaptiveClockHelper.calculateAutoFit(
                                            previewMask, realScreenW, realScreenH, previewHourSize, previewMinSize, densityVal,
                                            viewModel.isMagicShapeEnabled, viewModel.magicScale, viewModel.isCentered
                                        )
                                        if (result != null) {
                                            withContext(Dispatchers.Main) {
                                                previewHourSize = result.hourSize
                                                previewMinSize = result.minSize
                                                previewClockX = 0f
                                                previewClockY = result.yDp
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            enabled = previewMask != null
                        ) {
                            Icon(Icons.Default.AutoFixHigh, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Smart Fit", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { previewStretchEnabled = !previewStretchEnabled },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = if (previewStretchEnabled) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()
                        ) {
                            Icon(Icons.Default.Transform, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Adaptive", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (showApplyOptions) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            AmbientBottomSheet(
                onDismissRequest = { showApplyOptions = false },
                sheetState = sheetState,
                viewModel = viewModel,
                currentWallpaper = wallpaper
            ) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).navigationBarsPadding()) {
                    Text("Apply Lockscreen", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 24.dp))
                    
                    DestinationButton(
                        icon = Icons.Default.ScreenLockPortrait, 
                        title = "Clock Layout Only", 
                        subtitle = "Update clock position and style", 
                        isSetting = false
                    ) {
                        showApplyOptions = false
                        viewModel.updateClockStyle(context, previewHourSize, previewMinSize, previewStrokeWidth, previewRoundness)
                        viewModel.updateLockscreenClockPosition(context, previewClockX, previewClockY)
                        viewModel.updateLockscreenDatePosition(context, previewDateX, previewDateY)
                        viewModel.toggleClockStretch(context, previewStretchEnabled, previewMask, realScreenW, realScreenH)
                        viewModel.isLockscreenClockPreviewVisible = false
                        android.widget.Toast.makeText(context, "Clock Layout Applied", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    
                    DestinationButton(
                        icon = Icons.Default.Wallpaper, 
                        title = "Clock + Wallpaper", 
                        subtitle = "Update layout and set static wallpaper", 
                        isSetting = false
                    ) {
                        showApplyOptions = false
                        viewModel.updateClockStyle(context, previewHourSize, previewMinSize, previewStrokeWidth, previewRoundness)
                        viewModel.updateLockscreenClockPosition(context, previewClockX, previewClockY)
                        viewModel.updateLockscreenDatePosition(context, previewDateX, previewDateY)
                        viewModel.toggleClockStretch(context, previewStretchEnabled, previewMask, realScreenW, realScreenH)
                        onApplyClockAndWallpaperClick() 
                    }
                    
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun AnimatedWallpaperEngine(
    wallpaper: Wallpaper,
    viewModel: WallpaperViewModel,
    previewMask: Bitmap?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewOriginal by remember { mutableStateOf<Bitmap?>(null) }
    var activeWallpaperId by remember { mutableStateOf(wallpaper.id) }

    if (activeWallpaperId != wallpaper.id) {
        currentBitmap = null
        previewOriginal = null
        activeWallpaperId = wallpaper.id
    }

    LaunchedEffect(
        wallpaper, viewModel.isAnimationEnabled, viewModel.isMagicShapeEnabled,
        viewModel.isFilamentEnabled, viewModel.currentMagicShape, viewModel.currentBackgroundColor, 
        viewModel.is3DPopEnabled, viewModel.magicScale, viewModel.isCentered, viewModel.currentAnimationStyle
    ) {
        if (viewModel.isFilamentEnabled) {
            currentBitmap = null
        } else if (viewModel.isAnimationEnabled) {
            val components = viewModel.getPreviewAnimationComponents(context, wallpaper)
            previewOriginal = components.first
            if (components.first != null) currentBitmap = components.first 
        } else {
            val useMagic = viewModel.isMagicShapeEnabled
            val newBitmap = viewModel.getOrCreateProcessedBitmap(context, wallpaper, allowMagic = useMagic)
            if (newBitmap != null) currentBitmap = newBitmap
        }
    }
    
    val isDarkTheme = isSystemInDarkTheme()
    val backgroundColor = if (isDarkTheme) Color.Black else MaterialTheme.colorScheme.surface
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    val particleColor = if (isDarkTheme) Color.White else Color.Black
    val iconColor = if (isDarkTheme) Color.White else Color.Black

    Box(modifier = modifier.background(backgroundColor), contentAlignment = Alignment.Center) {
        
        if (currentBitmap == null && viewModel.isFilamentEnabled) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> TextureView(ctx).apply { FilamentTextureController(this) } }
            )
        } else if (currentBitmap == null) {
            if (wallpaper.uri != null) Image(painter = rememberAsyncImagePainter(wallpaper.uri), contentDescription = null, modifier = Modifier.fillMaxSize().blur(25.dp), contentScale = ContentScale.Crop)
            else Image(painter = painterResource(id = wallpaper.resourceId), contentDescription = null, modifier = Modifier.fillMaxSize().blur(25.dp), contentScale = ContentScale.Crop)
            Spacer(modifier = Modifier.fillMaxSize().background(overlayColor))
            ParticleLoadingOverlay(color = particleColor)
            val infiniteTransition = rememberInfiniteTransition(label = "IconPulse")
            val scale by infiniteTransition.animateFloat(initialValue = 1f, targetValue = 1.15f, animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "Scale")
            val glowAlpha by infiniteTransition.animateFloat(initialValue = 0.3f, targetValue = 0.7f, animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "Glow")
            Box(contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Canvas(modifier = Modifier.size(100.dp).scale(scale)) { drawCircle(brush = Brush.radialGradient(colors = listOf(particleColor.copy(alpha = glowAlpha), Color.Transparent), center = center, radius = size.width / 2)) }
                Icon(Icons.Default.AutoAwesome, "Processing", tint = iconColor, modifier = Modifier.size(48.dp).scale(scale))
            }
        }
        
        if (currentBitmap != null) {
            if (viewModel.isAnimationEnabled && previewOriginal != null) {
                val currentAnim = remember(viewModel.currentAnimationStyle) { AnimationFactory.getAnimation(viewModel.currentAnimationStyle).apply { onUnlock() } }
                var frameTimeNanos by remember { mutableLongStateOf(0L) }
                
                LaunchedEffect(currentAnim) {
                    var lastTime = withFrameNanos { it }
                    currentAnim.onUnlock()
                    while (true) {
                        frameTimeNanos = withFrameNanos { it }
                        val dt = (frameTimeNanos - lastTime) / 1_000_000_000f
                        lastTime = frameTimeNanos
                        currentAnim.update(dt.coerceAtMost(0.1f))
                    }
                }

                val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG) }
                val maskXferPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN) } }
                val clipPath = remember { android.graphics.Path() }
                val screenShapeRect = remember { android.graphics.RectF() }
                
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(currentAnim) {
                            detectTapGestures(
                                onPress = {
                                    currentAnim.onLock()
                                    tryAwaitRelease()
                                    currentAnim.onUnlock()
                                }
                            )
                        }
                ) {
                    frameTimeNanos.let {} 
                    val config = LiveWallpaperConfig(shapeName = viewModel.currentMagicShape.name, backgroundColor = viewModel.currentBackgroundColor, is3DPopEnabled = viewModel.is3DPopEnabled, scale = viewModel.magicScale, isCentered = viewModel.isCentered, animationStyle = viewModel.currentAnimationStyle.name, isMagicShapeEnabled = false, isAnimationEnabled = true, isFilamentEnabled = false, animParams = viewModel.currentAnimParams)
                    val geo = ShapeEffectHelper.getUnifiedGeometry(previewOriginal!!.width, previewOriginal!!.height, size.width, size.height, previewMask, config)
                    drawIntoCanvas { canvas -> currentAnim.draw(canvas.nativeCanvas, previewOriginal!!, previewMask, geo, config, paint, maskXferPaint, clipPath, screenShapeRect) }
                }

            } else {
                val infiniteTransition = rememberInfiniteTransition(label = "LivePreview")
                val finalScale by infiniteTransition.animateFloat(initialValue = 1f, targetValue = 1.03f, animationSpec = infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "animScale")

                AnimatedContent(
                    targetState = currentBitmap, label = "LiveDepthAnim", 
                    transitionSpec = { (fadeIn(animationSpec = tween(500)) + scaleIn(initialScale = 0.95f, animationSpec = tween(500, easing = LinearOutSlowInEasing))).togetherWith(fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 1.15f, animationSpec = tween(400, easing = FastOutSlowInEasing))) }
                ) { bitmap ->
                    if (bitmap != null) {
                        Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize().scale(finalScale), contentScale = ContentScale.Crop)
                    }
                }
            }
        }
    }
}

@Composable
fun ParticleLoadingOverlay(color: Color) {
    val density = LocalDensity.current
    val particles = remember { List(350) { Particle(initialX = Random.nextFloat(), initialY = Random.nextFloat(), radius = Random.nextFloat() * 3f + 1f, speed = Random.nextFloat() * 0.05f + 0.01f, swaySpeed = Random.nextFloat() * 2f + 1f, initialAlpha = Random.nextFloat() * 0.7f + 0.1f) } }
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { val startTime = withFrameNanos { it }; while (true) { withFrameNanos { frameTime -> time = (frameTime - startTime) / 1_000_000_000f } } }

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val swayPx = 15.dp.toPx()
        particles.forEachIndexed { index, p ->
            var yProgress = (p.initialY - (p.speed * time)) % 1f
            if (yProgress < 0) yProgress += 1f
            val currentY = yProgress * height
            val rawSway = sin((time * p.swaySpeed + index).toDouble()).toFloat()
            val currentX = (p.initialX * width) + (rawSway * swayPx)
            val blinkFactor = ((sin((time * p.swaySpeed * 3f + index).toDouble()).toFloat() + 1) / 2f).pow(2)
            drawCircle(color = color, radius = p.radius * density.density, center = Offset(currentX, currentY), alpha = (p.initialAlpha * blinkFactor).coerceIn(0f, 1f))
        }
    }
}

class FilamentTextureController(val textureView: android.view.TextureView) : android.view.TextureView.SurfaceTextureListener, android.view.Choreographer.FrameCallback {
    private var filamentEngine: com.google.android.filament.Engine? = null
    private var renderer: com.google.android.filament.Renderer? = null
    private var scene: com.google.android.filament.Scene? = null
    private var camera: com.google.android.filament.Camera? = null
    private var view: com.google.android.filament.View? = null
    private var swapChain: com.google.android.filament.SwapChain? = null
    private var assetLoader: com.google.android.filament.gltfio.AssetLoader? = null
    private var resourceLoader: com.google.android.filament.gltfio.ResourceLoader? = null
    private var filamentAsset: com.google.android.filament.gltfio.FilamentAsset? = null
    private var light: Int = 0
    private val choreographer = android.view.Choreographer.getInstance()
    private var androidSurface: android.view.Surface? = null
    private var rotationAngle = 0f

    init {
        runCatching { com.google.android.filament.Filament.init() }
        runCatching { com.google.android.filament.gltfio.Gltfio.init() }
        runCatching { System.loadLibrary("gltfio-jni") }
        textureView.surfaceTextureListener = this
        textureView.isOpaque = false
    }

    override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
        val engine = com.google.android.filament.Engine.create().also { filamentEngine = it }
        renderer = engine.createRenderer()
        scene = engine.createScene()
        camera = engine.createCamera(engine.entityManager.create())
        view = engine.createView()
        view?.scene = scene
        view?.camera = camera
        scene?.skybox = com.google.android.filament.Skybox.Builder().color(0.1f, 0.12f, 0.15f, 1.0f).build(engine)
        light = com.google.android.filament.EntityManager.get().create()
        com.google.android.filament.LightManager.Builder(com.google.android.filament.LightManager.Type.DIRECTIONAL).color(1.0f, 1.0f, 0.95f).intensity(50000.0f).direction(-1.0f, -1.0f, -1.0f).castShadows(true).build(engine, light)
        scene?.addEntity(light)
        assetLoader = com.google.android.filament.gltfio.AssetLoader(engine, com.google.android.filament.gltfio.UbershaderProvider(engine), com.google.android.filament.EntityManager.get())
        resourceLoader = com.google.android.filament.gltfio.ResourceLoader(engine)
        try {
            val customFile = java.io.File(textureView.context.filesDir, "custom_model.glb")
            if (customFile.exists()) {
                val bytes = customFile.readBytes()
                val buffer = java.nio.ByteBuffer.allocateDirect(bytes.size)
                buffer.put(bytes)
                buffer.rewind()
                filamentAsset = assetLoader?.createAsset(buffer)
                filamentAsset?.let { asset -> resourceLoader?.loadResources(asset); asset.releaseSourceData(); scene?.addEntities(asset.entities) }
            }
        } catch (e: Exception) { }
        androidSurface = android.view.Surface(surface)
        swapChain = engine.createSwapChain(androidSurface!!)
        view?.viewport = com.google.android.filament.Viewport(0, 0, width, height)
        camera?.setProjection(45.0, width.toDouble() / height.toDouble(), 0.1, 100.0, com.google.android.filament.Camera.Fov.VERTICAL)
        camera?.lookAt(0.0, 0.0, 5.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        choreographer.postFrameCallback(this)
    }

    override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
        view?.viewport = com.google.android.filament.Viewport(0, 0, width, height)
        camera?.setProjection(45.0, width.toDouble() / height.toDouble(), 0.1, 100.0, com.google.android.filament.Camera.Fov.VERTICAL)
    }

    override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
        choreographer.removeFrameCallback(this)
        filamentEngine?.let { engine ->
            filamentAsset?.let { assetLoader?.destroyAsset(it) }
            assetLoader?.destroy()
            resourceLoader?.destroy()
            engine.destroyEntity(light)
            swapChain?.let { engine.destroySwapChain(it) }
            renderer?.let { engine.destroyRenderer(it) }
            view?.let { engine.destroyView(it) }
            scene?.let { engine.destroyScene(it) }
            camera?.let { engine.destroyCameraComponent(it.entity) }
            engine.destroy()
        }
        filamentEngine = null
        androidSurface?.release()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}

    override fun doFrame(frameTimeNanos: Long) {
        if (filamentEngine != null && swapChain != null) {
            choreographer.postFrameCallback(this)
            filamentAsset?.let { asset ->
                val tm = filamentEngine?.transformManager
                val instance = tm?.getInstance(asset.root)
                if (instance != null && instance != 0) {
                    val halfExtent = asset.boundingBox.halfExtent
                    val maxExtent = kotlin.math.max(halfExtent[0], kotlin.math.max(halfExtent[1], halfExtent[2]))
                    val scaleFactor = if (maxExtent > 0f) 2.0f / maxExtent else 1.0f
                    val transform = FloatArray(16)
                    android.opengl.Matrix.setIdentityM(transform, 0)
                    android.opengl.Matrix.rotateM(transform, 0, rotationAngle, 0f, 1f, 0f)
                    android.opengl.Matrix.scaleM(transform, 0, scaleFactor, scaleFactor, scaleFactor)
                    android.opengl.Matrix.translateM(transform, 0, -asset.boundingBox.center[0], -asset.boundingBox.center[1], -asset.boundingBox.center[2])
                    tm.setTransform(instance, transform)
                    rotationAngle += 0.3f 
                }
            }
            if (renderer?.beginFrame(swapChain!!, frameTimeNanos) == true) {
                renderer?.render(view!!)
                renderer?.endFrame()
            }
        }
    }
}
