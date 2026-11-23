package com.android.CaveArt

import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.RenderNode
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.android.CaveArt.clockkit.*
import com.android.CaveArt.layer.DepthBlendStyle
import com.android.CaveArt.layer.LayerType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun ClockStudioPreviewCard(
    state: ClockStudioState,
    wallpaper: Wallpaper,
    viewModel: WallpaperViewModel,
    isMlLoading: Boolean,
    displayGeometry: NativeDisplayGeometry,
    previewOriginal: Bitmap?,
    previewMask: Bitmap?,
    timeString: String,
    dateText: String,
    customTypeface: Typeface,
    collisionMapArray: FloatArray?,
    focusedLayerType: LayerType?,
    sharedElementModifier: Modifier = Modifier,
    atGlanceRefreshTick: Int = 0,
    onDateClick: () -> Unit,
    onClockClick: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val realW = displayGeometry.widthPx
    val realH = displayGeometry.heightPx
    val densityVal = displayGeometry.density
    val statusBarPxInt = displayGeometry.statusBarHeightPx.toInt()
    val navBarPxInt = displayGeometry.navBarHeightPx.toInt()
    val navBarDp = displayGeometry.navBarHeightPx / densityVal
    val minSafeYDp = LockscreenSceneRenderer.computeMinSafeTopDp(displayGeometry.statusBarHeightPx, densityVal)
    val magneticSnapThresholdDp = (realW * LockscreenLayoutSpec.MAGNETIC_SNAP_WIDTH_RATIO) / densityVal
    val cardCornerRadiusDp = (displayGeometry.physicalCornerRadiusPx / densityVal).coerceAtLeast(0f)

    var isDraggingClock by remember { mutableStateOf(false) }
    var isDraggingDate by remember { mutableStateOf(false) }

    val activeStyle = remember(state.clockKitConfig.styleId) {
        ClockKitEngine.getStyle(state.clockKitConfig.styleId)
    }
    val latestStyle by rememberUpdatedState(activeStyle)
    val latestTime by rememberUpdatedState(timeString)
    val latestDate by rememberUpdatedState(dateText)
    val latestTypeface by rememberUpdatedState(customTypeface)

    val glanceSnapshot = remember(state.clockKitConfig, dateText, timeString, atGlanceRefreshTick) {
        AtAGlanceDataCollector.collect(context, state.clockKitConfig)
    }
    val latestSnapshot by rememberUpdatedState(glanceSnapshot)

    val needsContinuous = activeStyle.needsContinuousFrames(state.clockKitConfig, 0f)
    var animFrameMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(needsContinuous, isMlLoading) {
        if (needsContinuous && !isMlLoading) {
            while (true) {
                withFrameNanos {
                    animFrameMillis = System.currentTimeMillis()
                }
            }
        }
    }

    val sharedPathBounds = remember { RectF() }

    val hardwareRenderNode = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RenderNode("LockscreenPreviewHardwareNode").apply {
                setHasOverlappingRendering(false)
                clipToBounds = false
            }
        } else null
    }

    val measureContext = remember(realW, realH, densityVal, customTypeface, timeString, dateText) {
        ClockMeasureContext(
            context = context,
            screenW = realW,
            screenH = realH,
            density = densityVal,
            typeface = customTypeface,
            timeString = timeString.ifEmpty { "00:00" },
            dateString = dateText.ifEmpty { "Wednesday, 14 October" }
        )
    }

    LaunchedEffect(
        state.clockKitConfig.clockX,
        state.clockKitConfig.clockY,
        state.clockKitConfig.dateAttached,
        state.clockKitConfig.atGlanceEnabled,
        state.clockKitConfig.atGlanceStyle,
        state.clockKitConfig.styleId,
        state.clockKitConfig.scale,
        state.clockKitConfig.styleFloats,
        state.clockKitConfig.styleStrings,
        state.dateSize.floatValue,
        glanceSnapshot,
        timeString,
        customTypeface
    ) {
        val cfg = state.clockKitConfig
        val clamped = LockscreenSceneRenderer.clampConfigToScreen(
            context = context,
            cfg = cfg,
            screenW = realW,
            screenH = realH,
            density = densityVal,
            statusBarPadPx = statusBarPxInt,
            navBarPadPx = navBarPxInt,
            resolveDetachedOverlap = false,
            snapshotOverride = glanceSnapshot
        )
        if (abs(clamped.clockX - cfg.clockX) > 0.01f ||
            abs(clamped.dateX - cfg.dateX) > 0.01f ||
            abs(clamped.dateY - cfg.dateY) > 0.01f
        ) {
            state.updateConfig {
                it.copy(clockX = clamped.clockX, dateX = clamped.dateX, dateY = clamped.dateY)
            }
        }
    }

    val datePhysicsSpringY = remember { Animatable(state.clockKitConfig.dateY) }
    val latestDateX by rememberUpdatedState(state.clockKitConfig.dateX)
    val latestDateY by rememberUpdatedState(state.clockKitConfig.dateY)

    LaunchedEffect(
        isDraggingDate,
        isDraggingClock,
        state.clockKitConfig.dateAttached,
        state.clockKitConfig.atGlanceEnabled,
        state.clockKitConfig.atGlanceStyle,
        state.clockKitConfig.clockX,
        state.clockKitConfig.clockY,
        state.clockKitConfig.styleId,
        state.clockKitConfig.scale,
        state.clockKitConfig.styleFloats,
        state.clockKitConfig.styleStrings,
        state.dateSize.floatValue,
        glanceSnapshot,
        customTypeface,
        timeString
    ) {
        val cfg = state.clockKitConfig
        if (activeStyle.datePolicy(cfg) == ClockDatePolicy.EXTERNAL_STANDARD &&
            cfg.atGlanceEnabled && !cfg.dateAttached && !isDraggingDate && !isDraggingClock
        ) {
            val glanceM = AtAGlanceRenderer.measure(cfg, glanceSnapshot, densityVal, state.dateSize.floatValue, realW)
            val dateWDp = glanceM.widthPx / densityVal
            val dateHDp = glanceM.heightPx / densityVal
            val curDateX = latestDateX
            val curDateY = latestDateY
            val halfScreenDp = realW / densityVal / 2f

            val measured = activeStyle.measure(measureContext, cfg)
            val clockWDp = measured.contentWidthPx / densityVal
            val clockHDp = measured.contentHeightPx / densityVal
            val fallbackLeft = halfScreenDp + cfg.clockX - (clockWDp / 2f)
            val fallbackRight = fallbackLeft + clockWDp
            val fallbackTop = cfg.clockY
            val fallbackBottom = fallbackTop + clockHDp

            val hasDrawn = sharedPathBounds.width() > 0f && sharedPathBounds.height() > 0f
            val clockLeft = if (hasDrawn) min(sharedPathBounds.left / densityVal, fallbackLeft) else fallbackLeft
            val clockRight = if (hasDrawn) max(sharedPathBounds.right / densityVal, fallbackRight) else fallbackRight
            val clockTop = if (hasDrawn) min(sharedPathBounds.top / densityVal, fallbackTop) else fallbackTop
            val clockBottom = if (hasDrawn) max(sharedPathBounds.bottom / densityVal, fallbackBottom) else fallbackBottom
            val maxDateSafeYDp = ((realH / densityVal) - navBarDp - dateHDp - LockscreenLayoutSpec.EDITOR_BOTTOM_SAFE_DP)
                .coerceAtLeast(minSafeYDp)

            val resolvedY = LockscreenSceneRenderer.resolveDetachedOverlapTargetYDp(
                dateLeftDp = halfScreenDp + curDateX - (dateWDp / 2f),
                dateTopDp = curDateY,
                dateWidthDp = dateWDp,
                dateHeightDp = dateHDp,
                clockLeftDp = clockLeft,
                clockTopDp = clockTop,
                clockWidthDp = clockRight - clockLeft,
                clockHeightDp = clockBottom - clockTop,
                minSafeTopDp = minSafeYDp,
                maxSafeDateYDp = maxDateSafeYDp
            )

            if (resolvedY != null && abs(curDateY - resolvedY) > 1.0f) {
                datePhysicsSpringY.snapTo(curDateY)
                datePhysicsSpringY.animateTo(
                    targetValue = resolvedY,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) {
                    state.updateConfig { it.copy(dateY = value) }
                }
            }
        }
    }

    val gestureModifier = if (!isMlLoading) {
        Modifier.pointerInput(realW, realH) {
            var rawClockX = state.clockKitConfig.clockX
            var rawClockY = state.clockKitConfig.clockY
            var rawDateX = state.clockKitConfig.dateX
            var rawDateY = state.clockKitConfig.dateY

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = true)
                if (down.isConsumed) return@awaitEachGesture
                val timeStart = System.currentTimeMillis()
                var dragged = false

                val styleAtDown = latestStyle
                val cfgAtDown = state.clockKitConfig
                val previewScale = size.width / realW
                val externalDateActive = styleAtDown.datePolicy(cfgAtDown) == ClockDatePolicy.EXTERNAL_STANDARD && cfgAtDown.atGlanceEnabled
                val glanceM = AtAGlanceRenderer.measure(cfgAtDown, latestSnapshot, densityVal, state.dateSize.floatValue, realW)

                val scaledDateTop = (cfgAtDown.dateY * densityVal) * previewScale
                val scaledDateBottom = ((cfgAtDown.dateY * densityVal) + glanceM.heightPx) * previewScale
                val scaledDateCenterX = ((realW / 2f) + cfgAtDown.dateX * densityVal) * previewScale
                val scaledDateHalfW = (glanceM.widthPx / 2f) * previewScale
                val hitPad = LockscreenLayoutSpec.DATE_HIT_PAD_DP * densityVal * previewScale

                val hitDate = externalDateActive &&
                    down.position.y in (scaledDateTop - hitPad)..(scaledDateBottom + hitPad) &&
                    down.position.x in (scaledDateCenterX - scaledDateHalfW - hitPad)..(scaledDateCenterX + scaledDateHalfW + hitPad)
                val activeDrag = if (hitDate) "DATE" else "CLOCK"

                rawClockX = cfgAtDown.clockX
                rawClockY = cfgAtDown.clockY
                rawDateX = cfgAtDown.dateX
                rawDateY = cfgAtDown.dateY

                do {
                    val event = awaitPointerEvent()
                    val pan = event.calculatePan()
                    val zoom = event.calculateZoom()

                    if (pan.getDistance() > 2f || abs(zoom - 1f) > 0.01f) dragged = true

                    if (dragged) {
                        val curStyle = latestStyle
                        val curCfg = state.clockKitConfig
                        val deltaNativeDpX = (pan.x / previewScale) / densityVal
                        val deltaNativeDpY = (pan.y / previewScale) / densityVal

                        if (activeDrag == "DATE" &&
                            curStyle.datePolicy(curCfg) == ClockDatePolicy.EXTERNAL_STANDARD &&
                            curCfg.atGlanceEnabled &&
                            !curCfg.dateAttached
                        ) {
                            isDraggingDate = true
                            rawDateX += deltaNativeDpX
                            rawDateY += deltaNativeDpY
                            val clamped = LockscreenSceneRenderer.clampConfigToScreen(
                                context = context,
                                cfg = curCfg.copy(dateX = rawDateX, dateY = rawDateY),
                                screenW = realW,
                                screenH = realH,
                                density = densityVal,
                                statusBarPadPx = statusBarPxInt,
                                navBarPadPx = navBarPxInt,
                                resolveDetachedOverlap = false,
                                snapshotOverride = latestSnapshot
                            )
                            rawDateX = clamped.dateX
                            rawDateY = clamped.dateY
                            state.updateConfig { clamped }
                        } else {
                            isDraggingClock = true
                            val zoomedCfg = if (abs(zoom - 1f) > 0.005f) {
                                curStyle.onPinchZoom(curCfg, zoom)
                            } else curCfg

                            rawClockX += deltaNativeDpX
                            rawClockY += deltaNativeDpY
                            val clamped = LockscreenSceneRenderer.clampConfigToScreen(
                                context = context,
                                cfg = zoomedCfg.copy(clockX = rawClockX, clockY = rawClockY),
                                screenW = realW,
                                screenH = realH,
                                density = densityVal,
                                statusBarPadPx = statusBarPxInt,
                                navBarPadPx = navBarPxInt,
                                resolveDetachedOverlap = false,
                                snapshotOverride = latestSnapshot
                            )
                            rawClockX = clamped.clockX
                            rawClockY = clamped.clockY
                            state.updateConfig { clamped }
                        }
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                } while (event.changes.any { it.pressed })

                if (!dragged && (System.currentTimeMillis() - timeStart) < 300L) {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    if (activeDrag == "DATE") onDateClick() else onClockClick()
                }

                isDraggingClock = false
                isDraggingDate = false

                val endCfg = state.clockKitConfig
                val snapClockX = if (abs(endCfg.clockX) < magneticSnapThresholdDp) 0f else endCfg.clockX
                val snapDateX = if (!endCfg.dateAttached && abs(endCfg.dateX) < magneticSnapThresholdDp) 0f else endCfg.dateX
                if (snapClockX != endCfg.clockX || snapDateX != endCfg.dateX) {
                    state.updateConfig { it.copy(clockX = snapClockX, dateX = snapDateX) }
                }
            }
        }
    } else Modifier

    Card(
        modifier = Modifier
            .fillMaxHeight()
            .aspectRatio(displayGeometry.aspectRatio, matchHeightConstraintsFirst = true)
            .then(sharedElementModifier)
            .clip(RoundedCornerShape(cardCornerRadiusDp.dp))
            .then(gestureModifier),
        shape = RoundedCornerShape(cardCornerRadiusDp.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp
        ),
        colors = CardDefaults.cardColors(containerColor = Color.Black)
    ) {
        AnimatedContent(
            targetState = isMlLoading,
            transitionSpec = {
                fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(300))
            },
            label = "previewLoadingTransition"
        ) { loading ->
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncWallpaperImage(
                        wallpaper = wallpaper,
                        contentDescription = null,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize(),
                        allowMagic = false
                    )

                    M3ExpressiveMorphLoadingIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        sizeDp = 52.dp
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val previewScale = size.width / realW
                        val cfg = state.clockKitConfig
                        val frameMillis = if (needsContinuous) animFrameMillis else System.currentTimeMillis()
                        val depthLayer = state.layerStack.depthVisionLayer
                        val depthBlend = when (depthLayer.blendStyle) {
                            DepthBlendStyle.FULL_HEATMAP -> null
                            DepthBlendStyle.ATMOSPHERIC_SCREEN -> PorterDuff.Mode.SCREEN
                            DepthBlendStyle.VOLUMETRIC_OVERLAY -> PorterDuff.Mode.OVERLAY
                        }
                        val sceneParams = LockscreenSceneParams(
                            screenW = realW,
                            screenH = realH,
                            density = densityVal,
                            wallpaperBitmap = previewOriginal,
                            depthMaskBitmap = previewMask,
                            rawDepthBitmap = depthLayer.rawDepthMap,
                            collisionMap = collisionMapArray,
                            config = cfg,
                            isDepthEnabled = state.layerStack.isDepthEnabled,
                            wallpaperZoom = state.liveWallpaperZoom.floatValue,
                            bgVibrance = state.layerStack.backgroundLayer.structure,
                            bgOpacity = state.layerStack.backgroundLayer.opacity,
                            subjectOpacity = state.layerStack.subjectLayer.opacity,
                            showBackground = state.layerStack.backgroundLayer.isVisible,
                            showClock = state.layerStack.clockLayer.isVisible && timeString.isNotEmpty(),
                            showSubject = state.layerStack.subjectLayer.isVisible,
                            depthHeatMap = if (depthLayer.isVisible) depthLayer.depthHeatMap else null,
                            depthHeatMapOpacity = if (depthLayer.isVisible) depthLayer.opacity else 0f,
                            depthHeatMapBlendMode = depthBlend,
                            showUdfps = true,
                            udfpsX = state.udfpsX.floatValue,
                            udfpsY = state.udfpsY.floatValue,
                            udfpsRadius = state.udfpsRadius.floatValue,
                            notificationShelfTop = state.notificationShelfTop.floatValue,
                            showNotificationShelfGuide = isDraggingClock,
                            timeMillis = frameMillis,
                            is24Hour = android.text.format.DateFormat.is24HourFormat(context),
                            customTypeface = customTypeface,
                            atGlanceSnapshot = glanceSnapshot
                        )

                        drawIntoCanvas { canvas ->
                            val nativeCanvas = canvas.nativeCanvas
                            val drawResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hardwareRenderNode != null) {
                                hardwareRenderNode.setPosition(0, 0, realW.toInt(), realH.toInt())
                                hardwareRenderNode.pivotX = 0f
                                hardwareRenderNode.pivotY = 0f
                                hardwareRenderNode.scaleX = previewScale
                                hardwareRenderNode.scaleY = previewScale

                                val recordingCanvas = hardwareRenderNode.beginRecording(realW.toInt(), realH.toInt())
                                val res = try {
                                    LockscreenSceneRenderer.drawScene(
                                        context = context,
                                        canvas = recordingCanvas,
                                        viewW = realW,
                                        params = sceneParams,
                                        outClockBounds = sharedPathBounds
                                    )
                                } finally {
                                    hardwareRenderNode.endRecording()
                                }
                                nativeCanvas.drawRenderNode(hardwareRenderNode)
                                res
                            } else {
                                LockscreenSceneRenderer.drawScene(
                                    context = context,
                                    canvas = nativeCanvas,
                                    viewW = size.width,
                                    params = sceneParams,
                                    outClockBounds = sharedPathBounds
                                )
                            }

                            if (state.layerStack.isDepthEnabled && previewMask != null) {
                                val targetZ = if (drawResult.elevatedAboveSubject) 4 else 2
                                if (state.layerStack.clockLayer.zIndex != targetZ) {
                                    state.layerStack.clockLayer.zIndex = targetZ
                                }
                            }
                        }

                        if (isDraggingClock && sharedPathBounds.width() > 0f && sharedPathBounds.height() > 0f) {
                            val scale = size.width / realW
                            val padXPx = 12f * densityVal * scale
                            val padYPx = 8f * densityVal * scale

                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.6f),
                                topLeft = Offset(
                                    sharedPathBounds.left * scale - padXPx,
                                    sharedPathBounds.top * scale - padYPx
                                ),
                                size = Size(
                                    sharedPathBounds.width() * scale + (padXPx * 2f),
                                    sharedPathBounds.height() * scale + (padYPx * 2f)
                                ),
                                cornerRadius = CornerRadius(16.dp.toPx()),
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
