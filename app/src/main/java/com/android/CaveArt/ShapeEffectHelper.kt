package com.android.CaveArt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max

object ShapeEffectHelper {
	
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
        val rawBounds = Geometric.calculateCircleBounds(cutout, width, height, scaleFactor)

        val sideLength = max(rawBounds.width(), rawBounds.height())
        val centerX = rawBounds.centerX()
        val centerY = rawBounds.centerY()
        val halfSide = sideLength / 2f
        val verticalShift = if (enable3DPop) sideLength * 0.1f else 0f
        val shapeBounds = RectF(
            centerX - halfSide,
            centerY - halfSide + verticalShift,
            centerX + halfSide,
            centerY + halfSide + verticalShift
        )
        
        canvas.drawColor(backgroundColor)
        
        val saveCount = canvas.save()
        val shapePath = ShapePathProvider.getPathForShape(shape, shapeBounds)
        canvas.clipPath(shapePath)
        canvas.drawBitmap(original, 0f, 0f, paint)
        canvas.restoreToCount(saveCount)
        
        if (enable3DPop) {
            val popSaveCount = canvas.save()
            canvas.clipRect(0f, 0f, width.toFloat(), shapeBounds.bottom)
            val destRect = android.graphics.Rect(0, 0, width, height)
            canvas.drawBitmap(cutout, null, destRect, paint)
            
            canvas.restoreToCount(popSaveCount)
        }

        return finalBitmap
    }
}