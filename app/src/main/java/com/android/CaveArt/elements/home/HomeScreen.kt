package com.android.CaveArt

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

enum class EditorMode { 
    NONE, 
    CLOCK, 
    MAGIC_SHAPE, 
    ANIMATION, 
    FILAMENT_3D, 
    IMMERSIVE, 
    SETTINGS 
}

enum class TopScreenTab(val label: String) {
    WALLPAPER("Wallpaper"),
    CLOCK_SCREEN("Clock Screen")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SwipableWallpaperScreen(viewModel: WallpaperViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val darkTheme = isSystemInDarkTheme()
    val safeContext = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }
    }
    val settingsPrefs = remember(safeContext) {
        safeContext.getSharedPreferences("cave_art_settings", Context.MODE_PRIVATE)
    }

    var isCarouselPinned by remember {
        mutableStateOf(settingsPrefs.getBoolean("carousel_pinned", false))
    }
    var isDockExpanded by rememberSaveable {
        mutableStateOf(isCarouselPinned)
    }

    val updateCarouselPinned: (Boolean) -> Unit = { pinned ->
        isCarouselPinned = pinned
        isDockExpanded = pinned
        settingsPrefs.edit().putBoolean("carousel_pinned", pinned).apply()
    }

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

    val wallpapers = viewModel.allWallpapers
    val isLoading = viewModel.isLoading

    val mainPagerState = rememberPagerState(pageCount = { wallpapers.size })
    val carouselState = rememberCarouselState { wallpapers.size }

    var currentEditorMode by remember { mutableStateOf(EditorMode.NONE) }
    var activeTab by remember { mutableStateOf(TopScreenTab.WALLPAPER) }

    LaunchedEffect(currentEditorMode) {
        if (currentEditorMode != EditorMode.CLOCK) {
            activeTab = TopScreenTab.WALLPAPER
        }
    }

    val currentWallpaper = wallpapers.getOrNull(mainPagerState.currentPage)
    val isAnyEditorActive = currentEditorMode != EditorMode.NONE

    val onHaptics = {
        if (viewModel.isHapticsEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    BackHandler(enabled = isAnyEditorActive) { currentEditorMode = EditorMode.NONE }
    BackHandler(enabled = (isDockExpanded || isCarouselPinned) && !isAnyEditorActive) {
        if (!isCarouselPinned) {
            isDockExpanded = false
        } else {
            updateCarouselPinned(false)
        }
    }

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
    
    val expressiveBoundsSpring = spring<androidx.compose.ui.geometry.Rect>(
        dampingRatio = 0.78f,
        stiffness = 380f
    )

    SharedTransitionLayout {
        AnimatedContent(
            targetState = currentEditorMode,
            transitionSpec = {
                (fadeIn(spring(dampingRatio = 0.8f, stiffness = 400f)) + scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f))) togetherWith
                (fadeOut(tween(160)) + scaleOut(targetScale = 0.92f))
            },
            label = "StudioNavigation"
        ) { mode ->

            val sharedModifier = if (currentWallpaper != null) Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "card_${currentWallpaper.id}"),
                animatedVisibilityScope = this@AnimatedContent,
                boundsTransform = { _, _ -> expressiveBoundsSpring }
            ) else Modifier

            when (mode) {
                EditorMode.IMMERSIVE -> {
                    if (currentWallpaper != null) {
                        ImmersivePreview(
                            wallpaper = currentWallpaper,
                            viewModel = viewModel,
                            sharedElementModifier = sharedModifier,
                            onBack = { currentEditorMode = EditorMode.NONE }
                        )
                    }
                }
                EditorMode.CLOCK -> {
                    if (currentWallpaper != null) {
                        ClockStudio(
                            wallpaper = currentWallpaper,
                            viewModel = viewModel,
                            sharedElementModifier = sharedModifier,
                            onBack = { currentEditorMode = EditorMode.NONE },
                            onApplyRequested = { dest -> applyWallpaperAction(currentWallpaper, dest, false) }
                        )
                    }
                }
                EditorMode.MAGIC_SHAPE -> {
                    if (currentWallpaper != null) {
                        MagicShapeStudio(
                            wallpaper = currentWallpaper,
                            viewModel = viewModel,
                            sharedElementModifier = sharedModifier,
                            onBack = { currentEditorMode = EditorMode.NONE },
                            onApplyRequested = { applyWallpaperAction(currentWallpaper, WallpaperDestinations.FLAG_BOTH, true) }
                        )
                    }
                }
                EditorMode.ANIMATION -> {
                    if (currentWallpaper != null) {
                        AnimationStudio(
                            wallpaper = currentWallpaper,
                            viewModel = viewModel,
                            sharedElementModifier = sharedModifier,
                            onBack = { currentEditorMode = EditorMode.NONE },
                            onApplyRequested = { applyWallpaperAction(currentWallpaper, WallpaperDestinations.FLAG_BOTH, true) }
                        )
                    }
                }
                EditorMode.FILAMENT_3D -> {
                    Filament3DStudio(
                        wallpaper = currentWallpaper,
                        viewModel = viewModel,
                        onBack = { currentEditorMode = EditorMode.NONE },
                        onApplyRequested = {
                            applyWallpaperAction(currentWallpaper ?: return@Filament3DStudio, WallpaperDestinations.FLAG_BOTH, true)
                        }
                    )
                }
                EditorMode.SETTINGS -> {
                    SettingsScreen(
                        viewModel = viewModel,
                        currentWallpaper = currentWallpaper,
                        onBack = { currentEditorMode = EditorMode.NONE }
                    )
                }
                EditorMode.NONE -> {
                    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { paddingValues ->
                        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                            if (viewModel.isAmbientBlurEnabled && currentWallpaper != null) {
                                AnimatedContent(
                                    targetState = currentWallpaper,
                                    transitionSpec = { fadeIn(tween(800)) togetherWith fadeOut(tween(800)) },
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
                                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)))
                            }

                            Column(modifier = Modifier.fillMaxSize().padding(paddingValues).statusBarsPadding()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                                    ExpressiveSegmentedPill<TopScreenTab>(
                                        items = TopScreenTab.values().toList(),
                                        selectedItem = activeTab,
                                        onItemSelected = { selected: TopScreenTab ->
                                            activeTab = selected
                                            if (selected == TopScreenTab.CLOCK_SCREEN) {
                                                viewModel.isLockscreenClockPreviewVisible = true
                                                currentEditorMode = EditorMode.CLOCK
                                            }
                                        },
                                        labelProvider = { tab: TopScreenTab -> tab.label },
                                        iconProvider = null,
                                        modifier = Modifier.width(280.dp)
                                    )
                                }

                                val metrics = context.resources.displayMetrics

                                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
                                    if (!isLoading && wallpapers.isEmpty()) {
                                        Text("No Wallpapers Found.", modifier = Modifier.align(Alignment.Center))
                                    } else if (wallpapers.isNotEmpty()) {
                                        HorizontalPager(state = mainPagerState, modifier = Modifier.fillMaxSize()) { pageIndex ->
                                            val wp = wallpapers[pageIndex]
                                            val pageOffsetRaw = (mainPagerState.currentPage - pageIndex) + mainPagerState.currentPageOffsetFraction
                                            val pageOffset = pageOffsetRaw.absoluteValue.coerceIn(0f, 1f)
                                            val scale = lerp(0.86f, 1f, 1f - pageOffset)
                                            val alpha = lerp(0.45f, 1f, 1f - pageOffset)

                                            val interactionSource = remember { MutableInteractionSource() }
                                            val isPressed by interactionSource.collectIsPressedAsState()

                                            val cardScale by animateFloatAsState(
                                                targetValue = if (isPressed) 0.94f else 1f,
                                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                                label = "squishScale"
                                            )

                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(vertical = 12.dp)
                                                    .graphicsLayer {
                                                        this.alpha = alpha
                                                        scaleX = scale * cardScale
                                                        scaleY = scale * cardScale
                                                        rotationZ = pageOffsetRaw * 3f
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .aspectRatio(metrics.widthPixels.toFloat() / metrics.heightPixels.toFloat(), matchHeightConstraintsFirst = true)
                                                        .shadow(
                                                            elevation = if (pageIndex == mainPagerState.currentPage) 24.dp else 4.dp,
                                                            shape = MaterialTheme.shapes.extraLarge,
                                                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                                        )
                                                        .let { if (wp.id == currentWallpaper?.id) it.then(sharedModifier) else it }
                                                        .combinedClickable(
                                                            interactionSource = interactionSource,
                                                            indication = null,
                                                            onClick = { currentEditorMode = EditorMode.IMMERSIVE },
                                                            onLongClick = { onHaptics() }
                                                        ),
                                                    shape = MaterialTheme.shapes.extraLarge,
                                                    colors = CardDefaults.cardColors(containerColor = Color.Black)
                                                ) {
                                                    AsyncWallpaperImage(
                                                        wallpaper = wp,
                                                        contentDescription = null,
                                                        viewModel = viewModel,
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .graphicsLayer {
                                                                translationX = pageOffsetRaw * 120f
                                                                scaleX = 1.12f
                                                                scaleY = 1.12f
                                                            },
                                                        allowMagic = false
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                Box(contentAlignment = Alignment.BottomCenter) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        AnimatedVisibility(
                                            visible = !isDockExpanded && !isCarouselPinned,
                                            enter = fadeIn(tween(250)) + slideInVertically { it / 2 },
                                            exit = fadeOut(tween(200)) + slideOutVertically { it / 2 }
                                        ) {
                                            Box(modifier = Modifier.padding(bottom = 8.dp)) {
                                                FastScrollIndicator(
                                                    pagerState = mainPagerState,
                                                    onDragStartHaptics = { onHaptics(); viewModel.dismissFastScrollGuide() },
                                                    onPageChangeHaptics = onHaptics,
                                                    viewModel = viewModel
                                                )
                                            }
                                        }

                                        ConnectedWallpaperActions(
                                            currentWallpaper = currentWallpaper,
                                            viewModel = viewModel,
                                            isDockExpanded = isDockExpanded || isCarouselPinned,
                                            isFloating = viewModel.isFloatingDockEnabled,
                                            isPinned = isCarouselPinned,
                                            onDockExpandedChange = { expanded ->
                                                if (!isCarouselPinned) {
                                                    isDockExpanded = expanded
                                                }
                                            },
                                            onSetWallpaperClick = {
                                                if (currentWallpaper != null) {
                                                    wallpaperToApplyState = currentWallpaper
                                                    showDestinationSheet = true
                                                }
                                            },
                                            onMagicClick = { 
                                                viewModel.setMagicShapeEnabled(true)
                                                currentEditorMode = EditorMode.MAGIC_SHAPE 
                                            },
                                            onAnimationClick = { 
                                                viewModel.setAnimationEnabled(true)
                                                currentEditorMode = EditorMode.ANIMATION 
                                            },
                                            onFilamentClick = { 
                                                currentEditorMode = EditorMode.FILAMENT_3D
                                            },
                                            onAddClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                            onSettingsClick = { currentEditorMode = EditorMode.SETTINGS },
                                            carouselContent = {
                                                HeroCarouselWithIndicator(
                                                    filteredWallpapers = wallpapers,
                                                    pagerState = mainPagerState,
                                                    carouselState = carouselState,
                                                    onWallpaperClick = { i: Int -> scope.launch { mainPagerState.animateScrollToPage(i) }; onHaptics() },
                                                    onAddWallpaperClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                                    onPinToggleClick = {
                                                        val newPin = !isCarouselPinned
                                                        updateCarouselPinned(newPin)
                                                        onHaptics()
                                                    },
                                                    isPinned = isCarouselPinned,
                                                    viewModel = viewModel
                                                )
                                            },
                                            linearBarContent = {
                                                FastScrollIndicator(
                                                    pagerState = mainPagerState,
                                                    onDragStartHaptics = { onHaptics(); viewModel.dismissFastScrollGuide() },
                                                    onPageChangeHaptics = onHaptics,
                                                    viewModel = viewModel
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (showDestinationSheet && wallpaperToApplyState != null) {
                            val wallpaperToApply = wallpaperToApplyState!!
                            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                            AmbientBottomSheet(
                                onDismissRequest = { showDestinationSheet = false; wallpaperToApplyState = null },
                                sheetState = sheetState,
                                viewModel = viewModel,
                                currentWallpaper = currentWallpaper
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).navigationBarsPadding()) {
                                    StaggeredRow(0) { Text("Apply Wallpaper", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 20.dp)) }

                                    val applyAction: (Int) -> Unit = { dest ->
                                        isSettingWallpaper = true
                                        viewModel.setMagicShapeEnabled(false); viewModel.setAnimationEnabled(false); viewModel.setFilamentEnabled(false)
                                        scope.launch {
                                            setDeviceWallpaper(context, wallpaperToApply, dest, viewModel.isFixedAlignmentEnabled, viewModel)
                                            withContext(Dispatchers.Main) {
                                                showDestinationSheet = false
                                                isSettingWallpaper = false
                                                wallpaperToApplyState = null
                                                viewModel.isLockscreenClockPreviewVisible = false
                                                Toast.makeText(context, "Wallpaper Applied", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }

                                    StaggeredRow(1) { DestinationButton(Icons.Default.PhotoLibrary, "Home & Lock Screens", "Apply everywhere", isSettingWallpaper) { applyAction(WallpaperDestinations.FLAG_BOTH) } }
                                    Spacer(Modifier.height(12.dp))
                                    StaggeredRow(2) { DestinationButton(Icons.Default.PhotoLibrary, "Home Screen Only", "Apply to home", isSettingWallpaper) { applyAction(WallpaperDestinations.FLAG_HOME_SCREEN) } }
                                    Spacer(Modifier.height(12.dp))
                                    StaggeredRow(3) { DestinationButton(Icons.Default.Lock, "Lock Screen Only", "Apply to lock", isSettingWallpaper) { applyAction(WallpaperDestinations.FLAG_LOCK_SCREEN) } }
                                    Spacer(Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    AnimatedVisibility(
        visible = isSettingWallpaper,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.88f, animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f)),
        exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.88f)
    ) {
        LoadingOverlay(title = "Applying...")
    }
}
