package com.android.CaveArt

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.*
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun HeroCarouselWithIndicator(
    filteredWallpapers: List<Wallpaper>,
    pagerState: PagerState,
    carouselState: CarouselState,
    onWallpaperClick: (Int) -> Unit,
    onAddWallpaperClick: () -> Unit,
    onPinToggleClick: () -> Unit,
    isPinned: Boolean = false,
    viewModel: WallpaperViewModel
) {
    val carouselHeight = 90.dp
    val listState = rememberLazyListState()
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val coroutineScope = rememberCoroutineScope()
    val isFewItems = filteredWallpapers.size < 4
    
    LaunchedEffect(pagerState.currentPage) {
        if (!listState.isScrollInProgress && filteredWallpapers.isNotEmpty()) {
            listState.animateScrollToItem(pagerState.currentPage.coerceIn(0, filteredWallpapers.size - 1))
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isFewItems) {
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(carouselHeight)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                AddWallpaperCarouselCard(
                    onClick = onAddWallpaperClick,
                    modifier = Modifier
                        .width(48.dp)
                        .fillMaxHeight()
                )

                Spacer(Modifier.width(8.dp))

                filteredWallpapers.forEachIndexed { index, wp ->
                    val isSelected = pagerState.currentPage == index
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()

                    val itemScale by animateFloatAsState(
                        targetValue = if (isPressed) 0.90f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "fewItemsSquish"
                    )

                    Box(
                        modifier = Modifier
                            .width(58.dp)
                            .fillMaxHeight()
                            .graphicsLayer { scaleX = itemScale; scaleY = itemScale }
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(interactionSource = interactionSource, indication = null) {
                                onWallpaperClick(index)
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                    ) {
                        AsyncWallpaperImage(
                            wallpaper = wp,
                            contentDescription = null,
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize(),
                            allowMagic = false
                        )

                        if (isSelected) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawRoundRect(
                                    color = Color.White,
                                    size = size,
                                    cornerRadius = CornerRadius(16.dp.toPx()),
                                    style = Stroke(width = 3.dp.toPx())
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(8.dp))
                }

                PinWallpaperCarouselCard(
                    isPinned = isPinned,
                    onClick = onPinToggleClick,
                    modifier = Modifier
                        .width(48.dp)
                        .fillMaxHeight()
                )
            }
        } else {
        	
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(carouselHeight)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AddWallpaperCarouselCard(
                    onClick = onAddWallpaperClick,
                    modifier = Modifier
                        .width(48.dp)
                        .fillMaxHeight()
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    val availableWidth = maxWidth
                    val itemSpacing = 6.dp
                    val visibleCount = 4
                    val calculatedItemWidth = (availableWidth - (itemSpacing * (visibleCount - 1))) / visibleCount

                    LazyRow(
                        state = listState,
                        flingBehavior = flingBehavior,
                        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(filteredWallpapers) { index, wp ->
                            val isSelected = pagerState.currentPage == index
                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()

                            val itemScale by animateFloatAsState(
                                targetValue = if (isPressed) 0.90f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                label = "carouselSquish"
                            )

                            Box(
                                modifier = Modifier
                                    .width(calculatedItemWidth)
                                    .fillMaxHeight()
                                    .graphicsLayer { scaleX = itemScale; scaleY = itemScale }
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(interactionSource = interactionSource, indication = null) {
                                        onWallpaperClick(index)
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    }
                            ) {
                                AsyncWallpaperImage(
                                    wallpaper = wp,
                                    contentDescription = null,
                                    viewModel = viewModel,
                                    modifier = Modifier.fillMaxSize(),
                                    allowMagic = false
                                )

                                if (isSelected) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawRoundRect(
                                            color = Color.White,
                                            size = size,
                                            cornerRadius = CornerRadius(16.dp.toPx()),
                                            style = Stroke(width = 3.dp.toPx())
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                PinWallpaperCarouselCard(
                    isPinned = isPinned,
                    onClick = onPinToggleClick,
                    modifier = Modifier
                        .width(48.dp)
                        .fillMaxHeight()
                )
            }
        }
        
        AnimatedVisibility(
            visible = viewModel.showFastScrollGuide && pagerState.pageCount > 1,
            enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), expandFrom = Alignment.Bottom) + fadeIn(),
            exit = shrinkVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow), shrinkTowards = Alignment.Bottom) + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { viewModel.dismissFastScrollGuide() },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.TouchApp, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Drag to fast scroll", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.Close, "Dismiss", tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f), modifier = Modifier.size(14.dp).clickable { viewModel.dismissFastScrollGuide() })
                }
            }
        }
    }
}

