package com.android.CaveArt

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.math.max
import kotlin.math.min

object WallpaperDestinations {
    const val FLAG_HOME_SCREEN = 1
    const val FLAG_LOCK_SCREEN = 2
    const val FLAG_BOTH = FLAG_HOME_SCREEN or FLAG_LOCK_SCREEN
}

suspend fun setDeviceWallpaper(
    context: Context,
    wallpaper: Wallpaper,
    destination: Int,
    isFixedAlignmentEnabled: Boolean,
    viewModel: WallpaperViewModel
) = withContext(Dispatchers.IO) {
    val wallpaperManager = WallpaperManager.getInstance(context)
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val metrics = DisplayMetrics()
    @Suppress("DEPRECATION")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        windowManager.currentWindowMetrics.bounds.let {
            metrics.widthPixels = it.width()
            metrics.heightPixels = it.height()
        }
    } else {
        windowManager.defaultDisplay.getMetrics(metrics)
    }
    val screenWidth = metrics.widthPixels
    val screenHeight = metrics.heightPixels

    var bitmapToSet: Bitmap? = null
    var rawBitmap: Bitmap? = null
    
    try {
        val isMagic = viewModel.isMagicShapeEnabled || viewModel.isDebugMaskEnabled
        
        rawBitmap = if (isMagic) {
             viewModel.generateHighQualityFinalBitmap(context, wallpaper)
        } else {
             if (wallpaper.uri != null) {
                 BitmapHelper.decodeSampledBitmapFromUri(context, wallpaper.uri, 2500)
             } else {
                 val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                 BitmapFactory.decodeResource(context.resources, wallpaper.resourceId, options)
             }
        }

        if (rawBitmap == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error: Could not load image.", Toast.LENGTH_SHORT).show()
            }
            return@withContext
        }
        
        if (isFixedAlignmentEnabled) {
            val finalBitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(finalBitmap)
            
            canvas.drawColor(viewModel.currentBackgroundColor)

            val imageWidth = rawBitmap.width.toFloat()
            val imageHeight = rawBitmap.height.toFloat()
            val scale = if (isMagic) {
                
                min(screenWidth.toFloat() / imageWidth, screenHeight.toFloat() / imageHeight)
            } else {
                
                max(screenWidth.toFloat() / imageWidth, screenHeight.toFloat() / imageHeight)
            }

            val scaledWidth = imageWidth * scale
            val scaledHeight = imageHeight * scale
            val left = (screenWidth - scaledWidth) / 2f
            val top = (screenHeight - scaledHeight) / 2f

            val destRect = RectF(left, top, left + scaledWidth, top + scaledHeight)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            
            canvas.drawBitmap(rawBitmap, null, destRect, paint)

            if (rawBitmap != finalBitmap) {
                rawBitmap.recycle()
            }
            bitmapToSet = finalBitmap
        } else {
            
            bitmapToSet = rawBitmap
        }
        
        val allowParallax = !isFixedAlignmentEnabled
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            wallpaperManager.setBitmap(bitmapToSet, null, allowParallax, destination)
        } else {
            wallpaperManager.setBitmap(bitmapToSet)
        }
        
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Wallpaper '${wallpaper.title}' set successfully!", Toast.LENGTH_LONG).show()
        }
        
    } catch (e: Exception) {
        e.printStackTrace()
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Failed to set wallpaper: ${e.message}", Toast.LENGTH_LONG).show()
        }
    } finally {
        System.gc()
    }
}