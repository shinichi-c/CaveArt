package com.android.CaveArt

import android.content.Context
import android.graphics.RectF
import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.RoundedCorner
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
    val physicalCornerRadiusPx: Float,
    val wallpaperMaxScale: Float
)

object NativeDisplayMetrics {

    fun getGeometry(context: Context): NativeDisplayGeometry {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val resources = context.resources
        val density = resources.displayMetrics.density
        
        var statusBarHeight = 0f
        val statusResId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (statusResId > 0) {
            statusBarHeight = resources.getDimensionPixelSize(statusResId).toFloat()
        }

        var navBarHeight = 0f
        val navResId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (navResId > 0) {
            navBarHeight = resources.getDimensionPixelSize(navResId).toFloat()
        }
        
        var wallpaperZoom = 1.0f
        try {
            val zoomId = resources.getIdentifier("config_wallpaperMaxScale", "dimen", "android")
            if (zoomId > 0) {
                val typedValue = TypedValue()
                resources.getValue(zoomId, typedValue, true)
                val scale = typedValue.float
                if (scale in 1.0f..1.35f) {
                    wallpaperZoom = scale
                }
            }
        } catch (_: Exception) {}

        var widthPx: Float
        var heightPx: Float
        var cutoutRect: RectF? = null
        var physicalCornerRadius = 0f
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = wm.maximumWindowMetrics
            val bounds = windowMetrics.bounds
            widthPx = bounds.width().toFloat()
            heightPx = bounds.height().toFloat()

            val insets = windowMetrics.windowInsets
            val statusInsets = insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top.toFloat()
            if (statusInsets > 0f) statusBarHeight = statusInsets

            val navInsets = insets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()).bottom.toFloat()
            if (navInsets > 0f) navBarHeight = navInsets

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                insets.displayCutout?.boundingRects?.firstOrNull()?.let { rect ->
                    cutoutRect = RectF(rect)
                }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val topLeft = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
                val topRight = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)
                if (topLeft != null || topRight != null) {
                    physicalCornerRadius = (topLeft?.radius ?: topRight?.radius ?: 0).toFloat()
                }
            }
        } else {
            val realMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(realMetrics)
            widthPx = realMetrics.widthPixels.toFloat()
            heightPx = realMetrics.heightPixels.toFloat()
        }
        
        if (physicalCornerRadius <= 0f && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            physicalCornerRadius = (widthPx * 0.065f)
        }

        return NativeDisplayGeometry(
            widthPx = widthPx,
            heightPx = heightPx,
            density = density,
            aspectRatio = widthPx / heightPx,
            statusBarHeightPx = statusBarHeight,
            navBarHeightPx = navBarHeight,
            cameraCutoutRect = cutoutRect,
            physicalCornerRadiusPx = physicalCornerRadius,
            wallpaperMaxScale = wallpaperZoom
        )
    }
}