@Composable
private fun AddWallpaperCarouselCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.88f else 1f, spring(), label = "addCardSquish")

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.35f),
                size = size,
                cornerRadius = CornerRadius(16.dp.toPx()),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.AddPhotoAlternate,
                contentDescription = "Add Wallpaper",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Add",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, fontSize = 10.sp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun PinWallpaperCarouselCard(
    isPinned: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.88f else 1f, spring(), label = "pinCardSquish")
    val pinRotation by animateFloatAsState(if (isPinned) 0f else -35f, spring(), label = "pinRotate")

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isPinned) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                color = if (isPinned) Color.Transparent else Color.White.copy(alpha = 0.35f),
                size = size,
                cornerRadius = CornerRadius(16.dp.toPx()),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.PushPin,
                contentDescription = if (isPinned) "Unpin Carousel" else "Pin Carousel",
                tint = if (isPinned) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = pinRotation }
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (isPinned) "Pinned" else "Pin",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, fontSize = 10.sp),
                color = if (isPinned) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FastScrollIndicator(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface,
    trackWidth: Dp = 190.dp,
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
    val pagerOffsetPx = with(density) { (pagerState.currentPageOffsetFraction * 38.dp.toPx()) }
    val expressiveSpring = spring<Float>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)

    val animatedDragOffset by animateFloatAsState(
        targetValue = if (isDragging) rawDragOffset else pagerOffsetPx,
        animationSpec = expressiveSpring,
        label = "dragSpring"
    )

    val maxDragPx = with(density) { ((trackWidth / 2) - 16.dp).toPx() }
    val trackAlpha by animateFloatAsState(
        targetValue = if (isDragging || isPagerScrolling) 0.32f else 0.15f,
        animationSpec = tween(300),
        label = "trackAlpha"
    )

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

    val dpSpring = spring<Dp>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)

    Box(
        modifier = modifier
            .width(trackWidth)
            .height(32.dp)
            .then(fastScrollModifier),
        contentAlignment = Alignment.Center
    ) {
        when (viewModel.scrollStyle) {
            0 -> {
                val thumbWidth by animateDpAsState(if (isDragging) 40.dp else 22.dp, dpSpring, label = "w0")
                val thumbHeight by animateDpAsState(if (isDragging) 8.dp else 5.dp, dpSpring, label = "h0")
                Box(
                    modifier = Modifier
                        .offset(x = with(density) { animatedDragOffset.toDp() })
                        .size(thumbWidth, thumbHeight)
                        .shadow(if (isDragging) 8.dp else 2.dp, CircleShape, ambientColor = activeColor, spotColor = activeColor)
                        .background(activeColor, CircleShape)
                )
            }
            1 -> {
                val thumbWidth by animateDpAsState(if (isDragging) 42.dp else 24.dp, dpSpring, label = "w1")
                val thumbHeight by animateDpAsState(if (isDragging) 10.dp else 7.dp, dpSpring, label = "h1")
                Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = trackAlpha }) {
                    drawRoundRect(
                        color = inactiveColor,
                        topLeft = Offset(0f, size.height / 2f - 5.dp.toPx()),
                        size = Size(size.width, 10.dp.toPx()),
                        cornerRadius = CornerRadius(5.dp.toPx())
                    )
                }
                Box(
                    modifier = Modifier
                        .offset(x = with(density) { animatedDragOffset.toDp() })
                        .size(thumbWidth, thumbHeight)
                        .shadow(if (isDragging) 6.dp else 2.dp, CircleShape, ambientColor = activeColor, spotColor = activeColor)
                        .background(activeColor, CircleShape)
                )
            }
            2 -> {
                val stretch by animateFloatAsState(
                    targetValue = if (isDragging) abs(rawDragOffset) * 0.14f else 0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium),
                    label = "stretch2"
                )
                Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = trackAlpha }) {
                    drawLine(
                        color = inactiveColor,
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                val thumbW = 18.dp + with(density) { stretch.toDp() }
                Box(
                    modifier = Modifier
                        .offset(x = with(density) { animatedDragOffset.toDp() })
                        .size(thumbW, 8.dp)
                        .shadow(if (isDragging) 8.dp else 2.dp, CircleShape, ambientColor = activeColor, spotColor = activeColor)
                        .background(activeColor, CircleShape)
                )
            }
            3 -> {
                Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = trackAlpha }) {
                    drawLine(
                        color = inactiveColor,
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                Box(
                    modifier = Modifier
                        .offset(x = with(density) { animatedDragOffset.toDp() })
                        .size(14.dp, 14.dp)
                        .shadow(if (isDragging) 8.dp else 2.dp, CircleShape, ambientColor = activeColor, spotColor = activeColor)
                        .background(activeColor, CircleShape)
                )

                AnimatedVisibility(
                    visible = isDragging,
                    enter = fadeIn(tween(150)) + slideInVertically { 15 },
                    exit = fadeOut(tween(150)) + slideOutVertically { 15 },
                    modifier = Modifier.offset(x = with(density) { animatedDragOffset.toDp() }, y = (-26).dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(activeColor, RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1} / $pageCount",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            4 -> {
                val fullWidthPx = with(density) { trackWidth.toPx() }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val y = size.height / 2f
                    val thumbAbsoluteX = (size.width / 2f) + animatedDragOffset
                    for (i in 0 until pageCount) {
                        val dotX = if (pageCount > 1) (i.toFloat() / (pageCount - 1)) * fullWidthPx else size.width / 2f
                        val dist = abs(dotX - thumbAbsoluteX)
                        val pullY = if (dist < 26.dp.toPx()) (26.dp.toPx() - dist) * 0.42f else 0f
                        val isNear = dist < 9.dp.toPx()
                        drawCircle(
                            color = if (isNear) activeColor else inactiveColor.copy(alpha = trackAlpha),
                            radius = if (isNear) 5.dp.toPx() else 2.5.dp.toPx(),
                            center = Offset(dotX, y - pullY)
                        )
                    }
                }
            }
        }
    }
}
