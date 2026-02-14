package com.android.CaveArt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.max

object ShapeEffectHelper {
	
    fun calculateShapeBounds(
        originalWidth: Int,
        originalHeight: Int,
        cutout: Bitmap?,
        scaleFactor: Float,
        isCentered: Boolean
    ): RectF {
        val rawBounds = if (cutout != null) {
            Geometric.calculateCircleBounds(cutout, originalWidth, originalHeight, scaleFactor)
        } else {
            val cx = originalWidth / 2f
            val cy = originalHeight / 2f
            val r = kotlin.math.min(originalWidth, originalHeight) / 2f * scaleFactor
            RectF(cx - r, cy - r, cx + r, cy + r)
        }

        var centerX = rawBounds.centerX()
        var centerY = rawBounds.centerY()

        if (isCentered) {
            centerX = originalWidth / 2f
            centerY = originalHeight / 2f
        }

        val sideLength = max(rawBounds.width(), rawBounds.height())
        val safeSide = if (sideLength < 50f) originalWidth * 0.5f else sideLength
        val halfSide = safeSide / 2f
        
        return RectF(
            centerX - halfSide,
            centerY - halfSide,
            centerX + halfSide,
            centerY + halfSide
        )
    }
    
    fun createShapeCropBitmapWithPreCutout(
        context: Context,
        original: Bitmap,
        cutout: Bitmap, 
        shape: MagicShape,
        backgroundColor: Int,
        enable3DPop: Boolean,
        scaleFactor: Float,
        isCentered: Boolean
    ): Bitmap {
        val width = original.width
        val height = original.height
        
        val finalBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(finalBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        
        val shapeBounds = calculateShapeBounds(width, height, cutout, scaleFactor, isCentered)
        
        val originalCenterBounds = Geometric.calculateCircleBounds(cutout, width, height, scaleFactor)
        val shiftX = if (isCentered) (width / 2f) - originalCenterBounds.centerX() else 0f
        val shiftY = if (isCentered) (height / 2f) - originalCenterBounds.centerY() else 0f
        
        val verticalShift = if (enable3DPop) shapeBounds.height() * 0.1f else 0f
        val shiftedBounds = RectF(shapeBounds)
        shiftedBounds.offset(0f, verticalShift)

        canvas.drawColor(backgroundColor)
        
        val saveCount = canvas.save()
        val shapePath = ShapePathProvider.getPathForShape(shape, shiftedBounds)
        canvas.clipPath(shapePath)
        
        canvas.translate(shiftX, shiftY)
        canvas.drawBitmap(original, 0f, 0f, paint)
        canvas.restoreToCount(saveCount)
        
        if (enable3DPop) {
            val layerId = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
            
            canvas.translate(shiftX, shiftY)
            canvas.drawBitmap(cutout, 0f, 0f, paint)
            
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(original, 0f, 0f, paint)
            paint.xfermode = null
            
            canvas.restoreToCount(layerId)
        }

        return finalBitmap
    }
}