package com.android.CaveArt

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlinx.coroutines.*
import kotlin.math.sin
import kotlin.math.max

class CaveArtWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = CaveArtEngine()

    inner class CaveArtEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val scope = CoroutineScope(Dispatchers.IO + Job())
        
        private var config = LiveWallpaperConfig()
        private var originalBitmap: Bitmap? = null
        private var maskBitmap: Bitmap? = null 
        
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val maskXferPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }
        
        private var isVisible = false
        private var timeSeconds = 0f
        private var lastFrameTime = 0L

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
                    if (!config.imagePath.isNullOrEmpty()) BitmapFactory.decodeFile(config.imagePath)
                    else if (config.resourceId != 0) BitmapHelper.decodeSampledBitmapFromResource(resources, config.resourceId, 2500)
                    else null
                } catch (e: Exception) { null }

                val loadedMask = try {
                    if (!config.cutoutPath.isNullOrEmpty()) BitmapFactory.decodeFile(config.cutoutPath)
                    else null
                } catch (e: Exception) { null }

                withContext(Dispatchers.Main) {
                    originalBitmap?.recycle()
                    maskBitmap?.recycle()
                    originalBitmap = loadedOriginal
                    maskBitmap = loadedMask
                    draw()
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

            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) drawScene(canvas)
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas)
            }
        }

        private fun drawScene(canvas: Canvas) {
            val bmp = originalBitmap ?: return
            val screenW = canvas.width.toFloat()
            val screenH = canvas.height.toFloat()
            
            val geo = ShapeEffectHelper.getUnifiedGeometry(
                bmp.width, bmp.height, screenW, screenH, maskBitmap, config
            )
            
            val floatY = sin(timeSeconds * 0.8f) * 25f
            val breathe = 1.0f + (sin(timeSeconds * 0.4f) * 0.015f)
            val finalScale = geo.baseScale * breathe
            
            val imageScreenX: Float
            val imageScreenY: Float

            if (config.isCentered) {
                
                imageScreenX = (screenW / 2f) - (geo.subjectCenterX * finalScale)
                imageScreenY = (screenH / 2f) - (geo.subjectCenterY * finalScale)
            } else {
                
                imageScreenX = (screenW / 2f) - (bmp.width / 2f * finalScale)
                imageScreenY = (screenH / 2f) - (bmp.height / 2f * finalScale)
            }
            
            val animY = imageScreenY + floatY
            
            val drawMatrix = Matrix()
            drawMatrix.postScale(finalScale, finalScale)
            drawMatrix.postTranslate(imageScreenX, animY)
            
            val shapeLeft = imageScreenX + (geo.shapeBoundsRel.left * finalScale)
            val shapeTop = animY + (geo.shapeBoundsRel.top * finalScale)
            val shapeRight = imageScreenX + (geo.shapeBoundsRel.right * finalScale)
            val shapeBottom = animY + (geo.shapeBoundsRel.bottom * finalScale)
            
            val screenShapeBounds = RectF(shapeLeft, shapeTop, shapeRight, shapeBottom)
            
            val vShift = if (config.is3DPopEnabled) screenShapeBounds.height() * 0.1f else 0f
            screenShapeBounds.offset(0f, vShift)
            
            canvas.drawColor(config.backgroundColor)
            
            canvas.save()
            val shapeEnum = try { MagicShape.valueOf(config.shapeName) } catch(e:Exception) { MagicShape.SQUIRCLE }
            val path = ShapePathProvider.getPathForShape(shapeEnum, screenShapeBounds)
            canvas.clipPath(path)
            canvas.drawBitmap(bmp, drawMatrix, bitmapPaint)
            canvas.restore()
            
            if (config.is3DPopEnabled && maskBitmap != null) {
                val popMatrix = Matrix(drawMatrix)
                
                popMatrix.postTranslate(sin(timeSeconds * 0.8f) * 6f, 0f)

                val id = canvas.saveLayer(0f, 0f, screenW, screenH, null)
                canvas.drawBitmap(maskBitmap!!, popMatrix, bitmapPaint)
                canvas.drawBitmap(bmp, popMatrix, maskXferPaint)
                canvas.restoreToCount(id)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            reloadConfig()
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacks(drawRunner)
            scope.cancel()
            originalBitmap?.recycle()
            maskBitmap?.recycle()
        }
    }
}