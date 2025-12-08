package com.android.CaveArt

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory

object BitmapHelper {
    
    fun decodeSampledBitmapFromResource(
        res: Resources,
        resId: Int,
        maxDimension: Int
    ): Bitmap {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeResource(res, resId, options)
        
        options.inSampleSize = calculateInSampleSize(options, maxDimension)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        
        return BitmapFactory.decodeResource(res, resId, options) 
            ?: throw IllegalArgumentException("Resource not found or decode failed for ID: $resId")
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, maxDimension: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > maxDimension || width > maxDimension) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            
            while ((halfHeight / inSampleSize) >= maxDimension && (halfWidth / inSampleSize) >= maxDimension) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}