package com.android.CaveArt

import android.graphics.Bitmap
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

data class PopEligibility(
    val isEligible: Boolean,
    val isAbstract: Boolean = false,
    val reason: String,
    val subjectBounds: RectF?,
    val coveragePercent: Float,
    val borderDistances: BorderDistances?
)

data class BorderDistances(
    val fromLeft: Float,
    val fromTop: Float,
    val fromRight: Float,
    val fromBottom: Float
)

data class PopFraming(
    val shapeBounds: RectF,
    val popClipTop: Float,
    val isPopActive: Boolean
)

data class SafeScaleRange(
    val minScale: Float,
    val maxScale: Float,
    val defaultScale: Float
)

object SubjectPopAnalyzer {

    private const val MIN_WIDTH_RATIO = 0.12f
    private const val MIN_HEIGHT_RATIO = 0.10f
    private const val MIN_PIXEL_COVERAGE = 0.025f
    private const val MAX_COVERAGE_RATIO = 0.82f
    private const val SAFE_BORDER_PADDING_RATIO = 0.02f

    /**
     * Inspects the mask with solidity, headroom, and border-touch checks
     * to identify real subjects vs. abstract/pattern wallpapers.
     */
    fun analyzeEligibility(mask: Bitmap?): PopEligibility {
        if (mask == null) {
            return PopEligibility(
                isEligible = false,
                isAbstract = true,
                reason = "Abstract wallpaper: 3D Pop disabled for clean framing",
                subjectBounds = null,
                coveragePercent = 0f,
                borderDistances = null
            )
        }

        val w = mask.width
        val h = mask.height
        val totalPixels = w * h
        val pixels = IntArray(totalPixels)
        mask.getPixels(pixels, 0, w, 0, 0, w, h)

        var minX = w
        var maxX = 0
        var minY = h
        var maxY = 0
        var visiblePixels = 0

        for (y in 0 until h) {
            val offset = y * w
            for (x in 0 until w) {
                val alpha = (pixels[offset + x] ushr 24) and 0xFF
                if (alpha > 45) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                    visiblePixels++
                }
            }
        }

        if (visiblePixels == 0 || minX >= maxX || minY >= maxY) {
            return PopEligibility(
                isEligible = false,
                isAbstract = true,
                reason = "Abstract pattern: 3D Pop disabled for clean framing",
                subjectBounds = null,
                coveragePercent = 0f,
                borderDistances = null
            )
        }

        val sW = (maxX - minX).toFloat()
        val sH = (maxY - minY).toFloat()
        val coverage = visiblePixels.toFloat() / totalPixels.toFloat()

        val borderDistances = BorderDistances(
            fromLeft = minX.toFloat(),
            fromTop = minY.toFloat(),
            fromRight = (w - maxX).toFloat(),
            fromBottom = (h - maxY).toFloat()
        )

        val touchesTop = minY < (h * 0.035f)
        val touchesBottom = maxY > (h * 0.965f)
        val touchesLeft = minX < (w * 0.035f)
        val touchesRight = maxX > (w * 0.965f)
        
        if (touchesTop && (touchesLeft || touchesRight)) {
            return PopEligibility(
                isEligible = false,
                isAbstract = true,
                reason = "Abstract wallpaper: 3D Pop disabled for clean framing",
                subjectBounds = RectF(minX.toFloat(), minY.toFloat(), maxX.toFloat(), maxY.toFloat()),
                coveragePercent = coverage * 100f,
                borderDistances = borderDistances
            )
        }

        if (touchesTop && touchesBottom) {
            return PopEligibility(
                isEligible = false,
                isAbstract = true,
                reason = "Full-bleed background: 3D Pop disabled for clean framing",
                subjectBounds = RectF(minX.toFloat(), minY.toFloat(), maxX.toFloat(), maxY.toFloat()),
                coveragePercent = coverage * 100f,
                borderDistances = borderDistances
            )
        }
        
        val boundingArea = sW * sH
        val fillRatio = visiblePixels.toFloat() / boundingArea.coerceAtLeast(1f)
        if (fillRatio < 0.28f && coverage > 0.08f) {
            return PopEligibility(
                isEligible = false,
                isAbstract = true,
                reason = "Abstract pattern: 3D Pop disabled for clean framing",
                subjectBounds = RectF(minX.toFloat(), minY.toFloat(), maxX.toFloat(), maxY.toFloat()),
                coveragePercent = coverage * 100f,
                borderDistances = borderDistances
            )
        }
        
        if (sW < w * MIN_WIDTH_RATIO || sH < h * MIN_HEIGHT_RATIO || coverage < MIN_PIXEL_COVERAGE) {
            return PopEligibility(
                isEligible = false,
                isAbstract = false,
                reason = "Subject too small for depth pop (${(coverage * 100).toInt()}%)",
                subjectBounds = RectF(minX.toFloat(), minY.toFloat(), maxX.toFloat(), maxY.toFloat()),
                coveragePercent = coverage * 100f,
                borderDistances = borderDistances
            )
        }

