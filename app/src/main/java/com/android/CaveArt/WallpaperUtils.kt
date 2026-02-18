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
    
    var savedOriginalPath: String? = null
    var savedCutoutPath: String? = null
    
    val components = viewModel.getHighQualityComponents(context, wallpaper)
    val originalBitmap = components.first
    val cutoutBitmap = components.second
    
    if (originalBitmap != null) {
        try {
            val origFile = File(deviceProtectedContext.noBackupFilesDir, "boot_original.png")
            val origStream = FileOutputStream(origFile)
            originalBitmap.compress(Bitmap.CompressFormat.PNG, 100, origStream)
            origStream.flush(); origStream.close()
            savedOriginalPath = origFile.absolutePath
            
            if (cutoutBitmap != null && viewModel.isMagicShapeEnabled) {
                val cutFile = File(deviceProtectedContext.noBackupFilesDir, "boot_cutout.png")
                val cutStream = FileOutputStream(cutFile)
                cutoutBitmap.compress(Bitmap.CompressFormat.PNG, 100, cutStream)
                cutStream.flush(); cutStream.close()
                savedCutoutPath = cutFile.absolutePath
            }
        } catch (e: Exception) {
            Log.e("CaveArt", "Failed to save assets: ${e.message}")
        }
    }
    
    val config = LiveWallpaperConfig(
        imagePath = savedOriginalPath,
        cutoutPath = savedCutoutPath,
        resourceId = wallpaper.resourceId,
        shapeName = viewModel.currentMagicShape.name,
        backgroundColor = viewModel.currentBackgroundColor,
        is3DPopEnabled = viewModel.is3DPopEnabled,
        scale = viewModel.magicScale,
        isCentered = viewModel.isCentered,
        animationStyle = viewModel.currentAnimationStyle.name
    )
    
    WallpaperConfigManager.saveConfig(context, config)
    
    originalBitmap?.recycle()
    cutoutBitmap?.recycle()
    
    val isRoot = RootUtils.isRootAvailable()
    if (isRoot) {
        withContext(Dispatchers.Main) { Toast.makeText(context, "Attempting System Injection...", Toast.LENGTH_SHORT).show() }
        val resultLog = RootUtils.bruteForceSetWallpaper(context, CaveArtWallpaperService::class.java)
        if (resultLog.contains("SUCCESS")) {
             try { WallpaperManager.getInstance(context).clear(WallpaperManager.FLAG_LOCK) } catch (e: Exception) {}
             withContext(Dispatchers.Main) { Toast.makeText(context, "Applied via Root!", Toast.LENGTH_SHORT).show() }
             return@withContext
        }
    }
    
    withContext(Dispatchers.Main) {
        try {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(context, CaveArtWallpaperService::class.java))
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        windowManager.currentWindowMetrics.bounds.let { metrics.widthPixels = it.width(); metrics.heightPixels = it.height() }
    } else {
        windowManager.defaultDisplay.getMetrics(metrics)
    }
    
    val rawBitmap = viewModel.getOrCreateProcessedBitmap(context, wallpaper) ?: return@withContext

    try {
        if (isFixedAlignmentEnabled) {
            val finalBitmap = Bitmap.createBitmap(metrics.widthPixels, metrics.heightPixels, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(finalBitmap)
            if (viewModel.isMagicShapeEnabled) canvas.drawColor(viewModel.currentBackgroundColor)
            
            val scale = max(metrics.widthPixels.toFloat() / rawBitmap.width, metrics.heightPixels.toFloat() / rawBitmap.height)
            val w = rawBitmap.width * scale
            val h = rawBitmap.height * scale
            val left = (metrics.widthPixels - w) / 2f
            val top = (metrics.heightPixels - h) / 2f
            canvas.drawBitmap(rawBitmap, null, RectF(left, top, left+w, top+h), null)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) wallpaperManager.setBitmap(finalBitmap, null, false, destination)
            else wallpaperManager.setBitmap(finalBitmap)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) wallpaperManager.setBitmap(rawBitmap, null, true, destination)
            else wallpaperManager.setBitmap(rawBitmap)
        }
    } catch (e: Exception) { e.printStackTrace() }
}