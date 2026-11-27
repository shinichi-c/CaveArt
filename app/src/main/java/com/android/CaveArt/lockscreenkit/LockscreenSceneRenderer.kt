package com.android.CaveArt.lockscreenkit

import android.app.WallpaperColors
import android.content.Context
import android.graphics.*
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


object LockscreenLayoutSpec {
    const val DEFAULT_WALLPAPER_ZOOM = 1.10f
    const val SIDE_SAFE_MARGIN_DP = 18f
    const val MIN_SAFE_TOP_FALLBACK_DP = 38f
    const val NOTIF_CLOCK_TOP_DP = 84f
    const val COMPACT_DATE_TOP_DP = 46f
    const val COMPACT_DATE_SIZE_SP = 14.5f
    const val COMPACT_DATE_CLOCK_GAP_DP = 10f
    const val DETACHED_OVERLAP_MARGIN_DP = 16f
    const val MAGNETIC_SNAP_WIDTH_RATIO = 0.025f
    const val UDFPS_HIT_BUFFER_RATIO = 0.15f
    const val NOTIF_PUSH_MARGIN_DP = 22f
    const val DATE_HIT_PAD_DP = 14f
    const val SYSTEMUI_BOTTOM_SAFE_DP = 120f
    const val EDITOR_BOTTOM_SAFE_DP = 8f
}


object LockscreenControlSpec {
    val OCCLUSION_RANGE = 0.10f..0.85f
    val ZOOM_RANGE = 1.0f..1.35f
    val VIBRANCE_RANGE = -1.0f..1.0f
    val OPACITY_RANGE = 0.10f..1.0f
    val GRAVITY_INTENSITY_RANGE = 0.2f..2.5f

    fun formatPercent(v: Float): String = "${(v * 100).toInt()}%"
    fun formatZoom(v: Float): String = String.format("%.2fx", v)
    fun formatVibrance(v: Float): String {
        val n = (v * 50).toInt()
        return if (n > 0) "+$n" else "$n"
    }
    fun formatGravity(v: Float): String = String.format("%.1fx", v)
}

enum class BackdropStyle(val id: Int, val label: String) {
    OFF(0, "Solid"),
    AMBIENT_GLOW(1, "Ambient Glow"),
    BLUR_BACKGROUND(2, "Blur Background");

    companion object {
        fun fromId(id: Int): BackdropStyle = values().firstOrNull { it.id == id } ?: BLUR_BACKGROUND
    }
}


object BackdropSpec {
    const val AMBIENT_BLUR_DP = 85f
    const val BLUR_BG_DP = 28f

    const val AMBIENT_SCRIM_ALPHA_DARK = 0.68f
    const val AMBIENT_SCRIM_ALPHA_LIGHT = 0.72f

    const val BLUR_BG_SCRIM_ALPHA_DARK = 0.34f
    const val BLUR_BG_SCRIM_ALPHA_LIGHT = 0.38f
    const val BLUR_BG_BOTTOM_SCRIM_DARK = 0.50f
    const val BLUR_BG_BOTTOM_SCRIM_LIGHT = 0.56f

    const val BLUR_BG_SATURATION = 1.15f
    const val BLUR_BG_PREVIEW_SCALE = 1.08f

    const val XPOSED_AMBIENT_WIDTH = 96
    const val XPOSED_AMBIENT_RADIUS = 14
    const val XPOSED_BLUR_WIDTH = 180
    const val XPOSED_BLUR_RADIUS = 10
}

