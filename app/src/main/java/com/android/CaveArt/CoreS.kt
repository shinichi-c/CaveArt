@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
package com.android.CaveArt

import android.app.Activity
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun AsyncWallpaperImage(
    wallpaper: Wallpaper,
    contentDescription: String?,
    viewModel: WallpaperViewModel,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    allowMagic: Boolean = true
) {
    
    if (!allowMagic) {
        val model = wallpaper.uri ?: wallpaper.resourceId
        Image(
            painter = rememberAsyncImagePainter(model),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        
        val context = LocalContext.current
        var bitmap by remember(wallpaper) { mutableStateOf<Bitmap?>(null) }

        LaunchedEffect(wallpaper) {
            bitmap = viewModel.getOrCreateProcessedBitmap(context, wallpaper, true)
        }

        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = modifier
            )
        } else {
            Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmbientBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    viewModel: WallpaperViewModel,
    currentWallpaper: Wallpaper?,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null
    ) {
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))) {
            if (viewModel.isAmbientBlurEnabled && currentWallpaper != null) {
                AsyncWallpaperImage(
                    wallpaper = currentWallpaper,
                    contentDescription = null,
                    viewModel = viewModel,
                    modifier = Modifier.matchParentSize().blur(80.dp),
                    contentScale = ContentScale.Crop,
                    allowMagic = false
                )
                Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)))
            } else {
                Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceContainerHigh))
            }
            
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BottomSheetDefaults.DragHandle()
                }
                content()
            }
        }
    }
}

