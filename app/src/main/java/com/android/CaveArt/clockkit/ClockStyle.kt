package com.android.CaveArt.clockkit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

interface ClockStyle {
    val id: String
    val name: String
    val subtitle: String

    val handlesDepthManually: Boolean get() = false
    val handlesGravityManually: Boolean get() = false

    fun datePolicy(config: ClockKitConfig): ClockDatePolicy = ClockDatePolicy.EXTERNAL_STANDARD

    fun needsContinuousFrames(config: ClockKitConfig, notificationProgress: Float): Boolean = false

    fun needsCollisionMap(config: ClockKitConfig): Boolean = false

    fun describeControls(config: ClockKitConfig): List<ClockControl>

    fun onAction(
        actionId: String,
        config: ClockKitConfig,
        mask: Bitmap?,
        screenW: Float,
        screenH: Float,
        density: Float
    ): ClockKitConfig = config

    fun onPinchZoom(config: ClockKitConfig, zoomFactor: Float): ClockKitConfig =
        config.copy(scale = (config.scale * zoomFactor).coerceIn(0.45f, 2.2f))

    fun measure(ctx: ClockMeasureContext, config: ClockKitConfig): ClockMeasureResult

    fun render(canvas: Canvas, frame: ClockFrameContext, config: ClockKitConfig): ClockRenderResult

    /**
     * Optional foreground overlay pass drawn ABOVE the subject mask when depth is active.
     * Useful for split-depth styles (e.g. solid hours behind subject + hollow minutes in front).
     */
    fun renderOverlay(canvas: Canvas, frame: ClockFrameContext, config: ClockKitConfig): ClockRenderResult? = null

    fun renderThumbnail(
        context: Context,
        canvas: Canvas,
        bounds: RectF,
        config: ClockKitConfig,
        typeface: Typeface
    )
}

abstract class BaseClockStyle : ClockStyle {

    override fun renderThumbnail(
        context: Context,
        canvas: Canvas,
        bounds: RectF,
        config: ClockKitConfig,
        typeface: Typeface
    ) {
        val virtualW = 420f
        val virtualH = 560f
        val density = 1.2f
        val previewConfig = config.copy(isGravityEnabled = false, isDepthEnabled = false)

        val mCtx = ClockMeasureContext(
            context = context,
            screenW = virtualW,
            screenH = virtualH,
            density = density,
            typeface = typeface,
            timeString = "09:41",
            dateString = "Wed, 14 Oct"
        )
        val measured = measure(mCtx, previewConfig)

        val fCtx = ClockFrameContext(
            context = context,
            screenW = virtualW,
            screenH = virtualH,
            density = density,
            clockCenterX = virtualW / 2f,
            clockTopY = (virtualH - measured.contentHeightPx) / 2f,
            timeMillis = 1700000000000L,
            timeString = "09:41",
            dateString = "Wed, 14 Oct",
            is24Hour = true,
            typeface = typeface
        )

        val contentW = measured.contentWidthPx.coerceAtLeast(100f)
        val contentH = measured.contentHeightPx.coerceAtLeast(60f)
        val scale = minOf(
            (bounds.width() * 0.82f) / contentW,
            (bounds.height() * 0.78f) / contentH
        ).coerceAtMost(1.25f)

        canvas.save()
        canvas.translate(bounds.centerX(), bounds.centerY())
        canvas.scale(scale, scale)
        canvas.translate(-virtualW / 2f, -virtualH / 2f)
        render(canvas, fCtx, previewConfig)
        renderOverlay(canvas, fCtx, previewConfig)
        canvas.restore()
    }
}

object ClockTypefaceResolver {
    private val cache = ConcurrentHashMap<String, Typeface>()

    fun resolve(context: Context, fontName: String): Typeface {
        val key = fontName.ifEmpty { "default" }
        cache[key]?.let { return it }

        val tf = if (key == "default") {
            Typeface.create("sans-serif-bold", Typeface.NORMAL)
        } else {
            try {
                val assetCtx = if (context.packageName == "com.android.CaveArt") {
                    context
                } else {
                    context.createPackageContext("com.android.CaveArt", Context.CONTEXT_IGNORE_SECURITY)
                }
                Typeface.createFromAsset(assetCtx.assets, "fonts/$key")
            } catch (_: Exception) {
                Typeface.create("sans-serif-bold", Typeface.NORMAL)
            }
        }
        cache[key] = tf
        return tf
    }

    fun formatDate(timeMillis: Long, formatIndex: Int): String {
        val date = Date(timeMillis)
        val locale = Locale.getDefault()
        return when (formatIndex) {
            0 -> SimpleDateFormat("EEE, d MMMM", locale).format(date)
            1 -> SimpleDateFormat("EEE, d MMM", locale).format(date)
            2 -> SimpleDateFormat("d MMM yyyy", locale).format(date).uppercase(locale)
            3 -> SimpleDateFormat("EEEE, MMMM d", locale).format(date)
            4 -> SimpleDateFormat("EEEE • d MMM", locale).format(date)
            else -> SimpleDateFormat("EEE, d MMMM", locale).format(date)
        }
    }
}
