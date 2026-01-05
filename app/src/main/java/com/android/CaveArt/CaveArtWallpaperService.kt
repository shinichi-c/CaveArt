package com.android.CaveArt

import android.animation.ValueAnimator
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
import kotlin.math.min

class CaveArtWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return CaveArtEngine()
    }

    inner class CaveArtEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val scope = CoroutineScope(Dispatchers.IO + Job())
        
        private var config = LiveWallpaperConfig()
        private var originalBitmap: Bitmap? = null
        
        private val bgPaint = Paint().apply { style = Paint.Style.FILL }
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        
        private var transitionProgress = 0f
        private var isVisible = false
        
        private var floatAnimator: ValueAnimator? = null
        private var floatOffset = 0f
        private var transitionAnimator: ValueAnimator? = null

        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_USER_PRESENT -> animateToHome()
                    Intent.ACTION_SCREEN_OFF -> resetToLock()
                }
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
            startFloatAnimation()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            isVisible = visible
            if (visible) draw() else handler.removeCallbacks(drawRunner)
        }

        fun reloadConfig() {
            scope.launch {
                
                config = WallpaperConfigManager.loadConfig(applicationContext)
                
                val loadedBitmap = try {
                    
                    if (!config.imagePath.isNullOrEmpty()) {
                        BitmapFactory.decodeFile(config.imagePath)
                    } 
                    
                    else if (config.resourceId != 0) {
                        BitmapHelper.decodeSampledBitmapFromResource(resources, config.resourceId, 2500)
                    } else {
                        null
                    }
                } catch (e: Exception) { null }

                withContext(Dispatchers.Main) {
                    if (loadedBitmap != null) {
                        originalBitmap?.recycle()
                        originalBitmap = loadedBitmap
                    }
                    bgPaint.color = config.backgroundColor
                    transitionProgress = 0f 
                    draw()
                }
            }
        }

        private fun animateToHome() {
            transitionAnimator?.cancel()
            transitionAnimator = ValueAnimator.ofFloat(transitionProgress, 1f).apply {
                duration = 800
                interpolator = DecelerateInterpolator()
                addUpdateListener { 
                    transitionProgress = it.animatedValue as Float
                    draw()
                }
                start()
            }
        }

        private fun resetToLock() {
            transitionAnimator?.cancel()
            transitionProgress = 0f
            draw()
        }
        
        private fun startFloatAnimation() {
            floatAnimator = ValueAnimator.ofFloat(0f, 2 * Math.PI.toFloat()).apply {
                duration = 6000
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener {
                    floatOffset = sin(it.animatedValue as Float)
                    if (isVisible && transitionProgress > 0.05f) draw()
                }
                start()
            }
        }

        private val drawRunner = Runnable { draw() }

        private fun draw() {
            if (!isVisible) return
            val holder = surfaceHolder ?: return
            
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    drawContent(canvas)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (canvas != null) {
                    try { holder.unlockCanvasAndPost(canvas) } catch (e: Exception) {}
                }
            }
        }

        private fun drawContent(canvas: Canvas) {
            val bmp = originalBitmap ?: return
            if (bmp.isRecycled) return

            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()
            val iw = bmp.width.toFloat()
            val ih = bmp.height.toFloat()

            
            canvas.drawColor(Color.BLACK)
            if (transitionProgress > 0) {
                bgPaint.alpha = (transitionProgress * 255).toInt()
                canvas.drawRect(0f, 0f, w, h, bgPaint)
            }

            
            val scaleFull = max(w / iw, h / ih)
            val baseScale = min(w / iw, h / ih)
            val safeScale = config.scale.coerceIn(0.5f, 1.5f)
            val scaleShape = baseScale * safeScale
            val floatY = floatOffset * 30f 
            
            val currentScale = lerp(scaleFull, scaleShape, transitionProgress)
            val dx = (w - iw * currentScale) / 2f
            val dy = (h - ih * currentScale) / 2f + (floatY * transitionProgress)

            val saveCount = canvas.save()
            
            if (transitionProgress > 0.01f) {
                val shapeEnum = try { MagicShape.valueOf(config.shapeName) } catch(e:Exception) { MagicShape.SQUIRCLE }
                val cx = w / 2f
                val cy = h / 2f + (floatY * transitionProgress)
                val shapeSize = min(iw * currentScale, ih * currentScale)
                val halfShape = shapeSize / 2f
                val fullSize = max(w, h) * 1.5f
                val currentHalf = lerp(fullSize, halfShape, transitionProgress)
                
                val bounds = RectF(
                    cx - currentHalf,
                    cy - currentHalf,
                    cx + currentHalf,
                    cy + currentHalf
                )
                
                val path = ShapePathProvider.getPathForShape(shapeEnum, bounds)
                canvas.clipPath(path)
            }

            
            val matrix = Matrix()
            matrix.setScale(currentScale, currentScale)
            matrix.postTranslate(dx, dy)
            canvas.drawBitmap(bmp, matrix, bitmapPaint)
            
            canvas.restoreToCount(saveCount)
        }
        
        private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

        override fun onDestroy() {
            super.onDestroy()
            unregisterReceiver(receiver)
            floatAnimator?.cancel()
            transitionAnimator?.cancel()
            handler.removeCallbacks(drawRunner)
            scope.cancel()
            originalBitmap?.recycle()
        }
    }
}