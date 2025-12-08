package com.android.CaveArt

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.IOException
import kotlin.math.max

object WallpaperDestinations {
    const val FLAG_HOME_SCREEN = 1
    const val FLAG_LOCK_SCREEN = 2
    const val FLAG_BOTH = FLAG_HOME_SCREEN or FLAG_LOCK_SCREEN
}

suspend fun setDeviceWallpaper(
    context: Context,
    resourceId: Int,
    title: String,
    destination: Int,
    isFixedAlignmentEnabled: Boolean,
    viewModel: WallpaperViewModel
) = withContext(Dispatchers.IO) {
    val wallpaperManager = WallpaperManager.getInstance(context)
    val destinationText = when (destination) {
        WallpaperDestinations.FLAG_HOME_SCREEN -> "Home Screen"
        WallpaperDestinations.FLAG_LOCK_SCREEN -> "Lock Screen"
        WallpaperDestinations.FLAG_BOTH -> "Home and Lock Screens"
        else -> "Unknown"
    }
    
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
    var needsRecycle = false
    
    if (viewModel.isMagicShapeEnabled || viewModel.isDebugMaskEnabled) {
    	
        bitmapToSet = viewModel.generateHighQualityFinalBitmap(context, resourceId)
        needsRecycle = true
    } else {
        
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val rawBitmap = BitmapFactory.decodeResource(context.resources, resourceId, options)
        
        if (rawBitmap != null) {
            if (isFixedAlignmentEnabled) {
                
                val aspectRatio = screenWidth.toFloat() / screenHeight.toFloat()
                val originalRatio = rawBitmap.width.toFloat() / rawBitmap.height.toFloat()
                
                val targetWidth: Int
                val targetHeight: Int

                if (originalRatio > aspectRatio) {
                    targetHeight = screenHeight
                    targetWidth = (screenHeight.toFloat() * originalRatio).toInt()
                } else {
                    targetWidth = screenWidth
                    targetHeight = (screenWidth.toFloat() / originalRatio).toInt()
                }

                val scaledBitmap = Bitmap.createScaledBitmap(rawBitmap, targetWidth, targetHeight, true)
                if (rawBitmap != scaledBitmap) rawBitmap.recycle()
                
                val startX = max(0, (targetWidth - screenWidth) / 2)
                val startY = max(0, (targetHeight - screenHeight) / 2)
                
                bitmapToSet = Bitmap.createBitmap(scaledBitmap, startX, startY, screenWidth, screenHeight)
                scaledBitmap.recycle() 
                needsRecycle = true
            } else {
                
                bitmapToSet = rawBitmap
                needsRecycle = true
            }
        }
    }
    
    if (bitmapToSet == null) {
        android.os.Handler(context.mainLooper).post {
            Toast.makeText(context, "Error: Could not load image.", Toast.LENGTH_SHORT).show()
        }
        return@withContext
    }
    
    try {
        val allowParallax = !isFixedAlignmentEnabled
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            wallpaperManager.setBitmap(bitmapToSet, null, allowParallax, destination)
        } else {
            wallpaperManager.setBitmap(bitmapToSet)
        }
        
        android.os.Handler(context.mainLooper).post {
            Toast.makeText(context, "Wallpaper '$title' set successfully!", Toast.LENGTH_LONG).show()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        android.os.Handler(context.mainLooper).post {
            Toast.makeText(context, "Failed to set wallpaper: ${e.message}", Toast.LENGTH_LONG).show()
        }
    } finally {
        
        if (needsRecycle && bitmapToSet != null && !bitmapToSet.isRecycled) {
            bitmapToSet.recycle()
        }
        System.gc()
    }
}