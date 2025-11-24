package com.android.CaveArt

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import com.google.accompanist.systemuicontroller.rememberSystemUiController

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SwipableWallpaperScreen(viewModel: WallpaperViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val darkTheme = isSystemInDarkTheme()

    val window = (view.context as Activity).window
    val systemBarsColor = MaterialTheme.colorScheme.background

    val systemUiController = rememberSystemUiController()

    SideEffect {
        systemUiController.setStatusBarColor(systemBarsColor, darkIcons = !darkTheme)
        systemUiController.setNavigationBarColor(systemBarsColor, darkIcons = !darkTheme)
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

    val mainPagerState = rememberPagerState(
        pageCount = { filteredWallpapers.size }
    )

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

        AnimatedVisibility(
            visible = !isLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
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

                        if (filteredWallpapers.isEmpty()) {
                            Text(
                                "No Wallpapers Found.",
                                modifier = Modifier.align(Alignment.Center),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        } else {

                            HorizontalPager(
                                state = mainPagerState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(400.dp)
                                    .align(Alignment.Center),
                                contentPadding = PaddingValues(horizontal = 80.dp)
                            ) { pageIndex ->
                                val wallpaper = filteredWallpapers[pageIndex]

                                val pageOffset = (
                                        (mainPagerState.currentPage - pageIndex) + mainPagerState.currentPageOffsetFraction
                                        ).absoluteValue.coerceIn(0f, 1f)

                                val scale = lerp(
                                    start = 0.85f,
                                    stop = 1f,
                                    fraction = 1f - pageOffset
                                )

                                val alpha = lerp(
                                    start = 0.6f,
                                    stop = 1f,
                                    fraction = 1f - pageOffset
                                )

                                WallpaperPreviewCard(
                                    wallpaper = wallpaper,
                                    normalPageScale = scale,
                                    normalPageAlpha = alpha,
                                    onClick = { selectedWallpaperIndex = pageIndex },
                                    modifier = Modifier,
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
                                    modifier = Modifier,
                                    defaultTrackWidth = 80.dp,
                                    dragTrackWidth = 200.dp,
                                    defaultTrackHeight = 4.dp,
                                    dragTrackHeight = 10.dp,
                                    onDragStartHaptics = onDragStartHaptics,
                                    onPageChangeHaptics = onPageChangeHaptics
                                )
                            }

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
                                    onSettingsClick = { showSettingsPanel = true }
                                )
                            }
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Select Wallpaper Category",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Divider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .heightIn(max = 300.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
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
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                }
            }
        }


        if (showSettingsPanel) {
            SettingsSheet(
                viewModel = viewModel,
                onDismiss = { showSettingsPanel = false }
            )
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Apply Wallpaper To",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 24.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val applyWallpaperAction: (Int) -> Unit = { destination ->
                        isSettingWallpaper = true
                        scope.launch {
                            setDeviceWallpaper(
                                context,
                                wallpaperToApply.resourceId,
                                wallpaperToApply.title,
                                destination,
                                isFixedAlignmentEnabled,
                                viewModel
                            )
                            showDestinationSheet = false
                            isSettingWallpaper = false
                            wallpaperToApplyState = null
                        }
                    }

                    DestinationButton(
                        icon = Icons.Default.PhotoLibrary,
                        title = "Home & Lock Screens",
                        subtitle = "Set as both your main and lock screen wallpaper.",
                        isSetting = isSettingWallpaper,
                        onClick = { applyWallpaperAction(WallpaperDestinations.FLAG_BOTH) }
                    )

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    DestinationButton(
                        icon = Icons.Default.PhotoLibrary,
                        title = "Home Screen Only",
                        subtitle = "Set only for your main home screen.",
                        isSetting = isSettingWallpaper,
                        onClick = { applyWallpaperAction(WallpaperDestinations.FLAG_HOME_SCREEN) }
                    )

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    DestinationButton(
                        icon = Icons.Default.Lock,
                        title = "Lock Screen Only",
                        subtitle = "Set only for your device's lock screen.",
                        isSetting = isSettingWallpaper,
                        onClick = { applyWallpaperAction(WallpaperDestinations.FLAG_LOCK_SCREEN) }
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LoadingOverlay(title = "Scanning Wallpapers...")
        }

        AnimatedVisibility(
            visible = isSettingWallpaper && !isLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LoadingOverlay(title = "Setting Wallpaper...")
        }
    }
}


@Composable
fun ConnectedWallpaperActions(
    currentWallpaper: Wallpaper?,
    onSetWallpaperClick: () -> Unit,
    onFilterClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val enabled = currentWallpaper != null
    val buttonHeight = 48.dp
    val iconSize = 20.dp
    val commonButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {

        Button(
            onClick = onSettingsClick,
            modifier = Modifier
                .height(buttonHeight)
                .width(buttonHeight),
            colors = commonButtonColors,
            shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 0.dp, bottomEnd = 0.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
             Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "App Settings",
                modifier = Modifier.size(iconSize)
            )
        }

        Button(
            onClick = onSetWallpaperClick,
            enabled = enabled,
            modifier = Modifier
                .height(buttonHeight),
            colors = commonButtonColors,
            shape = RoundedCornerShape(0.dp)
        ) {
            Icon(Icons.Default.Image, contentDescription = "Set Wallpaper", modifier = Modifier.size(iconSize))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "APPLY",
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.labelLarge
            )
        }

        Button(
            onClick = onFilterClick,
            modifier = Modifier
                .height(buttonHeight)
                .width(buttonHeight),
            colors = commonButtonColors,
            shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 24.dp, bottomEnd = 24.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = "Filter Wallpapers",
                modifier = Modifier.size(iconSize)
            )
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

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(
            durationMillis = 150,
            easing = FastOutSlowInEasing
        ), label = "WallpaperScaleAnimation"
    )

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
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {

        when {
            isDebugMaskEnabled -> {
                DebugMaskImage(
                    resourceId = wallpaper.resourceId,
                    contentDescription = "Debug Mask Preview: ${wallpaper.title}",
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                Image(
                    painter = painterResource(id = wallpaper.resourceId),
                    contentDescription = "Preview: ${wallpaper.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun CategoryChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = title, fontWeight = FontWeight.SemiBold)
        }
    }
}


@Composable
fun DestinationButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isSetting: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = !isSetting,
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        contentPadding = PaddingValues(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.size(18.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(text = subtitle, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (isSetting) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}


@Composable
fun LoadingOverlay(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .padding(32.dp)
                .widthIn(min = 250.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(56.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "This might take a moment.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