@Composable
fun SwipableWallpaperScreen(viewModel: WallpaperViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val darkTheme = isSystemInDarkTheme()
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }
    
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) viewModel.addGalleryWallpaper(uri) }

    var isSettingWallpaper by remember { mutableStateOf(false) }
    var showDestinationSheet by remember { mutableStateOf(false) }
    var wallpaperToApplyState by remember { mutableStateOf<Wallpaper?>(null) }
    var showFilterPanel by remember { mutableStateOf(false) }
    var showSettingsPanel by remember { mutableStateOf(false) }

    val filteredWallpapers = viewModel.filteredWallpapers
    val selectedTag = viewModel.selectedTag
    val isLoading = viewModel.isLoading

    val mainPagerState = rememberPagerState(pageCount = { filteredWallpapers.size })
    val carouselState = rememberCarouselState { filteredWallpapers.size }

    var isImmersiveMode by remember { mutableStateOf(false) }
    var isCarouselVisible by rememberSaveable { mutableStateOf(true) }
    
    val onHaptics = { if (viewModel.isHapticsEnabled) view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY) }

    BackHandler(enabled = isImmersiveMode) { isImmersiveMode = false }
    BackHandler(enabled = viewModel.isMagicShapeEnabled) { viewModel.setMagicShapeEnabled(false) }
    
    LaunchedEffect(mainPagerState.currentPage, mainPagerState.isScrollInProgress) {
        if (filteredWallpapers.isNotEmpty()) {
            if (mainPagerState.isScrollInProgress) {
                carouselState.scrollToItem(mainPagerState.currentPage)
            } else {
                carouselState.animateScrollToItem(mainPagerState.currentPage)
            }
        }
    }

    val currentWallpaper = filteredWallpapers.getOrNull(mainPagerState.currentPage)

    Scaffold(
        containerColor = Color.Transparent, 
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
        	
            if (viewModel.isAmbientBlurEnabled && currentWallpaper != null) {
                AnimatedContent(
                    targetState = currentWallpaper,
                    transitionSpec = { fadeIn(tween(1000)) togetherWith fadeOut(tween(1000)) },
                    label = "ambient_background"
                ) { wp ->
                    AsyncWallpaperImage(
                        wallpaper = wp,
                        contentDescription = null,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize().blur(80.dp),
                        contentScale = ContentScale.Crop,
                        allowMagic = false
                    )
                }
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)))
            }
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .statusBarsPadding()
            ) {
            	
                AnimatedVisibility(
                    visible = !isImmersiveMode,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut()
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (viewModel.isMagicShapeEnabled) {
                        	
                            Surface(
                                modifier = Modifier.align(Alignment.CenterStart),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shadowElevation = 0.dp
                            ) {
                                IconButton(
                                    onClick = { viewModel.setMagicShapeEnabled(false) }, 
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(Icons.Default.ArrowBack, "Cancel", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                                }
                            }

                            Text(
                                text = "Effects",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black, 
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Surface(
                                onClick = { 
                                    wallpaperToApplyState = currentWallpaper
                                    showDestinationSheet = true 
                                },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.align(Alignment.CenterEnd).size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Check, "Apply", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                                }
                            }
                        } else {
                        	
                            Surface(
                                modifier = Modifier.align(Alignment.CenterStart),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shadowElevation = 0.dp
                            ) {
                                IconButton(onClick = { showFilterPanel = true }, modifier = Modifier.size(44.dp)) {
                                    Icon(Icons.Default.LocalOffer, "Tags", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                                }
                            }

                            AnimatedContent(
                                targetState = currentWallpaper?.title ?: "Wallpapers",
                                transitionSpec = {
                                    (fadeIn(tween(400)) + slideInVertically { height -> height / 2 }) togetherWith
                                    (fadeOut(tween(400)) + slideOutVertically { height -> -height / 2 })
                                },
                                label = "TitleAnimation",
                                modifier = Modifier.padding(horizontal = 88.dp) 
                            ) { targetTitle ->
                                Text(
                                    text = targetTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black, 
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            
                            Surface(
                                modifier = Modifier.align(Alignment.CenterEnd),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shadowElevation = 0.dp
                            ) {
                                IconButton(onClick = { showSettingsPanel = true }, modifier = Modifier.size(44.dp)) {
                                    Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
                
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (!isLoading && filteredWallpapers.isEmpty()) {
                        Text("No Wallpapers Found.", modifier = Modifier.align(Alignment.Center))
                    } else if (filteredWallpapers.isNotEmpty()) {
                        HorizontalPager(
                            state = mainPagerState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = !viewModel.isMagicShapeEnabled
                        ) { pageIndex ->
                            val wp = filteredWallpapers[pageIndex]
                            val isCurrentPage = pageIndex == mainPagerState.currentPage
                            val pageOffset = ((mainPagerState.currentPage - pageIndex) + mainPagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                            val scale = lerp(0.85f, 1f, 1f - pageOffset)
                            val alpha = lerp(0.4f, 1f, 1f - pageOffset)

                            WallpaperPreviewCard(
                                wallpaper = wp,
                                isCurrentPage = isCurrentPage,
                                normalPageAlpha = alpha,
                                normalPageScale = scale,
                                onTap = { 
                                    if (!viewModel.isMagicShapeEnabled) {
                                        isCarouselVisible = false
                                    }
                                },
                                onLongPress = {
                                    if (!viewModel.isMagicShapeEnabled) {
                                        isCarouselVisible = true
                                        if (viewModel.isHapticsEnabled) {
                                            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                        }
                                    }
                                },
                                viewModel = viewModel
                            )
                        }
                    }
                }
                
                AnimatedVisibility(
                    visible = !isImmersiveMode,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    Box(contentAlignment = Alignment.BottomCenter) {
                    	
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !viewModel.isMagicShapeEnabled,
                            enter = slideInVertically { it } + fadeIn(),
                            exit = slideOutVertically { it } + fadeOut()
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            	
                                HeroCarouselWithIndicator(
                                    filteredWallpapers = filteredWallpapers,
                                    pagerState = mainPagerState,
                                    carouselState = carouselState,
                                    isCarouselVisible = isCarouselVisible,
                                    onWallpaperClick = { i ->
                                        scope.launch { mainPagerState.animateScrollToPage(i) }
                                        onHaptics()
                                    },
                                    onHaptics = onHaptics,
                                    viewModel = viewModel
                                )

                                Box(
                                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    ConnectedWallpaperActions(
                                        currentWallpaper = currentWallpaper,
                                        onSetWallpaperClick = {
                                            if (currentWallpaper != null) {
                                                wallpaperToApplyState = currentWallpaper
                                                showDestinationSheet = true
                                            }
                                        },
                                        onMagicClick = { viewModel.setMagicShapeEnabled(true) },
                                        onAddClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                                    )
                                }
                            }
                        }
                        
                        androidx.compose.animation.AnimatedVisibility(
                            visible = viewModel.isMagicShapeEnabled && currentWallpaper != null,
                            enter = slideInVertically { it } + fadeIn(tween(400)),
                            exit = slideOutVertically { it } + fadeOut(tween(300))
                        ) {
                            if (currentWallpaper != null) {
                                MagicControlsSheet(
                                    wallpaper = currentWallpaper,
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                }
            }
        }
        
        if (showFilterPanel) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            AmbientBottomSheet(
                onDismissRequest = { showFilterPanel = false },
                sheetState = sheetState,
                viewModel = viewModel,
                currentWallpaper = currentWallpaper
            ) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Categories", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(viewModel.allTags) { tag ->
                            CategoryChip(
                                title = tag,
                                isSelected = tag == selectedTag,
                                onClick = {
                                    viewModel.selectTag(tag)
                                    showFilterPanel = false
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        if (showSettingsPanel) {
            SettingsSheet(
                viewModel = viewModel, 
                currentWallpaper = currentWallpaper,
                onDismiss = { showSettingsPanel = false }
            )
        }

        if (showDestinationSheet && wallpaperToApplyState != null) {
            val wallpaperToApply = wallpaperToApplyState!!
            val isFixedAlignmentEnabled = viewModel.isFixedAlignmentEnabled
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            
            AmbientBottomSheet(
                onDismissRequest = { showDestinationSheet = false; wallpaperToApplyState = null },
                sheetState = sheetState,
                viewModel = viewModel,
                currentWallpaper = currentWallpaper
            ) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).navigationBarsPadding()) {
                    Text("Apply Wallpaper To", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 24.dp))
                    
                    val applyAction: (Int) -> Unit = { dest ->
                        isSettingWallpaper = true
                        scope.launch {
                            setDeviceWallpaper(context, wallpaperToApply, dest, isFixedAlignmentEnabled, viewModel)
                            showDestinationSheet = false
                            isSettingWallpaper = false
                            wallpaperToApplyState = null
                            viewModel.setMagicShapeEnabled(false) 
                        }
                    }

                    DestinationButton(Icons.Default.PhotoLibrary, "Home & Lock Screens", "Set everywhere", isSettingWallpaper) { applyAction(WallpaperDestinations.FLAG_BOTH) }
                    Spacer(Modifier.height(12.dp))
                    DestinationButton(Icons.Default.PhotoLibrary, "Home Screen Only", "Set on home screen", isSettingWallpaper) { applyAction(WallpaperDestinations.FLAG_HOME_SCREEN) }
                    Spacer(Modifier.height(12.dp))
                    DestinationButton(Icons.Default.Lock, "Lock Screen Only", "Set on lock screen", isSettingWallpaper) { applyAction(WallpaperDestinations.FLAG_LOCK_SCREEN) }
                    
                    if (viewModel.isMagicShapeEnabled) {
                        Spacer(Modifier.height(12.dp))
                        DestinationButton(Icons.Default.AutoAwesome, "Live Wallpaper", "Animated interactive wallpaper", isSettingWallpaper) {
                            isSettingWallpaper = true
                            scope.launch {
                                setLiveWallpaper(context, wallpaperToApply, viewModel)
                                showDestinationSheet = false
                                isSettingWallpaper = false
                                wallpaperToApplyState = null
                                viewModel.setMagicShapeEnabled(false)
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        if (isSettingWallpaper) {
            LoadingOverlay(title = "Setting Wallpaper...")
        }
        
        AppUpdateHandler(
viewModel = viewModel,
            currentWallpaper = currentWallpaper
)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WallpaperPreviewCard(
    wallpaper: Wallpaper,
    isCurrentPage: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    viewModel: WallpaperViewModel,
    normalPageAlpha: Float,
    normalPageScale: Float
) {
    val isMagicActive = viewModel.isMagicShapeEnabled && isCurrentPage

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 16.dp) 
            .graphicsLayer { 
                alpha = normalPageAlpha
                scaleX = normalPageScale
                scaleY = normalPageScale 
            },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(10.5f / 19.5f)
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = onLongPress
                ),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            if (isMagicActive) {
                MagicEffectImage(
                    wallpaper = wallpaper,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
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
}

@Composable
fun ConnectedWallpaperActions(
    currentWallpaper: Wallpaper?, 
    onSetWallpaperClick: () -> Unit, 
    onMagicClick: () -> Unit,
    onAddClick: () -> Unit
) {
    val enabled = currentWallpaper != null
    
    HorizontalFloatingToolbar(
        expanded = true,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (enabled) onSetWallpaperClick() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
            ) {
                Icon(Icons.Default.Wallpaper, contentDescription = "Apply")
            }
        },
        modifier = Modifier.wrapContentWidth(),
        colors = FloatingToolbarColors(
            toolbarContainerColor = MaterialTheme.colorScheme.primaryContainer,
            toolbarContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            fabContainerColor = MaterialTheme.colorScheme.primary,
            fabContentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        IconButton(onClick = onAddClick) { 
            Icon(Icons.Default.AddPhotoAlternate, "Add", tint = MaterialTheme.colorScheme.onPrimaryContainer) 
        }
        IconButton(onClick = onMagicClick) {
            Icon(Icons.Default.AutoAwesome, "Magic Shape", tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
fun CategoryChip(title: String, isSelected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(title, fontWeight = FontWeight.Bold) },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(4.dp)
    )
}

@Composable
fun DestinationButton(icon: ImageVector, title: String, subtitle: String, isSetting: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = !isSetting,
        modifier = Modifier.fillMaxWidth().height(104.dp),
        shape = RoundedCornerShape(32.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f), 
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        contentPadding = PaddingValues(24.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(32.dp)); Spacer(Modifier.size(20.dp))
                Column { 
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium) 
                }
            }
            if (isSetting) CircularProgressIndicator(modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
fun LoadingOverlay(title: String) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.6f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Card(
            shape = RoundedCornerShape(32.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(Modifier.padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(strokeWidth = 6.dp, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(24.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            }
        }
    }
}
