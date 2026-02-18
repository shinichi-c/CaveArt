package com.android.CaveArt

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.palette.graphics.Palette
import com.android.CaveArt.animations.AnimationStyle
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sin
import kotlin.math.pow
import coil.compose.rememberAsyncImagePainter
import kotlin.random.Random

private data class Particle(
    val initialX: Float,
    val initialY: Float,
    val radius: Float,
    val speed: Float,
    val swaySpeed: Float,
    val initialAlpha: Float
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WallpaperDetailScreen(
    wallpapers: List<Wallpaper>,
    initialPageIndex: Int,
    onClose: () -> Unit,
    onApplyClick: (Wallpaper) -> Unit,
    isDebugMaskEnabled: Boolean,
    viewModel: WallpaperViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val window = (context as? Activity)?.window
    val systemUiController = rememberSystemUiController()
    
    SideEffect {
        if (window != null) WindowCompat.setDecorFitsSystemWindows(window, false)
        systemUiController.setStatusBarColor(Color.Transparent, darkIcons = false)
        systemUiController.setNavigationBarColor(Color.Transparent, darkIcons = false)
    }

    val detailPagerState = rememberPagerState(initialPage = initialPageIndex, pageCount = { wallpapers.size })
    val currentWallpaper = wallpapers.getOrNull(detailPagerState.currentPage) ?: return run { onClose() }
    val isMagicMode = viewModel.isMagicShapeEnabled

    BackHandler(enabled = isMagicMode) { viewModel.setMagicShapeEnabled(false) }

    var areControlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(isMagicMode, detailPagerState.currentPage) { areControlsVisible = true }
    
    val backgroundColor = MaterialTheme.colorScheme.background

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor).pointerInput(isMagicMode) {
        detectTapGestures(onTap = { if (isMagicMode) areControlsVisible = !areControlsVisible })
    }) {
        HorizontalPager(state = detailPagerState, modifier = Modifier.fillMaxSize()) { pageIndex ->
            val wallpaper = wallpapers[pageIndex]
            if (viewModel.isMagicShapeEnabled) {
                MagicEffectImage(wallpaper = wallpaper, viewModel = viewModel, modifier = Modifier.fillMaxSize())
            } else if (isDebugMaskEnabled) {
                DebugMaskImage(wallpaper = wallpaper, contentDescription = "Debug", viewModel = viewModel, modifier = Modifier.fillMaxSize())
            } else {
                if (wallpaper.uri != null) {
                    Image(painter = rememberAsyncImagePainter(wallpaper.uri), contentDescription = "Gallery", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Image(painter = painterResource(id = wallpaper.resourceId), contentDescription = "Original", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        }
        
        AnimatedVisibility(
            visible = !isMagicMode || areControlsVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                 IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
                 Column(horizontalAlignment = Alignment.End) {
                    Text(currentWallpaper.title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(currentWallpaper.tag.uppercase(), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        
        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            AnimatedVisibility(visible = !isMagicMode, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                Row(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(onClick = { viewModel.setMagicShapeEnabled(true) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer), shape = CircleShape, modifier = Modifier.size(56.dp), contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.Default.AutoAwesome, "Magic")
                    }
                    Button(onClick = { onApplyClick(currentWallpaper) }, modifier = Modifier.height(56.dp).widthIn(min = 140.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer), shape = RoundedCornerShape(28.dp)) {
                        Icon(Icons.Default.Image, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("APPLY", fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            AnimatedVisibility(visible = isMagicMode && areControlsVisible, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                MagicControlsSheet(
                    wallpaper = currentWallpaper,
                    viewModel = viewModel,
                    onCloseMagic = { viewModel.setMagicShapeEnabled(false) },
                    onApplyStatic = { onApplyClick(currentWallpaper) },
                    onApplyLive = { scope.launch { setLiveWallpaper(context, currentWallpaper, viewModel) } }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagicControlsSheet(
    wallpaper: Wallpaper,
    viewModel: WallpaperViewModel,
    onCloseMagic: () -> Unit,
    onApplyStatic: () -> Unit,
    onApplyLive: () -> Unit
) {
    val context = LocalContext.current
    var extractedColors by remember { mutableStateOf(listOf<Int>()) }
    var sliderPosition by remember { mutableFloatStateOf(viewModel.magicScale) }

    LaunchedEffect(wallpaper) {
        withContext(Dispatchers.IO) {
            val bitmap = if(wallpaper.uri != null) BitmapHelper.decodeSampledBitmapFromUri(context, wallpaper.uri, 500) else BitmapFactory.decodeResource(context.resources, wallpaper.resourceId)
            if (bitmap != null) {
                val palette = Palette.from(bitmap).generate()
                val targetSwatches = listOfNotNull(palette.vibrantSwatch, palette.darkVibrantSwatch, palette.lightVibrantSwatch, palette.mutedSwatch, palette.darkMutedSwatch, palette.lightMutedSwatch, palette.dominantSwatch)
                val extraSwatches = palette.swatches.sortedByDescending { it.population }.take(8)
                val finalColors = (targetSwatches + extraSwatches).map { it.rgb }.distinct() + listOf(android.graphics.Color.BLACK, android.graphics.Color.WHITE)
                
                withContext(Dispatchers.Main) {
                    extractedColors = finalColors.distinct()
                    if (finalColors.isNotEmpty()) viewModel.updateMagicConfig(viewModel.currentMagicShape, finalColors.first())
                }
                bitmap.recycle()
            }
        }
    }
    
    Card(
        modifier = Modifier.navigationBarsPadding().padding(16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCloseMagic) { Text("Cancel") }
                Row {
                    FilledTonalButton(onClick = onApplyLive, colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Live")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onApplyStatic) {
                        Icon(Icons.Default.Check, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Static")
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            
            Text("Animation Style", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                items(AnimationStyle.values()) { style ->
                    FilterChip(
                        selected = viewModel.currentAnimationStyle == style,
                        onClick = { viewModel.updateAnimationStyle(style) },
                        label = { Text(style.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer),
                        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = viewModel.currentAnimationStyle == style, borderColor = Color.Transparent)
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = viewModel.is3DPopEnabled, onClick = { viewModel.toggle3DPop() }, label = { Text("3D Pop") }, leadingIcon = { Icon(Icons.Default.Layers, null, Modifier.size(18.dp)) }, border = FilterChipDefaults.filterChipBorder(enabled = true, selected = viewModel.is3DPopEnabled, borderColor = Color.Transparent))
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = viewModel.isCentered, onClick = { viewModel.toggleCentered() }, label = { Text("Center") }, leadingIcon = { Icon(Icons.Default.FilterCenterFocus, null, Modifier.size(18.dp)) }, border = FilterChipDefaults.filterChipBorder(enabled = true, selected = viewModel.isCentered, borderColor = Color.Transparent))
            }
            
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Shape Size", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (sliderPosition < 1.0f) "Tight" else "Wide", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Slider(value = sliderPosition, onValueChange = { sliderPosition = it }, onValueChangeFinished = { viewModel.updateMagicScale(sliderPosition) }, valueRange = 0.5f..1.5f, steps = 5)
            }
            
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(MagicShape.values()) { shape ->
                    val isSelected = viewModel.currentMagicShape == shape
                    Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant).clickable { viewModel.updateMagicConfig(shape, viewModel.currentBackgroundColor) }, contentAlignment = Alignment.Center) {
                        ShapeIcon(shape = shape, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(extractedColors) { colorInt ->
                    val isSelected = viewModel.currentBackgroundColor == colorInt
                    Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(colorInt)).clickable { viewModel.updateMagicConfig(viewModel.currentMagicShape, colorInt) }.border(3.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun ShapeIcon(shape: MagicShape, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val rect = RectF(0f, 0f, size.width, size.height)
        drawPath(path = ShapePathProvider.getPathForShape(shape, rect).asComposePath(), color = color)
    }
}

@Composable
fun MagicEffectImage(
    wallpaper: Wallpaper,
    viewModel: WallpaperViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var activeWallpaperId by remember { mutableStateOf(wallpaper.id) }

    if (activeWallpaperId != wallpaper.id) {
        currentBitmap = null
        activeWallpaperId = wallpaper.id
    }

    LaunchedEffect(wallpaper, viewModel.currentMagicShape, viewModel.currentBackgroundColor, viewModel.is3DPopEnabled, viewModel.magicScale, viewModel.isCentered) {
        val newBitmap = viewModel.getOrCreateProcessedBitmap(context, wallpaper)
        if (newBitmap != null) currentBitmap = newBitmap
    }
    
    val isDarkTheme = isSystemInDarkTheme()
    val backgroundColor = if (isDarkTheme) Color.Black else MaterialTheme.colorScheme.surface
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    val particleColor = if (isDarkTheme) Color.White else Color.Black
    val iconColor = if (isDarkTheme) Color.White else Color.Black

    Box(modifier = modifier.background(backgroundColor), contentAlignment = Alignment.Center) {
        if (currentBitmap == null) {
            if (wallpaper.uri != null) {
                Image(painter = rememberAsyncImagePainter(wallpaper.uri), contentDescription = null, modifier = Modifier.fillMaxSize().blur(25.dp), contentScale = ContentScale.Crop)
            } else {
                Image(painter = painterResource(id = wallpaper.resourceId), contentDescription = null, modifier = Modifier.fillMaxSize().blur(25.dp), contentScale = ContentScale.Crop)
            }
            Spacer(modifier = Modifier.fillMaxSize().background(overlayColor))
            ParticleLoadingOverlay(color = particleColor)
            val infiniteTransition = rememberInfiniteTransition(label = "IconPulse")
            val scale by infiniteTransition.animateFloat(initialValue = 1f, targetValue = 1.15f, animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "Scale")
            val glowAlpha by infiniteTransition.animateFloat(initialValue = 0.3f, targetValue = 0.7f, animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "Glow")
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(100.dp).scale(scale)) { drawCircle(brush = Brush.radialGradient(colors = listOf(particleColor.copy(alpha = glowAlpha), Color.Transparent), center = center, radius = size.width / 2)) }
                Icon(Icons.Default.AutoAwesome, "Processing", tint = iconColor, modifier = Modifier.size(48.dp).scale(scale))
            }
        }
        if (currentBitmap != null) {
            AnimatedContent(
                targetState = currentBitmap, 
                label = "MagicDepthAnim", 
                transitionSpec = { 
                    (fadeIn(animationSpec = tween(500)) + scaleIn(initialScale = 0.95f, animationSpec = tween(500, easing = LinearOutSlowInEasing)))
                    .togetherWith(fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 1.15f, animationSpec = tween(400, easing = FastOutSlowInEasing))) 
                }
            ) { bitmap ->
                if (bitmap != null) Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
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

    Canvas(modifier = Modifier.fillMaxSize()) {
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