package com.android.CaveArt

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF

data class AutoFitResult(
    val hourSize: Float,
    val minSize: Float,
    val yDp: Float
)

object AdaptiveClockHelper {

    fun buildPath(
        timeString: String,
        startX: Float,
        startY: Float,
        absoluteClockX: Float,
        absoluteClockY: Float,
        hourH: Float,
        minH: Float,
        screenW: Float,
        screenH: Float,
        isStretchEnabled: Boolean,
        collisionMap: FloatArray?,
        density: Float,
        strokeWidth: Float,
        stretchProgress: Float = 1f 
    ): Path {
        val path = Path()
        val hourW = hourH * 0.55f
        val minW = minH * 0.55f
        val gap = hourH * 0.15f
        var currentX = startX

        val parts = timeString.split(":")
        val hours = parts.getOrNull(0) ?: "00"
        val mins = parts.getOrNull(1) ?: "00"

        fun getDropY(cx: Float, cw: Float, defaultH: Float): Float {
            val normalY = startY + defaultH
            
            if (!isStretchEnabled || collisionMap == null || collisionMap.isEmpty()) return normalY
            
            val absoluteX = cx + absoluteClockX
            val idxLeft = ((absoluteX / screenW) * 100f).toInt().coerceIn(0, 99)
            val idxRight = (((absoluteX + cw) / screenW) * 100f).toInt().coerceIn(0, 99)
            
            var minHitY = Float.MAX_VALUE
            for (i in idxLeft..idxRight) {
                if (collisionMap[i] < minHitY) {
                    minHitY = collisionMap[i]
                }
            }
            
            val padding = (20f * density) + ((strokeWidth * density) / 2f) 
            val exactSurfaceY = minHitY - absoluteClockY - padding
            
            val maxStretchLimit = startY + (defaultH * 2.2f) 
            val minCarHeight = startY + (defaultH * 0.4f)
            
            var finalY = exactSurfaceY
            
            if (minHitY >= screenH * 0.98f) {
                finalY = maxStretchLimit
            }
            
            val fullyStretchedY = finalY.coerceIn(minCarHeight, maxStretchLimit)
            
            return normalY + (fullyStretchedY - normalY) * stretchProgress
        }

        fun drawDigit(digit: Char, x: Float, w: Float, h: Float, dropY: Float) {
            val actualH = dropY - startY
            val midY = startY + actualH / 2f
            
            when (digit) {
                '0' -> { path.addRect(x, startY, x+w, dropY, Path.Direction.CW) }
                '1' -> { path.moveTo(x + w/2, startY); path.lineTo(x + w/2, dropY) }
                '2' -> { path.moveTo(x, startY); path.lineTo(x+w, startY); path.lineTo(x+w, midY); path.lineTo(x, midY); path.lineTo(x, dropY); path.lineTo(x+w, dropY) }
                '3' -> { path.moveTo(x, startY); path.lineTo(x+w, startY); path.lineTo(x+w, midY); path.lineTo(x, midY); path.moveTo(x+w, midY); path.lineTo(x+w, dropY); path.lineTo(x, dropY) }
                '4' -> { path.moveTo(x, startY); path.lineTo(x, midY); path.lineTo(x+w, midY); path.moveTo(x+w, startY); path.lineTo(x+w, dropY) }
                '5' -> { path.moveTo(x+w, startY); path.lineTo(x, startY); path.lineTo(x, midY); path.lineTo(x+w, midY); path.lineTo(x+w, dropY); path.lineTo(x, dropY) }
                '6' -> { path.moveTo(x+w, startY); path.lineTo(x, startY); path.lineTo(x, dropY); path.lineTo(x+w, dropY); path.lineTo(x+w, midY); path.lineTo(x, midY) }
                '7' -> { path.moveTo(x, startY); path.lineTo(x+w, startY); path.lineTo(x+w, dropY) }
                '8' -> { path.addRect(x, startY, x+w, midY, Path.Direction.CW); path.addRect(x, midY, x+w, dropY, Path.Direction.CW) }
                '9' -> { path.addRect(x, startY, x+w, midY, Path.Direction.CW); path.moveTo(x+w, midY); path.lineTo(x+w, dropY) }
            }
        }

        for (c in hours) {
            drawDigit(c, currentX, hourW, hourH, getDropY(currentX, hourW, hourH))
            currentX += hourW + gap
        }

        val colonX = currentX + (gap / 2f)
        val colonDropY = getDropY(colonX, gap, hourH)
        val colonH = colonDropY - startY
        path.moveTo(colonX, startY + colonH * 0.3f); path.lineTo(colonX, startY + colonH * 0.35f)
        path.moveTo(colonX, startY + colonH * 0.65f); path.lineTo(colonX, startY + colonH * 0.7f)
        currentX += gap * 2

        for (c in mins) {
            drawDigit(c, currentX, minW, minH, getDropY(currentX, minW, minH))
            currentX += minW + gap
        }
        return path
    }

