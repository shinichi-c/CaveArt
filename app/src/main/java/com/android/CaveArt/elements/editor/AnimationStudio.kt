package com.android.CaveArt

import android.graphics.Bitmap
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.CaveArt.animations.AnimationFactory
import com.android.CaveArt.animations.AnimationStyle
import com.android.CaveArt.animations.AnimSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private enum class MotionTab(val label: String) {
    ENGINES("Engines"),
    TUNING("Dynamics"),
    LAYERS("Layers")
}

fun sanitizeAnimationTitle(raw: String): String {
    return raw
        .replace("Material You", "Dynamic", ignoreCase = true)
        .replace("Google", "System", ignoreCase = true)
        .replace("Pixel", "Adaptive", ignoreCase = true)
        .replace("OneUI", "Modern", ignoreCase = true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimationStudio(
    wallpaper: Wallpaper,
    viewModel: WallpaperViewModel,
    sharedElementModifier: Modifier = Modifier,
    onBack: () -> Unit,
    onApplyRequested: (Int) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val isDarkTheme = isSystemInDarkTheme()

    var monetPalette by remember(wallpaper.id) { mutableStateOf(MonetEngine.getDefaultPalette()) }

    LaunchedEffect(wallpaper.id) {
        withContext(Dispatchers.IO) {
            val palette = MonetEngine.getPalette(context, wallpaper)
            withContext(Dispatchers.Main) {
                monetPalette = palette
                if (isDefaultColor(viewModel.currentBackgroundColor)) {
                    viewModel.updateMagicConfig(viewModel.currentMagicShape, palette.first())
                }
            }
        }
    }

    var activeTab by remember { mutableStateOf(MotionTab.ENGINES) }
    var previewOriginal by remember { mutableStateOf<Bitmap?>(null) }
    var previewMask by remember { mutableStateOf<Bitmap?>(null) }
    var isPressedOnCanvas by remember { mutableStateOf(false) }

    LaunchedEffect(wallpaper.id) {
        val components = viewModel.getPreviewAnimationComponents(context, wallpaper)
        previewOriginal = components.first
        previewMask = viewModel.getMaskForClock(context, wallpaper)
    }

    val currentAnim = remember(viewModel.currentAnimationStyle) {
        AnimationFactory.getAnimation(viewModel.currentAnimationStyle).apply { onUnlock() }
    }

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

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (viewModel.isAmbientBlurEnabled && previewOriginal != null) {
            AsyncWallpaperImage(
                wallpaper = wallpaper,
                contentDescription = null,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize().blur(90.dp),
                contentScale = ContentScale.Crop,
                allowMagic = false
            )
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)))
        }

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 10.dp, bottom = 8.dp, start = 18.dp, end = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(9f / 19.5f)
                            .then(sharedElementModifier)
                            .shadow(
                                elevation = 28.dp,
                                shape = MaterialTheme.shapes.extraLarge,
                                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            ),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = Color.Black)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (previewOriginal != null) {
                                val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG) }
                                val maskXferPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN) } }
                                val clipPath = remember { android.graphics.Path() }
                                val screenShapeRect = remember { RectF() }

                                Canvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(currentAnim) {
                                            detectTapGestures(
                                                onPress = {
                                                    isPressedOnCanvas = true
                                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                                    currentAnim.onLock()
                                                    tryAwaitRelease()
                                                    isPressedOnCanvas = false
                                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                                    currentAnim.onUnlock()
                                                }
                                            )
                                        }
                                ) {
                                    frameTimeNanos.let {}
                                    val config = LiveWallpaperConfig(
                                        shapeName = viewModel.currentMagicShape.name,
                                        backgroundColor = viewModel.currentBackgroundColor,
                                        is3DPopEnabled = viewModel.is3DPopEnabled,
                                        scale = viewModel.magicScale,
                                        isCentered = viewModel.isCentered,
                                        animationStyle = viewModel.currentAnimationStyle.name,
                                        isMagicShapeEnabled = false,
                                        isAnimationEnabled = true,
                                        animParams = viewModel.currentAnimParams
                                    )
                                    val geo = ShapeEffectHelper.getUnifiedGeometry(
                                        previewOriginal!!.width, previewOriginal!!.height,
                                        size.width, size.height, previewMask, config
                                    )
                                    drawIntoCanvas { canvas ->
                                        currentAnim.draw(
                                            canvas.nativeCanvas, previewOriginal!!, previewMask, geo, config,
                                            paint, maskXferPaint, clipPath, screenShapeRect
                                        )
                                    }
                                }

                                androidx.compose.animation.AnimatedVisibility(
                                    visible = !isPressedOnCanvas,
                                    enter = fadeIn(),
                                    exit = fadeOut(),
                                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.45f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Rounded.TouchApp, null, Modifier.size(13.dp), tint = Color.White.copy(alpha = 0.9f))
                                            Text(
                                                "Hold to preview lock",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                                color = Color.White.copy(alpha = 0.9f)
                                            )
                                        }
                                    }
                                }
                            }

                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(14.dp)
                                    .size(46.dp)
                                    .shadow(8.dp, RoundedCornerShape(16.dp))
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        viewModel.toggle3DPop()
                                    },
                                shape = RoundedCornerShape(16.dp),
                                color = if (viewModel.is3DPopEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.88f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Layers,
                                        contentDescription = "Toggle 3D Pop Depth",
                                        tint = if (viewModel.is3DPopEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 4.dp)
                            .size(46.dp)
                            .shadow(8.dp, CircleShape)
                            .clip(CircleShape)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onBack()
                            },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(24.dp, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)),
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MotionTab.values().forEach { tab ->
                                val isSelected = activeTab == tab
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                            activeTab = tab
                                        }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                            fontSize = 15.sp
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .height(3.dp)
                                            .width(if (isSelected) 24.dp else 0.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    )
                                }
                            }
                        }

                        AnimatedContent(
                            targetState = activeTab,
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                            label = "motionTabContent"
                        ) { currentTab ->
                            when (currentTab) {
                                MotionTab.ENGINES -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Motion Engines", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }

                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            items(AnimationStyle.values()) { style ->
                                                val isSelected = viewModel.currentAnimationStyle == style
                                                val haloPadding by animateDpAsState(if (isSelected) 4.dp else 0.dp, spring(), label = "engineHalo")

                                                Box(
                                                    modifier = Modifier
                                                        .size(width = 86.dp, height = 64.dp)
                                                        .clip(RoundedCornerShape(20.dp))
                                                        .clickable {
                                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                                            viewModel.updateAnimationStyle(style)
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isSelected) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .border(2.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                                                        )
                                                    }

                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .padding(haloPadding)
                                                            .clip(RoundedCornerShape(16.dp))
                                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        LiveAnimationTile(style)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                MotionTab.TUNING -> {
                                    val settings = currentAnim.getCustomSettings()
                                    val sliderSettings = settings.filterIsInstance<AnimSetting.Slider>()
                                    val toggleSettings = settings.filterIsInstance<AnimSetting.Toggle>()

                                    var activeSettingId by remember(currentAnim) {
                                        mutableStateOf(sliderSettings.firstOrNull()?.id ?: "")
                                    }

                                    val activeSetting = sliderSettings.find { it.id == activeSettingId }
                                        ?: sliderSettings.firstOrNull()

                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        if (sliderSettings.isNotEmpty() && activeSetting != null) {
                                            val currValue = viewModel.currentAnimParams[activeSetting.id] ?: activeSetting.defaultValue

                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(24.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.9f)
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        LazyRow(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .padding(end = 8.dp),
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            items(sliderSettings) { setting ->
                                                                CompactTabChip(
                                                                    title = sanitizeAnimationTitle(setting.title),
                                                                    isSelected = setting.id == activeSetting.id,
                                                                    onClick = { activeSettingId = setting.id }
                                                                )
                                                            }
                                                        }

                                                        Surface(
                                                            shape = CircleShape,
                                                            color = MaterialTheme.colorScheme.primaryContainer
                                                        ) {
                                                            Text(
                                                                text = String.format("%.1f", currValue),
                                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black, fontSize = 12.sp),
                                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                                            )
                                                        }
                                                    }

                                                    ExpressiveCapsuleSlider(
                                                        value = currValue,
                                                        onValueChange = { viewModel.updateAnimParam(activeSetting.id, it) },
                                                        valueRange = activeSetting.minValue..activeSetting.maxValue,
                                                        icon = Icons.Rounded.Tune,
                                                        label = ""
                                                    )
                                                }
                                            }
                                        }

                                        if (toggleSettings.isNotEmpty()) {
                                            toggleSettings.forEach { toggle ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 4.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(sanitizeAnimationTitle(toggle.title), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                                    val isChecked = (viewModel.currentAnimParams[toggle.id] ?: if (toggle.defaultValue) 1f else 0f) > 0.5f
                                                    Switch(
                                                        checked = isChecked,
                                                        onCheckedChange = { viewModel.updateAnimParam(toggle.id, if (it) 1f else 0f) }
                                                    )
                                                }
                                            }
                                        }

                                        if (sliderSettings.isEmpty() && toggleSettings.isEmpty()) {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().height(60.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    "Engine is fully automated",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                MotionTab.LAYERS -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("3D Pop Depth", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                            Switch(
                                                checked = viewModel.is3DPopEnabled,
                                                onCheckedChange = { viewModel.toggle3DPop() }
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Force Center", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                            Switch(
                                                checked = viewModel.isCentered,
                                                onCheckedChange = { viewModel.toggleCentered() }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(2.dp))

                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                onApplyRequested(WallpaperDestinations.FLAG_BOTH)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Set Live Motion", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactTabChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                fontSize = 12.sp
            ),
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun LiveAnimationTile(style: AnimationStyle) {
    val infiniteTransition = rememberInfiniteTransition(label = "AnimTile")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "TileTime"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.tertiary

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f

        when (style) {
            AnimationStyle.MORPH -> {
                val cols = 8; val rows = 5
                val spacingX = size.width / cols
                val spacingY = size.height / rows

                for (x in 1 until cols) {
                    val path = Path()
                    for (y in 0..10) {
                        val py = (y / 10f) * size.height
                        val warpX = sin(py * 0.05f + time * 3f) * cos((x * spacingX) * 0.05f + time * 3f) * 12f
                        if (y == 0) path.moveTo(x * spacingX + warpX, py) else path.lineTo(x * spacingX + warpX, py)
                    }
                    drawPath(path, primaryColor.copy(alpha = 0.4f), style = Stroke(width = 2.dp.toPx()))
                }
                for (y in 1 until rows) {
                    val path = Path()
                    for (x in 0..10) {
                        val px = (x / 10f) * size.width
                        val warpY = sin((y * spacingY) * 0.05f + time * 3f) * cos(px * 0.05f + time * 3f) * 12f
                        if (x == 0) path.moveTo(px, y * spacingY + warpY) else path.lineTo(px, y * spacingY + warpY)
                    }
                    drawPath(path, primaryColor.copy(alpha = 0.4f), style = Stroke(width = 2.dp.toPx()))
                }
            }
            AnimationStyle.NANO_ASSEMBLY -> {
                val rotAngle = (time / (2.0 * Math.PI).toFloat()) * 360f
                val cardW = 28.dp.toPx()
                val cardH = 42.dp.toPx()

                val auraPulse = (sin(time * 2f) + 1f) / 2f
                drawCircle(
                    Brush.radialGradient(
                        listOf(primaryColor.copy(alpha = 0.4f * auraPulse), Color.Transparent),
                        center = Offset(cx, cy)
                    ),
                    radius = 45.dp.toPx()
                )

                rotate(degrees = rotAngle, pivot = Offset(cx, cy)) {
                    drawRoundRect(
                        color = primaryColor.copy(alpha = 0.5f),
                        topLeft = Offset(cx - cardW / 2, cy - cardH / 2),
                        size = Size(cardW, cardH),
                        cornerRadius = CornerRadius(6.dp.toPx())
                    )

                    val sweepPos = (time * 1.5f % (2.0 * Math.PI).toFloat()) / (2.0 * Math.PI).toFloat()
                    val sweepY = (sweepPos * cardH) - (cardH / 2)
                    drawLine(
                        color = secondaryColor.copy(alpha = 0.9f),
                        start = Offset(cx - cardW / 2 - 4f, cy + sweepY),
                        end = Offset(cx + cardW / 2 + 4f, cy + sweepY),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            AnimationStyle.ORGANIC_BLOB -> {
                val path = Path()
                val baseR = 20.dp.toPx()
                for (i in 0..60) {
                    val a = (i / 60f) * 2f * PI.toFloat()
                    val offset = sin(a * 4f + time * 2.2f) * (baseR * 0.1f) + cos(a * 3f - time * 1.5f) * (baseR * 0.08f)
                    val r = baseR + offset
                    val x = cx + r * cos(a)
                    val y = cy + r * sin(a)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, primaryColor.copy(alpha = 0.8f))
            }
            AnimationStyle.FLUID_MORPH -> {
                for (i in 3 downTo 1) {
                    val scale = 1f + (i * 0.2f)
                    val phase = i * 0.5f
                    val path = Path()
                    val baseR = 14.dp.toPx() * scale
                    for (j in 0..60) {
                        val a = (j / 60f) * 2f * PI.toFloat()
                        val offset = sin(a * 3f + (time - phase)) * (baseR * 0.15f) + cos(a * 2f - (time - phase)) * (baseR * 0.1f)
                        val r = baseR + offset
                        val x = cx + r * cos(a)
                        val y = cy + r * sin(a)
                        if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    drawPath(path, primaryColor.copy(alpha = 0.25f))
                }
            }
            AnimationStyle.EXPRESSIVE_HORIZON -> {
                val pulse = (sin(time * 1.5f) + 1f) / 2f
                val bloomRadius = size.width * (0.7f + 0.3f * pulse)
                val bloomCenter = Offset(cx, cy * 1.1f)

                drawCircle(
                    brush = Brush.radialGradient(listOf(primaryColor.copy(alpha = 0.7f), Color.Transparent), center = bloomCenter),
                    radius = bloomRadius,
                    center = bloomCenter
                )

                val waveOffset = (time * 40f) % (size.width * 2)
                for (i in -2..2) {
                    val xBase = (i * 40f) + waveOffset - size.width
                    drawLine(
                        color = primaryColor.copy(alpha = 0.15f),
                        start = Offset(xBase, size.height),
                        end = Offset(xBase + size.width, 0f),
                        strokeWidth = 10.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}
