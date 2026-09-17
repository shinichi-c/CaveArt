package com.android.CaveArt

import android.graphics.*
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class UnifiedGeometry(
    val baseScale: Float,
    val shiftX: Float,
    val shiftY: Float,
    val subjectCenterX: Float,
    val subjectCenterY: Float,
    val shapeBoundsRel: RectF,
    val centeredShapeRect: RectF,
    val is3DPopEligible: Boolean,
    val targetImgScale: Float = baseScale,
    val targetTranslateX: Float = 0f,
    val targetTranslateY: Float = 0f
) {
    
    fun setupTransitionMatrices(
        imgW: Int,
        imgH: Int,
        screenW: Float,
        screenH: Float,
        config: LiveWallpaperConfig,
        transitionProgress: Float = 0f,
        centerProgress: Float = if (config.isCentered) 1f else 0f,
        outBodyMatrix: Matrix,
        outScreenShapeRect: RectF
    ) {
        val c = centerProgress.coerceIn(0f, 1f)
        val t = transitionProgress.coerceIn(0f, 1f)
        
        val naturalScale = baseScale * config.scale
        val naturalTx = (screenW - imgW * naturalScale) / 2f
        val naturalTy = (screenH - imgH * naturalScale) / 2f

        val naturalMatrix = Matrix().apply {
            postScale(naturalScale, naturalScale)
            postTranslate(naturalTx, naturalTy)
        }
        val mappedNaturalShape = RectF(shapeBoundsRel)
        naturalMatrix.mapRect(mappedNaturalShape)

        val naturalShapeCenterX = mappedNaturalShape.centerX()
        val naturalShapeCenterY = mappedNaturalShape.centerY()
        val naturalShapeRadius = mappedNaturalShape.width() / 2f
        
        val centeredScale = targetImgScale
        val centeredTx = targetTranslateX
        val centeredTy = targetTranslateY
        val centeredShapeCenterX = centeredShapeRect.centerX()
        val centeredShapeCenterY = centeredShapeRect.centerY()
        val centeredShapeRadius = centeredShapeRect.width() / 2f
        
        val startScale = naturalScale + (centeredScale - naturalScale) * c
        val startTx = naturalTx + (centeredTx - naturalTx) * c
        val startTy = naturalTy + (centeredTy - naturalTy) * c
        val startShapeCenterX = naturalShapeCenterX + (centeredShapeCenterX - naturalShapeCenterX) * c
        val startShapeCenterY = naturalShapeCenterY + (centeredShapeCenterY - naturalShapeCenterY) * c
        val startShapeRadius = naturalShapeRadius + (centeredShapeRadius - naturalShapeRadius) * c
        
        val endScale = baseScale
        val endTx = (screenW - imgW * endScale) / 2f
        val endTy = (screenH - imgH * endScale) / 2f
        val endShapeCenterX = screenW / 2f
        val endShapeCenterY = screenH / 2f
        val endShapeRadius = hypot(screenW, screenH)
        
        val currentScale = startScale + (endScale - startScale) * t
        val currentTx = startTx + (endTx - startTx) * t
        val currentTy = startTy + (endTy - startTy) * t

        outBodyMatrix.reset()
        outBodyMatrix.postScale(currentScale, currentScale)
        outBodyMatrix.postTranslate(currentTx, currentTy)

        val currentShapeCenterX = startShapeCenterX + (endShapeCenterX - startShapeCenterX) * t
        val currentShapeCenterY = startShapeCenterY + (endShapeCenterY - startShapeCenterY) * t
        val currentRadius = startShapeRadius + (endShapeRadius - startShapeRadius) * t

        outScreenShapeRect.set(
            currentShapeCenterX - currentRadius,
            currentShapeCenterY - currentRadius,
            currentShapeCenterX + currentRadius,
            currentShapeCenterY + currentRadius
        )
    }

    fun setupMatrices(
        imgW: Int,
        imgH: Int,
        screenW: Float,
        screenH: Float,
        config: LiveWallpaperConfig,
        outBodyMatrix: Matrix,
        outScreenShapeRect: RectF
    ) {
        setupTransitionMatrices(imgW, imgH, screenW, screenH, config, 0f, if (config.isCentered) 1f else 0f, outBodyMatrix, outScreenShapeRect)
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
        
        val shapeDiameter = screenW * 0.86f
        val shapeRadius = shapeDiameter / 2f
        val shapeCenterX = screenW / 2f
        val shapeCenterY = screenH / 2f

        val centeredShapeRect = RectF(
            shapeCenterX - shapeRadius,
            shapeCenterY - shapeRadius,
            shapeCenterX + shapeRadius,
            shapeCenterY + shapeRadius
        )

        var targetImgScale = baseScale
        var targetTx = (screenW - imgW * baseScale) / 2f
        var targetTy = (screenH - imgH * baseScale) / 2f

        if (eligibility.isEligible && eligibility.subjectBounds != null) {
            val sb = eligibility.subjectBounds
            val sW = sb.width()
            val sH = sb.height()
            val sCenterX = sb.centerX()
            val sTop = sb.top
            val sBottom = sb.bottom

            val isBottomCropped = (imgH - sBottom) < (imgH * 0.05f)

            val minScaleForWidth = (shapeDiameter * 1.05f) / imgW.toFloat()
            val availablePhotoHeight = (imgH - sTop).coerceAtLeast(sH)
            val minScaleForHeight = (shapeDiameter * 1.08f) / availablePhotoHeight
            val absoluteMinSafeScale = max(minScaleForWidth, minScaleForHeight)

            val widthTarget = shapeDiameter * 0.82f
            val heightTarget = shapeDiameter * 0.88f
            val scaleByWidth = widthTarget / sW
            val scaleByHeight = heightTarget / sH
            val baseSubjectScale = min(scaleByWidth, scaleByHeight * 1.15f)

            val desiredScale = baseSubjectScale * config.scale
            targetImgScale = max(desiredScale, absoluteMinSafeScale)

            val popBreakoutRatio = if (config.is3DPopEnabled) 0.20f else 0.0f
            val popAmount = sH * targetImgScale * popBreakoutRatio

            targetTx = shapeCenterX - (sCenterX * targetImgScale)
            targetTy = (shapeCenterY - shapeRadius - popAmount) - (sTop * targetImgScale)

            var adjustedCenterY = shapeCenterY
            if (isBottomCropped) {
                val photoBottom = targetTy + (imgH * targetImgScale)
                val shapeBottom = adjustedCenterY + shapeRadius
                if (photoBottom < shapeBottom) {
                    adjustedCenterY -= (shapeBottom - photoBottom)
                }
            } else {
                val photoBottom = targetTy + (imgH * targetImgScale)
                val shapeBottom = adjustedCenterY + shapeRadius
                if (photoBottom < shapeBottom) {
                    targetTy += (shapeBottom - photoBottom)
                }
            }

            val shapeLeft = shapeCenterX - shapeRadius
            val shapeRight = shapeCenterX + shapeRadius
            val photoRight = targetTx + (imgW * targetImgScale)

            if (targetTx > shapeLeft) targetTx = shapeLeft
            if (photoRight < shapeRight) targetTx += (shapeRight - photoRight)

            centeredShapeRect.set(
                shapeCenterX - shapeRadius,
                adjustedCenterY - shapeRadius,
                shapeCenterX + shapeRadius,
                adjustedCenterY + shapeRadius
            )
        } else {
            
            targetImgScale = max(shapeDiameter / imgW.toFloat(), shapeDiameter / imgH.toFloat()) * 1.08f * config.scale
            targetTx = shapeCenterX - (imgW * targetImgScale / 2f)
            targetTy = shapeCenterY - (imgH * targetImgScale / 2f)
        }
        
        val naturalFraming = if (eligibility.isEligible && eligibility.subjectBounds != null) {
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

        val rawNaturalShape = naturalFraming.shapeBounds

        return UnifiedGeometry(
            baseScale = baseScale,
            shiftX = 0f,
            shiftY = 0f,
            subjectCenterX = rawNaturalShape.centerX(),
            subjectCenterY = rawNaturalShape.centerY(),
            shapeBoundsRel = rawNaturalShape,
            centeredShapeRect = centeredShapeRect,
            is3DPopEligible = eligibility.isEligible,
            targetImgScale = targetImgScale,
            targetTranslateX = targetTx,
            targetTranslateY = targetTy
        )
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
            val breakoutCutoffY = screenShapeRect.top + (screenShapeRect.height() * 0.30f)
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
