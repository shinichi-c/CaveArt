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
    val shapeBoundsRel: RectF,
    val is3DPopEligible: Boolean,
    val targetImgScale: Float = baseScale,
    val targetTranslateX: Float = 0f,
    val targetTranslateY: Float = 0f
) {
    fun setupMatrices(
        imgW: Int,
        imgH: Int,
        screenW: Float,
        screenH: Float,
        config: LiveWallpaperConfig,
        outBodyMatrix: Matrix,
        outScreenShapeRect: RectF
    ) {
        if (config.isCentered && targetTranslateX != 0f) {
            
            outBodyMatrix.reset()
            outBodyMatrix.postScale(targetImgScale, targetImgScale)
            outBodyMatrix.postTranslate(targetTranslateX, targetTranslateY)
            outScreenShapeRect.set(shapeBoundsRel)
        } else {
            
            val currentImgScale = baseScale * config.scale
            val anchorX = if (config.isCentered) subjectCenterX else imgW / 2f
            val anchorY = if (config.isCentered) subjectCenterY else imgH / 2f

            outBodyMatrix.reset()
            outBodyMatrix.postTranslate(-anchorX, -anchorY)
            outBodyMatrix.postScale(currentImgScale, currentImgScale)
            outBodyMatrix.postTranslate(screenW / 2f, screenH / 2f)

            outScreenShapeRect.set(shapeBoundsRel)
            outBodyMatrix.mapRect(outScreenShapeRect)
        }
    }
}

object ShapeEffectHelper {

    fun getUnifiedGeometry(
        imgW: Int,
        imgH: Int,
        screenW: Float,
        screenH: Float,
        mask: Bitmap?,
        config: LiveWallpaperConfig
    ): UnifiedGeometry {
        val eligibility = SubjectPopAnalyzer.analyzeEligibility(mask)
        val baseScale = max(screenW / imgW, screenH / imgH)

        if (config.isCentered && eligibility.isEligible && eligibility.subjectBounds != null) {
            val sb = eligibility.subjectBounds
            val sW = sb.width()
            val sH = sb.height()
            val sCenterX = sb.centerX()
            val sTop = sb.top
            val sBottom = sb.bottom
            val shapeDiameter = screenW * 0.86f
            val shapeRadius = shapeDiameter / 2f
            val shapeCenterX = screenW / 2f
            val shapeCenterY = screenH / 2f
            val isBottomCropped = (imgH - sBottom) < (imgH * 0.05f)

            val minScaleForWidth = (shapeDiameter * 1.05f) / imgW.toFloat()
            val availablePhotoHeight = (imgH - sTop).coerceAtLeast(sH)
            val minScaleForHeight = (shapeDiameter * 1.05f) / availablePhotoHeight
            val absoluteMinSafeScale = max(minScaleForWidth, minScaleForHeight)
            
            val widthTarget = shapeDiameter * 0.82f
            val heightTarget = shapeDiameter * 0.88f
            val scaleByWidth = widthTarget / sW
            val scaleByHeight = heightTarget / sH
            val baseSubjectScale = min(scaleByWidth, scaleByHeight * 1.15f)

            val desiredScale = baseSubjectScale * config.scale
            val finalImgScale = max(desiredScale, absoluteMinSafeScale)
            
            val popBreakoutRatio = if (config.is3DPopEnabled) 0.20f else 0.0f
            val popAmount = sH * finalImgScale * popBreakoutRatio

            var targetTx = shapeCenterX - (sCenterX * finalImgScale)
            var targetTy = (shapeCenterY - shapeRadius - popAmount) - (sTop * finalImgScale)

            var adjustedShapeCenterY = shapeCenterY

            if (isBottomCropped) {
                val photoBottom = targetTy + (imgH * finalImgScale)
                val shapeBottom = adjustedShapeCenterY + shapeRadius
                if (photoBottom < shapeBottom) {
                    val deficit = shapeBottom - photoBottom
                    adjustedShapeCenterY -= deficit
                }
            } else {
                val photoBottom = targetTy + (imgH * finalImgScale)
                val shapeBottom = adjustedShapeCenterY + shapeRadius
                if (photoBottom < shapeBottom) {
                    targetTy += (shapeBottom - photoBottom)
                }
            }
            
            val shapeLeft = shapeCenterX - shapeRadius
            val shapeRight = shapeCenterX + shapeRadius
            val photoRight = targetTx + (imgW * finalImgScale)

            if (targetTx > shapeLeft) targetTx = shapeLeft
            if (photoRight < shapeRight) targetTx += (shapeRight - photoRight)

            val finalScreenShapeRect = RectF(
                shapeCenterX - shapeRadius,
                adjustedShapeCenterY - shapeRadius,
                shapeCenterX + shapeRadius,
                adjustedShapeCenterY + shapeRadius
            )

            return UnifiedGeometry(
                baseScale = baseScale,
                shiftX = 0f,
                shiftY = 0f,
                subjectCenterX = sCenterX,
                subjectCenterY = sb.centerY(),
                shapeBoundsRel = finalScreenShapeRect,
                is3DPopEligible = true,
                targetImgScale = finalImgScale,
                targetTranslateX = targetTx,
                targetTranslateY = targetTy
            )
        } else {
            val framing = if (eligibility.isEligible && eligibility.subjectBounds != null) {
                SubjectPopAnalyzer.calculatePopFraming(
                    subjectBounds = eligibility.subjectBounds,
                    imgW = imgW,
                    imgH = imgH,
                    is3DPopEnabled = config.is3DPopEnabled
                )
            } else {
                val r = min(imgW, imgH) * 0.42f
                PopFraming(
                    shapeBounds = RectF(imgW / 2f - r, imgH / 2f - r, imgW / 2f + r, imgH / 2f + r),
                    popClipTop = 0f,
                    isPopActive = false
                )
            }

            val rawShape = framing.shapeBounds
            return UnifiedGeometry(
                baseScale = baseScale,
                shiftX = 0f,
                shiftY = 0f,
                subjectCenterX = rawShape.centerX(),
                subjectCenterY = rawShape.centerY(),
                shapeBoundsRel = rawShape,
                is3DPopEligible = eligibility.isEligible
            )
        }
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
        
        if (config.is3DPopEnabled && geo.is3DPopEligible && cutout != null) {
            val layerId = canvas.saveLayer(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), null)
            
            canvas.save()
            val breakoutCutoffY = screenShapeRect.top + (screenShapeRect.height() * 0.32f)
            canvas.clipRect(0f, 0f, canvas.width.toFloat(), breakoutCutoffY)
            
            canvas.drawBitmap(cutout, bodyMatrix, bitmapPaint)
            canvas.drawBitmap(original, bodyMatrix, maskXferPaint)
            canvas.restore()

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

        val bodyMatrix = Matrix()
        val screenShape = RectF()
        geo.setupMatrices(w, h, w.toFloat(), h.toFloat(), config, bodyMatrix, screenShape)

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
