package com.android.CaveArt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.view.animation.DecelerateInterpolator
import kotlinx.coroutines.*
import kotlin.math.sin
import kotlin.math.max

class CaveArtWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return CaveArtEngine()
    }

    inner class CaveArtEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val scope = CoroutineScope(Dispatchers.IO + Job())
        
        private var config = LiveWallpaperConfig()
        
        private var originalBitmap: Bitmap? = null
        private var cutoutBitmap: Bitmap? = null
        
        private val bgPaint = Paint().apply { style = Paint.Style.FILL }
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        
        private var isVisible = false
        private var floatOffset = 0f
        private var breatheScale = 1.0f
        
        private var timeSeconds = 0f
        private var lastFrameTime = 0L

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            reloadConfig()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            isVisible = visible
            if (visible) {
                lastFrameTime = System.nanoTime()
                handler.post(drawRunner)
            } else {
                handler.removeCallbacks(drawRunner)
            }
        }

        fun reloadConfig() {
            scope.launch {
                config = WallpaperConfigManager.loadConfig(applicationContext)
                
                val loadedOriginal = try {
                    if (!config.imagePath.isNullOrEmpty()) {
                        BitmapFactory.decodeFile(config.imagePath)
                    } else if (config.resourceId != 0) {
                        BitmapHelper.decodeSampledBitmapFromResource(resources, config.resourceId, 2500)
                    } else null
                } catch (e: Exception) { null }
                
                val loadedCutout = try {
                    if (!config.cutoutPath.isNullOrEmpty()) {
                        BitmapFactory.decodeFile(config.cutoutPath)
                    } else null
                } catch (e: Exception) { null }

                withContext(Dispatchers.Main) {
                    originalBitmap?.recycle()
                    cutoutBitmap?.recycle()
                    
                    if (loadedOriginal != null) originalBitmap = loadedOriginal
                    if (loadedCutout != null) cutoutBitmap = loadedCutout
                    
                    bgPaint.color = config.backgroundColor
                    
                    if (isVisible) draw()
                }
            }
        }

        private val drawRunner = object : Runnable {
            override fun run() {
                draw()
                if (isVisible) handler.postDelayed(this, 16)
            }
        }

        private fun draw() {
            if (!isVisible) return
            val holder = surfaceHolder ?: return
            
            val now = System.nanoTime()
            val dt = (now - lastFrameTime) / 1_000_000_000f
            lastFrameTime = now
            timeSeconds += dt
            
            floatOffset = sin(timeSeconds * 0.5f)
            
            breatheScale = 1.0f + (sin(timeSeconds * 0.2f) * 0.02f)

            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    drawUnifiedFrame(canvas)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (canvas != null) {
                    try { holder.unlockCanvasAndPost(canvas) } catch (e: Exception) {}
                }
            }
        }

        private fun drawUnifiedFrame(canvas: Canvas) {
            val bmp = originalBitmap ?: return
            if (bmp.isRecycled) return
            
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()
            val iw = bmp.width.toFloat()
            val ih = bmp.height.toFloat()
            
            canvas.drawColor(config.backgroundColor)
            
            val scaleFull = max(w / iw, h / ih)
            val scaleFit = kotlin.math.min(w / iw, h / ih)
            
            val shapeBoundsRelToImage = ShapeEffectHelper.calculateShapeBounds(
                bmp.width, bmp.height, cutoutBitmap, config.scale, config.isCentered
            )
            
            var shiftX = 0f
            var shiftY = 0f
            if (config.isCentered) {
              
                val cutBounds = if(cutoutBitmap != null) Geometric.calculateCircleBounds(cutoutBitmap!!, bmp.width, bmp.height, config.scale) else RectF(0f,0f,0f,0f)
                shiftX = (iw / 2f) - cutBounds.centerX()
                shiftY = (ih / 2f) - cutBounds.centerY()
            }
               
            val centerX = w / 2f
            val centerY = h / 2f
            
            val screenShapeBounds = RectF(
                centerX - (shapeBoundsRelToImage.width() / 2f * finalScale),
                centerY - (shapeBoundsRelToImage.height() / 2f * finalScale) + (floatOffset * 20f),
                centerX + (shapeBoundsRelToImage.width() / 2f * finalScale),
                centerY + (shapeBoundsRelToImage.height() / 2f * finalScale) + (floatOffset * 20f)
            )
            
            val saveCount = canvas.save()
            
            val shapeEnum = try { MagicShape.valueOf(config.shapeName) } catch(e:Exception) { MagicShape.SQUIRCLE }
            val path = ShapePathProvider.getPathForShape(shapeEnum, screenShapeBounds)
            canvas.clipPath(path)
            
            val matrix = Matrix()
            matrix.postTranslate(-shapeBoundsRelToImage.centerX() + shiftX, -shapeBoundsRelToImage.centerY() + shiftY)
            matrix.postScale(finalScale, finalScale)
            matrix.postTranslate(screenShapeBounds.centerX(), screenShapeBounds.centerY())
            
            canvas.drawBitmap(bmp, matrix, bitmapPaint)
            canvas.restoreToCount(saveCount)
            
            if (config.is3DPopEnabled && cutoutBitmap != null && !cutoutBitmap!!.isRecycled) {
      
                val parallaxX = floatOffset * 5f
                matrix.postTranslate(parallaxX, 0f) 
                
                canvas.drawBitmap(cutoutBitmap!!, matrix, bitmapPaint)
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacks(drawRunner)
            scope.cancel()
            originalBitmap?.recycle()
            cutoutBitmap?.recycle()
        }
    }
}