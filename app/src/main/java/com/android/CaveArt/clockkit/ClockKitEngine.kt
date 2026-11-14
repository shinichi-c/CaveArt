package com.android.CaveArt.clockkit

import android.graphics.*
import com.android.CaveArt.AdaptiveClockHelper
import com.android.CaveArt.PerspectiveFitHelper
import com.android.CaveArt.clockkit.styles.AdaptiveVectorClockStyle
import com.android.CaveArt.clockkit.styles.EditorialSplitClockStyle
import com.android.CaveArt.clockkit.styles.MonolithClockStyle
import kotlin.math.max

object ClockKitEngine {

    val styles: List<ClockStyle> = listOf(
        AdaptiveVectorClockStyle(),
        EditorialSplitClockStyle(),
        MonolithClockStyle()
    )

    fun getStyle(styleId: String): ClockStyle {
        val resolvedId = if (styleId == "hyper_poster") "monolith" else styleId
        return styles.firstOrNull { it.id == resolvedId } ?: styles.first()
    }

    private class EngineScratchpad {
        val maskMatrix = Matrix()
        val erasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
    }

    private val scratchpad = ThreadLocal.withInitial { EngineScratchpad() }

    fun renderForPreview(
        canvas: Canvas,
        style: ClockStyle,
        frame: ClockFrameContext,
        config: ClockKitConfig
    ): Pair<ClockRenderResult, Boolean> {
        val gravityMatrix = computeGravityMatrix(style, frame, config)
        val result = if (gravityMatrix != null) {
            canvas.save()
            canvas.concat(gravityMatrix)
            val res = style.render(canvas, frame, config)
            canvas.restore()
            val mappedBounds = RectF(res.boundsOnScreen)
            gravityMatrix.mapRect(mappedBounds)
            res.occlusionPath?.transform(gravityMatrix)
            res.copy(boundsOnScreen = mappedBounds)
        } else {
            style.render(canvas, frame, config)
        }

        val mask = frame.depthMask
        var shouldElevateAboveSubject = false
        if (config.isDepthEnabled && mask != null && !mask.isRecycled) {
            val ratio = computeOcclusionRatio(result, mask, frame)
            if (ratio > config.depthOcclusionLimit) {
                shouldElevateAboveSubject = true
            }
        }
        return result to shouldElevateAboveSubject
    }

    fun renderPreviewOverlay(
        canvas: Canvas,
        style: ClockStyle,
        frame: ClockFrameContext,
        config: ClockKitConfig
    ): ClockRenderResult? {
        val gravityMatrix = computeGravityMatrix(style, frame, config)
        return if (gravityMatrix != null) {
            canvas.save()
            canvas.concat(gravityMatrix)
            val res = style.renderOverlay(canvas, frame, config)
            canvas.restore()
            res?.let {
                val mappedBounds = RectF(it.boundsOnScreen)
                gravityMatrix.mapRect(mappedBounds)
                it.copy(boundsOnScreen = mappedBounds)
            }
        } else {
            style.renderOverlay(canvas, frame, config)
        }
    }

    fun renderForSystemUI(
        canvas: Canvas,
        style: ClockStyle,
        frame: ClockFrameContext,
        config: ClockKitConfig
    ): ClockRenderResult {
        val mask = frame.depthMask
        val canDepthMask = config.isDepthEnabled &&
            !style.handlesDepthManually &&
            frame.notificationProgress < 0.2f &&
            mask != null && !mask.isRecycled

        val gravityMatrix = computeGravityMatrix(style, frame, config)

        val baseResult = if (!canDepthMask) {
            renderWithMatrix(canvas, style, frame, config, gravityMatrix)
        } else {
            val scratch = scratchpad.get()!!
            val layerId = canvas.saveLayer(0f, 0f, frame.screenW, frame.screenH, null)

            val res = renderWithMatrix(canvas, style, frame, config, gravityMatrix)
            val occlusionRatio = computeOcclusionRatio(res, mask!!, frame)

            if (occlusionRatio <= config.depthOcclusionLimit) {
                val baseScale = max(
                    frame.screenW / mask.width.toFloat(),
                    frame.screenH / mask.height.toFloat()
                ) * frame.zoomFactor
                val tx = (frame.screenW - mask.width * baseScale) / 2f
                val ty = (frame.screenH - mask.height * baseScale) / 2f

                scratch.maskMatrix.reset()
                scratch.maskMatrix.postScale(baseScale, baseScale)
                scratch.maskMatrix.postTranslate(tx, ty)

                canvas.drawBitmap(mask, scratch.maskMatrix, scratch.erasePaint)
            }

            canvas.restoreToCount(layerId)
            res
        }

        val overlayResult = if (gravityMatrix != null) {
            canvas.save()
            canvas.concat(gravityMatrix)
            val res = style.renderOverlay(canvas, frame, config)
            canvas.restore()
            res?.let {
                val mapped = RectF(it.boundsOnScreen)
                gravityMatrix.mapRect(mapped)
                it.copy(boundsOnScreen = mapped)
            }
        } else {
            style.renderOverlay(canvas, frame, config)
        }

        if (overlayResult != null) {
            val combinedBounds = RectF(baseResult.boundsOnScreen)
            combinedBounds.union(overlayResult.boundsOnScreen)
            return baseResult.copy(boundsOnScreen = combinedBounds)
        }
        return baseResult
    }

