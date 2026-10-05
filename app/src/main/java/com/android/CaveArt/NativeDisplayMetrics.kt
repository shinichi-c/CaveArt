package com.android.CaveArt

import android.content.Context
import android.graphics.RectF
import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.WindowInsets
import android.view.WindowManager

data class NativeDisplayGeometry(
    val widthPx: Float,
    val heightPx: Float,
    val density: Float,
    val aspectRatio: Float,
    val statusBarHeightPx: Float,
    val navBarHeightPx: Float,
    val cameraCutoutRect: RectF?,
    val wallpaperMaxScale: Float
)

object NativeDisplayMetrics {

    fun getGeometry(context: Context): NativeDisplayGeometry {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = context.resources.displayMetrics.density

        var widthPx: Float
        var heightPx: Float
        var statusBarHeight = 24f * density
        var navBarHeight = 48f * density
        var cutoutRect: RectF? = null
        
        var wallpaperZoom = 1.10f
        try {
            val resId = context.resources.getIdentifier("config_wallpaperMaxScale", "dimen", "android")
            if (resId > 0) {
                val typedValue = TypedValue()
                context.resources.getValue(resId, typedValue, true)
                val scale = typedValue.float
                if (scale in 1.0f..1.25f) {
                    wallpaperZoom = scale
                }
            }
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = wm.maximumWindowMetrics
            val bounds = windowMetrics.bounds
            widthPx = bounds.width().toFloat()
            heightPx = bounds.height().toFloat()

            val insets = windowMetrics.windowInsets
            statusBarHeight = insets.getInsetsIgnoringVisibility(
                WindowInsets.Type.statusBars()
            ).top.toFloat().coerceAtLeast(statusBarHeight)

            navBarHeight = insets.getInsetsIgnoringVisibility(
                WindowInsets.Type.navigationBars()
            ).bottom.toFloat().coerceAtLeast(navBarHeight)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                insets.displayCutout?.boundingRects?.firstOrNull()?.let { rect ->
                    cutoutRect = RectF(rect)
                }
            }
        } else {
            val realMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(realMetrics)
            widthPx = realMetrics.widthPixels.toFloat()
            heightPx = realMetrics.heightPixels.toFloat()
        }

        return NativeDisplayGeometry(
            widthPx = widthPx,
            heightPx = heightPx,
            density = density,
            aspectRatio = widthPx / heightPx,
            statusBarHeightPx = statusBarHeight,
            navBarHeightPx = navBarHeight,
            cameraCutoutRect = cutoutRect,
            wallpaperMaxScale = wallpaperZoom
        )
    }
}
