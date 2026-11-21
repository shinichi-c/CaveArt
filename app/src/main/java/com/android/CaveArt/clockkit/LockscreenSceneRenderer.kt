package com.android.CaveArt.clockkit

import android.content.Context
import android.graphics.*
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class LockscreenSceneParams(
    val screenW: Float,
    val screenH: Float,
    val density: Float,
    val wallpaperBitmap: Bitmap?,
    val depthMaskBitmap: Bitmap?,
    val rawDepthBitmap: Bitmap?,
    val collisionMap: FloatArray?,
    val config: ClockKitConfig,
    val isDepthEnabled: Boolean,
    val wallpaperZoom: Float,
    val bgVibrance: Float = 0f,
    val bgOpacity: Float = 1f,
    val subjectOpacity: Float = 1f,
    val showUdfps: Boolean = false,
    val udfpsX: Float = -1f,
    val udfpsY: Float = -1f,
    val udfpsRadius: Float = 0f,
    val atGlanceSnapshot: AtAGlanceSnapshot? = null
)

object LockscreenSceneRenderer {

    fun computeDateSizeSp(cfg: ClockKitConfig): Float {
        return if (cfg.styleId == "adaptive_vector") {
            (cfg.getFloat("adaptive_vector", "hour_size", 100f) * 0.17f).coerceIn(14f, 20f)
        } else {
            (16.5f * cfg.scale).coerceIn(14f, 20f)
        }
    }

