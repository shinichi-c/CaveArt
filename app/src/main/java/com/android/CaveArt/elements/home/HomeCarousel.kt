package com.android.CaveArt

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroCarouselWithIndicator(
    filteredWallpapers: List<Wallpaper>, 
    pagerState: PagerState, 
    carouselState: CarouselState, 
    onWallpaperClick: (Int) -> Unit, 
    viewModel: WallpaperViewModel
) {
    val context = LocalContext.current
    val metrics = remember { context.resources.displayMetrics }
    val screenAspectRatio = remember { metrics.widthPixels.toFloat() / metrics.heightPixels.toFloat() }
    val preferredItemWidth = 85.dp
    val computedHeight = preferredItemWidth / screenAspectRatio

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        
        HorizontalMultiBrowseCarousel(
            state = carouselState, 
            preferredItemWidth = preferredItemWidth, 
            itemSpacing = 8.dp, 
            contentPadding = PaddingValues(horizontal = 0.dp), 
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp).height(computedHeight)
        ) { index ->
            AsyncWallpaperImage(
                wallpaper = filteredWallpapers[index], 
                contentDescription = null, 
                viewModel = viewModel, 
                modifier = Modifier.fillMaxSize().maskClip(MaterialTheme.shapes.extraLarge).clickable { onWallpaperClick(index) }, 
                allowMagic = false
            )
        }
        
        AnimatedVisibility(
            visible = viewModel.showFastScrollGuide && pagerState.pageCount > 1, 
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(tween(500)), 
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(tween(300))
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "drag_guide")
            val dragX by infiniteTransition.animateFloat(initialValue = -12f, targetValue = 12f, animationSpec = infiniteRepeatable(animation = tween(1200, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "drag_x")
            Surface(modifier = Modifier.padding(bottom = 6.dp).clickable { viewModel.dismissFastScrollGuide() }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary, shadowElevation = 4.dp) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TouchApp, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.offset(x = dragX.dp).size(20.dp))
                    Spacer(Modifier.width(20.dp))
                    Text("Drag to fast scroll", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.Default.Close, "Dismiss", tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f), modifier = Modifier.size(16.dp).clickable { viewModel.dismissFastScrollGuide() })
                }
            }
        }
    }
}

