package com.android.CaveArt

import android.graphics.Bitmap
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.android.CaveArt.clockkit.*
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun ClockStyleCarousel(
    pagerState: PagerState,
    currentConfig: ClockKitConfig,
    displayGeometry: NativeDisplayGeometry,
    previewOriginal: Bitmap?,
    previewMask: Bitmap?,
    rawDepthMap: Bitmap?,
    liveWallpaperZoom: Float,
    dateSizeDp: Float,
    timeString: String,
    dateText: String,
    customTypeface: Typeface,
    collisionMapArray: FloatArray?,
    onStyleChanged: (ClockStyle) -> Unit,
    onConfirmAndExit: (ClockStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val styles = ClockKitEngine.styles

    LaunchedEffect(pagerState.settledPage) {
        val settled = styles[pagerState.settledPage]
        if (settled.id != currentConfig.styleId) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onStyleChanged(settled)
        }
    }

    val realW = displayGeometry.widthPx
    val realH = displayGeometry.heightPx
    val densityVal = displayGeometry.density
    val statusBarPxInt = displayGeometry.statusBarHeightPx.toInt()
    val navBarPxInt = displayGeometry.navBarHeightPx.toInt()
    val cardCornerRadiusDp = (displayGeometry.physicalCornerRadiusPx / densityVal * 0.90f).coerceAtLeast(22f)

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val cardHeight = maxHeight * 0.94f
        val cardWidth = cardHeight * displayGeometry.aspectRatio
        val horizontalPeekPadding = ((maxWidth - cardWidth) / 2f).coerceAtLeast(44.dp)

        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = horizontalPeekPadding),
            pageSpacing = 18.dp,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
        ) { pageIndex ->
            val style = styles[pageIndex]
            val pageOffset = ((pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction)
                .absoluteValue.coerceIn(0f, 1f)
            val scale = lerp(0.88f, 1f, 1f - pageOffset)
            val alpha = lerp(0.45f, 1f, 1f - pageOffset)
            val isCentered = pageIndex == pagerState.currentPage
            val cardShape = RoundedCornerShape(cardCornerRadiusDp.dp)

            val cardConfig = remember(currentConfig, style.id, realW, realH, densityVal) {
                LockscreenSceneRenderer.syncAttachedDatePosition(
                    context = context,
                    cfg = currentConfig.copy(styleId = style.id),
                    statusBarPadPx = statusBarPxInt,
                    density = densityVal,
                    screenW = realW,
                    screenH = realH,
                    navBarPadPx = navBarPxInt
                )
            }
            val glanceSnapshot = remember(cardConfig, dateText, timeString) {
                AtAGlanceDataCollector.collect(context, cardConfig)
            }

            val needsContinuous = style.needsContinuousFrames(cardConfig, 0f) && isCentered
            var liveMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(needsContinuous) {
                if (needsContinuous) {
                    while (true) {
                        withFrameNanos { liveMillis = System.currentTimeMillis() }
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(displayGeometry.aspectRatio, matchHeightConstraintsFirst = true)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                        this.shape = cardShape
                        this.clip = true
                    }
                    .clip(cardShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        if (isCentered) {
                            onConfirmAndExit(style)
                        } else {
                            scope.launch { pagerState.animateScrollToPage(pageIndex) }
                        }
                    },
                shape = cardShape,
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp
                ),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val frameNow = if (needsContinuous) liveMillis else System.currentTimeMillis()
                    drawIntoCanvas { canvas ->
                        LockscreenSceneRenderer.drawScene(
                            context = context,
                            canvas = canvas.nativeCanvas,
                            viewW = size.width,
                            params = LockscreenSceneParams(
                                screenW = realW,
                                screenH = realH,
                                density = densityVal,
                                wallpaperBitmap = previewOriginal,
                                depthMaskBitmap = previewMask,
                                rawDepthBitmap = rawDepthMap,
                                collisionMap = collisionMapArray,
                                config = cardConfig,
                                isDepthEnabled = cardConfig.isDepthEnabled,
                                wallpaperZoom = liveWallpaperZoom,
                                showClock = timeString.isNotEmpty(),
                                timeMillis = frameNow,
                                is24Hour = android.text.format.DateFormat.is24HourFormat(context),
                                customTypeface = customTypeface,
                                atGlanceSnapshot = glanceSnapshot
                            )
                        )
                    }
                }
            }
        }
    }
}
