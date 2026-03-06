package com.android.CaveArt

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
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
        
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 12.dp), 
            contentAlignment = Alignment.Center
        ) {
            FastScrollIndicator(
                pagerState = pagerState,
                onDragStartHaptics = onHaptics,
                onPageChangeHaptics = onHaptics,
                inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                activeColor = MaterialTheme.colorScheme.primary
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
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
    defaultTrackWidth: Dp = 80.dp,
    dragTrackWidth: Dp = 200.dp,
    defaultTrackHeight: Dp = 4.dp,
    dragTrackHeight: Dp = 10.dp,
    onDragStartHaptics: () -> Unit,
    onPageChangeHaptics: () -> Unit
) {
    if (pagerState.pageCount <= 1) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var isDragging by remember { mutableStateOf(false) }
    var trackSize by remember { mutableStateOf(IntSize.Zero) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) } 
    
    val currentTrackWidth = if (isDragging) dragTrackWidth else defaultTrackWidth
    val currentTrackHeight = if (isDragging) dragTrackHeight else defaultTrackHeight
    
    val absoluteTransitionFraction = pagerState.currentPageOffsetFraction.absoluteValue
    val transitionFraction = pagerState.currentPageOffsetFraction
    
    val heightIncrease = 2.dp * absoluteTransitionFraction 
    val activeTrackHeight = currentTrackHeight + heightIncrease

    val activeWidth = currentTrackWidth * absoluteTransitionFraction

    val offsetX: Dp = if (transitionFraction < 0) {
        0.dp
    } else {
        currentTrackWidth - activeWidth
    }
    
    val fastScrollModifier = Modifier
        .onSizeChanged { trackSize = it }
        .pointerInput(pagerState.pageCount) {
            detectDragGestures(
                onDragStart = { startOffset: Offset ->
                    isDragging = true
                    dragOffsetX = startOffset.x.coerceIn(0f, trackSize.width.toFloat())
                    onDragStartHaptics() 

                    val trackWidthPx = trackSize.width.toFloat()
                    val pageCount = pagerState.pageCount
                    if (trackWidthPx > 0) {
                        val dragFraction = (startOffset.x / trackWidthPx).coerceIn(0f, 1f)
                        val targetPage = (dragFraction * (pageCount - 1)).roundToInt().coerceIn(0, pageCount - 1)

                        if (targetPage != pagerState.currentPage) {
                            scope.launch {
                                pagerState.scrollToPage(targetPage)
                            }
                        }
                    }
                },
                onDrag = { change, _ ->
                    change.consume() 

                    val trackWidthPx = trackSize.width.toFloat()
                    val newDragX = change.position.x.coerceIn(0f, trackWidthPx)
                    dragOffsetX = newDragX

                    val pageCount = pagerState.pageCount
                    if (trackWidthPx > 0) {
                        val dragFraction = (newDragX / trackWidthPx).coerceIn(0f, 1f)
                        val targetPage = (dragFraction * (pageCount - 1)).roundToInt().coerceIn(0, pageCount - 1)

                        if (targetPage != pagerState.currentPage) {
                            onPageChangeHaptics()
                            scope.launch {
                                pagerState.scrollToPage(targetPage)
                            }
                        }
                    }
                },
                onDragEnd = { 
                    isDragging = false
                    dragOffsetX = 0f
                },
                onDragCancel = {
                    isDragging = false
                    dragOffsetX = 0f
                }
            )
        }
        
    Box(
        modifier = modifier
            .then(fastScrollModifier)
            .width(currentTrackWidth)
            .height(currentTrackHeight)
            .background(inactiveColor, RoundedCornerShape(50))
    ) {
        
        Box(
            modifier = Modifier
                .offset(x = offsetX)
                .width(activeWidth)
                .height(activeTrackHeight)
                .background(activeColor, RoundedCornerShape(50))
                .align(Alignment.CenterStart)
        )
        
        if (isDragging) {
            val dotSize = 12.dp 
            val dotOffsetXDp = with(density) { dragOffsetX.toDp() }
            
            val constrainedDotOffset = dotOffsetXDp.coerceIn(0.dp, currentTrackWidth) - dotSize / 2
            
            Box(
                modifier = Modifier
                    .offset(x = constrainedDotOffset)
                    .size(dotSize)
                    .background(activeColor, CircleShape)
                    .align(Alignment.CenterStart) 
            )
        }
    }
}
