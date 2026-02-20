package com.android.CaveArt

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri

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
    
    fun decodeSampledBitmapFromUri(
        context: Context,
        uri: Uri,
        maxDimension: Int
    ): Bitmap {
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }
            
            options.inSampleSize = calculateInSampleSize(options, maxDimension)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            
            val bitmap = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            } ?: throw IllegalArgumentException("Decode failed for URI: $uri")
            
            var orientation = ExifInterface.ORIENTATION_NORMAL
            try {
                context.contentResolver.openInputStream(uri)?.use { exifStream ->
                    val exif = ExifInterface(exifStream)
                    orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            return rotateBitmap(bitmap, orientation)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1.0f, 1.0f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.postRotate(180f)
                matrix.preScale(-1.0f, 1.0f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1.0f, 1.0f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1.0f, 1.0f)
            }
            else -> return bitmap
        }
        
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) {
            bitmap.recycle()
        }
        return rotated
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