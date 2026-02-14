package com.android.CaveArt

import android.graphics.*
import kotlin.math.max
import kotlin.math.min

data class UnifiedGeometry(
    val baseScale: Float,
    val shiftX: Float,
    val shiftY: Float,
    val subjectCenterX: Float,
    val subjectCenterY: Float,
    val shapeBoundsRel: RectF 
)

object ShapeEffectHelper {

    fun getUnifiedGeometry(imgW: Int, imgH: Int, screenW: Float, screenH: Float, mask: Bitmap?, config: LiveWallpaperConfig): UnifiedGeometry {
        val rawSubject = if (mask != null) {
            Geometric.calculateCircleBounds(mask, imgW, imgH, config.scale)
        } else {
            val r = min(imgW, imgH) / 2f * config.scale
            RectF(imgW/2f - r, imgH/2f - r, imgW/2f + r, imgH/2f + r)
        }

        val shiftX = if (config.isCentered) (imgW / 2f) - rawSubject.centerX() else 0f
        val shiftY = if (config.isCentered) (imgH / 2f) - rawSubject.centerY() else 0f
        val baseScale = max(screenW / imgW, screenH / imgH)

        val side = max(rawSubject.width(), rawSubject.height())
        val halfSide = (if (side < 50f) imgW * 0.5f else side) / 2f
        val shapeBoundsRel = RectF(
            rawSubject.centerX() - halfSide,
            rawSubject.centerY() - halfSide,
            rawSubject.centerX() + halfSide,
            rawSubject.centerY() + halfSide
        )

        return UnifiedGeometry(baseScale, shiftX, shiftY, rawSubject.centerX(), rawSubject.centerY(), shapeBoundsRel)
    }
    
    fun createShapeCropBitmapWithPreCutout(original: Bitmap, cutout: Bitmap, config: LiveWallpaperConfig): Bitmap {
        val w = original.width
        val h = original.height
        val geo = getUnifiedGeometry(w, h, w.toFloat(), h.toFloat(), cutout, config)
        
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        canvas.drawColor(config.backgroundColor)
        
        val anchorX = if (config.isCentered) geo.subjectCenterX else w / 2f
        val anchorY = if (config.isCentered) geo.subjectCenterY else h / 2f

        val matrix = Matrix()
        matrix.postTranslate(-anchorX, -anchorY)
        matrix.postTranslate(w / 2f, h / 2f)
        
        val screenShape = RectF(geo.shapeBoundsRel)
        val shapeMatrix = Matrix()
        shapeMatrix.postTranslate(-anchorX, -anchorY)
        shapeMatrix.postTranslate(w / 2f, h / 2f)
        shapeMatrix.mapRect(screenShape)
        
        val vShift = if (config.is3DPopEnabled) screenShape.height() * 0.1f else 0f
        screenShape.offset(0f, vShift)
        
        canvas.save()
        val shapeEnum = try { MagicShape.valueOf(config.shapeName) } catch(e:Exception) { MagicShape.SQUIRCLE }
        val path = ShapePathProvider.getPathForShape(shapeEnum, screenShape)
        canvas.clipPath(path)
        canvas.drawBitmap(original, matrix, paint)
        canvas.restore()
        
        if (config.is3DPopEnabled) {
            val layerId = canvas.saveLayer(0f, 0f, w.toFloat(), h.toFloat(), null)
            canvas.drawBitmap(cutout, matrix, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(original, matrix, paint)
            paint.xfermode = null
            canvas.restoreToCount(layerId)
        }
        return result
    }
}