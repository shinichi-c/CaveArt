package com.android.CaveArt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder
import com.android.CaveArt.animations.MorphAnimation
import com.android.CaveArt.animations.WallpaperAnimation
import kotlinx.coroutines.*

class CaveArtWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = CaveArtEngine()

    inner class CaveArtEngine : Engine(), Choreographer.FrameCallback {
        private val scope = CoroutineScope(Dispatchers.IO + Job())
        private var config = LiveWallpaperConfig()
        
        private var originalBitmap: Bitmap? = null
        private var maskBitmap: Bitmap? = null 
        
        private val currentAnimation: WallpaperAnimation = MorphAnimation()
        
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val maskXferPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }
        private val clipPath = Path()
        private val screenShapeRect = RectF()
        private var cachedGeometry: UnifiedGeometry? = null
        
        private var isVisible = false
        private var lastFrameTimeNanos = 0L
        private val choreographer = Choreographer.getInstance()

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
                lastFrameTimeNanos = System.nanoTime()
                choreographer.postFrameCallback(this)
            } else {
                choreographer.removeFrameCallback(this)
                currentAnimation.onLock()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            updateGeometry()
            if (isVisible) draw()
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!isVisible) return
            val dt = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f
            lastFrameTimeNanos = frameTimeNanos
            val safeDt = if (dt > 0.1f) 0.1f else dt

            currentAnimation.update(safeDt)
            draw()
            choreographer.postFrameCallback(this)
        }

        private fun draw() {
            val holder = surfaceHolder ?: return
            val bmp = originalBitmap ?: return
            val geo = cachedGeometry ?: return

            var canvas: Canvas? = null
            try {
                canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    holder.lockHardwareCanvas()
                } else {
                    holder.lockCanvas()
                }

                if (canvas != null) {
                    val screenW = canvas.width.toFloat()
                    val screenH = canvas.height.toFloat()
                    
                    val state = currentAnimation.calculateState(
                        geo, screenW, screenH, bmp.width, bmp.height, config.is3DPopEnabled
                    )

                    canvas.drawColor(config.backgroundColor)
                    
                    screenShapeRect.set(geo.shapeBoundsRel)
                    state.shapeMatrix.mapRect(screenShapeRect)
                    screenShapeRect.offset(0f, state.vShift)
                    
                    clipPath.rewind()
                    val shapeEnum = try { MagicShape.valueOf(config.shapeName) } catch(e:Exception) { MagicShape.SQUIRCLE }
                    ShapePathProvider.updatePathForShape(clipPath, shapeEnum, screenShapeRect)

                    canvas.save()
                    canvas.clipPath(clipPath)
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
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (canvas != null) {
                    try { holder.unlockCanvasAndPost(canvas) } catch (e: Exception) {}
                }
            }
        }

        private fun updateGeometry() {
            val bmp = originalBitmap ?: return
            val frame = surfaceHolder?.surfaceFrame ?: return
            if (frame.width() <= 0 || frame.height() <= 0) return
            
            cachedGeometry = ShapeEffectHelper.getUnifiedGeometry(
                bmp.width, bmp.height, 
                frame.width().toFloat(), frame.height().toFloat(), 
                maskBitmap, config
            )
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
                    
                    updateGeometry()
                    if (isVisible) draw()
                }
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            try { unregisterReceiver(receiver) } catch (e: Exception) {}
            choreographer.removeFrameCallback(this)
            scope.cancel()
            originalBitmap?.recycle()
            maskBitmap?.recycle()
        }
    }
}