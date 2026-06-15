package com.android.CaveArt

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.max

data class AutoFitResult(
    val hourSize: Float,
    val minSize: Float,
    val yDp: Float
)

class ClockPaths(val hours: Path = Path(), val colon: Path = Path(), val mins: Path = Path())

object AdaptiveClockHelper {

    fun measureClockWidth(timeString: String, hourSizePx: Float, minSizePx: Float, typeface: Typeface, isVertical: Boolean): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface }
        val colonIdx = timeString.indexOf(':')
        val hours = if (colonIdx != -1) timeString.substring(0, colonIdx) else "00"
        val mins = if (colonIdx != -1) timeString.substring(colonIdx + 1) else "00"
        
        if (isVertical) {
            val hStr = hours.padStart(2, '0')
            val mStr = mins.padStart(2, '0')
            
            paint.textSize = hourSizePx
            val wH0 = paint.measureText(hStr[0].toString())
            val wH1 = paint.measureText(hStr[1].toString())
            
            paint.textSize = minSizePx
            val wM0 = paint.measureText(mStr[0].toString())
            val wM1 = paint.measureText(mStr[1].toString())
            
            val col0Width = max(wH0, wM0)
            val col1Width = max(wH1, wM1)
            val gapX = hourSizePx * 0.1f
            
            return col0Width + gapX + col1Width
        } else {
            var total = 0f
            paint.textSize = hourSizePx
            for (c in hours) {
                val w = paint.measureText(c.toString())
                total += w + w * 0.15f
            }
            if (colonIdx != -1) {
                val cw = paint.measureText(":")
                total += cw + cw * 0.5f
            }
            paint.textSize = minSizePx
            for (c in mins) {
                val w = paint.measureText(c.toString())
                total += w + w * 0.15f
            }
            return total
        }
    }

    fun buildPaths(
        timeString: String,
        startX: Float,
        startY: Float,
        absoluteClockX: Float,
        absoluteClockY: Float,
        hourSizePx: Float,
        minSizePx: Float,
        typeface: Typeface,
        screenW: Float,
        screenH: Float,
        isStretchEnabled: Boolean,
        isVertical: Boolean,
        collisionMap: FloatArray?,
        density: Float,
        strokeWidth: Float,
        stretchProgress: Float = 1f
    ): ClockPaths {
        
        val paths = ClockPaths()
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface }
        
        var currentX = startX
        val charPath = Path()
        val charBounds = RectF()
        val transformMatrix = Matrix()

        val colonIdx = timeString.indexOf(':')
        val hours = if (colonIdx != -1) timeString.substring(0, colonIdx) else "00"
        val mins = if (colonIdx != -1) timeString.substring(colonIdx + 1) else "00"

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
            val maxStretchLimit = startY + (defaultH * 2.8f) 
            val minCarHeight = startY + (defaultH * 0.4f)
            
            var finalY = exactSurfaceY
            if (minHitY >= screenH * 0.98f) {
                finalY = maxStretchLimit
            }
            
            val fullyStretchedY = finalY.coerceIn(minCarHeight, maxStretchLimit)
            return normalY + (fullyStretchedY - normalY) * stretchProgress
        }

        if (isVertical) {
            val hStr = hours.padStart(2, '0')
            val mStr = mins.padStart(2, '0')
            val gapX = hourSizePx * 0.1f
            val gapY = hourSizePx * 0.05f
            
            textPaint.textSize = hourSizePx
            val wH0 = textPaint.measureText(hStr[0].toString())
            val wH1 = textPaint.measureText(hStr[1].toString())
            textPaint.textSize = minSizePx
            val wM0 = textPaint.measureText(mStr[0].toString())
            val wM1 = textPaint.measureText(mStr[1].toString())
            
            val col0Width = max(wH0, wM0)
            val col1Width = max(wH1, wM1)
            
            for (col in 0..1) {
                val hChar = hStr[col].toString()
                val mChar = mStr[col].toString()
                val colWidth = if (col == 0) col0Width else col1Width
                
                val defaultTotalHeight = hourSizePx + minSizePx + gapY
                val dropY = getDropY(currentX, colWidth, defaultTotalHeight)
                val totalAvailableH = dropY - startY
                
                val targetCharHeight = ((totalAvailableH - gapY) / 2f).coerceAtLeast(hourSizePx * 0.4f)
                
                textPaint.textSize = hourSizePx
                charPath.rewind()
                textPaint.getTextPath(hChar, 0, 1, 0f, 0f, charPath)
                charPath.computeBounds(charBounds, true)
                if (charBounds.height() > 0) {
                    val offsetY = startY - charBounds.top
                    val scaleY = if (isStretchEnabled) targetCharHeight / charBounds.height() else 1f
                    transformMatrix.reset()
                    val centerOffsetX = currentX + (colWidth - textPaint.measureText(hChar)) / 2f
                    transformMatrix.postTranslate(centerOffsetX, offsetY)
                    transformMatrix.postScale(1f, scaleY, currentX, startY)
                    charPath.transform(transformMatrix)
                    paths.hours.addPath(charPath)
                }
                
                textPaint.textSize = minSizePx
                charPath.rewind()
                textPaint.getTextPath(mChar, 0, 1, 0f, 0f, charPath)
                charPath.computeBounds(charBounds, true)
                if (charBounds.height() > 0) {
                    val actualMStartY = if (isStretchEnabled) startY + targetCharHeight + gapY else startY + hourSizePx + gapY
                    val offsetY = actualMStartY - charBounds.top
                    val scaleY = if (isStretchEnabled) targetCharHeight / charBounds.height() else 1f
                    transformMatrix.reset()
                    val centerOffsetX = currentX + (colWidth - textPaint.measureText(mChar)) / 2f
                    transformMatrix.postTranslate(centerOffsetX, offsetY)
                    transformMatrix.postScale(1f, scaleY, currentX, actualMStartY)
                    charPath.transform(transformMatrix)
                    paths.mins.addPath(charPath)
                }
                
                currentX += colWidth + gapX
            }

        } else {
            fun appendText(text: String, sizePx: Float, targetPath: Path, isColon: Boolean = false) {
                textPaint.textSize = sizePx
                for (i in text.indices) {
                    val charStr = text[i].toString()
                    val charWidth = textPaint.measureText(charStr)
                    
                    charPath.rewind()
                    textPaint.getTextPath(charStr, 0, 1, 0f, 0f, charPath)
                    charPath.computeBounds(charBounds, true)

                    val charActualHeight = charBounds.height()
                    if (charActualHeight > 0) {
                        val offsetY = startY - charBounds.top
                        transformMatrix.reset()
                        transformMatrix.postTranslate(currentX, offsetY)
                        charPath.transform(transformMatrix)
                        
                        if (!isColon) {
                            val dropY = getDropY(currentX, charWidth, sizePx)
                            val targetHeight = dropY - startY
                            
                            if (isStretchEnabled && targetHeight > charActualHeight) {
                                val scaleY = targetHeight / charActualHeight
                                transformMatrix.reset()
                                transformMatrix.postScale(1f, scaleY, currentX, startY)
                                charPath.transform(transformMatrix)
                            }
                        }
                    }
                    targetPath.addPath(charPath)
                    val gap = if (isColon) charWidth * 0.5f else charWidth * 0.15f
                    currentX += charWidth + gap
                }
            }

            appendText(hours, hourSizePx, paths.hours)
            if (colonIdx != -1) appendText(":", hourSizePx, paths.colon, true)
            appendText(mins, minSizePx, paths.mins)
        }

        return paths
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
