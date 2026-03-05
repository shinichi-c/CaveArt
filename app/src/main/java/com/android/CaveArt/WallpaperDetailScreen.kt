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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.android.CaveArt.animations.AnimationStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sin
import kotlin.math.pow
import coil3.compose.rememberAsyncImagePainter
import kotlin.random.Random
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score
import com.materialkolor.hct.Hct

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
fun MagicControlsSheet(
    wallpaper: Wallpaper,
    viewModel: WallpaperViewModel
) {
    val context = LocalContext.current
    var extractedColors by remember { mutableStateOf(listOf<Int>()) }
    var sliderPosition by remember { mutableFloatStateOf(viewModel.magicScale) }
    
    var selectedTab by remember { mutableStateOf("Shape") }
    val tabs = listOf("Shape", "Animation", "Style")

    LaunchedEffect(wallpaper) {
        withContext(Dispatchers.IO) {
            val bitmap = if(wallpaper.uri != null) BitmapHelper.decodeSampledBitmapFromUri(context, wallpaper.uri, 500) else BitmapFactory.decodeResource(context.resources, wallpaper.resourceId)
            if (bitmap != null) {
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 112, 112, true)
                val pixels = IntArray(scaledBitmap.width * scaledBitmap.height)
                scaledBitmap.getPixels(pixels, 0, scaledBitmap.width, 0, 0, scaledBitmap.width, scaledBitmap.height)
                
                val quantizerResult = QuantizerCelebi.quantize(pixels, 128)
                val rankedColors: List<Int> = Score.score(quantizerResult)
                
                var finalColors = rankedColors.distinct().take(5)
                
                if (finalColors.size < 5 && finalColors.isNotEmpty()) {
                    val seed = finalColors.first()
                    val hct = Hct.fromInt(seed)
                    
                    val generated = listOf(
                        Hct.from(hct.hue + 60.0, hct.chroma, hct.tone).toInt(),
                        Hct.from(hct.hue - 60.0, hct.chroma, hct.tone).toInt(),
                        Hct.from(hct.hue + 180.0, hct.chroma, hct.tone).toInt(),
                        Hct.from(hct.hue, hct.chroma, 30.0).toInt(),
                        Hct.from(hct.hue, hct.chroma, 80.0).toInt()
                    )
                    finalColors = (finalColors + generated).distinct().take(5)
                } else if (finalColors.isEmpty()) {
                    
                    finalColors = listOf(
                        android.graphics.Color.parseColor("#4CAF50"),
                        android.graphics.Color.parseColor("#2196F3"),
                        android.graphics.Color.parseColor("#FF9800"),
                        android.graphics.Color.parseColor("#E91E63"),
                        android.graphics.Color.parseColor("#9C27B0")
                    )
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
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        
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
                                        modifier = Modifier
                                            .weight(1f) 
                                            .aspectRatio(1f) 
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { viewModel.updateMagicConfig(shape, viewModel.currentBackgroundColor) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        ShapeIcon(
                                            shape = shape, 
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, 
                                            modifier = Modifier.fillMaxSize(0.45f)
                                        )
                                    }
                                }
                            }
                            
                            Spacer(Modifier.height(28.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                            ) {
                                extractedColors.forEach { colorInt ->
                                    val isSelected = viewModel.currentBackgroundColor == colorInt
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .border(3.dp, Color(colorInt), CircleShape)
                                            )
                                        }
                                        
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize(if (isSelected) 0.65f else 1f) 
                                                .clip(CircleShape)
                                                .background(Color(colorInt))
                                                .clickable { viewModel.updateMagicConfig(viewModel.currentMagicShape, colorInt) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    "Animation" -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Animation Style", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AnimationStyle.values().forEach { style ->
                                    val isSelected = viewModel.currentAnimationStyle == style
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.updateAnimationStyle(style) },
                                        label = { Text(style.label, fontWeight = FontWeight.Bold) },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer),
                                        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = isSelected, borderColor = Color.Transparent)
                                    )
                                }
                            }
                        }
                    }
                    "Style" -> {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                FilterChip(
                                    selected = viewModel.is3DPopEnabled, 
                                    onClick = { viewModel.toggle3DPop() }, 
                                    label = { Text("3D Pop", fontWeight = FontWeight.Bold) }, 
                                    leadingIcon = { Icon(Icons.Default.Layers, null, Modifier.size(18.dp)) }, 
                                    shape = RoundedCornerShape(16.dp), border = null
                                )
                                FilterChip(
                                    selected = viewModel.isCentered, 
                                    onClick = { viewModel.toggleCentered() }, 
                                    label = { Text("Center", fontWeight = FontWeight.Bold) }, 
                                    leadingIcon = { Icon(Icons.Default.FilterCenterFocus, null, Modifier.size(18.dp)) }, 
                                    shape = RoundedCornerShape(16.dp), border = null
                                )
                            }
                            Spacer(Modifier.height(24.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Shape Size", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(if (sliderPosition < 1.0f) "Tight" else "Wide", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(value = sliderPosition, onValueChange = { sliderPosition = it }, onValueChangeFinished = { viewModel.updateMagicScale(sliderPosition) }, valueRange = 0.5f..1.5f, steps = 5)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        
        Surface(
            modifier = Modifier.navigationBarsPadding().padding(bottom = 12.dp).height(64.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val isSelected = selectedTab == tab
                    val tabIcon = when (tab) {
                        "Shape" -> Icons.Default.Category
                        "Animation" -> Icons.Default.Animation
                        else -> Icons.Default.Palette
                    }

                    Box(
                        modifier = Modifier
                            .height(48.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .clickable { selectedTab = tab }
                            .padding(horizontal = if (isSelected) 20.dp else 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = tabIcon, 
                                    contentDescription = null, 
                                    modifier = Modifier.size(18.dp), 
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Text(
                                text = tab,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
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
            val styleName = viewModel.currentAnimationStyle.name
            val isBigBang = styleName.contains("BIG", true) || styleName.contains("BANG", true)
            val isMorph = styleName.contains("MORPH", true) || styleName.contains("ORGANIC", true)
            
            val infiniteTransition = rememberInfiniteTransition(label = "LivePreview")
            
            val targetScale = if (isBigBang) 1.25f else 1.05f
            val scaleDuration = if (isBigBang) 800 else 3000
            
            val scaleAnim by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = targetScale,
                animationSpec = infiniteRepeatable(
                    animation = tween(scaleDuration, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )
            
            val rotationAnim by infiniteTransition.animateFloat(
                initialValue = if (isMorph) -3f else 0f,
                targetValue = if (isMorph) 3f else 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(4000, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "rotation"
            )

            AnimatedContent(
                targetState = currentBitmap, 
                label = "MagicDepthAnim", 
                transitionSpec = { 
                    (fadeIn(animationSpec = tween(500)) + scaleIn(initialScale = 0.95f, animationSpec = tween(500, easing = LinearOutSlowInEasing)))
                    .togetherWith(fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 1.15f, animationSpec = tween(400, easing = FastOutSlowInEasing))) 
                }
            ) { bitmap ->
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(), 
                        contentDescription = null, 
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scaleAnim
                                scaleY = scaleAnim
                                rotationZ = rotationAnim
                            }, 
                        contentScale = ContentScale.Crop
                    )
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
