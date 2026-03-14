package com.android.CaveArt

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroCarouselWithIndicator(
    filteredWallpapers: List<Wallpaper>,
    pagerState: PagerState,
    carouselState: CarouselState,
    isCarouselVisible: Boolean,
    onWallpaperClick: (Int) -> Unit,
    onHaptics: () -> Unit,
    viewModel: WallpaperViewModel
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
    	
        AnimatedVisibility(
            visible = isCarouselVisible,
            enter = expandVertically(expandFrom = Alignment.Bottom, animationSpec = tween(400, easing = FastOutSlowInEasing)) + 
                    scaleIn(transformOrigin = TransformOrigin(0.5f, 1f), animationSpec = tween(400, easing = FastOutSlowInEasing)) + 
                    fadeIn(tween(300)),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom, animationSpec = tween(400, easing = FastOutSlowInEasing)) + 
                   scaleOut(transformOrigin = TransformOrigin(0.5f, 1f), animationSpec = tween(400, easing = FastOutSlowInEasing)) + 
                   fadeOut(tween(250))
        ) {
            HorizontalMultiBrowseCarousel(
                state = carouselState,
                preferredItemWidth = 140.dp, 
                itemSpacing = 12.dp, 
                contentPadding = PaddingValues(horizontal = 0.dp), 
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 24.dp) 
                    .height(190.dp)
            ) { i ->
                AsyncWallpaperImage(
                    wallpaper = filteredWallpapers[i],
                    contentDescription = null,
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxSize()
                        .maskClip(MaterialTheme.shapes.extraLarge)
                        .clickable { onWallpaperClick(i) },
                    allowMagic = false
                )
            }
        }
        
        AnimatedVisibility(
            visible = viewModel.showFastScrollGuide && pagerState.pageCount > 1 && isCarouselVisible,
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(tween(500)),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(tween(300))
        ) {
            
            val infiniteTransition = rememberInfiniteTransition(label = "drag_guide")
            val dragX by infiniteTransition.animateFloat(
                initialValue = -12f,
                targetValue = 12f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "drag_x"
            )

            Surface(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .clickable { viewModel.dismissFastScrollGuide() },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.offset(x = dragX.dp).size(20.dp)
                    )
                    Spacer(Modifier.width(20.dp))
                    Text(
                        "Drag to fast scroll",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp).clickable { viewModel.dismissFastScrollGuide() }
                    )
                }
            }
        }
        
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 12.dp), 
            contentAlignment = Alignment.Center
        ) {
            FastScrollIndicator(
                pagerState = pagerState,
                onDragStartHaptics = { 
                    onHaptics()
                    viewModel.dismissFastScrollGuide() 
                },
                onPageChangeHaptics = onHaptics,
                inactiveColor = MaterialTheme.colorScheme.onSurface,
                activeColor = MaterialTheme.colorScheme.primary,
                trackWidth = 200.dp
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FastScrollIndicator(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface,
    trackWidth: Dp = 200.dp,
    onDragStartHaptics: () -> Unit,
    onPageChangeHaptics: () -> Unit
) {
    if (pagerState.pageCount <= 1) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var isDragging by remember { mutableStateOf(false) }
    var rawDragOffset by remember { mutableFloatStateOf(0f) }
    var initialPage by remember { mutableIntStateOf(0) }
    val isPagerScrolling = pagerState.isScrollInProgress && !isDragging
    val thumbWidth by animateDpAsState(
        targetValue = when {
            isDragging -> 40.dp
            isPagerScrolling -> 28.dp
            else -> 20.dp
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "thumbWidth"
    )
    
    val thumbHeight by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 6.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "thumbHeight"
    )
    
    val trackAlpha by animateFloatAsState(
        targetValue = if (isDragging || isPagerScrolling) 0.15f else 0f,
        animationSpec = tween(400),
        label = "trackAlpha"
    )
    
    val pagerOffsetPx = with(density) { (pagerState.currentPageOffsetFraction * 40.dp.toPx()) }
    
    val animatedDragOffset by animateFloatAsState(
        targetValue = if (isDragging) rawDragOffset else pagerOffsetPx,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "dragSpring"
    )

    val pageCount = pagerState.pageCount
    val maxDragPx = with(density) { ((trackWidth / 2) - 20.dp).toPx() } 

    val fastScrollModifier = Modifier.pointerInput(pageCount) {
        detectDragGestures(
            onDragStart = { 
                isDragging = true
                initialPage = pagerState.currentPage
                rawDragOffset = 0f
                onDragStartHaptics()
            },
            onDrag = { change, dragAmount ->
                change.consume()
                
                rawDragOffset = (rawDragOffset + dragAmount.x).coerceIn(-maxDragPx, maxDragPx)
                
                val dragFraction = rawDragOffset / maxDragPx
                
                val calculatedTargetPage = if (dragFraction > 0f) {
                    
                    initialPage + dragFraction * (pageCount - 1 - initialPage)
                } else {
                    
                    initialPage + dragFraction * initialPage
                }
                
                val targetPage = calculatedTargetPage.roundToInt().coerceIn(0, pageCount - 1)

                if (targetPage != pagerState.currentPage) {
                    onPageChangeHaptics()
                    scope.launch { pagerState.scrollToPage(targetPage) }
                }
            },
            onDragEnd = { 
                isDragging = false
                rawDragOffset = 0f
            },
            onDragCancel = { 
                isDragging = false
                rawDragOffset = 0f
            }
        )
    }
        
    Box(
        modifier = modifier
            .width(trackWidth)
            .height(48.dp)
            .then(fastScrollModifier),
        contentAlignment = Alignment.Center 
    ) {
        
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = trackAlpha }) {
            val lineThickness = 2.dp.toPx()
            val y = size.height / 2
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.1f to inactiveColor,
                    0.9f to inactiveColor,
                    1f to Color.Transparent
                ),
                topLeft = Offset(0f, y - lineThickness / 2),
                size = Size(size.width, lineThickness),
                cornerRadius = CornerRadius(lineThickness / 2)
            )
        }
        
        Box(
            modifier = Modifier
                .offset(x = with(density) { animatedDragOffset.toDp() })
                .size(width = thumbWidth, height = thumbHeight)
                .shadow(
                    elevation = if (isDragging) 8.dp else 2.dp,
                    shape = CircleShape, 
                    spotColor = activeColor,
                    ambientColor = activeColor
                )
                .background(activeColor, CircleShape)
        )
    }
}
