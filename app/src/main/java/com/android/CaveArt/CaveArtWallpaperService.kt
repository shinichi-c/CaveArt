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
import com.android.CaveArt.animations.MorphAnimation
import com.android.CaveArt.animations.WallpaperAnimation
import kotlinx.coroutines.*

class CaveArtWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = CaveArtEngine()

    inner class CaveArtEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val scope = CoroutineScope(Dispatchers.IO + Job())
        private var config = LiveWallpaperConfig()
        private var originalBitmap: Bitmap? = null
        private var maskBitmap: Bitmap? = null 
        
        private val currentAnimation: WallpaperAnimation = MorphAnimation()
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val maskXferPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }
        
        private var isVisible = false
        private var lastFrameTime = 0L

        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_USER_PRESENT) currentAnimation.onUnlock()
                else if (intent?.action == Intent.ACTION_SCREEN_OFF) currentAnimation.onLock()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            registerReceiver(receiver, filter)
            reloadConfig()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            isVisible = visible
            if (visible) {
                lastFrameTime = System.nanoTime()
                handler.post(drawRunner)
            } else {
                handler.removeCallbacks(drawRunner)
                currentAnimation.onLock()
            }
        }

        private val drawRunner = object : Runnable {
            override fun run() {
                draw()
                if (isVisible) handler.postDelayed(this, 16)
            }
        }

        private fun draw() {
            val holder = surfaceHolder ?: return
            val now = System.nanoTime()
            var dt = (now - lastFrameTime) / 1_000_000_000f
            lastFrameTime = now
            if (dt > 0.1f) dt = 0.1f 

            currentAnimation.update(dt)

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

            val geo = ShapeEffectHelper.getUnifiedGeometry(bmp.width, bmp.height, screenW, screenH, maskBitmap, config)
            val state = currentAnimation.calculateState(geo, screenW, screenH, bmp.width, bmp.height, config.is3DPopEnabled)

            val screenShape = RectF(geo.shapeBoundsRel)
            state.shapeMatrix.mapRect(screenShape)
            screenShape.offset(0f, state.vShift)

            canvas.drawColor(config.backgroundColor)

            canvas.save()
            val shapeEnum = try { MagicShape.valueOf(config.shapeName) } catch(e:Exception) { MagicShape.SQUIRCLE }
            val path = ShapePathProvider.getPathForShape(shapeEnum, screenShape)
            canvas.clipPath(path)
            canvas.drawBitmap(bmp, state.bodyMatrix, bitmapPaint)
            canvas.restore()

            if (config.is3DPopEnabled && maskBitmap != null && state.popAlpha > 0) {
                val id = canvas.saveLayer(0f, 0f, screenW, screenH, null)
                bitmapPaint.alpha = state.popAlpha
                canvas.drawBitmap(maskBitmap!!, state.popMatrix, bitmapPaint)
                canvas.drawBitmap(bmp, state.popMatrix, maskXferPaint)
                bitmapPaint.alpha = 255
                canvas.restoreToCount(id)
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
                }
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            try { unregisterReceiver(receiver) } catch (e: Exception) {}
            handler.removeCallbacks(drawRunner)
            scope.cancel()
            originalBitmap?.recycle()
            maskBitmap?.recycle()
        }
    }
}