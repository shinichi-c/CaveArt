package com.android.CaveArt

import android.content.Context
import android.graphics.*
import kotlin.math.max
import kotlin.math.min

object ShapeEffectHelper {

    suspend fun createShapeCropBitmap(
        context: Context,
        originalBitmap: Bitmap,
        shape: MagicShape,
        backgroundColor: Int,
        enable3DPop: Boolean,
        scaleFactor: Float
    ): Bitmap {
        return originalBitmap
    }

    fun createShapeCropBitmapWithPreCutout(
        context: Context,
        original: Bitmap,
        cutout: Bitmap, 
        shape: MagicShape,
        backgroundColor: Int,
        enable3DPop: Boolean,
        scaleFactor: Float
    ): Bitmap {
        val width = original.width
        val height = original.height
        
        val finalBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(finalBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        
        canvas.drawColor(backgroundColor)
        
        val subjectBounds = Geometric.getSmartBounds(cutout, width, height)
        val subjectCenterX = subjectBounds.centerX()
        val subjectCenterY = subjectBounds.centerY()
        
        val minScreenDim = min(width, height).toFloat()
        val baseSize = minScreenDim * 0.65f 
        val shapeSize = baseSize * scaleFactor
        val halfShape = shapeSize / 2f
        
        val safeCenterX = subjectCenterX.coerceIn(width * 0.3f, width * 0.7f)
        val safeCenterY = subjectCenterY.coerceIn(height * 0.3f, height * 0.7f)

        val shapeBounds = RectF(
            safeCenterX - halfShape,
            safeCenterY - halfShape,
            safeCenterX + halfShape,
            safeCenterY + halfShape
        )
        
        val saveCount = canvas.save()
        val shapePath = ShapePathProvider.getPathForShape(shape, shapeBounds)
        canvas.clipPath(shapePath)
        canvas.drawBitmap(original, 0f, 0f, paint)
        canvas.restoreToCount(saveCount)
        
        if (enable3DPop) {
            val destRect = Rect(0, 0, width, height)
            canvas.drawBitmap(cutout, null, destRect, paint)
        }

        return finalBitmap
    }
}