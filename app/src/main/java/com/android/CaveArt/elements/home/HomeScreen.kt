package com.android.CaveArt

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

enum class EditorMode { NONE, CLOCK, EFFECTS, IMMERSIVE }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
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
    var showSettingsPanel by remember { mutableStateOf(false) }

    val wallpapers = viewModel.allWallpapers
    val isLoading = viewModel.isLoading

    val mainPagerState = rememberPagerState(pageCount = { wallpapers.size })
    val carouselState = rememberCarouselState { wallpapers.size }

    var isDockExpanded by rememberSaveable { mutableStateOf(false) }
    var currentEditorMode by remember { mutableStateOf(EditorMode.NONE) }
    val currentWallpaper = wallpapers.getOrNull(mainPagerState.currentPage)

    val isEffectActive = viewModel.isMagicShapeEnabled || viewModel.isAnimationEnabled || viewModel.isFilamentEnabled
    val isClockActive = viewModel.isLockscreenClockPreviewVisible
    val isAnyEditorActive = currentEditorMode != EditorMode.NONE

    val onHaptics = { if (viewModel.isHapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }

    BackHandler(enabled = isAnyEditorActive) { currentEditorMode = EditorMode.NONE }
    BackHandler(enabled = isDockExpanded && !isAnyEditorActive) { isDockExpanded = false }
    
    LaunchedEffect(mainPagerState.currentPage, mainPagerState.isScrollInProgress) {
        if (wallpapers.isNotEmpty()) {
            if (mainPagerState.isScrollInProgress) carouselState.scrollToItem(mainPagerState.currentPage)
            else carouselState.animateScrollToItem(mainPagerState.currentPage)
        }
    }

    val applyWallpaperAction: (Wallpaper, Int, Boolean) -> Unit = { wp, dest, isLive ->
        isSettingWallpaper = true
        if (!isLive) {
            viewModel.setMagicShapeEnabled(false)
            viewModel.setAnimationEnabled(false)
            viewModel.setFilamentEnabled(false)
        }
        scope.launch {
            if (isLive) setLiveWallpaper(context, wp, viewModel)
            else setDeviceWallpaper(context, wp, dest, viewModel.isFixedAlignmentEnabled, viewModel)
            
            withContext(Dispatchers.Main) {
                isSettingWallpaper = false
                currentEditorMode = EditorMode.NONE
                Toast.makeText(context, "Wallpaper Applied", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    SharedTransitionLayout {
        AnimatedContent(
            targetState = currentEditorMode,
            transitionSpec = {
                if (targetState == EditorMode.IMMERSIVE || initialState == EditorMode.IMMERSIVE) {
                    (fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.85f, animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))) togetherWith 
                    (fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.85f, animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)))
                } else {
                    fadeIn(spring(dampingRatio = Spring.DampingRatioNoBouncy)) togetherWith fadeOut(spring(dampingRatio = Spring.DampingRatioNoBouncy))
                }
            },
            label = "StudioNavigation"
        ) { mode ->
        
            val sharedModifier = if (currentWallpaper != null) Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "card_${currentWallpaper.id}"),
                animatedVisibilityScope = this@AnimatedContent
            ) else Modifier

            when (mode) {
                EditorMode.IMMERSIVE -> {
                    if (currentWallpaper != null) ImmersivePreview(wallpaper = currentWallpaper, viewModel = viewModel, sharedElementModifier = sharedModifier, onBack = { currentEditorMode = EditorMode.NONE })
                }
                EditorMode.CLOCK -> {
                    if (currentWallpaper != null) ClockStudio(wallpaper = currentWallpaper, viewModel = viewModel, sharedElementModifier = sharedModifier, onBack = { currentEditorMode = EditorMode.NONE }, onApplyRequested = { dest -> applyWallpaperAction(currentWallpaper, dest, false) })
                }
                EditorMode.EFFECTS -> {
                    if (currentWallpaper != null) EffectsStudio(wallpaper = currentWallpaper, viewModel = viewModel, sharedElementModifier = sharedModifier, onBack = { currentEditorMode = EditorMode.NONE }, onApplyRequested = { applyWallpaperAction(currentWallpaper, WallpaperDestinations.FLAG_BOTH, true) })
                }
                EditorMode.NONE -> {
                    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { paddingValues ->
                        
                        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                            if (viewModel.isAmbientBlurEnabled && currentWallpaper != null) {
                                AnimatedContent(targetState = currentWallpaper, transitionSpec = { fadeIn(tween(1000)) togetherWith fadeOut(tween(1000)) }, label = "ambient_background") { wp ->
                                    AsyncWallpaperImage(wallpaper = wp, contentDescription = null, viewModel = viewModel, modifier = Modifier.fillMaxSize().blur(80.dp), contentScale = ContentScale.Crop, allowMagic = false)
                                }
                                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)))
                            }
                            
                            Column(modifier = Modifier.fillMaxSize().padding(paddingValues).statusBarsPadding()) {
                                
                                Spacer(Modifier.height(16.dp))
                                val metrics = context.resources.displayMetrics
                                
                                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
                                    if (!isLoading && wallpapers.isEmpty()) {
                                        Text("No Wallpapers Found.", modifier = Modifier.align(Alignment.Center))
                                    } else if (wallpapers.isNotEmpty()) {
                                        HorizontalPager(state = mainPagerState, modifier = Modifier.fillMaxSize()) { pageIndex ->
                                            val wp = wallpapers[pageIndex]
                                            val pageOffsetRaw = (mainPagerState.currentPage - pageIndex) + mainPagerState.currentPageOffsetFraction
                                            val pageOffset = pageOffsetRaw.absoluteValue.coerceIn(0f, 1f)
                                            val scale = lerp(0.85f, 1f, 1f - pageOffset)
                                            val alpha = lerp(0.4f, 1f, 1f - pageOffset)
                                                                            val interactionSource = remember { MutableInteractionSource() }
                                            val isPressed by interactionSource.collectIsPressedAsState()
                                            
                                            val bouncySpring = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                                            val dpSpring = spring<androidx.compose.ui.unit.Dp>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                                            
                                            val cardScale by animateFloatAsState(targetValue = if (isPressed) 0.94f else 1f, animationSpec = bouncySpring, label = "squishScale")
                                            val cornerRadius by animateDpAsState(targetValue = if (isPressed) 28.dp else 40.dp, animationSpec = dpSpring, label = "squishCorner")

                                            Box(
                                                modifier = Modifier.fillMaxSize().padding(vertical = 12.dp).graphicsLayer { 
                                                    this.alpha = alpha
                                                    scaleX = scale * cardScale
                                                    scaleY = scale * cardScale 
                                                    rotationZ = pageOffsetRaw * 4f 
                                                }, 
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Card(
                                                    modifier = Modifier.fillMaxHeight().aspectRatio(metrics.widthPixels.toFloat() / metrics.heightPixels.toFloat(), matchHeightConstraintsFirst = true)
                                                        .let { if (wp.id == currentWallpaper?.id) it.then(sharedModifier) else it }
                                                        .combinedClickable(
                                                            interactionSource = interactionSource, indication = null,
                                                            onClick = { currentEditorMode = EditorMode.IMMERSIVE },
                                                            onLongClick = { if (viewModel.isHapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
                                                        ), 
                                                    shape = RoundedCornerShape(cornerRadius), 
                                                    elevation = CardDefaults.cardElevation(if(isPressed) 8.dp else 2.dp)
                                                ) {
                                                    AsyncWallpaperImage(
                                                        wallpaper = wp, contentDescription = null, viewModel = viewModel, 
                                                        modifier = Modifier.fillMaxSize().graphicsLayer {
                                                            translationX = pageOffsetRaw * 150f 
                                                            scaleX = 1.15f 
                                                            scaleY = 1.15f
                                                        }, 
                                                        allowMagic = false
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                Box(contentAlignment = Alignment.BottomCenter) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        androidx.compose.animation.AnimatedVisibility(visible = !isDockExpanded, enter = fadeIn(tween(300)), exit = fadeOut(tween(200))) {
                                            Box(modifier = Modifier.padding(bottom = 8.dp)) { FastScrollIndicator(pagerState = mainPagerState, onDragStartHaptics = { onHaptics(); viewModel.dismissFastScrollGuide() }, onPageChangeHaptics = onHaptics) }
                                        }
                                        ConnectedWallpaperActions(
                                            currentWallpaper = currentWallpaper, viewModel = viewModel, 
                                            isDockExpanded = isDockExpanded, onDockExpandedChange = { isDockExpanded = it },
                                            isFloating = viewModel.isFloatingDockEnabled,
                                            onSetWallpaperClick = { if (currentWallpaper != null) { wallpaperToApplyState = currentWallpaper; showDestinationSheet = true } },
                                            onMagicClick = { viewModel.setMagicShapeEnabled(true); currentEditorMode = EditorMode.EFFECTS },
                                            onAnimationClick = { viewModel.setAnimationEnabled(true); currentEditorMode = EditorMode.EFFECTS },
                                            onFilamentClick = { viewModel.setFilamentEnabled(true); currentEditorMode = EditorMode.EFFECTS },
                                            onLockscreenClick = { viewModel.isLockscreenClockPreviewVisible = true; currentEditorMode = EditorMode.CLOCK },
                                            onAddClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                            onSettingsClick = { showSettingsPanel = true },
                                            carouselContent = { HeroCarouselWithIndicator(filteredWallpapers = wallpapers, pagerState = mainPagerState, carouselState = carouselState, onWallpaperClick = { i: Int -> scope.launch { mainPagerState.animateScrollToPage(i) }; onHaptics() }, viewModel = viewModel) },
                                            linearBarContent = { FastScrollIndicator(pagerState = mainPagerState, onDragStartHaptics = { onHaptics(); viewModel.dismissFastScrollGuide() }, onPageChangeHaptics = onHaptics) }
                                        )
                                    }
                                }
                            }
                        }

                        if (showSettingsPanel) SettingsSheet(viewModel = viewModel, currentWallpaper = currentWallpaper, onDismiss = { showSettingsPanel = false })

                        if (showDestinationSheet && wallpaperToApplyState != null) {
                            val wallpaperToApply = wallpaperToApplyState!!
                            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                            AmbientBottomSheet(onDismissRequest = { showDestinationSheet = false; wallpaperToApplyState = null }, sheetState = sheetState, viewModel = viewModel, currentWallpaper = currentWallpaper) {
                                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).navigationBarsPadding()) {
                                    StaggeredRow(0) { Text("Apply Wallpaper To", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 24.dp)) }
                                    
                                    val applyAction: (Int) -> Unit = { dest ->
                                        isSettingWallpaper = true
                                        viewModel.setMagicShapeEnabled(false); viewModel.setAnimationEnabled(false); viewModel.setFilamentEnabled(false)
                                        scope.launch {
                                            setDeviceWallpaper(context, wallpaperToApply, dest, viewModel.isFixedAlignmentEnabled, viewModel)
                                            withContext(Dispatchers.Main) { showDestinationSheet = false; isSettingWallpaper = false; wallpaperToApplyState = null; viewModel.isLockscreenClockPreviewVisible = false; Toast.makeText(context, "Wallpaper Applied", Toast.LENGTH_SHORT).show() }
                                        }
                                    }

                                    StaggeredRow(1) { DestinationButton(Icons.Default.PhotoLibrary, "Home & Lock Screens", "Set everywhere", isSettingWallpaper) { applyAction(WallpaperDestinations.FLAG_BOTH) } }
                                    Spacer(Modifier.height(12.dp))
                                    StaggeredRow(2) { DestinationButton(Icons.Default.PhotoLibrary, "Home Screen Only", "Set on home screen", isSettingWallpaper) { applyAction(WallpaperDestinations.FLAG_HOME_SCREEN) } }
                                    Spacer(Modifier.height(12.dp))
                                    StaggeredRow(3) { DestinationButton(Icons.Default.Lock, "Lock Screen Only", "Set on lock screen", isSettingWallpaper) { applyAction(WallpaperDestinations.FLAG_LOCK_SCREEN) } }
                                    if (isEffectActive || isClockActive) {
                                        Spacer(Modifier.height(12.dp))
                                        StaggeredRow(4) { 
                                            DestinationButton(Icons.Default.AutoAwesome, "Live Wallpaper", "Animated interactive wallpaper", isSettingWallpaper) {
                                                isSettingWallpaper = true
                                                scope.launch {
                                                    setLiveWallpaper(context, wallpaperToApply, viewModel)
                                                    withContext(Dispatchers.Main) { showDestinationSheet = false; isSettingWallpaper = false; wallpaperToApplyState = null; viewModel.setMagicShapeEnabled(false); viewModel.setAnimationEnabled(false); viewModel.setFilamentEnabled(false); viewModel.isLockscreenClockPreviewVisible = false; Toast.makeText(context, "Live Wallpaper Applied", Toast.LENGTH_SHORT).show() }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isSettingWallpaper) LoadingOverlay(title = "Applying...")
    val cw = wallpapers.getOrNull(mainPagerState.currentPage)
    if (cw != null) AppUpdateHandler(viewModel = viewModel, currentWallpaper = cw)
}
