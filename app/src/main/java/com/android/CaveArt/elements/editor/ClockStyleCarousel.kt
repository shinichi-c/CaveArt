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
import com.android.CaveArt.layer.LayerCompositor
import com.android.CaveArt.layer.LockscreenLayerStack
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.max

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
    val statusBarDp = displayGeometry.statusBarHeightPx / densityVal
    val minSafeYDp = statusBarDp + (displayGeometry.statusBarHeightPx * 0.12f / densityVal)
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

            val cardConfig = remember(currentConfig, style.id) {
                currentConfig.copy(styleId = style.id)
            }
            val glanceSnapshot = remember(cardConfig, dateText, timeString) {
                AtAGlanceDataCollector.collect(context, cardConfig)
            }
            val cardLayerStack = remember(style.id, cardConfig.isDepthEnabled, cardConfig.depthOcclusionLimit) {
                LockscreenLayerStack().apply {
                    setDepthClockActive(cardConfig.isDepthEnabled)
                    depthOcclusionLimit = cardConfig.depthOcclusionLimit
                    backgroundLayer.isVisible = true
                    subjectLayer.isVisible = true
                }
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
                    val previewScale = size.width / realW
                    val frameNow = if (needsContinuous) liveMillis else System.currentTimeMillis()

                    drawIntoCanvas { canvas ->
                        val nc = canvas.nativeCanvas
                        nc.save()
                        nc.scale(previewScale, previewScale)

                        var capturedFrame: ClockFrameContext? = null

                        previewOriginal?.let { bmp ->
                            LayerCompositor.composite(
                                canvas = nc,
                                screenW = realW,
                                screenH = realH,
                                originalBmp = bmp,
                                maskBmp = previewMask,
                                zoom = liveWallpaperZoom,
                                stack = cardLayerStack,
                                focusedLayerType = null
                            ) { targetCanvas ->
                                val showExternalDate = style.datePolicy(cardConfig) == ClockDatePolicy.EXTERNAL_STANDARD && cardConfig.atGlanceEnabled
                                if (showExternalDate) {
                                    val safeDateYDp = if (cardConfig.dateAttached) {
                                        val mCtx = ClockMeasureContext(
                                            context = context,
                                            screenW = realW,
                                            screenH = realH,
                                            density = densityVal,
                                            typeface = customTypeface,
                                            timeString = timeString.ifEmpty { "00:00" },
                                            dateString = dateText
                                        )
                                        val measured = style.measure(mCtx, cardConfig)
                                        val glanceM = AtAGlanceRenderer.measure(cardConfig, glanceSnapshot, densityVal, dateSizeDp, realW)
                                        val dateLineHDp = glanceM.heightPx / densityVal
                                        val strokeW = cardConfig.getFloat("adaptive_vector", "stroke_width", 8f)
                                        val gapDp = (strokeW * 0.4f) + (dateSizeDp * 0.35f)
                                        val idealAboveDp = cardConfig.clockY - dateLineHDp - gapDp
                                        if (idealAboveDp < minSafeYDp && !style.needsCollisionMap(cardConfig)) {
                                            cardConfig.clockY + (measured.contentHeightPx / densityVal) + gapDp
                                        } else {
                                            max(minSafeYDp, idealAboveDp)
                                        }
                                    } else {
                                        cardConfig.dateY
                                    }

                                    val nativeDateX = (realW / 2f) + ((if (cardConfig.dateAttached) cardConfig.clockX else cardConfig.dateX) * densityVal)
                                    val nativeDateY = safeDateYDp * densityVal
                                    AtAGlanceRenderer.draw(
                                        canvas = targetCanvas,
                                        centerX = nativeDateX,
                                        topY = nativeDateY,
                                        config = cardConfig,
                                        snapshot = glanceSnapshot,
                                        density = densityVal,
                                        textColor = cardConfig.color,
                                        dateSizeSp = dateSizeDp,
                                        maxScreenWidthPx = realW
                                    )
                                }

                                if (timeString.isNotEmpty()) {
                                    val fCtx = ClockFrameContext(
                                        context = context,
                                        screenW = realW,
                                        screenH = realH,
                                        density = densityVal,
                                        clockCenterX = (realW / 2f) + (cardConfig.clockX * densityVal),
                                        clockTopY = cardConfig.clockY * densityVal,
                                        timeMillis = frameNow,
                                        timeString = timeString,
                                        dateString = dateText,
                                        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
                                        typeface = customTypeface,
                                        notificationProgress = 0f,
                                        collisionMap = collisionMapArray,
                                        depthMask = previewMask,
                                        rawDepthMap = rawDepthMap,
                                        zoomFactor = liveWallpaperZoom
                                    )
                                    capturedFrame = fCtx

                                    val (_, elevate) = ClockKitEngine.renderForPreview(
                                        canvas = targetCanvas,
                                        style = style,
                                        frame = fCtx,
                                        config = cardConfig
                                    )
                                    if (cardLayerStack.isDepthEnabled && previewMask != null) {
                                        cardLayerStack.clockLayer.zIndex = if (elevate) 4 else 2
                                    }
                                }
                            }

                            capturedFrame?.let { fCtx ->
                                ClockKitEngine.renderPreviewOverlay(
                                    canvas = nc,
                                    style = style,
                                    frame = fCtx,
                                    config = cardConfig
                                )
                            }
                        }

                        nc.restore()
                    }
                }
            }
        }
    }
}
