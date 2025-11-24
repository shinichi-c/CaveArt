package com.android.CaveArt

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.RectF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.palette.graphics.Palette
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        
        HorizontalPager(state = detailPagerState, modifier = Modifier.fillMaxSize()) { pageIndex ->
            val resourceId = wallpapers[pageIndex].resourceId
            
            if (viewModel.isMagicShapeEnabled) {
                MagicEffectImage(
                    resourceId = resourceId,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (isDebugMaskEnabled) {
                DebugMaskImage(
                    resourceId = resourceId,
                    contentDescription = "Debug",
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    painter = painterResource(id = resourceId),
                    contentDescription = "Original",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
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
                visible = isMagicMode,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                MagicControlsSheet(
                    currentWallpaperId = currentWallpaper.resourceId,
                    viewModel = viewModel,
                    onCloseMagic = { viewModel.setMagicShapeEnabled(false) },
                    onApply = { onApplyClick(currentWallpaper) }
                )
            }
        }
    }
}

@Composable
fun MagicControlsSheet(
    currentWallpaperId: Int,
    viewModel: WallpaperViewModel,
    onCloseMagic: () -> Unit,
    onApply: () -> Unit
) {
    val context = LocalContext.current
    
    var extractedColors by remember { mutableStateOf(listOf<Int>()) }
    var isPaletteLoading by remember { mutableStateOf(true) }

    LaunchedEffect(currentWallpaperId) {
        isPaletteLoading = true
        withContext(Dispatchers.IO) {
            try {
                val bitmap = BitmapFactory.decodeResource(context.resources, currentWallpaperId)
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
                    if (finalColors.isNotEmpty()) {
                         viewModel.updateMagicConfig(viewModel.currentMagicShape, finalColors.first())
                    }
                }
                bitmap.recycle()
            } catch (e: Exception) { e.printStackTrace() }
        }
        isPaletteLoading = false
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 0.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCloseMagic) { Text("Cancel") }
                Text("Effects", fontWeight = FontWeight.Bold)
                IconButton(
                    onClick = onApply,
                    modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterChip(
                    selected = viewModel.is3DPopEnabled,
                    onClick = { viewModel.toggle3DPop() },
                    label = { Text("3D Pop") },
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

            Spacer(modifier = Modifier.height(8.dp))
            
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
fun MagicEffectImage(resourceId: Int, viewModel: WallpaperViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var processedBitmap by remember(resourceId, viewModel.currentMagicShape, viewModel.currentBackgroundColor, viewModel.is3DPopEnabled) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }

    LaunchedEffect(resourceId, viewModel.currentMagicShape, viewModel.currentBackgroundColor, viewModel.is3DPopEnabled) {
        processedBitmap = viewModel.getOrCreateProcessedBitmap(context, resourceId)
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (processedBitmap != null) {
            Image(
                bitmap = processedBitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop 
            )
        } else {
            CircularProgressIndicator(color = Color.White)
        }
    }
}