        if (coverage > MAX_COVERAGE_RATIO && (sW > w * 0.92f && sH > h * 0.92f)) {
            return PopEligibility(
                isEligible = false,
                isAbstract = true,
                reason = "Full-bleed photo: 3D Pop disabled for clean framing",
                subjectBounds = RectF(minX.toFloat(), minY.toFloat(), maxX.toFloat(), maxY.toFloat()),
                coveragePercent = coverage * 100f,
                borderDistances = borderDistances
            )
        }

        return PopEligibility(
            isEligible = true,
            isAbstract = false,
            reason = "3D Pop Ready",
            subjectBounds = RectF(minX.toFloat(), minY.toFloat(), maxX.toFloat(), maxY.toFloat()),
            coveragePercent = coverage * 100f,
            borderDistances = borderDistances
        )
    }

    fun calculateSafeScaleRange(
        subjectBounds: RectF?,
        imgW: Int,
        imgH: Int
    ): SafeScaleRange {
        if (subjectBounds == null || imgW <= 0 || imgH <= 0) {
            return SafeScaleRange(minScale = 0.85f, maxScale = 1.35f, defaultScale = 1.0f)
        }

        val distToClosestEdge = minOf(
            subjectBounds.left,
            imgW - subjectBounds.right,
            subjectBounds.top,
            imgH - subjectBounds.bottom
        ).coerceAtLeast(0f)

        val edgeProximityRatio = distToClosestEdge / minOf(imgW, imgH).toFloat()

        val minScale = when {
            edgeProximityRatio < 0.05f -> 0.92f
            edgeProximityRatio < 0.12f -> 0.85f
            else -> 0.75f
        }

        return SafeScaleRange(
            minScale = minScale,
            maxScale = 1.35f,
            defaultScale = 1.0f
        )
    }

    fun calculatePopFraming(
        subjectBounds: RectF,
        imgW: Int,
        imgH: Int,
        is3DPopEnabled: Boolean
    ): PopFraming {
        val sW = subjectBounds.width()
        val sH = subjectBounds.height()
        val sCenterX = subjectBounds.centerX()
        val sCenterY = subjectBounds.centerY()

        val safePadX = imgW * SAFE_BORDER_PADDING_RATIO
        val safePadY = imgH * SAFE_BORDER_PADDING_RATIO

        if (!is3DPopEnabled) {
            val maxAllowedRadiusX = min(sCenterX - safePadX, (imgW - safePadX) - sCenterX)
            val maxAllowedRadiusY = min(sCenterY - safePadY, (imgH - safePadY) - sCenterY)
            val maxAllowedRadius = min(maxAllowedRadiusX, maxAllowedRadiusY).coerceAtLeast(50f)

            val idealRadius = max(sW, sH) * 0.70f
            val radius = min(idealRadius, maxAllowedRadius)

            val bounds = RectF(
                sCenterX - radius,
                sCenterY - radius,
                sCenterX + radius,
                sCenterY + radius
            )
            return PopFraming(bounds, popClipTop = 0f, isPopActive = false)
        }

        val idealRadius = max(sW * 0.60f, sH * 0.75f)
        val breakoutRatio = 0.20f
        val popHorizonY = subjectBounds.top + (sH * breakoutRatio)

        val maxBottomSpace = (imgH - safePadY) - popHorizonY
        val clampedRadius = min(idealRadius, maxBottomSpace / 2f).coerceAtLeast(sW * 0.45f)

        var shapeCenterY = popHorizonY + clampedRadius
        var shapeCenterX = sCenterX

        if (shapeCenterY + clampedRadius > imgH - safePadY) {
            shapeCenterY = (imgH - safePadY) - clampedRadius
        }
        if (shapeCenterX - clampedRadius < safePadX) {
            shapeCenterX = safePadX + clampedRadius
        }
        if (shapeCenterX + clampedRadius > imgW - safePadX) {
            shapeCenterX = (imgW - safePadX) - clampedRadius
        }

        val shapeBounds = RectF(
            (shapeCenterX - clampedRadius).coerceAtLeast(safePadX),
            (shapeCenterY - clampedRadius).coerceAtLeast(safePadY),
            (shapeCenterX + clampedRadius).coerceAtMost(imgW - safePadX),
            (shapeCenterY + clampedRadius).coerceAtMost(imgH - safePadY)
        )

        return PopFraming(
            shapeBounds = shapeBounds,
            popClipTop = subjectBounds.top,
            isPopActive = true
        )
    }
}
