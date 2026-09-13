package com.android.CaveArt

import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class MagicTab(val label: String) {
    SHAPES("Shapes"),
    PALETTE("Palette"),
    APPEARANCE("Appearance")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagicShapeStudio(
    wallpaper: Wallpaper,
    viewModel: WallpaperViewModel,
    sharedElementModifier: Modifier = Modifier,
    onBack: () -> Unit,
    onApplyRequested: (Int) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val isDarkTheme = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf(MagicTab.SHAPES) }
    var previewOriginal by remember { mutableStateOf<Bitmap?>(null) }
    var previewCutout by remember { mutableStateOf<Bitmap?>(null) }

    var popEligibility by remember {
        mutableStateOf(PopEligibility(isEligible = false, reason = "Analyzing...", subjectBounds = null, coveragePercent = 0f, borderDistances = null))
    }
    var safeScaleRange by remember {
        mutableStateOf(SafeScaleRange(minScale = 0.8f, maxScale = 1.3f, defaultScale = 1.0f))
    }

    val cachedPalette = remember(wallpaper.id, isDarkTheme) {
        MonetEngine.getCachedPalette(wallpaper.id, isDarkTheme)
    }
    var monetPalette by remember(wallpaper.id, isDarkTheme) {
        mutableStateOf(cachedPalette ?: MonetEngine.getDefaultPalette())
    }

    LaunchedEffect(wallpaper.id) {
        val initialColor = viewModel.getColorForWallpaper(wallpaper.id)
            ?: cachedPalette?.firstOrNull()
        if (initialColor != null) {
            viewModel.updateMagicConfig(viewModel.currentMagicShape, initialColor)
        }
        val comp = viewModel.getHighQualityComponents(context, wallpaper)
        previewOriginal = comp.first
        previewCutout = comp.second

        withContext(Dispatchers.Default) {
            val analysis = SubjectPopAnalyzer.analyzeEligibility(comp.second)
            val range = SubjectPopAnalyzer.calculateSafeScaleRange(
                subjectBounds = analysis.subjectBounds,
                imgW = comp.first?.width ?: 1080,
                imgH = comp.first?.height ?: 1920
            )

            withContext(Dispatchers.Main) {
                popEligibility = analysis
                safeScaleRange = range
                val safeScale = viewModel.magicScale.coerceIn(range.minScale, range.maxScale)
                if (safeScale != viewModel.magicScale) {
                    viewModel.updateMagicScale(safeScale)
                }
                if (!analysis.isEligible && viewModel.is3DPopEnabled) {
                    viewModel.toggle3DPop()
                }
            }
        }
    }

    LaunchedEffect(wallpaper.id, isDarkTheme) {
        withContext(Dispatchers.IO) {
            val palette = MonetEngine.getThemePalette(context, wallpaper, isDarkTheme).allColors
            withContext(Dispatchers.Main) {
                monetPalette = palette
                val chosenColor = viewModel.getColorForWallpaper(wallpaper.id) ?: palette.first()
                viewModel.updateMagicConfig(viewModel.currentMagicShape, chosenColor)
            }
        }
    }

    var fromShape by remember { mutableStateOf<MagicShape>(viewModel.currentMagicShape) }
    var toShape by remember { mutableStateOf<MagicShape>(viewModel.currentMagicShape) }
    val morphProgress = remember { Animatable(1f) }

    fun triggerShapeMorph(newShape: MagicShape) {
        if (newShape == toShape) return
        fromShape = toShape
        toShape = newShape
        viewModel.updateMagicConfig(newShape, viewModel.currentBackgroundColor)
        scope.launch {
            morphProgress.snapTo(0f)
            morphProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
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
                                val paint = remember {
                                    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
                                }
                                val maskXferPaint = remember {
                                    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                                    }
                                }
                                val screenShapeRect = remember { RectF() }
                                val liveShapePath = remember { android.graphics.Path() }
                                val bodyMatrix = remember { android.graphics.Matrix() }

                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val progress = morphProgress.value
                                    val config = LiveWallpaperConfig(
                                        shapeName = toShape.name,
                                        backgroundColor = viewModel.currentBackgroundColor,
                                        is3DPopEnabled = viewModel.is3DPopEnabled,
                                        scale = viewModel.magicScale,
                                        isCentered = viewModel.isCentered,
                                        isMagicShapeEnabled = true
                                    )

                                    val geo = ShapeEffectHelper.getUnifiedGeometry(
                                        previewOriginal!!.width, previewOriginal!!.height,
                                        size.width, size.height, previewCutout, config
                                    )

                                    geo.setupMatrices(
                                        imgW = previewOriginal!!.width,
                                        imgH = previewOriginal!!.height,
                                        screenW = size.width,
                                        screenH = size.height,
                                        config = config,
                                        outBodyMatrix = bodyMatrix,
                                        outScreenShapeRect = screenShapeRect
                                    )

                                    PixelShapeMorpher.buildMorphedPath(
                                        fromShape = fromShape,
                                        toShape = toShape,
                                        progress = progress,
                                        bounds = screenShapeRect,
                                        targetPath = liveShapePath
                                    )

                                    drawIntoCanvas { canvas ->
                                        ShapeEffectHelper.drawLivePixelShape(
                                            canvas = canvas.nativeCanvas,
                                            original = previewOriginal!!,
                                            cutout = previewCutout,
                                            geo = geo,
                                            config = config,
                                            shapePath = liveShapePath,
                                            screenShapeRect = screenShapeRect,
                                            bodyMatrix = bodyMatrix,
                                            bitmapPaint = paint,
                                            maskXferPaint = maskXferPaint
                                        )
                                    }
                                }
                            } else {
                                AsyncWallpaperImage(
                                    wallpaper = wallpaper,
                                    contentDescription = null,
                                    viewModel = viewModel,
                                    modifier = Modifier.fillMaxSize(),
                                    allowMagic = false
                                )
                            }

                            val isSupported = popEligibility.isEligible
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(14.dp)
                                    .size(48.dp)
                                    .shadow(8.dp, RoundedCornerShape(16.dp))
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        if (isSupported) {
                                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                            viewModel.toggle3DPop()
                                        } else {
                                            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                                            Toast.makeText(context, popEligibility.reason, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                shape = RoundedCornerShape(16.dp),
                                color = when {
                                    !isSupported -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    viewModel.is3DPopEnabled -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.88f)
                                }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isSupported) Icons.Rounded.Layers else Icons.Rounded.LayersClear,
                                        contentDescription = "Toggle 3D Pop Depth",
                                        tint = when {
                                            !isSupported -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                            viewModel.is3DPopEnabled -> MaterialTheme.colorScheme.onPrimary
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier.size(24.dp)
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
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
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
                            MagicTab.values().forEach { tab ->
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
                            transitionSpec = {
                                fadeIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) togetherWith
                                fadeOut(tween(120))
                            },
                            label = "magicTabContent"
                        ) { currentTab ->
                            when (currentTab) {
                                MagicTab.SHAPES -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        SectionHeaderWithChevron("Official Material 3 Shapes")
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            items(MagicShape.values()) { shape ->
                                                val isSelected = toShape == shape
                                                ShapePreviewCard(
                                                    shape = shape,
                                                    isSelected = isSelected,
                                                    onClick = {
                                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                                        triggerShapeMorph(shape)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                MagicTab.PALETTE -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        SectionHeaderWithChevron("Wallpaper Color Harmonization")
                                        ExpressiveColorHaloSelector(
                                            colors = monetPalette,
                                            selectedColor = viewModel.currentBackgroundColor,
                                            onColorSelected = { colorInt ->
                                                viewModel.saveColorForWallpaper(wallpaper.id, colorInt)
                                                viewModel.updateMagicConfig(toShape, colorInt)
                                            }
                                        )
                                    }
                                }

                                MagicTab.APPEARANCE -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("3D Depth Pop", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                                Text(
                                                    text = popEligibility.reason,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (popEligibility.isEligible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                                )
                                            }
                                            Switch(
                                                checked = viewModel.is3DPopEnabled && popEligibility.isEligible,
                                                enabled = popEligibility.isEligible,
                                                onCheckedChange = { viewModel.toggle3DPop() }
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Center Subject", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                            Switch(
                                                checked = viewModel.isCentered,
                                                onCheckedChange = { viewModel.toggleCentered() }
                                            )
                                        }
                                        
                                        ExpressiveCapsuleSlider(
                                            value = viewModel.magicScale.coerceIn(safeScaleRange.minScale, safeScaleRange.maxScale),
                                            onValueChange = { viewModel.updateMagicScale(it) },
                                            valueRange = safeScaleRange.minScale..safeScaleRange.maxScale,
                                            icon = Icons.Rounded.ZoomIn,
                                            label = "Cutout Scale"
                                        )
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
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Set Magic Shape", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderWithChevron(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ShapePreviewCard(
    shape: MagicShape,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val innerScale by animateFloatAsState(
        targetValue = if (isSelected) 0.85f else 0.98f,
        animationSpec = spring(stiffness = 600f, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "shapeHaloScale"
    )

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = innerScale
                    scaleY = innerScale
                }
                .clip(CircleShape)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                ),
            contentAlignment = Alignment.Center
        ) {
            ShapeIcon(
                shape = shape,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(38.dp)
            )
        }
    }
}