data class LockscreenSceneParams(
    val screenW: Float,
    val screenH: Float,
    val density: Float,
    val wallpaperBitmap: Bitmap?,
    val depthMaskBitmap: Bitmap?,
    val rawDepthBitmap: Bitmap?,
    val collisionMap: FloatArray?,
    val config: LockscreenConfig,
    val isDepthEnabled: Boolean,
    val wallpaperZoom: Float,
    val bgVibrance: Float = 0f,
    val bgOpacity: Float = 1f,
    val subjectOpacity: Float = 1f,
    val showUdfps: Boolean = false,
    val udfpsX: Float = -1f,
    val udfpsY: Float = -1f,
    val udfpsRadius: Float = 0f,
    val atGlanceSnapshot: AtAGlanceSnapshot? = null,
    val showBackground: Boolean = true,
    val showClock: Boolean = true,
    val showSubject: Boolean = true,
    val depthHeatMap: Bitmap? = null,
    val depthHeatMapOpacity: Float = 0f,
    val depthHeatMapBlendMode: PorterDuff.Mode? = null,
    val notificationShelfTop: Float = -1f,
    val showNotificationShelfGuide: Boolean = false,
    val timeMillis: Long = System.currentTimeMillis(),
    val is24Hour: Boolean? = null,
    val customTypeface: Typeface? = null,
    val fallbackWallpaperColors: WallpaperColors? = null,
    val notificationProgress: Float = 0f,
    val previewNotifications: List<LockNotifAppGroup>? = null,
    val previewExpandedGroupId: String? = null,
    val previewCollapsingGroupId: String? = null,
    val previewExpandProgress: Float = 1f,
    val previewPeekGroupId: String? = null,
    val previewPeekProgress: Float = 0f
)

data class LockscreenSceneDrawResult(
    val clockBoundsOnScreen: RectF,
    val elevatedAboveSubject: Boolean,
    val notificationBoundsOnScreen: RectF = RectF(),
    val notificationHitLayout: NotificationHitLayout = NotificationHitLayout()
)

object LockscreenSceneRenderer {

    fun computeDateSizeSp(cfg: LockscreenConfig): Float {
        return if (cfg.styleId == "adaptive_vector") {
            (cfg.getFloat("adaptive_vector", "hour_size", 100f) * 0.17f).coerceIn(14f, 20f)
        } else {
            (16.5f * cfg.scale).coerceIn(14f, 20f)
        }
    }

    fun computeMinSafeTopDp(statusBarHeightPx: Float, density: Float): Float {
        val statusDp = if (density > 0f) statusBarHeightPx / density else 0f
        return if (statusDp > 0f) {
            statusDp + (statusDp * 0.12f)
        } else {
            LockscreenLayoutSpec.MIN_SAFE_TOP_FALLBACK_DP
        }
    }

    fun computeAttachedDateGapDp(
        cfg: LockscreenConfig,
        dateSizeSp: Float = computeDateSizeSp(cfg)
    ): Float {
        val strokeW = cfg.getFloat("adaptive_vector", "stroke_width", 8f)
        return (strokeW * 0.4f) + (dateSizeSp * 0.35f)
    }

    fun computeCompactClockTopPx(
        hasExternalDate: Boolean,
        compactDateHeightPx: Float,
        density: Float
    ): Float {
        if (!hasExternalDate) return LockscreenLayoutSpec.NOTIF_CLOCK_TOP_DP * density
        val dateTop = LockscreenLayoutSpec.COMPACT_DATE_TOP_DP * density
        val gap = LockscreenLayoutSpec.COMPACT_DATE_CLOCK_GAP_DP * density
        return max(LockscreenLayoutSpec.NOTIF_CLOCK_TOP_DP * density, dateTop + compactDateHeightPx + gap)
    }

    fun computeEffectiveMarginsPx(
        clockTopMarginPx: Int,
        dateTopMarginPx: Int,
        cfg: LockscreenConfig,
        isExternalDate: Boolean,
        needsCollisionMap: Boolean,
        dateHeightPx: Float,
        clockHeightPx: Float,
        density: Float
    ): Pair<Int, Int> {
        if (!isExternalDate || !cfg.dateAttached) {
            return clockTopMarginPx to dateTopMarginPx
        }
        val gapPx = computeAttachedDateGapDp(cfg) * density
        val compoundOffsetPx = dateHeightPx + gapPx
        val minSafeTopPx = LockscreenLayoutSpec.MIN_SAFE_TOP_FALLBACK_DP * density

        val effectiveClockTopPx = if (needsCollisionMap) {
            max(clockTopMarginPx.toFloat(), minSafeTopPx + compoundOffsetPx).toInt()
        } else {
            clockTopMarginPx
        }
        val idealAbovePx = effectiveClockTopPx - compoundOffsetPx
        val effectiveDateTopPx = if (idealAbovePx < minSafeTopPx && !needsCollisionMap) {
            (effectiveClockTopPx + clockHeightPx + gapPx).toInt()
        } else {
            max(minSafeTopPx, idealAbovePx).toInt()
        }
        return effectiveClockTopPx to effectiveDateTopPx
    }