@Composable
fun FastScrollIndicator(
    pagerState: PagerState, 
    modifier: Modifier = Modifier, 
    activeColor: Color = MaterialTheme.colorScheme.primary, 
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface, 
    trackWidth: Dp = 200.dp, 
    onDragStartHaptics: () -> Unit, 
    onPageChangeHaptics: () -> Unit,
    viewModel: WallpaperViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val pageCount = pagerState.pageCount
    if (pageCount <= 1) return
    
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var isDragging by remember { mutableStateOf(false) }
    var rawDragOffset by remember { mutableFloatStateOf(0f) }
    var initialPage by remember { mutableIntStateOf(0) }
    
    val isPagerScrolling = pagerState.isScrollInProgress && !isDragging
    
    val pagerOffsetPx = with(density) { (pagerState.currentPageOffsetFraction * 40.dp.toPx()) }
    val animatedDragOffset by animateFloatAsState(
        targetValue = if (isDragging) rawDragOffset else pagerOffsetPx, 
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), 
        label = "dragSpring"
    )

    val maxDragPx = with(density) { ((trackWidth / 2) - 20.dp).toPx() } 
    val trackAlpha by animateFloatAsState(targetValue = if (isDragging || isPagerScrolling) 0.25f else 0.1f, animationSpec = tween(400), label = "trackAlpha")
    
    val fastScrollModifier = Modifier.pointerInput(pageCount) {
        detectHorizontalDragGestures(
            onDragStart = { 
                isDragging = true
                initialPage = pagerState.currentPage
                rawDragOffset = 0f
                onDragStartHaptics() 
            },
            onDragEnd = { isDragging = false; rawDragOffset = 0f },
            onDragCancel = { isDragging = false; rawDragOffset = 0f },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                rawDragOffset = (rawDragOffset + dragAmount).coerceIn(-maxDragPx, maxDragPx)
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
            }
        )
    }
    
    Box(modifier = modifier.width(trackWidth).height(32.dp).then(fastScrollModifier), contentAlignment = Alignment.Center) {
        
        when (viewModel.scrollStyle) {
            0 -> {
                val thumbWidth by animateDpAsState(if (isDragging) 40.dp else 20.dp, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "w")
                val thumbHeight by animateDpAsState(if (isDragging) 8.dp else 6.dp, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "h")
                Box(modifier = Modifier.offset(x = with(density) { animatedDragOffset.toDp() }).size(thumbWidth, thumbHeight).shadow(elevation = if (isDragging) 8.dp else 2.dp, shape = CircleShape, ambientColor = activeColor, spotColor = activeColor).background(activeColor, CircleShape))
            }
            1 -> {
                val thumbWidth by animateDpAsState(if (isDragging) 40.dp else 24.dp, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "w")
                val thumbHeight by animateDpAsState(if (isDragging) 12.dp else 8.dp, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "h")
                Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = trackAlpha }) {
                    drawRoundRect(color = inactiveColor, topLeft = Offset(0f, size.height / 2f - 6.dp.toPx()), size = Size(size.width, 12.dp.toPx()), cornerRadius = CornerRadius(6.dp.toPx()))
                }
                Box(modifier = Modifier.offset(x = with(density) { animatedDragOffset.toDp() }).size(thumbWidth, thumbHeight).shadow(elevation = if (isDragging) 8.dp else 2.dp, shape = CircleShape, ambientColor = activeColor, spotColor = activeColor).background(activeColor, CircleShape))
            }
            2 -> {
                val stretch by animateFloatAsState(if (isDragging) abs(rawDragOffset) * 0.12f else 0f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium), label = "stretch")
                Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = trackAlpha }) {
                    drawLine(color = inactiveColor, start = Offset(0f, size.height / 2f), end = Offset(size.width, size.height / 2f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
                val thumbW = 16.dp + with(density) { stretch.toDp() }
                Box(modifier = Modifier.offset(x = with(density) { animatedDragOffset.toDp() }).size(thumbW, 10.dp).shadow(elevation = if (isDragging) 8.dp else 2.dp, shape = CircleShape, ambientColor = activeColor, spotColor = activeColor).background(activeColor, CircleShape))
            }
            3 -> {
                Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = trackAlpha }) {
                    drawLine(color = inactiveColor, start = Offset(0f, size.height / 2f), end = Offset(size.width, size.height / 2f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
                Box(modifier = Modifier.offset(x = with(density) { animatedDragOffset.toDp() }).size(16.dp, 16.dp).shadow(elevation = if (isDragging) 8.dp else 2.dp, shape = CircleShape, ambientColor = activeColor, spotColor = activeColor).background(activeColor, CircleShape))
                
                androidx.compose.animation.AnimatedVisibility(
                    visible = isDragging, enter = fadeIn() + slideInVertically { 20 }, exit = fadeOut() + slideOutVertically { 20 },
                    modifier = Modifier.offset(x = with(density) { animatedDragOffset.toDp() }, y = (-28).dp)
                ) {
                    Box(modifier = Modifier.background(activeColor, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("${pagerState.currentPage + 1} / $pageCount", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }
            4 -> {
                val fullWidthPx = with(density) { trackWidth.toPx() }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val y = size.height / 2f
                    val thumbAbsoluteX = (size.width / 2f) + animatedDragOffset
                    for (i in 0 until pageCount) {
                        val dotX = (i.toFloat() / (pageCount - 1)) * fullWidthPx
                        val dist = abs(dotX - thumbAbsoluteX)
                        val pullY = if (dist < 24.dp.toPx()) (24.dp.toPx() - dist) * 0.4f else 0f
                        val isNear = dist < 8.dp.toPx()
                        drawCircle(color = if (isNear) activeColor else inactiveColor.copy(alpha = trackAlpha), radius = if (isNear) 5.dp.toPx() else 2.dp.toPx(), center = Offset(dotX, y - pullY))
                    }
                }
            }
        }
    }
}
