package com.android.CaveArt

import android.graphics.Bitmap
import android.graphics.DashPathEffect
import android.graphics.Paint
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
import androidx.compose.ui.draw.shadow
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
import com.android.CaveArt.layer.LayerCompositor
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
    onDateClick: () -> Unit,
    onClockClick: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val realW = displayGeometry.widthPx
    val realH = displayGeometry.heightPx
    val densityVal = displayGeometry.density
    val statusBarDp = displayGeometry.statusBarHeightPx / densityVal
    val navBarDp = displayGeometry.navBarHeightPx / densityVal
    val minSafeYDp = statusBarDp + (displayGeometry.statusBarHeightPx * 0.12f / densityVal)
    val magneticSnapThresholdDp = (realW * 0.025f) / densityVal
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

    val datePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            setShadowLayer(10f, 0f, 3f, android.graphics.Color.argb(160, 0, 0, 0))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
    }
    val notificationPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(200, 255, 183, 77)
            strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(20f, 20f), 0f)
        }
    }
    val udfpsPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f)
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

    LaunchedEffect(state.clockKitConfig, state.dateSize.floatValue, dateText, timeString, customTypeface) {
        val cfg = state.clockKitConfig
        val sideSafeMarginDp = 18f
        val halfScreenDp = realW / densityVal / 2f

        val measured = activeStyle.measure(measureContext, cfg)
        val clockWidthDp = measured.contentWidthPx / densityVal
        val halfClockWDp = clockWidthDp / 2f
        val minSafeClockX = (-halfScreenDp + halfClockWDp + sideSafeMarginDp).coerceAtMost(0f)
        val maxSafeClockX = (halfScreenDp - halfClockWDp - sideSafeMarginDp).coerceAtLeast(0f)

        val clampedClockX = cfg.clockX.coerceIn(minSafeClockX, maxSafeClockX)
        var newDateX = cfg.dateX
        var newDateY = cfg.dateY

        if (activeStyle.datePolicy(cfg) == ClockDatePolicy.EXTERNAL_STANDARD && cfg.dateAttached) {
            datePaint.textSize = state.dateSize.floatValue * densityVal
            val sampleDate = if (dateText.isNotEmpty()) dateText else "Wednesday, 14 October"
            val dateWidthDp = datePaint.measureText(sampleDate) / densityVal
            val halfDateWDp = dateWidthDp / 2f
            val minSafeDateX = (-halfScreenDp + halfDateWDp + sideSafeMarginDp).coerceAtMost(0f)
            val maxSafeDateX = (halfScreenDp - halfDateWDp - sideSafeMarginDp).coerceAtLeast(0f)

            newDateX = clampedClockX.coerceIn(minSafeDateX, maxSafeDateX)

            val fontMetrics = datePaint.fontMetrics
            val dateLineHeightDp = (fontMetrics.descent - fontMetrics.ascent) / densityVal
            val strokeW = cfg.getFloat("adaptive_vector", "stroke_width", 8f)
            val dynamicGapDp = (strokeW * 0.4f) + (dateLineHeightDp * 0.35f)
            val compoundDateOffsetDp = dateLineHeightDp + dynamicGapDp
            val idealAboveYDp = cfg.clockY - compoundDateOffsetDp
            val touchesTopBorder = idealAboveYDp < minSafeYDp

            newDateY = if (activeStyle.needsCollisionMap(cfg)) {
                max(minSafeYDp, idealAboveYDp)
            } else if (touchesTopBorder) {
                val clockEstimatedHDp = measured.contentHeightPx / densityVal
                cfg.clockY + clockEstimatedHDp + dynamicGapDp
            } else {
                max(minSafeYDp, idealAboveYDp)
            }
        }

        if (abs(clampedClockX - cfg.clockX) > 0.01f ||
            abs(newDateX - cfg.dateX) > 0.01f ||
            abs(newDateY - cfg.dateY) > 0.01f
        ) {
            state.updateConfig {
                it.copy(clockX = clampedClockX, dateX = newDateX, dateY = newDateY)
            }
        }
    }

    val datePhysicsSpringY = remember { Animatable(state.clockKitConfig.dateY) }
    LaunchedEffect(
        isDraggingDate, isDraggingClock,
        state.clockKitConfig, state.dateSize.floatValue
    ) {
        val cfg = state.clockKitConfig
        if (activeStyle.datePolicy(cfg) == ClockDatePolicy.EXTERNAL_STANDARD &&
            !cfg.dateAttached && !isDraggingDate && !isDraggingClock
        ) {
            datePaint.textSize = state.dateSize.floatValue * densityVal
            val sampleDate = if (dateText.isNotEmpty()) dateText else "Wednesday, 14 October"
            val dateWDp = datePaint.measureText(sampleDate) / densityVal
            val dateHDp = (datePaint.fontMetrics.descent - datePaint.fontMetrics.ascent) / densityVal
            val nativeDateCenterX = (realW / densityVal / 2f) + cfg.dateX
            val dateLeft = nativeDateCenterX - (dateWDp / 2f)
            val dateRight = nativeDateCenterX + (dateWDp / 2f)
            val dateTop = cfg.dateY
            val dateBottom = dateTop + dateHDp

            val clockLeft = sharedPathBounds.left / densityVal
            val clockRight = sharedPathBounds.right / densityVal
            val clockTop = sharedPathBounds.top / densityVal
            val clockBottom = sharedPathBounds.bottom / densityVal

            val marginDp = 16f
            val xOverlap = max(dateLeft, clockLeft) < min(dateRight, clockRight)
            val yOverlap = (dateBottom + marginDp > clockTop) && (dateTop < clockBottom + marginDp)

            if (xOverlap && yOverlap && sharedPathBounds.width() > 0 && sharedPathBounds.height() > 0) {
                val clockCenterY = (clockTop + clockBottom) / 2f
                val dateCenterY = (dateTop + dateBottom) / 2f
                val maxDateSafeYDp = (realH / densityVal) - navBarDp - (state.dateSize.floatValue * 1.5f)

                val targetY = if (dateCenterY < clockCenterY) {
                    val candidateAbove = clockTop - dateHDp - marginDp
                    if (candidateAbove >= minSafeYDp) candidateAbove else clockBottom + marginDp
                } else {
                    val candidateBelow = clockBottom + marginDp
                    if (candidateBelow <= maxDateSafeYDp) candidateBelow else clockTop - dateHDp - marginDp
                }

                val safeClampedY = targetY.coerceIn(minSafeYDp, maxDateSafeYDp)
                if (abs(datePhysicsSpringY.targetValue - safeClampedY) > 1.5f) {
                    datePhysicsSpringY.snapTo(cfg.dateY)
                    datePhysicsSpringY.animateTo(
                        targetValue = safeClampedY,
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
                val externalDateActive = styleAtDown.datePolicy(cfgAtDown) == ClockDatePolicy.EXTERNAL_STANDARD
                val scaledDateY = (cfgAtDown.dateY * densityVal) * previewScale
                val scaledClockY = (cfgAtDown.clockY * densityVal) * previewScale
                val distToDate = abs(down.position.y - scaledDateY)
                val distToClock = abs(down.position.y - scaledClockY)
                val dateHitThresholdPx = (state.dateSize.floatValue * densityVal * 1.5f) * previewScale
                val activeDrag = if (externalDateActive && distToDate < distToClock && distToDate < dateHitThresholdPx) "DATE" else "CLOCK"

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

                        val sideSafeMarginDp = 18f
                        val halfScreenDp = realW / densityVal / 2f

                        val mCtx = ClockMeasureContext(
                            context = context,
                            screenW = realW,
                            screenH = realH,
                            density = densityVal,
                            typeface = latestTypeface,
                            timeString = latestTime.ifEmpty { "00:00" },
                            dateString = latestDate.ifEmpty { "Wednesday, 14 October" }
                        )
                        val measured = curStyle.measure(mCtx, curCfg)
                        val clockWidthDp = measured.contentWidthPx / densityVal
                        val clockHeightDp = measured.contentHeightPx / densityVal
                        val halfClockWDp = clockWidthDp / 2f

                        val minSafeClockX = (-halfScreenDp + halfClockWDp + sideSafeMarginDp).coerceAtMost(0f)
                        val maxSafeClockX = (halfScreenDp - halfClockWDp - sideSafeMarginDp).coerceAtLeast(0f)
                        val maxClockSafeYDp = ((realH / densityVal) - navBarDp - (clockHeightDp * 0.55f)).coerceAtLeast(minSafeYDp)

                        if (activeDrag == "DATE" &&
                            curStyle.datePolicy(curCfg) == ClockDatePolicy.EXTERNAL_STANDARD &&
                            !curCfg.dateAttached
                        ) {
                            isDraggingDate = true
                            val sampleDate = latestDate.ifEmpty { "Wednesday, 14 October" }
                            val dateWidthDp = datePaint.measureText(sampleDate) / densityVal
                            val halfDateWDp = dateWidthDp / 2f
                            val minSafeDateX = (-halfScreenDp + halfDateWDp + sideSafeMarginDp).coerceAtMost(0f)
                            val maxSafeDateX = (halfScreenDp - halfDateWDp - sideSafeMarginDp).coerceAtLeast(0f)
                            val maxDateSafeYDp = (realH / densityVal) - navBarDp - (state.dateSize.floatValue * 1.5f)

                            rawDateX += deltaNativeDpX
                            rawDateY += deltaNativeDpY
                            val nextDateX = rawDateX.coerceIn(minSafeDateX, maxSafeDateX)
                            val nextDateY = rawDateY.coerceIn(minSafeYDp, maxDateSafeYDp)
                            rawDateX = nextDateX
                            rawDateY = nextDateY
                            state.updateConfig { it.copy(dateX = nextDateX, dateY = nextDateY) }
                        } else {
                            isDraggingClock = true
                            val zoomedCfg = if (abs(zoom - 1f) > 0.005f) {
                                curStyle.onPinchZoom(curCfg, zoom)
                            } else curCfg

                            rawClockX += deltaNativeDpX
                            rawClockY += deltaNativeDpY

                            val fontMetrics = datePaint.fontMetrics
                            val dateLineHeightDp = (fontMetrics.descent - fontMetrics.ascent) / densityVal
                            val strokeW = zoomedCfg.getFloat("adaptive_vector", "stroke_width", 8f)
                            val dynamicGapDp = (strokeW * 0.4f) + (dateLineHeightDp * 0.35f)
                            val compoundDateOffsetDp = dateLineHeightDp + dynamicGapDp

                            val effectiveMinClockYDp = if (
                                curStyle.datePolicy(zoomedCfg) == ClockDatePolicy.EXTERNAL_STANDARD &&
                                zoomedCfg.dateAttached &&
                                curStyle.needsCollisionMap(zoomedCfg)
                            ) {
                                minSafeYDp + compoundDateOffsetDp
                            } else {
                                minSafeYDp
                            }

                            val nextClockX = rawClockX.coerceIn(minSafeClockX, maxSafeClockX)
                            val nextClockY = rawClockY.coerceIn(effectiveMinClockYDp, maxClockSafeYDp)
                            rawClockX = nextClockX
                            rawClockY = nextClockY
                            state.updateConfig { zoomedCfg.copy(clockX = nextClockX, clockY = nextClockY) }
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
            .shadow(28.dp, RoundedCornerShape(cardCornerRadiusDp.dp))
            .clip(RoundedCornerShape(cardCornerRadiusDp.dp))
            .then(gestureModifier),
        shape = RoundedCornerShape(cardCornerRadiusDp.dp),
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

                        drawIntoCanvas { canvas ->
                            val nativeCanvas = canvas.nativeCanvas
                            var capturedFrameCtx: ClockFrameContext? = null

                            val drawClockAndDateRoutine: (android.graphics.Canvas) -> Unit = { targetCanvas ->
                                if (activeStyle.datePolicy(cfg) == ClockDatePolicy.EXTERNAL_STANDARD && dateText.isNotEmpty()) {
                                    datePaint.color = cfg.color
                                    datePaint.textSize = state.dateSize.floatValue * densityVal
                                    val nativeDateX = (realW / 2f) + (cfg.dateX * densityVal)
                                    val nativeDateY = cfg.dateY * densityVal
                                    val drawBaselineY = nativeDateY - datePaint.fontMetrics.top
                                    targetCanvas.drawText(dateText, nativeDateX, drawBaselineY, datePaint)
                                }

                                if (timeString.isNotEmpty()) {
                                    val frameCtx = ClockFrameContext(
                                        context = context,
                                        screenW = realW,
                                        screenH = realH,
                                        density = densityVal,
                                        clockCenterX = (realW / 2f) + (cfg.clockX * densityVal),
                                        clockTopY = cfg.clockY * densityVal,
                                        timeMillis = frameMillis,
                                        timeString = timeString,
                                        dateString = dateText,
                                        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
                                        typeface = customTypeface,
                                        notificationProgress = 0f,
                                        collisionMap = collisionMapArray,
                                        depthMask = previewMask,
                                        rawDepthMap = state.layerStack.depthVisionLayer.rawDepthMap,
                                        zoomFactor = state.liveWallpaperZoom.floatValue
                                    )
                                    capturedFrameCtx = frameCtx

                                    val (renderResult, elevateAboveSubject) = ClockKitEngine.renderForPreview(
                                        canvas = targetCanvas,
                                        style = activeStyle,
                                        frame = frameCtx,
                                        config = cfg
                                    )

                                    sharedPathBounds.set(renderResult.boundsOnScreen)

                                    if (state.layerStack.isDepthEnabled && previewMask != null) {
                                        state.layerStack.clockLayer.zIndex = if (elevateAboveSubject) 4 else 2
                                    }
                                }
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hardwareRenderNode != null) {
                                hardwareRenderNode.setPosition(0, 0, realW.toInt(), realH.toInt())
                                hardwareRenderNode.pivotX = 0f
                                hardwareRenderNode.pivotY = 0f
                                hardwareRenderNode.scaleX = previewScale
                                hardwareRenderNode.scaleY = previewScale

                                val recordingCanvas = hardwareRenderNode.beginRecording(realW.toInt(), realH.toInt())
                                try {
                                    previewOriginal?.let { bmp ->
                                        LayerCompositor.composite(
                                            canvas = recordingCanvas,
                                            screenW = realW,
                                            screenH = realH,
                                            originalBmp = bmp,
                                            maskBmp = previewMask,
                                            zoom = state.liveWallpaperZoom.floatValue,
                                            stack = state.layerStack,
                                            focusedLayerType = focusedLayerType,
                                            drawClockAndDate = drawClockAndDateRoutine
                                        )
                                    }

                                    capturedFrameCtx?.let { fCtx ->
                                        if (state.layerStack.clockLayer.isVisible) {
                                            val overlayRes = ClockKitEngine.renderPreviewOverlay(
                                                canvas = recordingCanvas,
                                                style = activeStyle,
                                                frame = fCtx,
                                                config = cfg
                                            )
                                            if (overlayRes != null) {
                                                sharedPathBounds.union(overlayRes.boundsOnScreen)
                                            }
                                        }
                                    }

                                    val nsslTop = state.notificationShelfTop.floatValue
                                    if (nsslTop > 0f && isDraggingClock && sharedPathBounds.bottom > nsslTop) {
                                        recordingCanvas.drawLine(
                                            16f * densityVal, nsslTop,
                                            realW - (16f * densityVal), nsslTop,
                                            notificationPaint
                                        )
                                    }

                                    val sensorR = state.udfpsRadius.floatValue
                                    if (sensorR > 0f && state.udfpsX.floatValue > 0f) {
                                        val cx = state.udfpsX.floatValue
                                        val cy = state.udfpsY.floatValue
                                        val sensorBufferPx = sensorR * 0.15f
                                        val isOverlapping = (cx in (sharedPathBounds.left - sensorBufferPx)..(sharedPathBounds.right + sensorBufferPx)) &&
                                            (cy in (sharedPathBounds.top - sensorBufferPx)..(sharedPathBounds.bottom + sensorBufferPx))

                                        udfpsPaint.color = if (isOverlapping) {
                                            android.graphics.Color.argb(230, 255, 82, 82)
                                        } else {
                                            android.graphics.Color.argb(90, 255, 255, 255)
                                        }
                                        udfpsPaint.strokeWidth = (if (isOverlapping) 2.5f else 1.5f) * densityVal
                                        recordingCanvas.drawCircle(cx, cy, sensorR, udfpsPaint)
                                    }
                                } finally {
                                    hardwareRenderNode.endRecording()
                                }
                                nativeCanvas.drawRenderNode(hardwareRenderNode)
                            } else {
                                nativeCanvas.save()
                                nativeCanvas.scale(previewScale, previewScale)
                                previewOriginal?.let { bmp ->
                                    LayerCompositor.composite(
                                        canvas = nativeCanvas,
                                        screenW = realW,
                                        screenH = realH,
                                        originalBmp = bmp,
                                        maskBmp = previewMask,
                                        zoom = state.liveWallpaperZoom.floatValue,
                                        stack = state.layerStack,
                                        focusedLayerType = focusedLayerType,
                                        drawClockAndDate = drawClockAndDateRoutine
                                    )
                                }
                                capturedFrameCtx?.let { fCtx ->
                                    if (state.layerStack.clockLayer.isVisible) {
                                        val overlayRes = ClockKitEngine.renderPreviewOverlay(
                                            canvas = nativeCanvas,
                                            style = activeStyle,
                                            frame = fCtx,
                                            config = cfg
                                        )
                                        if (overlayRes != null) {
                                            sharedPathBounds.union(overlayRes.boundsOnScreen)
                                        }
                                    }
                                }
                                nativeCanvas.restore()
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