    fun clampConfigToScreen(
        context: Context,
        cfg: ClockKitConfig,
        screenW: Float,
        screenH: Float,
        density: Float,
        statusBarPadPx: Int,
        navBarPadPx: Int,
        resolveDetachedOverlap: Boolean = true
    ): ClockKitConfig {
        if (screenW <= 0f || screenH <= 0f || density <= 0f) return cfg
        val clockStyle = ClockKitEngine.getStyle(cfg.styleId)
        val now = System.currentTimeMillis()
        val is24 = DateFormat.is24HourFormat(context)
        val timeStr = SimpleDateFormat(if (is24) "HH:mm" else "hh:mm", Locale.getDefault())
            .format(Date(now)).ifEmpty { "09:41" }
        val dateStr = ClockTypefaceResolver.formatDate(now, cfg.dateFormat).ifEmpty { "Wednesday, 14 October" }
        val tf = ClockTypefaceResolver.resolve(context, cfg.font)

        val measured = clockStyle.measure(
            ClockMeasureContext(context, screenW, screenH, density, tf, timeStr, dateStr),
            cfg
        )

        val sideSafeMarginDp = 18f
        val halfScreenDp = screenW / density / 2f
        val clockWidthDp = measured.contentWidthPx / density
        val clockHeightDp = measured.contentHeightPx / density
        val halfClockWDp = clockWidthDp / 2f

        val minSafeClockX = (-halfScreenDp + halfClockWDp + sideSafeMarginDp).coerceAtMost(0f)
        val maxSafeClockX = (halfScreenDp - halfClockWDp - sideSafeMarginDp).coerceAtLeast(0f)

        val statusBarDp = statusBarPadPx / density
        val navBarDp = navBarPadPx / density
        val minSafeYDp = statusBarDp + (statusBarDp * 0.12f)
        val maxClockSafeYDp = ((screenH / density) - navBarDp - (clockHeightDp * 0.55f)).coerceAtLeast(minSafeYDp)

        val dateSp = computeDateSizeSp(cfg)
        val snapshot = AtAGlanceDataCollector.collect(context, cfg, now)
        val glanceMeasured = AtAGlanceRenderer.measure(cfg, snapshot, density, dateSp, screenW)
        val dateLineHeightDp = glanceMeasured.heightPx / density
        val dateWidthDp = glanceMeasured.widthPx / density
        val halfDateWDp = dateWidthDp / 2f
        val minSafeDateX = (-halfScreenDp + halfDateWDp + sideSafeMarginDp).coerceAtMost(0f)
        val maxSafeDateX = (halfScreenDp - halfDateWDp - sideSafeMarginDp).coerceAtLeast(0f)

        val strokeW = cfg.getFloat("adaptive_vector", "stroke_width", 8f)
        val dynamicGapDp = (strokeW * 0.4f) + (dateSp * 0.35f)
        val compoundDateOffsetDp = dateLineHeightDp + dynamicGapDp

        val isExternalDate = clockStyle.datePolicy(cfg) == ClockDatePolicy.EXTERNAL_STANDARD && cfg.atGlanceEnabled
        val effectiveMinClockYDp = if (isExternalDate && cfg.dateAttached && clockStyle.needsCollisionMap(cfg)) {
            minSafeYDp + compoundDateOffsetDp
        } else {
            minSafeYDp
        }

        val clampedClockX = cfg.clockX.coerceIn(minSafeClockX, maxSafeClockX)
        val clampedClockY = cfg.clockY.coerceIn(effectiveMinClockYDp, maxClockSafeYDp.coerceAtLeast(effectiveMinClockYDp))

        var finalDateX = cfg.dateX.coerceIn(minSafeDateX, maxSafeDateX)
        var finalDateY = cfg.dateY

        if (isExternalDate && cfg.dateAttached) {
            finalDateX = clampedClockX.coerceIn(minSafeDateX, maxSafeDateX)
            val idealAboveYDp = clampedClockY - compoundDateOffsetDp
            finalDateY = if (clockStyle.needsCollisionMap(cfg)) {
                max(minSafeYDp, idealAboveYDp)
            } else if (idealAboveYDp < minSafeYDp) {
                clampedClockY + clockHeightDp + dynamicGapDp
            } else {
                max(minSafeYDp, idealAboveYDp)
            }
        } else if (isExternalDate) {
            val maxDateSafeYDp = ((screenH / density) - navBarDp - dateLineHeightDp - 8f).coerceAtLeast(minSafeYDp)
            finalDateY = cfg.dateY.coerceIn(minSafeYDp, maxDateSafeYDp)
            if (resolveDetachedOverlap) {
                val clockLeft = halfScreenDp + clampedClockX - halfClockWDp
                val clockRight = halfScreenDp + clampedClockX + halfClockWDp
                val dateLeft = halfScreenDp + finalDateX - halfDateWDp
                val dateRight = halfScreenDp + finalDateX + halfDateWDp
                val marginDp = 16f
                val xOverlap = max(dateLeft, clockLeft) < min(dateRight, clockRight)
                val yOverlap = (finalDateY + dateLineHeightDp + marginDp > clampedClockY) &&
                    (finalDateY < clampedClockY + clockHeightDp + marginDp)
                if (xOverlap && yOverlap) {
                    val clockCenterY = clampedClockY + clockHeightDp / 2f
                    val dateCenterY = finalDateY + dateLineHeightDp / 2f
                    finalDateY = if (dateCenterY < clockCenterY) {
                        val above = clampedClockY - dateLineHeightDp - marginDp
                        if (above >= minSafeYDp) above else (clampedClockY + clockHeightDp + marginDp).coerceAtMost(maxDateSafeYDp)
                    } else {
                        val below = clampedClockY + clockHeightDp + marginDp
                        if (below <= maxDateSafeYDp) below else (clampedClockY - dateLineHeightDp - marginDp).coerceAtLeast(minSafeYDp)
                    }
                }
            }
        }

        return cfg.copy(
            clockX = clampedClockX,
            clockY = clampedClockY,
            dateX = finalDateX,
            dateY = finalDateY
        )
    }

    fun syncAttachedDatePosition(
        context: Context,
        cfg: ClockKitConfig,
        statusBarPadPx: Int,
        density: Float,
        screenW: Float,
        screenH: Float,
        navBarPadPx: Int = 0
    ): ClockKitConfig = clampConfigToScreen(context, cfg, screenW, screenH, density, statusBarPadPx, navBarPadPx, true)