    fun computeNotificationsMaxBottomPx(
        screenH: Float,
        density: Float,
        udfpsY: Float,
        udfpsRadius: Float
    ): Float {
        val defaultSafeBottom = screenH - (LockscreenLayoutSpec.SYSTEMUI_BOTTOM_SAFE_DP * density)
        return if (udfpsRadius > 0f && udfpsY > 0f) {
            min(defaultSafeBottom, udfpsY - udfpsRadius - (18f * density))
        } else {
            defaultSafeBottom
        }.coerceAtLeast(120f * density)
    }

    fun computeNotificationsTopPx(
        anchorBottomPx: Float,
        notifHeightPx: Float,
        maxBottomPx: Float,
        density: Float,
        isCollapsingStyle: Boolean = true,
        isIconsOnly: Boolean = false,
        clockTopPx: Float = -1f,
        dateBottomPx: Float = -1f,
        dateTopPx: Float = -1f
    ): Float {
        val idealTop = anchorBottomPx + (LockscreenLayoutSpec.NOTIF_PUSH_MARGIN_DP * density)
        if (idealTop + notifHeightPx <= maxBottomPx) {
            return idealTop
        }
        if (!isCollapsingStyle) {
            val tightTop = anchorBottomPx + (8f * density)
            if (tightTop + notifHeightPx <= maxBottomPx) {
                return tightTop
            }
            if (isIconsOnly && clockTopPx > 0f) {
                val upperSlotTop = if (dateBottomPx > 0f) {
                    dateBottomPx + (8f * density)
                } else {
                    LockscreenLayoutSpec.COMPACT_DATE_TOP_DP * density
                }
                if (upperSlotTop + notifHeightPx + (10f * density) <= clockTopPx) {
                    return upperSlotTop
                }
                val minTop = LockscreenLayoutSpec.COMPACT_DATE_TOP_DP * density
                val topObstacle = if (dateTopPx > 0f) min(dateTopPx, clockTopPx) else clockTopPx
                if (minTop + notifHeightPx + (10f * density) <= topObstacle) {
                    return (topObstacle - notifHeightPx - (12f * density)).coerceAtLeast(minTop)
                }
            }
        }
        return (maxBottomPx - notifHeightPx).coerceAtLeast(LockscreenLayoutSpec.NOTIF_CLOCK_TOP_DP * density)
    }

    fun resolveDetachedOverlapTargetYDp(
        dateLeftDp: Float,
        dateTopDp: Float,
        dateWidthDp: Float,
        dateHeightDp: Float,
        clockLeftDp: Float,
        clockTopDp: Float,
        clockWidthDp: Float,
        clockHeightDp: Float,
        minSafeTopDp: Float,
        maxSafeDateYDp: Float
    ): Float? {
        val dateRightDp = dateLeftDp + dateWidthDp
        val dateBottomDp = dateTopDp + dateHeightDp
        val clockRightDp = clockLeftDp + clockWidthDp
        val clockBottomDp = clockTopDp + clockHeightDp

        val marginDp = LockscreenLayoutSpec.DETACHED_OVERLAP_MARGIN_DP
        val xOverlap = max(dateLeftDp, clockLeftDp) < min(dateRightDp, clockRightDp)
        val yOverlap = (dateBottomDp + marginDp > clockTopDp) && (dateTopDp < clockBottomDp + marginDp)
        if (!xOverlap || !yOverlap) return null

        val clockCenterY = (clockTopDp + clockBottomDp) / 2f
        val dateCenterY = (dateTopDp + dateBottomDp) / 2f

        val targetY = if (dateCenterY < clockCenterY) {
            val above = clockTopDp - dateHeightDp - marginDp
            if (above >= minSafeTopDp) above else clockBottomDp + marginDp
        } else {
            val below = clockBottomDp + marginDp
            if (below <= maxSafeDateYDp) below else clockTopDp - dateHeightDp - marginDp
        }
        return targetY.coerceIn(minSafeTopDp, maxSafeDateYDp.coerceAtLeast(minSafeTopDp))
    }

