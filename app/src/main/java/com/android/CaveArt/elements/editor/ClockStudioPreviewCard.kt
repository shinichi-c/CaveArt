package com.android.CaveArt

import android.graphics.Bitmap
import android.graphics.CornerPathEffect
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
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
    currentLayerActions: List<LayerAction>,
    activeActionIndex: Int,
    onActionSelected: (Int) -> Unit,
    sharedElementModifier: Modifier = Modifier,
    onDateClick: () -> Unit,
    onClockClick: () -> Unit
) {
    val view = LocalView.current
    val realW = displayGeometry.widthPx
    val realH = displayGeometry.heightPx
    val densityVal = displayGeometry.density
    val statusBarDp = displayGeometry.statusBarHeightPx / densityVal
    val navBarDp = displayGeometry.navBarHeightPx / densityVal
    val minSafeYDp = statusBarDp + (displayGeometry.statusBarHeightPx * 0.12f / densityVal)
    val minClockSizeDp = (realW * 0.08f) / densityVal
    val maxClockSizeDp = (realW * 0.55f) / densityVal
    val magneticSnapThresholdDp = (realW * 0.025f) / densityVal
    val cardCornerRadiusDp = (displayGeometry.physicalCornerRadiusPx / densityVal).coerceAtLeast(0f)

    var isDraggingClock by remember { mutableStateOf(false) }
    var isDraggingDate by remember { mutableStateOf(false) }

    val vectorPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL_AND_STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            setShadowLayer(15f, 0f, 5f, android.graphics.Color.argb(160, 0, 0, 0))
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
    val combinedClockPath = remember { Path() }
    val sharedPathBounds = remember { RectF() }

    val hardwareRenderNode = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RenderNode("LockscreenPreviewHardwareNode").apply {
                setHasOverlappingRendering(false)
                clipToBounds = false
            }
        } else null
    }

    LaunchedEffect(
        state.clockX.floatValue, state.clockY.floatValue, state.dateAttached.value,
        state.hourSize.floatValue, state.clockLayout.intValue, state.dateSize.floatValue,
        state.stretchEnabled.value, dateText, timeString, customTypeface, state.strokeWidth.floatValue
    ) {
        val sideSafeMarginDp = 18f
        val halfScreenDp = realW / densityVal / 2f
        val hPx = state.hourSize.floatValue * densityVal
        val mPx = state.minSize.floatValue * densityVal
        val isVert = state.clockLayout.intValue == 1
        val sampleTime = if (timeString.isNotEmpty()) timeString else "00:00"
        val totalClockWidthPx = AdaptiveClockHelper.measureClockWidth(sampleTime, hPx, mPx, customTypeface, isVert)
        val strokePaddingPx = (state.strokeWidth.floatValue * densityVal) + (4f * densityVal)
        val clockWidthDp = (totalClockWidthPx + strokePaddingPx) / densityVal
        val halfClockWDp = clockWidthDp / 2f
        val minSafeClockX = -halfScreenDp + halfClockWDp + sideSafeMarginDp
        val maxSafeClockX = halfScreenDp - halfClockWDp - sideSafeMarginDp

        if (minSafeClockX <= maxSafeClockX) {
            state.clockX.floatValue = state.clockX.floatValue.coerceIn(minSafeClockX, maxSafeClockX)
        } else {
            state.clockX.floatValue = 0f
        }

        datePaint.textSize = state.dateSize.floatValue * densityVal
        val sampleDate = if (dateText.isNotEmpty()) dateText else "Wednesday, 14 October"
        val dateWidthDp = datePaint.measureText(sampleDate) / densityVal
        val halfDateWDp = dateWidthDp / 2f
        val minSafeDateX = -halfScreenDp + halfDateWDp + sideSafeMarginDp
        val maxSafeDateX = halfScreenDp - halfDateWDp - sideSafeMarginDp

        if (state.dateAttached.value) {
            state.dateX.floatValue = if (minSafeDateX <= maxSafeDateX) {
                state.clockX.floatValue.coerceIn(minSafeDateX, maxSafeDateX)
            } else 0f

            val fontMetrics = datePaint.fontMetrics
            val dateLineHeightDp = (fontMetrics.descent - fontMetrics.ascent) / densityVal
            val dynamicGapDp = (state.strokeWidth.floatValue * 0.4f) + (dateLineHeightDp * 0.35f)
            val compoundDateOffsetDp = dateLineHeightDp + dynamicGapDp
            val idealAboveYDp = state.clockY.floatValue - compoundDateOffsetDp
            val touchesTopBorder = idealAboveYDp < minSafeYDp

            if (state.stretchEnabled.value) {
                state.dateY.floatValue = max(minSafeYDp, idealAboveYDp)
            } else {
                if (touchesTopBorder) {
                    val clockEstimatedHDp = if (isVert) {
                        (state.hourSize.floatValue * 2.15f) + (state.strokeWidth.floatValue * 0.5f)
                    } else {
                        (state.hourSize.floatValue * 1.15f) + (state.strokeWidth.floatValue * 0.5f)
                    }
                    state.dateY.floatValue = state.clockY.floatValue + clockEstimatedHDp + dynamicGapDp
                } else {
                    state.dateY.floatValue = max(minSafeYDp, idealAboveYDp)
                }
            }
        }
    }

    val datePhysicsSpringY = remember { Animatable(state.dateY.floatValue) }
    LaunchedEffect(
        isDraggingDate, isDraggingClock, state.dateAttached.value,
        state.clockX.floatValue, state.clockY.floatValue, state.dateX.floatValue,
        state.hourSize.floatValue, state.clockLayout.intValue
    ) {
        if (!state.dateAttached.value && !isDraggingDate && !isDraggingClock) {
            datePaint.textSize = state.dateSize.floatValue * densityVal
            val sampleDate = if (dateText.isNotEmpty()) dateText else "Wednesday, 14 October"
            val dateWDp = datePaint.measureText(sampleDate) / densityVal
            val dateHDp = (datePaint.fontMetrics.descent - datePaint.fontMetrics.ascent) / densityVal
            val nativeDateCenterX = (realW / densityVal / 2f) + state.dateX.floatValue
            val dateLeft = nativeDateCenterX - (dateWDp / 2f)
            val dateRight = nativeDateCenterX + (dateWDp / 2f)
            val dateTop = state.dateY.floatValue
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
                    datePhysicsSpringY.snapTo(state.dateY.floatValue)
                    datePhysicsSpringY.animateTo(
                        targetValue = safeClampedY,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) {
                        state.dateY.floatValue = value
                    }
                }
            }
        }
    }

    val gestureModifier = if (!isMlLoading) {
        Modifier.pointerInput(realW, realH) {
            var rawClockX = state.clockX.floatValue
            var rawClockY = state.clockY.floatValue
            var rawDateX = state.dateX.floatValue
            var rawDateY = state.dateY.floatValue

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = true)
                if (down.isConsumed) return@awaitEachGesture
                val timeStart = System.currentTimeMillis()
                var dragged = false

                val previewScale = size.width / realW
                val scaledDateY = (state.dateY.floatValue * densityVal) * previewScale
                val scaledClockY = (state.clockY.floatValue * densityVal) * previewScale
                val distToDate = abs(down.position.y - scaledDateY)
                val distToClock = abs(down.position.y - scaledClockY)
                val dateHitThresholdPx = (state.dateSize.floatValue * densityVal * 1.5f) * previewScale
                val activeDrag = if (distToDate < distToClock && distToDate < dateHitThresholdPx) "DATE" else "CLOCK"

                rawClockX = state.clockX.floatValue
                rawClockY = state.clockY.floatValue
                rawDateX = state.dateX.floatValue
                rawDateY = state.dateY.floatValue

                do {
                    val event = awaitPointerEvent()
                    val pan = event.calculatePan()
                    val zoom = event.calculateZoom()

                    if (pan.getDistance() > 2f || abs(zoom - 1f) > 0.01f) dragged = true

                    if (dragged) {
                        val deltaNativeDpX = (pan.x / previewScale) / densityVal
                        val deltaNativeDpY = (pan.y / previewScale) / densityVal
                        val maxClockSafeYDp = (realH / densityVal) - navBarDp - (state.hourSize.floatValue * 0.75f)
                        val maxDateSafeYDp = (realH / densityVal) - navBarDp - (state.dateSize.floatValue * 1.5f)

                        val sideSafeMarginDp = 18f
                        val halfScreenDp = realW / densityVal / 2f

                        val sampleDate = if (dateText.isNotEmpty()) dateText else "Wednesday, 14 October"
                        val dateWidthDp = datePaint.measureText(sampleDate) / densityVal
                        val halfDateWDp = dateWidthDp / 2f
                        val minSafeDateX = -halfScreenDp + halfDateWDp + sideSafeMarginDp
                        val maxSafeDateX = halfScreenDp - halfDateWDp - sideSafeMarginDp

                        val hPx = state.hourSize.floatValue * densityVal
                        val mPx = state.minSize.floatValue * densityVal
                        val isVert = state.clockLayout.intValue == 1
                        val sampleTime = if (timeString.isNotEmpty()) timeString else "00:00"
                        val rawClockWidthPx = AdaptiveClockHelper.measureClockWidth(sampleTime, hPx, mPx, customTypeface, isVert)
                        val strokePaddingPx = (state.strokeWidth.floatValue * densityVal) + (4f * densityVal)
                        val clockWidthDp = (rawClockWidthPx + strokePaddingPx) / densityVal
                        val halfClockWDp = clockWidthDp / 2f
                        val minSafeClockX = -halfScreenDp + halfClockWDp + sideSafeMarginDp
                        val maxSafeClockX = halfScreenDp - halfClockWDp - sideSafeMarginDp

                        if (activeDrag == "DATE" && !state.dateAttached.value) {
                            isDraggingDate = true
                            rawDateX += deltaNativeDpX
                            rawDateY += deltaNativeDpY
                            state.dateX.floatValue = if (minSafeDateX <= maxSafeDateX) {
                                rawDateX.coerceIn(minSafeDateX, maxSafeDateX)
                            } else 0f
                            state.dateY.floatValue = rawDateY.coerceIn(minSafeYDp, maxDateSafeYDp)
                            rawDateX = state.dateX.floatValue
                            rawDateY = state.dateY.floatValue
                        } else {
                            isDraggingClock = true
                            state.hourSize.floatValue = (state.hourSize.floatValue * zoom).coerceIn(minClockSizeDp, maxClockSizeDp)
                            state.minSize.floatValue = (state.minSize.floatValue * zoom).coerceIn(minClockSizeDp, maxClockSizeDp)
                            rawClockX += deltaNativeDpX
                            rawClockY += deltaNativeDpY
                            val fontMetrics = datePaint.fontMetrics
                            val dateLineHeightDp = (fontMetrics.descent - fontMetrics.ascent) / densityVal
                            val dynamicGapDp = (state.strokeWidth.floatValue * 0.4f) + (dateLineHeightDp * 0.35f)
                            val compoundDateOffsetDp = dateLineHeightDp + dynamicGapDp

                            val effectiveMinClockYDp = if (state.dateAttached.value && state.stretchEnabled.value) {
                                minSafeYDp + compoundDateOffsetDp
                            } else {
                                minSafeYDp
                            }

                            state.clockX.floatValue = if (minSafeClockX <= maxSafeClockX) {
                                rawClockX.coerceIn(minSafeClockX, maxSafeClockX)
                            } else 0f
                            state.clockY.floatValue = rawClockY.coerceIn(effectiveMinClockYDp, maxClockSafeYDp)
                            rawClockX = state.clockX.floatValue
                            rawClockY = state.clockY.floatValue
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

                if (abs(state.clockX.floatValue) < magneticSnapThresholdDp) {
                    state.clockX.floatValue = 0f
                    rawClockX = 0f
                }
                if (!state.dateAttached.value && abs(state.dateX.floatValue) < magneticSnapThresholdDp) {
                    state.dateX.floatValue = 0f
                    rawDateX = 0f
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

                        drawIntoCanvas { canvas ->
                            val nativeCanvas = canvas.nativeCanvas

                            val drawClockAndDateRoutine: (android.graphics.Canvas) -> Unit = { targetCanvas ->
                                if (dateText.isNotEmpty()) {
                                    datePaint.color = state.clockColor.intValue
                                    datePaint.textSize = state.dateSize.floatValue * densityVal
                                    val nativeDateX = (realW / 2f) + (state.dateX.floatValue * densityVal)
                                    val nativeDateY = state.dateY.floatValue * densityVal
                                    val drawBaselineY = nativeDateY - datePaint.fontMetrics.top
                                    targetCanvas.drawText(dateText, nativeDateX, drawBaselineY, datePaint)
                                }

                                if (timeString.isNotEmpty()) {
                                    val hPx = state.hourSize.floatValue * densityVal
                                    val mPx = state.minSize.floatValue * densityVal
                                    val isVert = state.clockLayout.intValue == 1
                                    val totalWidth = AdaptiveClockHelper.measureClockWidth(timeString, hPx, mPx, customTypeface, isVert)
                                    val nativeClockCenterX = (realW / 2f) + (state.clockX.floatValue * densityVal)
                                    val startX = nativeClockCenterX - (totalWidth / 2f)
                                    val startY = state.clockY.floatValue * densityVal

                                    val paths = AdaptiveClockHelper.buildPaths(
                                        timeString = timeString,
                                        startX = startX,
                                        startY = startY,
                                        absoluteClockX = 0f,
                                        absoluteClockY = 0f,
                                        hourSizePx = hPx,
                                        minSizePx = mPx,
                                        typeface = customTypeface,
                                        screenW = realW,
                                        screenH = realH,
                                        isStretchEnabled = state.stretchEnabled.value,
                                        isVertical = isVert,
                                        collisionMap = collisionMapArray,
                                        density = densityVal,
                                        strokeWidth = state.strokeWidth.floatValue,
                                        stretchProgress = 1f
                                    )
                                    
                                    if (state.gravityEnabled.value) {
                                        val rawDepth = state.layerStack.depthVisionLayer.rawDepthMap
                                        val rawBounds = RectF()
                                        val tempPath = Path().apply {
                                            addPath(paths.hours)
                                            addPath(paths.colon)
                                            addPath(paths.mins)
                                            computeBounds(rawBounds, true)
                                        }

                                        val perspective = PerspectiveFitHelper.calculateAutoPerspective(
                                            rawDepthMap = rawDepth,
                                            clockRect = rawBounds,
                                            sampleRectOnScreen = rawBounds,
                                            screenW = realW,
                                            screenH = realH,
                                            intensity = state.gravityIntensity.floatValue,
                                            mode = state.gravityMode.value
                                        )

                                        paths.hours.transform(perspective.transformMatrix)
                                        paths.colon.transform(perspective.transformMatrix)
                                        paths.mins.transform(perspective.transformMatrix)
                                    }

                                    combinedClockPath.rewind()
                                    combinedClockPath.addPath(paths.hours)
                                    combinedClockPath.addPath(paths.colon)
                                    combinedClockPath.addPath(paths.mins)
                                    combinedClockPath.computeBounds(sharedPathBounds, true)

                                    if (state.layerStack.isDepthEnabled && previewMask != null) {
                                        val ratio = AdaptiveClockHelper.calculateOcclusionRatio(
                                            clockPath = combinedClockPath,
                                            mask = previewMask,
                                            screenW = realW,
                                            screenH = realH,
                                            clockAbsoluteX = 0f,
                                            clockAbsoluteY = 0f,
                                            zoomFactor = state.liveWallpaperZoom.floatValue
                                        )
                                        val isOccludedTooMuch = ratio > state.layerStack.depthOcclusionLimit
                                        state.layerStack.clockLayer.zIndex = if (isOccludedTooMuch) 3 else 1
                                    }

                                    vectorPaint.strokeWidth = state.strokeWidth.floatValue * densityVal
                                    vectorPaint.pathEffect = CornerPathEffect(state.roundness.floatValue * densityVal)
                                    vectorPaint.color = if (state.dualTone.value) android.graphics.Color.WHITE else state.clockColor.intValue
                                    targetCanvas.drawPath(paths.hours, vectorPaint)
                                    targetCanvas.drawPath(paths.colon, vectorPaint)

                                    vectorPaint.color = state.clockColor.intValue
                                    targetCanvas.drawPath(paths.mins, vectorPaint)
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
                                nativeCanvas.restore()
                            }
                        }

                        if (isDraggingClock) {
                            val scale = size.width / realW
                            val padXPx = (state.strokeWidth.floatValue * densityVal + 8f * densityVal) * scale
                            val padYPx = (state.strokeWidth.floatValue * densityVal * 0.5f + 6f * densityVal) * scale

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

                    if (currentLayerActions.size > 1) {
                        VerticalLayerActionSwitcher(
                            actions = currentLayerActions,
                            selectedIndex = activeActionIndex,
                            onActionSelected = onActionSelected,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 12.dp)
                        )
                    }
                }
            }
        }
    }
}