    fun drawScene(
        context: Context,
        canvas: Canvas,
        viewW: Float,
        params: LockscreenSceneParams,
        outClockBounds: RectF? = null
    ) {
        val realW = params.screenW.coerceAtLeast(1f)
        val realH = params.screenH.coerceAtLeast(1f)
        val previewScale = viewW / realW
        val cfg = params.config
        val frameNow = System.currentTimeMillis()
        val is24 = DateFormat.is24HourFormat(context)
        val timeStr = SimpleDateFormat(if (is24) "HH:mm" else "hh:mm", Locale.getDefault()).format(Date(frameNow))
        val dateStr = ClockTypefaceResolver.formatDate(frameNow, cfg.dateFormat)
        val tf = ClockTypefaceResolver.resolve(context, cfg.font)
        val clockStyle = ClockKitEngine.getStyle(cfg.styleId)

        canvas.drawColor(Color.BLACK)
        canvas.save()
        canvas.scale(previewScale, previewScale)

        val wp = params.wallpaperBitmap
        val bgMatrix = Matrix()
        val maskMatrix = Matrix()
        if (wp != null && !wp.isRecycled) {
            val baseScale = max(realW / wp.width.toFloat(), realH / wp.height.toFloat()) * params.wallpaperZoom
            val destW = wp.width * baseScale
            val destH = wp.height * baseScale
            val tx = (realW - destW) / 2f
            val ty = (realH - destH) / 2f
            bgMatrix.postScale(baseScale, baseScale)
            bgMatrix.postTranslate(tx, ty)

            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                alpha = (params.bgOpacity * 255).toInt().coerceIn(0, 255)
                if (params.bgVibrance != 0f) {
                    colorFilter = ColorMatrixColorFilter(
                        ColorMatrix().apply { setSaturation((1f + params.bgVibrance * 0.5f).coerceIn(0f, 2f)) }
                    )
                }
            }
            canvas.drawBitmap(wp, bgMatrix, bgPaint)

            val mask = params.depthMaskBitmap
            if (mask != null && !mask.isRecycled) {
                maskMatrix.postScale(destW / mask.width.toFloat(), destH / mask.height.toFloat())
                maskMatrix.postTranslate(tx, ty)
            }
        }

        val frameCtx = ClockFrameContext(
            context = context,
            screenW = realW,
            screenH = realH,
            density = params.density,
            clockCenterX = (realW / 2f) + (cfg.clockX * params.density),
            clockTopY = cfg.clockY * params.density,
            timeMillis = frameNow,
            timeString = timeStr,
            dateString = dateStr,
            is24Hour = is24,
            typeface = tf,
            notificationProgress = 0f,
            collisionMap = params.collisionMap,
            depthMask = params.depthMaskBitmap,
            rawDepthMap = params.rawDepthBitmap,
            zoomFactor = params.wallpaperZoom
        )

        if (clockStyle.datePolicy(cfg) == ClockDatePolicy.EXTERNAL_STANDARD && cfg.atGlanceEnabled) {
            val dateSp = computeDateSizeSp(cfg)
            val snapshot = params.atGlanceSnapshot ?: AtAGlanceDataCollector.collect(context, cfg, frameNow)
            val nativeDateX = (realW / 2f) + (cfg.dateX * params.density)
            val nativeDateY = cfg.dateY * params.density
            AtAGlanceRenderer.draw(
                canvas = canvas,
                centerX = nativeDateX,
                topY = nativeDateY,
                config = cfg,
                snapshot = snapshot,
                density = params.density,
                textColor = cfg.color,
                dateSizeSp = dateSp,
                maxScreenWidthPx = realW
            )
        }

        val (renderRes, elevateAboveSubject) = ClockKitEngine.renderForPreview(canvas, clockStyle, frameCtx, cfg)
        outClockBounds?.set(renderRes.boundsOnScreen)