    fun clampConfigToScreen(
        context: Context,
        cfg: LockscreenConfig,
        screenW: Float,
        screenH: Float,
        density: Float,
        statusBarPadPx: Int,
        navBarPadPx: Int,
        resolveDetachedOverlap: Boolean = true,
        drawnClockBoundsPx: RectF? = null,
        snapshotOverride: AtAGlanceSnapshot? = null
    ): LockscreenConfig {
        if (screenW <= 0f || screenH <= 0f || density <= 0f) return cfg
        val clockStyle = ClockEngine.getStyle(cfg.styleId)
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

        val sideSafeMarginDp = LockscreenLayoutSpec.SIDE_SAFE_MARGIN_DP
        val halfScreenDp = screenW / density / 2f
        val clockWidthDp = measured.contentWidthPx / density
        val clockHeightDp = measured.contentHeightPx / density
        val halfClockWDp = clockWidthDp / 2f

        val minSafeClockX = (-halfScreenDp + halfClockWDp + sideSafeMarginDp).coerceAtMost(0f)
        val maxSafeClockX = (halfScreenDp - halfClockWDp - sideSafeMarginDp).coerceAtLeast(0f)

        val navBarDp = navBarPadPx / density
        val minSafeYDp = computeMinSafeTopDp(statusBarPadPx.toFloat(), density)
        val maxClockSafeYDp = ((screenH / density) - navBarDp - (clockHeightDp * 0.55f)).coerceAtLeast(minSafeYDp)

        val dateSp = computeDateSizeSp(cfg)
        val snapshot = snapshotOverride ?: AtAGlanceDataCollector.collect(context, cfg, now)
        val glanceMeasured = AtAGlanceRenderer.measure(cfg, snapshot, density, dateSp, screenW)
        val dateLineHeightDp = glanceMeasured.heightPx / density
        val dateWidthDp = glanceMeasured.widthPx / density
        val halfDateWDp = dateWidthDp / 2f
        val minSafeDateX = (-halfScreenDp + halfDateWDp + sideSafeMarginDp).coerceAtMost(0f)
        val maxSafeDateX = (halfScreenDp - halfDateWDp - sideSafeMarginDp).coerceAtLeast(0f)

        val dynamicGapDp = computeAttachedDateGapDp(cfg, dateSp)
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
            val maxDateSafeYDp = ((screenH / density) - navBarDp - dateLineHeightDp - LockscreenLayoutSpec.EDITOR_BOTTOM_SAFE_DP)
                .coerceAtLeast(minSafeYDp)
            finalDateY = cfg.dateY.coerceIn(minSafeYDp, maxDateSafeYDp)
            if (resolveDetachedOverlap) {
                val fallbackLeftDp = halfScreenDp + clampedClockX - halfClockWDp
                val fallbackTopDp = clampedClockY
                val hasDrawn = drawnClockBoundsPx != null && drawnClockBoundsPx.width() > 0f && drawnClockBoundsPx.height() > 0f
                val clockLeftDp = if (hasDrawn) min(drawnClockBoundsPx!!.left / density, fallbackLeftDp) else fallbackLeftDp
                val clockRightDp = if (hasDrawn) max(drawnClockBoundsPx!!.right / density, fallbackLeftDp + clockWidthDp) else (fallbackLeftDp + clockWidthDp)
                val clockTopDp = if (hasDrawn) min(drawnClockBoundsPx!!.top / density, fallbackTopDp) else fallbackTopDp
                val clockBottomDp = if (hasDrawn) max(drawnClockBoundsPx!!.bottom / density, fallbackTopDp + clockHeightDp) else (fallbackTopDp + clockHeightDp)

                resolveDetachedOverlapTargetYDp(
                    dateLeftDp = halfScreenDp + finalDateX - halfDateWDp,
                    dateTopDp = finalDateY,
                    dateWidthDp = dateWidthDp,
                    dateHeightDp = dateLineHeightDp,
                    clockLeftDp = clockLeftDp,
                    clockTopDp = clockTopDp,
                    clockWidthDp = clockRightDp - clockLeftDp,
                    clockHeightDp = clockBottomDp - clockTopDp,
                    minSafeTopDp = minSafeYDp,
                    maxSafeDateYDp = maxDateSafeYDp
                )?.let { resolvedY ->
                    finalDateY = resolvedY
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
        cfg: LockscreenConfig,
        statusBarPadPx: Int,
        density: Float,
        screenW: Float,
        screenH: Float,
        navBarPadPx: Int = 0
    ): LockscreenConfig = clampConfigToScreen(context, cfg, screenW, screenH, density, statusBarPadPx, navBarPadPx, true)

    fun drawScene(
        context: Context,
        canvas: Canvas,
        viewW: Float,
        params: LockscreenSceneParams,
        outClockBounds: RectF? = null
    ): LockscreenSceneDrawResult {
        val realW = params.screenW.coerceAtLeast(1f)
        val realH = params.screenH.coerceAtLeast(1f)
        val previewScale = viewW / realW
        val cfg = params.config
        val frameNow = params.timeMillis
        val is24 = params.is24Hour ?: DateFormat.is24HourFormat(context)
        val timeStr = SimpleDateFormat(if (is24) "HH:mm" else "hh:mm", Locale.getDefault()).format(Date(frameNow))
        val dateStr = ClockTypefaceResolver.formatDate(frameNow, cfg.dateFormat)
        val tf = params.customTypeface ?: ClockTypefaceResolver.resolve(context, cfg.font)
        val clockStyle = ClockEngine.getStyle(cfg.styleId)
        val notifStyle = NotificationStyle.fromId(cfg.notificationStyle)
        val notifP = if (notifStyle.collapsesClockWhenNotifsPresent) {
            params.notificationProgress.coerceIn(0f, 1f)
        } else {
            0f
        }

        val localBounds = RectF()
        val notifBounds = RectF()
        var elevatedAboveSubject = false

        canvas.drawColor(Color.BLACK)
        canvas.save()
        canvas.scale(previewScale, previewScale)

        val wp = params.wallpaperBitmap
        val bgMatrix = Matrix()
        val maskMatrix = Matrix()
        val destRect = RectF(0f, 0f, realW, realH)

        if (wp != null && !wp.isRecycled) {
            val baseScale = max(realW / wp.width.toFloat(), realH / wp.height.toFloat()) * params.wallpaperZoom
            val destW = wp.width * baseScale
            val destH = wp.height * baseScale
            val tx = (realW - destW) / 2f
            val ty = (realH - destH) / 2f
            destRect.set(tx, ty, tx + destW, ty + destH)

            bgMatrix.postScale(baseScale, baseScale)
            bgMatrix.postTranslate(tx, ty)

            if (params.showBackground) {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    alpha = (params.bgOpacity * 255).toInt().coerceIn(0, 255)
                    if (params.bgVibrance != 0f) {
                        colorFilter = ColorMatrixColorFilter(
                            ColorMatrix().apply { setSaturation((1f + params.bgVibrance * 0.5f).coerceIn(0f, 2f)) }
                        )
                    }
                }
                canvas.drawBitmap(wp, bgMatrix, bgPaint)
            }

            val mask = params.depthMaskBitmap
            if (mask != null && !mask.isRecycled) {
                maskMatrix.postScale(destW / mask.width.toFloat(), destH / mask.height.toFloat())
                maskMatrix.postTranslate(tx, ty)
            }
        }

        val heatMap = params.depthHeatMap
        if (heatMap != null && !heatMap.isRecycled && params.depthHeatMapOpacity > 0.05f) {
            val depthMatrix = Matrix().apply {
                postScale(destRect.width() / heatMap.width.toFloat(), destRect.height() / heatMap.height.toFloat())
                postTranslate(destRect.left, destRect.top)
            }
            val depthPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                alpha = (params.depthHeatMapOpacity.coerceIn(0f, 1f) * 255).toInt().coerceIn(0, 255)
                params.depthHeatMapBlendMode?.let { mode ->
                    xfermode = PorterDuffXfermode(mode)
                }
            }
            canvas.drawBitmap(heatMap, depthMatrix, depthPaint)
        }

        val mask = params.depthMaskBitmap
        fun drawSubjectCutout() {
            if (wp != null && !wp.isRecycled && mask != null && !mask.isRecycled) {
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
        }

        if (params.showSubject && (!params.isDepthEnabled || !params.showClock) && (!params.showBackground || params.subjectOpacity < 0.999f)) {
            drawSubjectCutout()
        }

        val isExternalDate = clockStyle.datePolicy(cfg) == ClockDatePolicy.EXTERNAL_STANDARD && cfg.atGlanceEnabled
        val snapshot = params.atGlanceSnapshot ?: AtAGlanceDataCollector.collect(context, cfg, frameNow)
        val compactDateM = if (isExternalDate) {
            AtAGlanceRenderer.measure(cfg, snapshot, params.density, LockscreenLayoutSpec.COMPACT_DATE_SIZE_SP, realW)
        } else null

        val idleClockX = (realW / 2f) + (cfg.clockX * params.density)
        val idleClockY = cfg.clockY * params.density
        val compactClockX = realW / 2f
        val compactClockY = computeCompactClockTopPx(
            hasExternalDate = isExternalDate,
            compactDateHeightPx = compactDateM?.heightPx ?: 0f,
            density = params.density
        )

        val frameCtx = ClockFrameContext(
            context = context,
            screenW = realW,
            screenH = realH,
            density = params.density,
            clockCenterX = idleClockX + (compactClockX - idleClockX) * notifP,
            clockTopY = idleClockY + (compactClockY - idleClockY) * notifP,
            timeMillis = frameNow,
            timeString = timeStr,
            dateString = dateStr,
            is24Hour = is24,
            typeface = tf,
            notificationProgress = notifP,
            collisionMap = params.collisionMap,
            depthMask = mask,
            rawDepthMap = params.rawDepthBitmap,
            zoomFactor = params.wallpaperZoom
        )

        var dateTopPx = -1f
        var dateBottomPx = 0f
        if (params.showClock) {
            if (isExternalDate) {
                val idleDateSp = computeDateSizeSp(cfg)
                val dateSp = idleDateSp + (LockscreenLayoutSpec.COMPACT_DATE_SIZE_SP - idleDateSp) * notifP
                val idleDateX = (realW / 2f) + (cfg.dateX * params.density)
                val idleDateY = cfg.dateY * params.density
                val compactDateX = realW / 2f
                val compactDateY = LockscreenLayoutSpec.COMPACT_DATE_TOP_DP * params.density

                val nativeDateX = idleDateX + (compactDateX - idleDateX) * notifP
                val nativeDateY = idleDateY + (compactDateY - idleDateY) * notifP
                val effectiveBgOpacity = if (params.showBackground) params.bgOpacity else 0f

                val glanceDrawn = AtAGlanceRenderer.drawAuto(
                    canvas = canvas,
                    centerX = nativeDateX,
                    topY = nativeDateY,
                    config = cfg,
                    snapshot = snapshot,
                    density = params.density,
                    dateSizeSp = dateSp,
                    maxScreenWidthPx = realW,
                    wallpaperBitmap = if (params.showBackground) wp else null,
                    screenW = realW,
                    screenH = realH,
                    wallpaperZoom = params.wallpaperZoom,
                    bgOpacity = effectiveBgOpacity,
                    fallbackColors = params.fallbackWallpaperColors
                )
                dateTopPx = nativeDateY
                dateBottomPx = nativeDateY + glanceDrawn.heightPx
            }

            if (timeStr.isNotEmpty()) {
                val (renderRes, elevate) = ClockEngine.renderForPreview(canvas, clockStyle, frameCtx, cfg)
                localBounds.set(renderRes.boundsOnScreen)
                elevatedAboveSubject = elevate
            }
        }

        if (params.showSubject && params.showClock && params.isDepthEnabled && !elevatedAboveSubject && notifP < 0.2f) {
            drawSubjectCutout()
        }

        if (params.showClock && timeStr.isNotEmpty()) {
            val overlayRes = ClockEngine.renderPreviewOverlay(canvas, clockStyle, frameCtx, cfg)
            if (overlayRes != null) {
                localBounds.union(overlayRes.boundsOnScreen)
            }
        }

        outClockBounds?.set(localBounds)

        var hitLayoutOut = NotificationHitLayout()
        val previewNotifs = params.previewNotifications
        if (!previewNotifs.isNullOrEmpty() && params.showClock) {
            val anchorBottom = max(localBounds.bottom, dateBottomPx).coerceAtLeast(110f * params.density)
            val maxBottom = computeNotificationsMaxBottomPx(realH, params.density, params.udfpsY, params.udfpsRadius)
            val activeExpanded = params.previewExpandedGroupId ?: params.previewCollapsingGroupId
            val notifH = NotificationKitRenderer.measureTotalHeightPx(
                groups = previewNotifs,
                config = cfg,
                density = params.density,
                expandedGroupId = activeExpanded,
                expandProgress = params.previewExpandProgress,
                screenW = realW
            )
            val notifTop = computeNotificationsTopPx(
                anchorBottomPx = anchorBottom,
                notifHeightPx = notifH,
                maxBottomPx = maxBottom,
                density = params.density,
                isCollapsingStyle = notifStyle.collapsesClockWhenNotifsPresent,
                isIconsOnly = notifStyle == NotificationStyle.ICONS_ONLY,
                clockTopPx = if (!localBounds.isEmpty) localBounds.top else frameCtx.clockTopY,
                dateBottomPx = if (isExternalDate) dateBottomPx else -1f,
                dateTopPx = if (isExternalDate) dateTopPx else -1f
            )
            val effectiveBgOpacity = if (params.showBackground) params.bgOpacity else 0f

            hitLayoutOut = NotificationKitRenderer.draw(
                canvas = canvas,
                groups = previewNotifs,
                config = cfg,
                screenW = realW,
                screenH = realH,
                topY = notifTop,
                maxBottomY = maxBottom,
                density = params.density,
                nowMillis = frameNow,
                wallpaperBitmap = if (params.showBackground) wp else null,
                wallpaperZoom = params.wallpaperZoom,
                bgOpacity = effectiveBgOpacity,
                fallbackColors = params.fallbackWallpaperColors,
                expandedGroupId = params.previewExpandedGroupId,
                collapsingGroupId = params.previewCollapsingGroupId,
                expandProgress = params.previewExpandProgress,
                peekGroupId = params.previewPeekGroupId,
                peekProgress = params.previewPeekProgress,
                isNightMode = NotificationKitRenderer.isSystemNightMode(context),
                clockAvoidRect = if (!localBounds.isEmpty) localBounds else null
            )
            notifBounds.set(hitLayoutOut.totalBounds)
        }

        if (params.showNotificationShelfGuide && params.notificationShelfTop > 0f && localBounds.bottom > params.notificationShelfTop) {
            val notificationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(200, 255, 183, 77)
                strokeWidth = 3f
                pathEffect = DashPathEffect(floatArrayOf(20f, 20f), 0f)
            }
            canvas.drawLine(
                16f * params.density, params.notificationShelfTop,
                realW - (16f * params.density), params.notificationShelfTop,
                notificationPaint
            )
        }

        if (params.showUdfps && params.udfpsRadius > 0f && params.udfpsX > 0f) {
            val ur = params.udfpsRadius
            val ux = params.udfpsX
            val uy = params.udfpsY
            val buf = ur * LockscreenLayoutSpec.UDFPS_HIT_BUFFER_RATIO
            val overlapping = !localBounds.isEmpty &&
                (ux in (localBounds.left - buf)..(localBounds.right + buf)) &&
                (uy in (localBounds.top - buf)..(localBounds.bottom + buf))
            val udfpsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                setStyle(Paint.Style.STROKE)
                strokeWidth = (if (overlapping) 2.5f else 1.5f) * params.density
                color = if (overlapping) Color.argb(230, 255, 82, 82) else Color.argb(100, 255, 255, 255)
                pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f)
            }
            canvas.drawCircle(ux, uy, ur, udfpsPaint)
        }

        canvas.restore()
        return LockscreenSceneDrawResult(
            clockBoundsOnScreen = localBounds,
            elevatedAboveSubject = elevatedAboveSubject,
            notificationBoundsOnScreen = notifBounds,
            notificationHitLayout = hitLayoutOut
        )
    }

    fun createNotificationFrostBitmap(src: Bitmap): Bitmap {
        val targetW = 220
        val targetH = ((src.height.toFloat() / src.width.coerceAtLeast(1)) * targetW)
            .roundToInt().coerceIn(120, 480)
        val small = Bitmap.createScaledBitmap(src, targetW, targetH, true)
        fastBoxBlurInPlace(small, radius = 14, passes = 3)

        val boosted = Bitmap.createBitmap(small.width, small.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(boosted)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.28f) })
        }
        canvas.drawBitmap(small, 0f, 0f, paint)
        small.recycle()
        return boosted
    }

    fun createNotificationAmbientBitmap(src: Bitmap): Bitmap {
        val targetW = BackdropSpec.XPOSED_AMBIENT_WIDTH
        val targetH = ((src.height.toFloat() / src.width.coerceAtLeast(1)) * targetW)
            .roundToInt().coerceIn(64, 240)
        val small = Bitmap.createScaledBitmap(src, targetW, targetH, true)
        fastBoxBlurInPlace(small, radius = BackdropSpec.XPOSED_AMBIENT_RADIUS, passes = 3)

        val boosted = Bitmap.createBitmap(small.width, small.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(boosted)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.35f) })
        }
        canvas.drawBitmap(small, 0f, 0f, paint)
        small.recycle()
        return boosted
    }

    fun createBlurredBackdrop(
        src: Bitmap,
        style: BackdropStyle = BackdropStyle.BLUR_BACKGROUND
    ): Bitmap {
        if (style == BackdropStyle.OFF) {
            return Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        }
        val targetW = if (style == BackdropStyle.BLUR_BACKGROUND) {
            BackdropSpec.XPOSED_BLUR_WIDTH
        } else {
            BackdropSpec.XPOSED_AMBIENT_WIDTH
        }
        val radius = if (style == BackdropStyle.BLUR_BACKGROUND) {
            BackdropSpec.XPOSED_BLUR_RADIUS
        } else {
            BackdropSpec.XPOSED_AMBIENT_RADIUS
        }
        val targetH = ((src.height.toFloat() / src.width.coerceAtLeast(1)) * targetW)
            .roundToInt().coerceIn(96, 420)
        val small = Bitmap.createScaledBitmap(src, targetW, targetH, true)
        fastBoxBlurInPlace(small, radius = radius, passes = 3)

        if (style == BackdropStyle.BLUR_BACKGROUND && BackdropSpec.BLUR_BG_SATURATION != 1f) {
            val saturated = Bitmap.createBitmap(small.width, small.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(saturated)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(
                    ColorMatrix().apply { setSaturation(BackdropSpec.BLUR_BG_SATURATION) }
                )
            }
            canvas.drawBitmap(small, 0f, 0f, paint)
            small.recycle()
            return saturated
        }
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