    fun generateCollisionMap(mask: Bitmap, screenW: Float, screenH: Float): String {
        val w = mask.width
        val h = mask.height
        val pixels = IntArray(w * h)
        mask.getPixels(pixels, 0, w, 0, 0, w, h)

        val fillScale = kotlin.math.max(screenW / w, screenH / h)
        val matrix = Matrix().apply {
            postTranslate(-w / 2f, -h / 2f)
            postScale(fillScale, fillScale)
            postTranslate(screenW / 2f, screenH / 2f)
        }
        val inverse = Matrix()
        matrix.invert(inverse)

        val buckets = FloatArray(100) { screenH }
        val stepY = 10 
        
        for (i in 0 until 100) {
            val screenX = (i / 100f) * screenW + (screenW / 200f)
            var hitY = screenH
            
            for (sy in 0 until screenH.toInt() step stepY) {
                val pts = floatArrayOf(screenX, sy.toFloat())
                inverse.mapPoints(pts)
                val mx = pts[0].toInt()
                val my = pts[1].toInt()
                
                if (mx in 0 until w && my in 0 until h) {
                    val alpha = (pixels[my * w + mx] ushr 24) and 0xFF
                    if (alpha > 40) { 
                        hitY = sy.toFloat()
                        break
                    }
                }
            }
            buckets[i] = hitY
        }

        return buckets.joinToString(",")
    }

    fun calculateAutoFit(
        mask: Bitmap, screenW: Float, screenH: Float, 
        currentHourSize: Float, currentMinSize: Float, 
        density: Float, isMagicShapeEnabled: Boolean, 
        magicScale: Float, isCentered: Boolean
    ): AutoFitResult? {
        val rawBounds = Geometric.getTightSubjectBounds(mask) ?: return null

        val fillScale = kotlin.math.max(screenW / mask.width, screenH / mask.height)
        val matrix = Matrix()
        
        if (isMagicShapeEnabled) {
            val config = LiveWallpaperConfig(scale = magicScale, isCentered = isCentered, isMagicShapeEnabled = true)
            val geo = ShapeEffectHelper.getUnifiedGeometry(mask.width, mask.height, screenW, screenH, mask, config)
            val anchorX = if (isCentered) geo.subjectCenterX else mask.width / 2f
            val anchorY = if (isCentered) geo.subjectCenterY else mask.height / 2f
            val currentScale = fillScale * magicScale
            
            matrix.postTranslate(-anchorX, -anchorY)
            matrix.postScale(currentScale, currentScale)
            matrix.postTranslate(screenW / 2f, screenH / 2f)
        } else {
            matrix.postTranslate(-mask.width / 2f, -mask.height / 2f)
            matrix.postScale(fillScale, fillScale)
            matrix.postTranslate(screenW / 2f, screenH / 2f)
        }

        val screenBounds = RectF(rawBounds)
        matrix.mapRect(screenBounds)

        val topSpacePx = screenBounds.top
        val paddingPx = 20f * density
        val availableHeightPx = topSpacePx - paddingPx

        if (availableHeightPx > 40f * density) {
            val currentMaxDp = kotlin.math.max(currentHourSize, currentMinSize)
            val currentMaxPx = currentMaxDp * density
            
            var scaleRatio = 1.0f
            if (currentMaxPx > availableHeightPx) {
                scaleRatio = availableHeightPx / currentMaxPx
            }
            
            val newHourSize = (currentHourSize * scaleRatio).coerceIn(30f, 200f)
            val newMinSize = (currentMinSize * scaleRatio).coerceIn(30f, 200f)
            val newMaxPx = kotlin.math.max(newHourSize, newMinSize) * density
            
            val newYPx = (topSpacePx / 2f) - (newMaxPx / 2f)
            val newYDp = (newYPx / density).coerceAtLeast(15f)

            return AutoFitResult(newHourSize, newMinSize, newYDp)
        } else {
            return AutoFitResult(50f, 35f, 25f)
        }
    }
}