        val mask = params.depthMaskBitmap
        if (wp != null && !wp.isRecycled && mask != null && !mask.isRecycled && params.isDepthEnabled && !elevateAboveSubject) {
            val layerId = canvas.saveLayer(0f, 0f, realW, realH, null)
            val mPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                alpha = (params.subjectOpacity * 255).toInt().coerceIn(0, 255)
            }
            canvas.drawBitmap(mask, maskMatrix, mPaint)
            val xfer = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            }
            canvas.drawBitmap(wp, bgMatrix, xfer)
            canvas.restoreToCount(layerId)
        }

        val overlayRes = ClockKitEngine.renderPreviewOverlay(canvas, clockStyle, frameCtx, cfg)
        if (overlayRes != null) {
            outClockBounds?.union(overlayRes.boundsOnScreen)
        }

        if (params.showUdfps && params.udfpsRadius > 0f && params.udfpsX > 0f) {
            val ur = params.udfpsRadius
            val ux = params.udfpsX
            val uy = params.udfpsY
            val overlapping = outClockBounds != null &&
                (ux in (outClockBounds.left - ur * 0.15f)..(outClockBounds.right + ur * 0.15f)) &&
                (uy in (outClockBounds.top - ur * 0.15f)..(outClockBounds.bottom + ur * 0.15f))
            val udfpsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                setStyle(Paint.Style.STROKE)
                strokeWidth = (if (overlapping) 2.5f else 1.5f) * params.density
                color = if (overlapping) Color.argb(230, 255, 82, 82) else Color.argb(100, 255, 255, 255)
                pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f)
            }
            canvas.drawCircle(ux, uy, ur, udfpsPaint)
        }

        canvas.restore()
    }

    fun createBlurredBackdrop(src: Bitmap): Bitmap {
        val targetW = 96
        val targetH = ((src.height.toFloat() / src.width.coerceAtLeast(1)) * targetW)
            .roundToInt().coerceIn(96, 220)
        val small = Bitmap.createScaledBitmap(src, targetW, targetH, true)
        fastBoxBlurInPlace(small, radius = 14, passes = 3)
        return small
    }

    private fun fastBoxBlurInPlace(bmp: Bitmap, radius: Int, passes: Int) {
        val w = bmp.width
        val h = bmp.height
        val pix = IntArray(w * h)
        val tmp = IntArray(w * h)
        bmp.getPixels(pix, 0, w, 0, 0, w, h)
        val div = radius * 2 + 1

        repeat(passes) {
            for (y in 0 until h) {
                val row = y * w
                var rSum = 0; var gSum = 0; var bSum = 0
                for (i in -radius..radius) {
                    val c = pix[row + i.coerceIn(0, w - 1)]
                    rSum += (c shr 16) and 0xFF
                    gSum += (c shr 8) and 0xFF
                    bSum += c and 0xFF
                }
                for (x in 0 until w) {
                    tmp[row + x] = Color.argb(255, rSum / div, gSum / div, bSum / div)
                    val addIdx = (x + radius + 1).coerceAtMost(w - 1)
                    val remIdx = (x - radius).coerceAtLeast(0)
                    val cAdd = pix[row + addIdx]
                    val cRem = pix[row + remIdx]
                    rSum += ((cAdd shr 16) and 0xFF) - ((cRem shr 16) and 0xFF)
                    gSum += ((cAdd shr 8) and 0xFF) - ((cRem shr 8) and 0xFF)
                    bSum += (cAdd and 0xFF) - (cRem and 0xFF)
                }
            }
            for (x in 0 until w) {
                var rSum = 0; var gSum = 0; var bSum = 0
                for (i in -radius..radius) {
                    val c = tmp[i.coerceIn(0, h - 1) * w + x]
                    rSum += (c shr 16) and 0xFF
                    gSum += (c shr 8) and 0xFF
                    bSum += c and 0xFF
                }
                for (y in 0 until h) {
                    pix[y * w + x] = Color.argb(255, rSum / div, gSum / div, bSum / div)
                    val addIdx = (y + radius + 1).coerceAtMost(h - 1)
                    val remIdx = (y - radius).coerceAtLeast(0)
                    val cAdd = tmp[addIdx * w + x]
                    val cRem = tmp[remIdx * w + x]
                    rSum += ((cAdd shr 16) and 0xFF) - ((cRem shr 16) and 0xFF)
                    gSum += ((cAdd shr 8) and 0xFF) - ((cRem shr 8) and 0xFF)
                    bSum += (cAdd and 0xFF) - (cRem and 0xFF)
                }
            }
        }
        bmp.setPixels(pix, 0, w, 0, 0, w, h)
    }
}
