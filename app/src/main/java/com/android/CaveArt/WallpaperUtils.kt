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
private const val FLAG_HOME_SCREEN = 1
private const val FLAG_LOCK_SCREEN = 2
private const val FLAG_BOTH = FLAG_HOME_SCREEN or FLAG_LOCK_SCREEN

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
    
    var bitmapToSet: Bitmap? = viewModel.getOrCreateProcessedBitmap(
        context,
        resourceId
    )

    var didRecycleOriginal = false
    var isLocalBitmap = false
    
    if (bitmapToSet != null) {
    
        isLocalBitmap = false
    } else {
        val originalBitmap = BitmapFactory.decodeResource(context.resources, resourceId)

        if (isFixedAlignmentEnabled) {
            
            val aspectRatio = screenWidth.toFloat() / screenHeight.toFloat()
            val originalRatio = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()

            val targetWidth: Int
            val targetHeight: Int

            if (originalRatio > aspectRatio) {
                
                targetHeight = screenHeight
                targetWidth = (screenHeight.toFloat() * originalRatio).toInt()
            } else {
                
                targetWidth = screenWidth
                targetHeight = (screenWidth.toFloat() / originalRatio).toInt()
            }

            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)
            val startX = max(0, (targetWidth - screenWidth) / 2)
            val startY = max(0, (targetHeight - screenHeight) / 2)
            
            bitmapToSet = Bitmap.createBitmap(scaledBitmap, startX, startY, screenWidth, screenHeight)

            scaledBitmap.recycle()
            originalBitmap.recycle()
            didRecycleOriginal = true
            isLocalBitmap = true
        } else {
        	
            val systemWidth = wallpaperManager.desiredMinimumWidth
            val systemHeight = wallpaperManager.desiredMinimumHeight
            
            if (originalBitmap.width < systemWidth || originalBitmap.height < systemHeight) {
                bitmapToSet = Bitmap.createScaledBitmap(
                    originalBitmap,
                    max(originalBitmap.width, systemWidth),
                    max(originalBitmap.height, systemHeight),
                    true
                )
                originalBitmap.recycle()
                didRecycleOriginal = true
                isLocalBitmap = true
            } else {
                bitmapToSet = originalBitmap
                isLocalBitmap = false
            }
        }
    }
    
    try {
        val allowParallax = !isFixedAlignmentEnabled
        
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            wallpaperManager.setBitmap(bitmapToSet, null, allowParallax, destination)
        } else {
            wallpaperManager.setBitmap(bitmapToSet)
        }
        
        if (isLocalBitmap && bitmapToSet != null) {
        	
            bitmapToSet.recycle()
        }

        android.os.Handler(context.mainLooper).post {
            Toast.makeText(context, "Wallpaper '" + title + "' set successfully to " + destinationText + "!", Toast.LENGTH_LONG).show()
        }
    } catch (e: IOException) {
        e.printStackTrace()
        android.os.Handler(context.mainLooper).post {
            Toast.makeText(context, "Failed to set wallpaper: " + e.message, Toast.LENGTH_LONG).show()
        }
    } catch (e: SecurityException) {
        e.printStackTrace()
        android.os.Handler(context.mainLooper).post {
            Toast.makeText(context, "Permission error. Ensure SET_WALLPAPER is in AndroidManifest.", Toast.LENGTH_LONG).show()
        }
    }
}
