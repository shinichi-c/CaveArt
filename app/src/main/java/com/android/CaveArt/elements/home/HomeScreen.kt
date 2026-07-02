package com.android.CaveArt

import android.app.Activity
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
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
    
    val galleryLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) viewModel.addGalleryWallpaper(uri) }

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
    
    val isEffectActive = viewModel.isMagicShapeEnabled || viewModel.isAnimationEnabled || viewModel.isFilamentEnabled
    val isClockActive = viewModel.isLockscreenClockPreviewVisible
    val isAnyEditorActive = isEffectActive || isClockActive
    
    val onHaptics = { if (viewModel.isHapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }

    BackHandler(enabled = isImmersiveMode) { isImmersiveMode = false }
    BackHandler(enabled = isAnyEditorActive) { 
        viewModel.setMagicShapeEnabled(false); viewModel.setAnimationEnabled(false)
        viewModel.setFilamentEnabled(false); viewModel.isLockscreenClockPreviewVisible = false
    }
    
    LaunchedEffect(mainPagerState.currentPage, mainPagerState.isScrollInProgress) {
        if (filteredWallpapers.isNotEmpty()) {
            if (mainPagerState.isScrollInProgress) carouselState.scrollToItem(mainPagerState.currentPage)
            else carouselState.animateScrollToItem(mainPagerState.currentPage)
        }
    }

    val currentWallpaper = filteredWallpapers.getOrNull(mainPagerState.currentPage)

    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
            if (viewModel.isAmbientBlurEnabled && currentWallpaper != null) {
                AnimatedContent(targetState = currentWallpaper, transitionSpec = { fadeIn(tween(1000)) togetherWith fadeOut(tween(1000)) }, label = "ambient_background") { wp ->
                    AsyncWallpaperImage(wallpaper = wp, contentDescription = null, viewModel = viewModel, modifier = Modifier.fillMaxSize().blur(80.dp), contentScale = ContentScale.Crop, allowMagic = false)
                }
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)))
            }
            
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp).statusBarsPadding()) {
                androidx.compose.animation.AnimatedVisibility(visible = !isImmersiveMode && !isClockActive, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
                        if (isAnyEditorActive) {
                            Surface(modifier = Modifier.align(Alignment.CenterStart), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, shadowElevation = 0.dp) {
                                IconButton(onClick = { viewModel.setMagicShapeEnabled(false); viewModel.setAnimationEnabled(false); viewModel.setFilamentEnabled(false); viewModel.isLockscreenClockPreviewVisible = false }, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.ArrowBack, "Cancel", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp)) }
                            }
                            Text(text = if (isClockActive) "Lockscreen Editor" else "Effects", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                            Surface(onClick = { wallpaperToApplyState = currentWallpaper; showDestinationSheet = true }, shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.align(Alignment.CenterEnd).size(44.dp)) {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Check, "Apply", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp)) }
                            }
                        } else {
                            Surface(modifier = Modifier.align(Alignment.CenterStart), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, shadowElevation = 0.dp) {
                                IconButton(onClick = { showFilterPanel = true }, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.LocalOffer, "Tags", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp)) }
                            }
                            AnimatedContent(targetState = currentWallpaper?.title ?: "Wallpapers", transitionSpec = { (fadeIn(tween(400)) + slideInVertically { height -> height / 2 }) togetherWith (fadeOut(tween(400)) + slideOutVertically { height -> -height / 2 }) }, label = "TitleAnimation", modifier = Modifier.padding(horizontal = 88.dp)) { targetTitle ->
                                Text(text = targetTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Surface(modifier = Modifier.align(Alignment.CenterEnd), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, shadowElevation = 0.dp) {
                                IconButton(onClick = { showSettingsPanel = true }, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp)) }
                            }
                        }
                    }
                }
                
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (!isLoading && filteredWallpapers.isEmpty()) {
                        Text("No Wallpapers Found.", modifier = Modifier.align(Alignment.Center))
                    } else if (filteredWallpapers.isNotEmpty()) {
                        HorizontalPager(state = mainPagerState, modifier = Modifier.fillMaxSize(), userScrollEnabled = !isAnyEditorActive) { pageIndex ->
                            val wp = filteredWallpapers[pageIndex]
                            val isCurrentPage = pageIndex == mainPagerState.currentPage
                            val pageOffset = ((mainPagerState.currentPage - pageIndex) + mainPagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                            val scale = lerp(0.85f, 1f, 1f - pageOffset)
                            val alpha = lerp(0.4f, 1f, 1f - pageOffset)

                            WallpaperPreviewCard(
                                wallpaper = wp, isCurrentPage = isCurrentPage, normalPageAlpha = alpha, normalPageScale = scale,
                                onTap = { if (!isAnyEditorActive) isCarouselVisible = false },
                                onLongPress = { if (!isAnyEditorActive) { isCarouselVisible = true; if (viewModel.isHapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) } },
                                onApplyClockAndWallpaperClick = {
                                    isSettingWallpaper = true
                                    scope.launch {
                                        setDeviceWallpaper(context, wp, WallpaperDestinations.FLAG_BOTH, viewModel.isFixedAlignmentEnabled, viewModel)
                                        isSettingWallpaper = false
                                        viewModel.isLockscreenClockPreviewVisible = false
                                        withContext(Dispatchers.Main) { Toast.makeText(context, "Clock & Wallpaper Applied", Toast.LENGTH_SHORT).show() }
                                    }
                                },
                                viewModel = viewModel
                            )
                        }
                    }
                }
                
                androidx.compose.animation.AnimatedVisibility(visible = !isImmersiveMode, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                    Box(contentAlignment = Alignment.BottomCenter) {
                        androidx.compose.animation.AnimatedVisibility(visible = !isAnyEditorActive, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                HeroCarouselWithIndicator(filteredWallpapers = filteredWallpapers, pagerState = mainPagerState, carouselState = carouselState, isCarouselVisible = isCarouselVisible, onWallpaperClick = { i -> scope.launch { mainPagerState.animateScrollToPage(i) }; onHaptics() }, onHaptics = onHaptics, viewModel = viewModel)
                                Box(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
                                    ConnectedWallpaperActions(currentWallpaper = currentWallpaper, onSetWallpaperClick = { if (currentWallpaper != null) { wallpaperToApplyState = currentWallpaper; showDestinationSheet = true } }, onMagicClick = { viewModel.setMagicShapeEnabled(true) }, onAnimationClick = { viewModel.setAnimationEnabled(true) }, onFilamentClick = { viewModel.setFilamentEnabled(true) }, onLockscreenClick = { viewModel.isLockscreenClockPreviewVisible = true }, onAddClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                                }
                            }
                        }
                        androidx.compose.animation.AnimatedVisibility(visible = isEffectActive && currentWallpaper != null, enter = slideInVertically { it } + fadeIn(tween(400)), exit = slideOutVertically { it } + fadeOut(tween(300))) {
                            if (currentWallpaper != null) EffectsControlsSheet(wallpaper = currentWallpaper, viewModel = viewModel)
                        }
                    }
                }
            }
        }
        
        if (showFilterPanel) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            AmbientBottomSheet(onDismissRequest = { showFilterPanel = false }, sheetState = sheetState, viewModel = viewModel, currentWallpaper = currentWallpaper) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Categories", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        items(viewModel.allTags) { tag -> CategoryChip(title = tag, isSelected = tag == selectedTag, onClick = { viewModel.selectTag(tag); showFilterPanel = false }) }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        if (showSettingsPanel) SettingsSheet(viewModel = viewModel, currentWallpaper = currentWallpaper, onDismiss = { showSettingsPanel = false })

        if (showDestinationSheet && wallpaperToApplyState != null) {
            val wallpaperToApply = wallpaperToApplyState!!
            val isFixedAlignmentEnabled = viewModel.isFixedAlignmentEnabled
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            
            AmbientBottomSheet(onDismissRequest = { showDestinationSheet = false; wallpaperToApplyState = null }, sheetState = sheetState, viewModel = viewModel, currentWallpaper = currentWallpaper) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).navigationBarsPadding()) {
                    Text("Apply Wallpaper To", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 24.dp))
                    val applyAction: (Int) -> Unit = { dest ->
                        isSettingWallpaper = true
                        scope.launch {
                            setDeviceWallpaper(context, wallpaperToApply, dest, isFixedAlignmentEnabled, viewModel)
                            showDestinationSheet = false; isSettingWallpaper = false; wallpaperToApplyState = null
                            viewModel.setMagicShapeEnabled(false); viewModel.setAnimationEnabled(false); viewModel.setFilamentEnabled(false); viewModel.isLockscreenClockPreviewVisible = false
                        }
                    }
                    DestinationButton(Icons.Default.PhotoLibrary, "Home & Lock Screens", "Set everywhere", isSettingWallpaper) { applyAction(WallpaperDestinations.FLAG_BOTH) }
                    Spacer(Modifier.height(12.dp))
                    DestinationButton(Icons.Default.PhotoLibrary, "Home Screen Only", "Set on home screen", isSettingWallpaper) { applyAction(WallpaperDestinations.FLAG_HOME_SCREEN) }
                    Spacer(Modifier.height(12.dp))
                    DestinationButton(Icons.Default.Lock, "Lock Screen Only", "Set on lock screen", isSettingWallpaper) { applyAction(WallpaperDestinations.FLAG_LOCK_SCREEN) }
                    if (isEffectActive || isClockActive) {
                        Spacer(Modifier.height(12.dp))
                        DestinationButton(Icons.Default.AutoAwesome, "Live Wallpaper", "Animated interactive wallpaper", isSettingWallpaper) {
                            isSettingWallpaper = true
                            scope.launch {
                                setLiveWallpaper(context, wallpaperToApply, viewModel)
                                showDestinationSheet = false; isSettingWallpaper = false; wallpaperToApplyState = null
                                viewModel.setMagicShapeEnabled(false); viewModel.setAnimationEnabled(false); viewModel.setFilamentEnabled(false); viewModel.isLockscreenClockPreviewVisible = false
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        if (isSettingWallpaper) LoadingOverlay(title = "Setting Wallpaper...")
        AppUpdateHandler(viewModel = viewModel, currentWallpaper = currentWallpaper)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WallpaperPreviewCard(wallpaper: Wallpaper, isCurrentPage: Boolean, onTap: () -> Unit, onLongPress: () -> Unit, onApplyClockAndWallpaperClick: () -> Unit, viewModel: WallpaperViewModel, normalPageAlpha: Float, normalPageScale: Float) {
    val isLiveActive = (viewModel.isMagicShapeEnabled || viewModel.isAnimationEnabled || viewModel.isFilamentEnabled) && isCurrentPage
    val isClockEditor = viewModel.isLockscreenClockPreviewVisible && isCurrentPage
    val context = LocalContext.current
    val metrics = remember { context.resources.displayMetrics }
    val screenAspectRatio = remember { metrics.widthPixels.toFloat() / metrics.heightPixels.toFloat() }

    Box(modifier = Modifier.fillMaxSize().padding(bottom = 16.dp).graphicsLayer { alpha = normalPageAlpha; scaleX = normalPageScale; scaleY = normalPageScale }, contentAlignment = Alignment.Center) {
        if (isClockEditor) {
            LockscreenEditor(wallpaper = wallpaper, viewModel = viewModel, modifier = Modifier.fillMaxSize(), onApplyClockAndWallpaperClick = onApplyClockAndWallpaperClick)
        } else {
            Card(modifier = Modifier.fillMaxHeight(0.90f).aspectRatio(screenAspectRatio).combinedClickable(onClick = onTap, onLongClick = onLongPress), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(0.dp)) {
                if (isLiveActive) LockscreenEditor(wallpaper = wallpaper, viewModel = viewModel, modifier = Modifier.fillMaxSize(), onApplyClockAndWallpaperClick = onApplyClockAndWallpaperClick)
                else AsyncWallpaperImage(wallpaper = wallpaper, contentDescription = null, viewModel = viewModel, modifier = Modifier.fillMaxSize(), allowMagic = false)
            }
        }
    }
}
