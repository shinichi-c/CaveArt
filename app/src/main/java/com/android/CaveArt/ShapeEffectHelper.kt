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

    fun getUnifiedGeometry(
        imgW: Int,
        imgH: Int,
        screenW: Float,
        screenH: Float,
        mask: Bitmap?,
        config: LiveWallpaperConfig
    ): UnifiedGeometry {
        val rawSubject = if (mask != null && config.isMagicShapeEnabled) {
            Geometric.calculateCircleBounds(mask, imgW, imgH, 1.0f)
        } else {
            val r = min(imgW, imgH) * 0.38f
            RectF(imgW / 2f - r, imgH / 2f - r, imgW / 2f + r, imgH / 2f + r)
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

    fun drawLivePixelShape(
        canvas: Canvas,
        original: Bitmap,
        cutout: Bitmap?,
        geo: UnifiedGeometry,
        config: LiveWallpaperConfig,
        shapePath: Path,
        screenShapeRect: RectF,
        bodyMatrix: Matrix,
        bitmapPaint: Paint,
        maskXferPaint: Paint
    ) {
        canvas.drawColor(config.backgroundColor)

        canvas.save()
        canvas.clipPath(shapePath)
        canvas.drawBitmap(original, bodyMatrix, bitmapPaint)
        canvas.restore()

        if (config.is3DPopEnabled && cutout != null) {
            val layerId = canvas.saveLayer(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), null)
            canvas.drawBitmap(cutout, bodyMatrix, bitmapPaint)
            canvas.drawBitmap(original, bodyMatrix, maskXferPaint)
            canvas.restoreToCount(layerId)
        }
    }

    fun createShapeCropBitmapWithPreCutout(
        original: Bitmap,
        cutout: Bitmap?,
        config: LiveWallpaperConfig
    ): Bitmap {
        if (!config.isMagicShapeEnabled) return original

        val w = original.width
        val h = original.height
        val geo = getUnifiedGeometry(w, h, w.toFloat(), h.toFloat(), cutout, config)

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val maskXferPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }

        val anchorX = if (config.isCentered) geo.subjectCenterX else w / 2f
        val anchorY = if (config.isCentered) geo.subjectCenterY else h / 2f
        val currentImgScale = geo.baseScale * config.scale

        val bodyMatrix = Matrix().apply {
            postTranslate(-anchorX, -anchorY)
            postScale(currentImgScale, currentImgScale)
            postTranslate(w / 2f, h / 2f)
        }

        val screenShape = RectF(geo.shapeBoundsRel)
        bodyMatrix.mapRect(screenShape)

        val vShift = if (config.is3DPopEnabled) screenShape.height() * 0.12f else 0f
        screenShape.offset(0f, vShift)

        val shapeEnum = MagicShape.fromName(config.shapeName)
        val path = Path()
        PixelShapeMorpher.buildMorphedPath(shapeEnum, shapeEnum, 1.0f, screenShape, path)

        drawLivePixelShape(
            canvas = canvas,
            original = original,
            cutout = cutout,
            geo = geo,
            config = config,
            shapePath = path,
            screenShapeRect = screenShape,
            bodyMatrix = bodyMatrix,
            bitmapPaint = paint,
            maskXferPaint = maskXferPaint
        )

        return result
    }
}
