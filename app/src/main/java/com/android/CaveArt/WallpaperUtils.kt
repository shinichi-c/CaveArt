package com.android.CaveArt

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

object WallpaperDestinations {
    const val FLAG_HOME_SCREEN = 1
    const val FLAG_LOCK_SCREEN = 2
    const val FLAG_BOTH = FLAG_HOME_SCREEN or FLAG_LOCK_SCREEN
}

suspend fun setLiveWallpaper(
    context: Context,
    wallpaper: Wallpaper,
    viewModel: WallpaperViewModel
) = withContext(Dispatchers.IO) {
	
    val deviceProtectedContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        context.createDeviceProtectedStorageContext()
    } else {
        context
    }
    
    var savedPath: String? = null
    
    if (wallpaper.uri != null) {
        try {
            
            val bitmap = viewModel.generateHighQualityFinalBitmap(context, wallpaper)
            
            if (bitmap != null) {
                
                val file = File(deviceProtectedContext.noBackupFilesDir, "boot_wallpaper.png")
                val stream = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.flush()
                stream.close()
                
                savedPath = file.absolutePath
                
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("CaveArt", "Failed to save boot image: ${e.message}")
        }
    }
    
    val config = LiveWallpaperConfig(
        imagePath = savedPath,
        resourceId = wallpaper.resourceId,
        shapeName = viewModel.currentMagicShape.name,
        backgroundColor = viewModel.currentBackgroundColor,
        is3DPopEnabled = viewModel.is3DPopEnabled,
        scale = viewModel.magicScale
    )
    WallpaperConfigManager.saveConfig(context, config)
    
    val isRoot = RootUtils.isRootAvailable()
    
    if (isRoot) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Attempting System Injection...", Toast.LENGTH_SHORT).show()
        }

        val resultLog = RootUtils.bruteForceSetWallpaper(context, CaveArtWallpaperService::class.java)

        if (resultLog.contains("SUCCESS")) {
        	
            try {
                val wm = WallpaperManager.getInstance(context)
                wm.clear(WallpaperManager.FLAG_LOCK)
            } catch (e: Exception) {
                try {
                    Runtime.getRuntime().exec(arrayOf("su", "-c", "cmd wallpaper clear 2")).waitFor()
                } catch (ex: Exception) { ex.printStackTrace() }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Injection Successful!", Toast.LENGTH_SHORT).show()
            }
            return@withContext
        } else {
            Log.e("CaveArt", "Injection failed.")
            RootUtils.saveLogToSdCard(resultLog)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Injection Failed. Log saved.", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    withContext(Dispatchers.Main) {
        if (!isRoot) {
            Toast.makeText(context, "Root not found. Opening picker.", Toast.LENGTH_SHORT).show()
        }
        try {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            intent.putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(context, CaveArtWallpaperService::class.java)
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Error opening wallpaper picker", Toast.LENGTH_SHORT).show()
        }
    }
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

    val rawBitmap = viewModel.generateHighQualityFinalBitmap(context, wallpaper)

    if (rawBitmap == null) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Error: Could not load image.", Toast.LENGTH_SHORT).show()
        }
        return@withContext
    }
    
    var bitmapToSet: Bitmap? = null

    try {
        if (isFixedAlignmentEnabled) {
            val finalBitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(finalBitmap)
            
            if (viewModel.isMagicShapeEnabled || viewModel.isDebugMaskEnabled) {
                canvas.drawColor(viewModel.currentBackgroundColor)
            }

            val imageWidth = rawBitmap.width.toFloat()
            val imageHeight = rawBitmap.height.toFloat()
            
            val isMagic = viewModel.isMagicShapeEnabled
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

            if (rawBitmap != finalBitmap) rawBitmap.recycle()
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
            Toast.makeText(context, "Wallpaper set successfully!", Toast.LENGTH_SHORT).show()
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