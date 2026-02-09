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
        val rawBounds = Geometric.calculateCircleBounds(cutout, width, height, scaleFactor)

        var centerX = rawBounds.centerX()
        var centerY = rawBounds.centerY()
        var shiftX = 0f
        var shiftY = 0f

        if (isCentered) {
            val canvasCenterX = width / 2f
            val canvasCenterY = height / 2f
            
            shiftX = canvasCenterX - centerX
            shiftY = canvasCenterY - centerY
            
            centerX = canvasCenterX
            centerY = canvasCenterY
        }
        
        val sideLength = max(rawBounds.width(), rawBounds.height())
        val safeSide = if (sideLength < 50f) width * 0.5f else sideLength
        val halfSide = safeSide / 2f
        val verticalShift = if (enable3DPop) safeSide * 0.1f else 0f
        
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
        
        canvas.translate(shiftX, shiftY)
        
        canvas.drawBitmap(original, 0f, 0f, paint)
        canvas.restoreToCount(saveCount)
        
        if (enable3DPop) {
            try {
                
                val saveLayer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
                
                canvas.translate(shiftX, shiftY)
                
                val destRect = Rect(0, 0, width, height)
                
                canvas.drawBitmap(cutout, null, destRect, paint)
                
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                canvas.drawBitmap(original, 0f, 0f, paint)
                paint.xfermode = null
                
                canvas.restoreToCount(saveLayer)
                
            } catch (e: Exception) {
                
                val layerId = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
                
                canvas.translate(shiftX, shiftY)
                
                val destRect = Rect(0, 0, width, height)
                canvas.drawBitmap(cutout, null, destRect, paint)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                canvas.drawBitmap(original, 0f, 0f, paint)
                paint.xfermode = null
                canvas.restoreToCount(layerId)
            }
        }

        return finalBitmap
    }
}