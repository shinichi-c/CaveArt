package com.android.CaveArt

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
    var isAssetsLoading by remember { mutableStateOf(true) }
    
    var isHoldingPreview by remember { mutableStateOf(false) }
    val simulatedUnlockProgress = remember { Animatable(0f) }

    var popEligibility by remember {
        mutableStateOf(PopEligibility(isEligible = false, isAbstract = false, reason = "Analyzing...", subjectBounds = null, coveragePercent = 0f, borderDistances = null))
    }
    var safeScaleRange by remember {
        mutableStateOf(SafeScaleRange(minScale = 0.8f, maxScale = 1.35f, defaultScale = 1.0f))
    }

    val cachedPalette = remember(wallpaper.id, isDarkTheme) {
        MonetEngine.getCachedPalette(wallpaper.id, isDarkTheme)
    }
    var monetPalette by remember(wallpaper.id, isDarkTheme) {
        mutableStateOf(cachedPalette ?: MonetEngine.getDefaultPalette())
    }

    LaunchedEffect(wallpaper.id) {
        isAssetsLoading = true
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
                isAssetsLoading = false
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
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            isHoldingPreview = true
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            scope.launch {
                                                simulatedUnlockProgress.animateTo(
                                                    targetValue = 1f,
                                                    animationSpec = spring(
                                                        dampingRatio = 0.76f,
                                                        stiffness = 320f
                                                    )
                                                )
                                            }
                                            tryAwaitRelease()
                                            isHoldingPreview = false
                                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                            scope.launch {
                                                simulatedUnlockProgress.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = spring(
                                                        dampingRatio = 0.76f,
                                                        stiffness = 320f
                                                    )
                                                )
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (previewOriginal != null && !isAssetsLoading) {
                                val paint = remember {
                                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                                }
                                val maskXferPaint = remember {
                                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                                    }
                                }
                                val screenShapeRect = remember { RectF() }
                                val liveShapePath = remember { Path() }
                                val bodyMatrix = remember { Matrix() }

                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val progress = morphProgress.value
                                    val unlockT = simulatedUnlockProgress.value
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

                                    geo.setupTransitionMatrices(
                                        imgW = previewOriginal!!.width,
                                        imgH = previewOriginal!!.height,
                                        screenW = size.width,
                                        screenH = size.height,
                                        config = config,
                                        transitionProgress = unlockT,
                                        outBodyMatrix = bodyMatrix,
                                        outScreenShapeRect = screenShapeRect
                                    )

                                    val bgAlpha = ((1f - unlockT) * 255).toInt().coerceIn(0, 255)
                                    val dynamicBg = (bgAlpha shl 24) or (viewModel.currentBackgroundColor and 0x00FFFFFF)
                                    drawIntoCanvas { it.nativeCanvas.drawColor(dynamicBg) }

                                    PixelShapeMorpher.buildMorphedPath(
                                        fromShape = fromShape,
                                        toShape = toShape,
                                        progress = progress,
                                        bounds = screenShapeRect,
                                        targetPath = liveShapePath
                                    )

                                    drawIntoCanvas { canvas ->
                                        val nc = canvas.nativeCanvas
                                        nc.save()
                                        nc.clipPath(liveShapePath)
                                        nc.drawBitmap(previewOriginal!!, bodyMatrix, paint)
                                        nc.restore()

                                        if (config.is3DPopEnabled && geo.is3DPopEligible && previewCutout != null && unlockT < 0.6f) {
                                            val popAlpha = ((1f - (unlockT / 0.6f)) * 255).toInt().coerceIn(0, 255)
                                            paint.alpha = popAlpha
                                            val layerId = nc.saveLayer(0f, 0f, size.width, size.height, null)
                                            nc.save()
                                            val breakoutCutoffY = screenShapeRect.top + (screenShapeRect.height() * 0.30f)
                                            nc.clipRect(0f, 0f, size.width, breakoutCutoffY)
                                            nc.drawBitmap(previewCutout!!, bodyMatrix, paint)
                                            nc.drawBitmap(previewOriginal!!, bodyMatrix, maskXferPaint)
                                            nc.restore()
                                            nc.restoreToCount(layerId)
                                            paint.alpha = 255
                                        }
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
                                M3ExpressiveMorphLoadingIndicator(sizeDp = 52.dp)
                            }
                            
                            PreviewGuidancePill(
                                visible = !isHoldingPreview,
                                text = "Hold to preview unlock",
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 16.dp)
                            )
                            
                            AbstractPatternBadge(
                                visible = popEligibility.isAbstract && !isAssetsLoading,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 48.dp)
                            )
                            
                            val canShow3DPop = popEligibility.isEligible && !popEligibility.isAbstract

                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 16.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                shadowElevation = 10.dp,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ExpressiveCenterModePill(
                                        icon = if (viewModel.isCentered) Icons.Rounded.CenterFocusStrong else Icons.Rounded.CenterFocusWeak,
                                        contentDescription = "Toggle Center Mode",
                                        isActive = viewModel.isCentered,
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                            viewModel.toggleCentered()
                                        }
                                    )

                                    if (canShow3DPop) {
                                        VerticalDivider(
                                            modifier = Modifier
                                                .height(20.dp)
                                                .width(1.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                        )

                                        ExpressiveCenterModePill(
                                            icon = Icons.Rounded.Layers,
                                            contentDescription = "Toggle 3D Pop Depth",
                                            isActive = viewModel.is3DPopEnabled,
                                            isEnabled = true,
                                            onClick = {
                                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                                viewModel.toggle3DPop()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onBack()
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
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
                        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
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

                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            M3DocLinkPill(
                                                label = "M3 Shapes Principles",
                                                url = "https://m3.material.io/styles/shape/overview-principles"
                                            )
                                        }
                                    }
                                }

                                MagicTab.PALETTE -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ExpressiveColorHaloSelector(
                                            colors = monetPalette,
                                            selectedColor = viewModel.currentBackgroundColor,
                                            onColorSelected = { colorInt ->
                                                viewModel.saveColorForWallpaper(wallpaper.id, colorInt)
                                                viewModel.updateMagicConfig(toShape, colorInt)
                                            }
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            M3DocLinkPill(
                                                label = "M3 Color System",
                                                url = "https://m3.material.io/styles/color/system/overview"
                                            )
                                        }
                                    }
                                }

                                MagicTab.APPEARANCE -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
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
private fun PreviewGuidancePill(
    visible: Boolean,
    text: String,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
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
                    text = text,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun AbstractPatternBadge(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(250)) + slideInVertically { -20 },
        exit = fadeOut(tween(200)) + slideOutVertically { -20 },
        modifier = modifier
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("🫟", fontSize = 13.sp)
                Text(
                    text = "Abstract Pattern",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        letterSpacing = 0.2.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ExpressiveCenterModePill(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    isEnabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "iconPillSquish"
    )

    Box(
        modifier = Modifier
            .size(38.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(
                if (isActive) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = isEnabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = when {
                !isEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                isActive -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun M3DocLinkPill(
    label: String,
    url: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        modifier = modifier
            .clip(CircleShape)
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp)
            )
        }
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