    private fun renderWithMatrix(
        canvas: Canvas,
        style: ClockStyle,
        frame: ClockFrameContext,
        config: ClockKitConfig,
        gravityMatrix: Matrix?
    ): ClockRenderResult {
        if (gravityMatrix == null) return style.render(canvas, frame, config)
        canvas.save()
        canvas.concat(gravityMatrix)
        val res = style.render(canvas, frame, config)
        canvas.restore()
        val mappedBounds = RectF(res.boundsOnScreen)
        gravityMatrix.mapRect(mappedBounds)
        res.occlusionPath?.transform(gravityMatrix)
        return res.copy(boundsOnScreen = mappedBounds)
    }

    private fun computeGravityMatrix(
        style: ClockStyle,
        frame: ClockFrameContext,
        config: ClockKitConfig
    ): Matrix? {
        if (!config.isGravityEnabled ||
            style.handlesGravityManually ||
            frame.rawDepthMap == null ||
            frame.rawDepthMap.isRecycled
        ) {
            return null
        }

        val measured = style.measure(
            ClockMeasureContext(
                context = frame.context,
                screenW = frame.screenW,
                screenH = frame.screenH,
                density = frame.density,
                typeface = frame.typeface,
                timeString = frame.timeString,
                dateString = frame.dateString,
                notificationProgress = frame.notificationProgress
            ),
            config
        )

        val approxRect = RectF(
            frame.clockCenterX - (measured.contentWidthPx / 2f),
            frame.clockTopY,
            frame.clockCenterX + (measured.contentWidthPx / 2f),
            frame.clockTopY + measured.contentHeightPx
        )

        return PerspectiveFitHelper.calculateAutoPerspective(
            rawDepthMap = frame.rawDepthMap,
            clockRect = approxRect,
            sampleRectOnScreen = approxRect,
            screenW = frame.screenW,
            screenH = frame.screenH,
            intensity = config.gravityIntensity,
            mode = config.gravityMode
        ).transformMatrix
    }

    private fun computeOcclusionRatio(
        result: ClockRenderResult,
        mask: Bitmap,
        frame: ClockFrameContext
    ): Float {
        if (result.occlusionPath != null && !result.occlusionPath.isEmpty) {
            return AdaptiveClockHelper.calculateOcclusionRatio(
                clockPath = result.occlusionPath,
                mask = mask,
                screenW = frame.screenW,
                screenH = frame.screenH,
                clockAbsoluteX = 0f,
                clockAbsoluteY = 0f,
                zoomFactor = frame.zoomFactor
            )
        }

        val b = result.boundsOnScreen
        if (b.width() <= 0f || b.height() <= 0f) return 0f

        val mw = mask.width.toFloat()
        val mh = mask.height.toFloat()
        val fillScale = max(frame.screenW / mw, frame.screenH / mh) * frame.zoomFactor
        val tx = (frame.screenW - mw * fillScale) / 2f
        val ty = (frame.screenH - mh * fillScale) / 2f

        val steps = 16
        var hits = 0
        var total = 0
        for (ix in 0 until steps) {
            val sx = b.left + (ix + 0.5f) * (b.width() / steps)
            val mx = ((sx - tx) / fillScale).toInt()
            if (mx !in 0 until mask.width) continue
            for (iy in 0 until steps) {
                val sy = b.top + (iy + 0.5f) * (b.height() / steps)
                val my = ((sy - ty) / fillScale).toInt()
                if (my in 0 until mask.height) {
                    total++
                    val alpha = (mask.getPixel(mx, my) ushr 24) and 0xFF
                    if (alpha > 45) hits++
                }
            }
        }
        return if (total > 0) hits.toFloat() / total.toFloat() else 0f
    }
}
