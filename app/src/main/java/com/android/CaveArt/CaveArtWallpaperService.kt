package com.android.CaveArt

import android.app.KeyguardManager
import android.app.WallpaperColors
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder
import com.android.CaveArt.animations.AnimationFactory
import com.android.CaveArt.animations.AnimationStyle
import com.android.CaveArt.animations.WallpaperAnimation
import kotlinx.coroutines.*
import kotlin.math.max
import kotlin.math.sin

class CaveArtWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = CaveArtEngine()

    inner class CaveArtEngine : Engine(), Choreographer.FrameCallback {
        private val scope = CoroutineScope(Dispatchers.IO + Job())
        private var config = LiveWallpaperConfig()
        
        private var originalBitmap: Bitmap? = null
        private var maskBitmap: Bitmap? = null 
        
        private var currentAnimation: WallpaperAnimation = AnimationFactory.getAnimation(AnimationStyle.NANO_ASSEMBLY)
        private var currentAnimationStyle: String? = null
        
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val maskXferPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }
        private val clipPath = Path()
        private val screenShapeRect = RectF()
        private var cachedGeometry: UnifiedGeometry? = null
        
        private val _bodyMatrix = Matrix()
        private val _shapeMatrix = Matrix()

        private var isVisible = false
        private var lastFrameTimeNanos = 0L
        private val choreographer = Choreographer.getInstance()

        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_USER_PRESENT -> currentAnimation.onUnlock()
                    Intent.ACTION_SCREEN_OFF -> currentAnimation.onLock()
                }
            }
        }
        
        override fun onComputeColors(): WallpaperColors? {
            return try {
                if (config.backgroundColor != 0 && config.isMagicShapeEnabled) {
                    val primary = Color.valueOf(config.backgroundColor)
                    WallpaperColors(primary, null, null)
                } else if (originalBitmap != null) {
                    WallpaperColors.fromBitmap(originalBitmap!!)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
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
                val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                if (km.isKeyguardLocked) currentAnimation.onLock() else currentAnimation.onUnlock()

                lastFrameTimeNanos = System.nanoTime()
                
                choreographer.postFrameCallback(this)
            } else {
                choreographer.removeFrameCallback(this)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            updateGeometry()
            if (isVisible) {
                if (!config.isAnimationEnabled) currentAnimation.update(1.0f)
                draw()
            }
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!isVisible) return
            
            val dt = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f
            lastFrameTimeNanos = frameTimeNanos
            val safeDt = if (dt > 0.1f) 0.1f else dt

            if (config.isAnimationEnabled) {
                currentAnimation.update(safeDt)
            }
            
            draw()
            choreographer.postFrameCallback(this)
        }

        private fun draw() {
            val holder = surfaceHolder ?: return
            val bmp = originalBitmap ?: return
            val geo = cachedGeometry ?: return

            var canvas: Canvas? = null
            try {
                canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) holder.lockHardwareCanvas() else holder.lockCanvas()

                if (canvas != null) {

                    if (config.isAnimationEnabled) {
                        val state = currentAnimation.calculateState(
                            geo, canvas.width.toFloat(), canvas.height.toFloat(), bmp.width, bmp.height, config.is3DPopEnabled
                        )

                        val isUsingShader = currentAnimation.applyShader(
                            bitmapPaint, bmp, state, canvas.width.toFloat(), canvas.height.toFloat()
                        )
                        
                        canvas.drawColor(Color.BLACK)
                        canvas.save()
                        
                        if (isUsingShader) {
                            canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), bitmapPaint)
                        } else {
                            canvas.drawBitmap(bmp, state.bodyMatrix, bitmapPaint)
                        }
                        
                        canvas.restore()
                        bitmapPaint.shader = null 

                    } else if (config.isMagicShapeEnabled) {
                        val timeSeconds = System.nanoTime() / 1_000_000_000f
                        val breathScale = 1.0f + (sin(timeSeconds * 1.5f) * 0.02f)
                        val breathY = sin(timeSeconds * 1.2f) * 8f

                        val currentImgScale = geo.baseScale * config.scale * breathScale

                        val anchorX = if (config.isCentered) geo.subjectCenterX else bmp.width / 2f
                        val anchorY = if (config.isCentered) geo.subjectCenterY else bmp.height / 2f

                        _bodyMatrix.reset()
                        _bodyMatrix.postTranslate(-anchorX, -anchorY)
                        _bodyMatrix.postScale(currentImgScale, currentImgScale)
                        _bodyMatrix.postTranslate(canvas.width / 2f, (canvas.height / 2f) + breathY)

                        canvas.drawColor(config.backgroundColor)
                        
                        screenShapeRect.set(geo.shapeBoundsRel)
                        _shapeMatrix.set(_bodyMatrix)
                        _shapeMatrix.mapRect(screenShapeRect)
                        
                        val vShift = if (config.is3DPopEnabled) screenShapeRect.height() * 0.12f else 0f
                        screenShapeRect.offset(0f, vShift)
                        
                        clipPath.rewind()
                        val shapeEnum = try { MagicShape.valueOf(config.shapeName) } catch(e:Exception) { MagicShape.SQUIRCLE }
                        ShapePathProvider.updatePathForShape(clipPath, shapeEnum, screenShapeRect)

                        canvas.save()
                        canvas.clipPath(clipPath)
                        canvas.drawBitmap(bmp, _bodyMatrix, bitmapPaint)
                        canvas.restore()

                        if (config.is3DPopEnabled && maskBitmap != null) {
                            val popMatrix = Matrix(_bodyMatrix)
                            
                            val id = canvas.saveLayer(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), null)
                            canvas.drawBitmap(maskBitmap!!, popMatrix, bitmapPaint)
                            canvas.drawBitmap(bmp, popMatrix, maskXferPaint)
                            canvas.restoreToCount(id)
                        }
                    } else {
                        canvas.drawColor(Color.BLACK)
                        _bodyMatrix.reset()
                        _bodyMatrix.postScale(geo.baseScale, geo.baseScale)
                        canvas.drawBitmap(bmp, _bodyMatrix, bitmapPaint)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() } finally {
                if (canvas != null) try { holder.unlockCanvasAndPost(canvas) } catch (e: Exception) {}
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
                val newConfig = WallpaperConfigManager.loadConfig(applicationContext)
                
                withContext(Dispatchers.Main) {
                    if (currentAnimationStyle != newConfig.animationStyle) {
                        currentAnimationStyle = newConfig.animationStyle
                        val style = try { AnimationStyle.valueOf(newConfig.animationStyle) } catch(e:Exception) { AnimationStyle.NANO_ASSEMBLY }
                        currentAnimation = AnimationFactory.getAnimation(style)
                    }
                    config = newConfig
                }
                
                var finalSampleSize = 1
                
                val loadedOriginal = try {
                    if (!config.imagePath.isNullOrEmpty()) {
                        val metrics = resources.displayMetrics
                        val maxDim = max(metrics.widthPixels, metrics.heightPixels)
                        
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(config.imagePath, options)
                        
                        val halfH = options.outHeight / 2
                        val halfW = options.outWidth / 2
                        while ((halfH / finalSampleSize) >= maxDim && (halfW / finalSampleSize) >= maxDim) {
                            finalSampleSize *= 2
                        }
                        
                        options.inSampleSize = finalSampleSize
                        options.inJustDecodeBounds = false
                        options.inPreferredConfig = Bitmap.Config.ARGB_8888
                        
                        BitmapFactory.decodeFile(config.imagePath, options)
                    }
                    else if (config.resourceId != 0) BitmapHelper.decodeSampledBitmapFromResource(resources, config.resourceId, 2500)
                    else null
                } catch (e: Exception) { null }

                var loadedMask = try {
                    if (!config.cutoutPath.isNullOrEmpty() && config.isMagicShapeEnabled) {
                        val options = BitmapFactory.Options()
                        options.inSampleSize = finalSampleSize
                        options.inPreferredConfig = Bitmap.Config.ARGB_8888
                        BitmapFactory.decodeFile(config.cutoutPath, options)
                    }
                    else null
                } catch (e: Exception) { null }
                
                if (loadedMask != null && loadedOriginal != null) {
                    if (loadedMask.width != loadedOriginal.width || loadedMask.height != loadedOriginal.height) {
                        val scaledMask = Bitmap.createScaledBitmap(loadedMask, loadedOriginal.width, loadedOriginal.height, true)
                        if (scaledMask != loadedMask) {
                            loadedMask.recycle()
                            loadedMask = scaledMask
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    originalBitmap?.recycle()
                    maskBitmap?.recycle()
                    originalBitmap = loadedOriginal
                    maskBitmap = loadedMask
                    
                    notifyColorsChanged()
                    
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
