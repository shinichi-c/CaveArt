package com.android.CaveArt

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
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
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sin
import kotlin.math.pow
import coil.compose.rememberAsyncImagePainter

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
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        systemUiController.setStatusBarColor(Color.Transparent, darkIcons = false)
        systemUiController.setNavigationBarColor(Color.Transparent, darkIcons = false)
    }

    val detailPagerState = rememberPagerState(initialPage = initialPageIndex, pageCount = { wallpapers.size })
    val currentWallpaper = wallpapers.getOrNull(detailPagerState.currentPage) ?: return run { onClose() }
    
    val isMagicMode = viewModel.isMagicShapeEnabled

    BackHandler(enabled = isMagicMode) {
        viewModel.setMagicShapeEnabled(false)
    }

    var areControlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(isMagicMode, detailPagerState.currentPage) {
        areControlsVisible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(isMagicMode) {
                detectTapGestures(
                    onTap = {
                        if (isMagicMode) {
                            areControlsVisible = !areControlsVisible
                        }
                    }
                )
            }
    ) {
        
        HorizontalPager(state = detailPagerState, modifier = Modifier.fillMaxSize()) { pageIndex ->
            val wallpaper = wallpapers[pageIndex]
            
            if (viewModel.isMagicShapeEnabled) {
                MagicEffectImage(
                    wallpaper = wallpaper,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (isDebugMaskEnabled) {
                DebugMaskImage(
                    wallpaper = wallpaper,
                    contentDescription = "Debug",
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                if (wallpaper.uri != null) {
                    Image(
                        painter = rememberAsyncImagePainter(wallpaper.uri),
                        contentDescription = "Gallery Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(id = wallpaper.resourceId),
                        contentDescription = "Original",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
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
                 IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = currentWallpaper.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                     Text(
                        text = currentWallpaper.tag.uppercase(),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        
        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
        	
            AnimatedVisibility(
                visible = !isMagicMode,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { viewModel.setMagicShapeEnabled(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, "Magic Effects")
                    }

                    Button(
                        onClick = { onApplyClick(currentWallpaper) },
                        modifier = Modifier.height(56.dp).widthIn(min = 140.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Icon(Icons.Default.Image, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("APPLY", fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            AnimatedVisibility(
                visible = isMagicMode && areControlsVisible,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                MagicControlsSheet(
                    wallpaper = currentWallpaper,
                    viewModel = viewModel,
                    onCloseMagic = { viewModel.setMagicShapeEnabled(false) },
                    onApplyStatic = { onApplyClick(currentWallpaper) },
                    onApplyLive = {
                        scope.launch {
                            setLiveWallpaper(context, currentWallpaper, viewModel)
                        }
                    }
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
            try {
                val bitmap = if(wallpaper.uri != null) {
                    BitmapHelper.decodeSampledBitmapFromUri(context, wallpaper.uri, 500)
                } else {
                    BitmapFactory.decodeResource(context.resources, wallpaper.resourceId)
                }
                
                if (bitmap != null) {
                    val palette = Palette.from(bitmap).generate()
                    val swatches = listOfNotNull(
                        palette.vibrantSwatch?.rgb, palette.darkVibrantSwatch?.rgb,
                        palette.dominantSwatch?.rgb, palette.mutedSwatch?.rgb,
                        palette.lightVibrantSwatch?.rgb
                    ).distinct()
                    val finalColors = if (swatches.isEmpty()) {
                        listOf(0xFF4CAF50.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt())
                    } else {
                        swatches + listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
                    }
                    withContext(Dispatchers.Main) {
                        extractedColors = finalColors
                        if (finalColors.isNotEmpty() && viewModel.currentBackgroundColor == 0xFF4CAF50.toInt()) {
                             viewModel.updateMagicConfig(viewModel.currentMagicShape, finalColors.first())
                        }
                    }
                    bitmap.recycle()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
    
    Card(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(16.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCloseMagic) { Text("Cancel") }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    
                    FilledTonalButton(
                        onClick = onApplyLive,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Live")
                    }
                    
                    Spacer(Modifier.width(8.dp))
                    
                    Button(onClick = onApplyStatic) {
                        Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Static")
                    }
                }
            }

            Divider(Modifier.padding(vertical = 12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterChip(
                    selected = viewModel.is3DPopEnabled,
                    onClick = { viewModel.toggle3DPop() },
                    label = { Text("3D Pop (Static Only)") },
                    leadingIcon = { 
                        Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(18.dp)) 
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    border = FilterChipDefaults.filterChipBorder(enabled = true, selected = viewModel.is3DPopEnabled, borderColor = Color.Transparent)
                )
                Spacer(modifier = Modifier.weight(1f))
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Shape Size", 
                        style = MaterialTheme.typography.labelMedium, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (sliderPosition < 1.0f) "Tight (More Pop)" else "Wide (Less Pop)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Slider(
                    value = sliderPosition,
                    onValueChange = { sliderPosition = it },
                    onValueChangeFinished = {
                        viewModel.updateMagicScale(sliderPosition)
                    },
                    valueRange = 0.5f..1.5f,
                    steps = 5
                )
            }
            
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(MagicShape.values()) { shape ->
                    val isSelected = viewModel.currentMagicShape == shape
                    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    val iconColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(bgColor)
                            .clickable { viewModel.updateMagicConfig(shape, viewModel.currentBackgroundColor) },
                        contentAlignment = Alignment.Center
                    ) {
                        ShapeIcon(shape = shape, color = iconColor, modifier = Modifier.size(28.dp))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(extractedColors) { colorInt ->
                    val isSelected = viewModel.currentBackgroundColor == colorInt
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(colorInt))
                            .clickable { viewModel.updateMagicConfig(viewModel.currentMagicShape, colorInt) }
                            .border(
                                width = 3.dp, 
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, 
                                shape = CircleShape
                            )
                    )
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
    var processedBitmap by remember(
        wallpaper, 
        viewModel.currentMagicShape, 
        viewModel.currentBackgroundColor, 
        viewModel.is3DPopEnabled,
        viewModel.magicScale
    ) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }

    LaunchedEffect(
        wallpaper, 
        viewModel.currentMagicShape, 
        viewModel.currentBackgroundColor, 
        viewModel.is3DPopEnabled,
        viewModel.magicScale
    ) {
        processedBitmap = viewModel.getOrCreateProcessedBitmap(context, wallpaper)
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val isProcessing = processedBitmap == null
        
        if (wallpaper.uri != null) {
            Image(
                painter = rememberAsyncImagePainter(wallpaper.uri),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isProcessing && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.blur(20.dp)
                        } else {
                            Modifier
                        }
                    ),
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                painter = painterResource(id = wallpaper.resourceId),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isProcessing && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.blur(20.dp)
                        } else {
                            Modifier
                        }
                    ),
                contentScale = ContentScale.Crop
            )
        }

        if (!isProcessing) {
            Image(
                bitmap = processedBitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop 
            )
        } else {
            
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
            )
            
            ParticleLoadingOverlay()
            
            val infiniteTransition = rememberInfiniteTransition(label = "IconPulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "Scale"
            )
            val glowAlpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 0.7f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "Glow"
            )

            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(100.dp).scale(scale)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = glowAlpha), Color.Transparent),
                            center = center,
                            radius = size.width / 2
                        )
                    )
                }
                
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Processing",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(48.dp).scale(scale)
                )
            }
        }
    }
}

@Composable
fun ParticleLoadingOverlay() {
    val density = LocalDensity.current
    
    val particles = remember {
        List(350) {
            Particle(
                initialX = Math.random().toFloat(),
                initialY = Math.random().toFloat(),
                radius = (Math.random() * 3 + 1).toFloat(), 
                speed = (Math.random() * 0.05 + 0.01).toFloat(), 
                swaySpeed = (Math.random() * 2 + 1).toFloat(), 
                initialAlpha = (Math.random() * 0.7 + 0.1).toFloat() 
            )
        }
    }
    
    var time by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val startTime = withFrameNanos { it }
        while (true) {
            withFrameNanos { frameTime ->
                time = (frameTime - startTime) / 1_000_000_000f 
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        particles.forEachIndexed { index, p ->
            var yProgress = (p.initialY - (p.speed * time)) % 1f
            if (yProgress < 0) yProgress += 1f
            val currentY = yProgress * height
            
            val swayOffset = sin(time * p.swaySpeed + index) * 15.dp.toPx()
            val currentX = (p.initialX * width) + swayOffset
            
            val blinkSpeed = p.swaySpeed * 3f
            val rawBlink = sin(time * blinkSpeed + index)
            val blinkFactor = ((rawBlink + 1) / 2f).pow(2)
            
            val currentAlpha = (p.initialAlpha * blinkFactor).coerceIn(0f, 1f)

            drawCircle(
                color = Color.White,
                radius = p.radius * density.density,
                center = Offset(currentX, currentY),
                alpha = currentAlpha
            )
        }
    }
}

private data class Particle(
    val initialX: Float,
    val initialY: Float,
    val radius: Float,
    val speed: Float,
    val swaySpeed: Float,
    val initialAlpha: Float
)