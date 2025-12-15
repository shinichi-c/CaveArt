package com.android.CaveArt

import android.app.Activity
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import com.google.accompanist.systemuicontroller.rememberSystemUiController

@Composable
fun AsyncWallpaperImage(
    wallpaper: Wallpaper,
    contentDescription: String?,
    viewModel: WallpaperViewModel,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    allowMagic: Boolean = true
) {
    val context = LocalContext.current
    var bitmap by remember(wallpaper, allowMagic) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(wallpaper, allowMagic) {
        bitmap = viewModel.getOrCreateProcessedBitmap(context, wallpaper, allowMagic)
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SwipableWallpaperScreen(viewModel: WallpaperViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val darkTheme = isSystemInDarkTheme()

    val systemBarsColor = MaterialTheme.colorScheme.background
    val systemUiController = rememberSystemUiController()

    SideEffect {
        systemUiController.setStatusBarColor(systemBarsColor, darkIcons = !darkTheme)
        systemUiController.setNavigationBarColor(systemBarsColor, darkIcons = !darkTheme)
    }
    
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.addGalleryWallpaper(uri)
        }
    }

    var isSettingWallpaper by remember { mutableStateOf(false) }
    var showDestinationSheet by remember { mutableStateOf(false) }
    var wallpaperToApplyState by remember { mutableStateOf<Wallpaper?>(null) }
    var selectedWallpaperIndex by remember { mutableIntStateOf(-1) }
    var showFilterPanel by remember { mutableStateOf(false) }
    var showSettingsPanel by remember { mutableStateOf(false) }

    val isDetailViewActive = selectedWallpaperIndex != -1

    BackHandler(enabled = isDetailViewActive) {
        selectedWallpaperIndex = -1
    }

    val filteredWallpapers = viewModel.filteredWallpapers
    val selectedTag = viewModel.selectedTag
    val isLoading = viewModel.isLoading
    val isDebugMaskEnabled = viewModel.isDebugMaskEnabled

    val mainPagerState = rememberPagerState(pageCount = { filteredWallpapers.size })

    var animateCardKey by remember { mutableIntStateOf(mainPagerState.currentPage) }
    var lastPage by remember { mutableIntStateOf(mainPagerState.currentPage) }

    LaunchedEffect(mainPagerState.currentPage) {
        if (lastPage != mainPagerState.currentPage) {
            animateCardKey = mainPagerState.currentPage
        }
        lastPage = mainPagerState.currentPage
    }

    val onDragStartHaptics: () -> Unit = {
        if (viewModel.isHapticsEnabled) {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        }
    }
    val onPageChangeHaptics: () -> Unit = {
        if (viewModel.isHapticsEnabled) {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    LaunchedEffect(selectedTag, filteredWallpapers.size) {
        if (filteredWallpapers.isNotEmpty()) {
            val targetPage = mainPagerState.currentPage.coerceIn(0, filteredWallpapers.size - 1)
            mainPagerState.scrollToPage(targetPage)
        }
    }

    var currentWallpaper by remember { mutableStateOf<Wallpaper?>(null) }
    LaunchedEffect(mainPagerState.currentPage, filteredWallpapers) {
        currentWallpaper = if (filteredWallpapers.isNotEmpty() && mainPagerState.currentPage < filteredWallpapers.size) {
            filteredWallpapers[mainPagerState.currentPage]
        } else {
            null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            
            AnimatedVisibility(
                visible = isDetailViewActive,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                if (filteredWallpapers.isNotEmpty()) {
                    WallpaperDetailScreen(
                        wallpapers = filteredWallpapers,
                        initialPageIndex = selectedWallpaperIndex,
                        onClose = { selectedWallpaperIndex = -1 },
                        onApplyClick = { wallpaper ->
                            wallpaperToApplyState = wallpaper
                            showDestinationSheet = true
                        },
                        isDebugMaskEnabled = isDebugMaskEnabled,
                        viewModel = viewModel
                    )
                }
            }

            AnimatedVisibility(
                visible = !isDetailViewActive,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    val wallpaper = currentWallpaper

                    if (wallpaper != null) {
                        Text(
                            text = wallpaper.title,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(top = 16.dp),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    if (!isLoading && filteredWallpapers.isEmpty()) {
                        Text(
                            "No Wallpapers Found.",
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    } else if (filteredWallpapers.isNotEmpty()) {
                        HorizontalPager(
                            state = mainPagerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp)
                                .align(Alignment.Center),
                            contentPadding = PaddingValues(horizontal = 80.dp)
                        ) { pageIndex ->
                            val wp = filteredWallpapers[pageIndex]
                            val pageOffset = ((mainPagerState.currentPage - pageIndex) + mainPagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                            val scale = lerp(0.85f, 1f, 1f - pageOffset)
                            val alpha = lerp(0.6f, 1f, 1f - pageOffset)

                            WallpaperPreviewCard(
                                wallpaper = wp,
                                normalPageScale = scale,
                                normalPageAlpha = alpha,
                                onClick = { selectedWallpaperIndex = pageIndex },
                                animationTriggerKey = animateCardKey,
                                pageIndex = pageIndex,
                                isDebugMaskEnabled = isDebugMaskEnabled,
                                viewModel = viewModel
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(bottom = 100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            FastScrollIndicator(
                                pagerState = mainPagerState,
                                onDragStartHaptics = onDragStartHaptics,
                                onPageChangeHaptics = onPageChangeHaptics
                            )
                        }
                    } else {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    
                    if (!isLoading) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ConnectedWallpaperActions(
                                currentWallpaper = currentWallpaper,
                                onSetWallpaperClick = {
                                    if (currentWallpaper != null) {
                                        wallpaperToApplyState = currentWallpaper
                                        showDestinationSheet = true
                                    } else {
                                        Toast.makeText(context, "Please select a wallpaper first.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onFilterClick = { showFilterPanel = true },
                                onSettingsClick = { showSettingsPanel = true },
                                onAddClick = {
                                    galleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showFilterPanel) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showFilterPanel = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Categories", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Divider(modifier = Modifier.padding(vertical = 12.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                }
            }
        }

        if (showSettingsPanel) {
            SettingsSheet(viewModel = viewModel, onDismiss = { showSettingsPanel = false })
        }

        if (showDestinationSheet && wallpaperToApplyState != null) {
            val wallpaperToApply = wallpaperToApplyState!!
            val isFixedAlignmentEnabled = viewModel.isFixedAlignmentEnabled
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showDestinationSheet = false; wallpaperToApplyState = null },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Apply Wallpaper To", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 24.dp))
                    
                    val applyAction: (Int) -> Unit = { dest ->
                        isSettingWallpaper = true
                        scope.launch {
                            setDeviceWallpaper(context, wallpaperToApply, dest, isFixedAlignmentEnabled, viewModel)
                            showDestinationSheet = false
                            isSettingWallpaper = false
                            wallpaperToApplyState = null
                        }
                    }

                    DestinationButton(Icons.Default.PhotoLibrary, "Home & Lock Screens", "Set everywhere", isSettingWallpaper) { applyAction(WallpaperDestinations.FLAG_BOTH) }
                    Divider(Modifier.padding(vertical = 12.dp))
                    DestinationButton(Icons.Default.PhotoLibrary, "Home Screen Only", "Set on home screen", isSettingWallpaper) { applyAction(WallpaperDestinations.FLAG_HOME_SCREEN) }
                    Divider(Modifier.padding(vertical = 12.dp))
                    DestinationButton(Icons.Default.Lock, "Lock Screen Only", "Set on lock screen", isSettingWallpaper) { applyAction(WallpaperDestinations.FLAG_LOCK_SCREEN) }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }

        if (isSettingWallpaper) {
            LoadingOverlay(title = "Setting Wallpaper...")
        }
    }
}

@Composable
fun WallpaperPreviewCard(
    wallpaper: Wallpaper,
    onClick: () -> Unit,
    viewModel: WallpaperViewModel,
    modifier: Modifier = Modifier,
    normalPageScale: Float,
    normalPageAlpha: Float,
    animationTriggerKey: Int = -1,
    pageIndex: Int = -2,
    isDebugMaskEnabled: Boolean
) {
    val isAnimated = animationTriggerKey == pageIndex
    val targetScale = if (isAnimated) 1.05f else 1f
    val animatedScale by animateFloatAsState(targetValue = targetScale, animationSpec = tween(150, easing = FastOutSlowInEasing), label = "Scale")
    val combinedScale = normalPageScale * animatedScale

    Card(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp)
            .clickable(onClick = onClick)
            .graphicsLayer {
                scaleX = combinedScale
                scaleY = combinedScale
                alpha = normalPageAlpha
            },
        shape = RoundedCornerShape(32.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        if (isDebugMaskEnabled) {
            DebugMaskImage(
                wallpaper = wallpaper,
                contentDescription = "Debug: ${wallpaper.title}",
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncWallpaperImage(
                wallpaper = wallpaper,
                contentDescription = "Preview: ${wallpaper.title}",
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
                allowMagic = false
            )
        }
    }
}

@Composable
fun ConnectedWallpaperActions(
    currentWallpaper: Wallpaper?, 
    onSetWallpaperClick: () -> Unit, 
    onFilterClick: () -> Unit, 
    onSettingsClick: () -> Unit,
    onAddClick: () -> Unit
) {
    val enabled = currentWallpaper != null
    val buttonHeight = 48.dp
    val commonColors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        
        Button(
            onClick = onSettingsClick, 
            modifier = Modifier.size(buttonHeight), 
            colors = commonColors, 
            shape = RoundedCornerShape(24.dp, 0.dp, 0.dp, 24.dp), 
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(Icons.Default.Settings, "Settings", Modifier.size(20.dp))
        }
        
        Button(
            onClick = onAddClick,
            modifier = Modifier.size(buttonHeight),
            colors = commonColors,
            shape = RoundedCornerShape(0.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
             Icon(Icons.Default.Add, "Add", Modifier.size(20.dp))
        }
        
        Button(
            onClick = onSetWallpaperClick, 
            enabled = enabled, 
            modifier = Modifier.height(buttonHeight), 
            colors = commonColors, 
            shape = RoundedCornerShape(0.dp)
        ) {
            Icon(Icons.Default.Image, "Set", Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("APPLY", fontWeight = FontWeight.Bold)
        }
        
        Button(
            onClick = onFilterClick, 
            modifier = Modifier.size(buttonHeight), 
            colors = commonColors, 
            shape = RoundedCornerShape(0.dp, 24.dp, 24.dp, 0.dp), 
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(Icons.Default.FilterList, "Filter", Modifier.size(20.dp))
        }
    }
}

@Composable
fun CategoryChip(title: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(title, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun DestinationButton(icon: ImageVector, title: String, subtitle: String, isSetting: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = !isSetting,
        modifier = Modifier.fillMaxWidth().height(88.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
        contentPadding = PaddingValues(20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, title, Modifier.size(28.dp)); Spacer(Modifier.size(18.dp))
                Column { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodyMedium) }
            }
            if (isSetting) CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun LoadingOverlay(title: String) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.7f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface), modifier = Modifier.padding(32.dp).widthIn(min = 250.dp)) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(56.dp)); Spacer(Modifier.height(24.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("This might take a moment.", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}