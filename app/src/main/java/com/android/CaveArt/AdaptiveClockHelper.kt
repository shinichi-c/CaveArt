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
	
    private val sharedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sharedTransformMatrix = Matrix()
    private val sharedCharPath = Path()
    private val sharedCharBounds = RectF()
    private val sharedPaths = ClockPaths()

    fun measureClockWidth(timeString: String, hourSizePx: Float, minSizePx: Float, typeface: Typeface, isVertical: Boolean): Float {
        return measureClockWidth(timeString, hourSizePx, minSizePx, typeface, if (isVertical) 1f else 0f)
    }

    fun measureClockWidth(timeString: String, hourSizePx: Float, minSizePx: Float, typeface: Typeface, layoutProgress: Float): Float {
        sharedTextPaint.typeface = typeface
        val colonIdx = timeString.indexOf(':')
        val hours = if (colonIdx != -1) timeString.substring(0, colonIdx) else "00"
        val mins = if (colonIdx != -1) timeString.substring(colonIdx + 1) else "00"
        val hStr = hours.padStart(2, '0')
        val mStr = mins.padStart(2, '0')
        
        var totalHoriz = 0f
        sharedTextPaint.textSize = hourSizePx
        for (c in hours) {
            val w = sharedTextPaint.measureText(c.toString())
            totalHoriz += w + w * 0.15f
        }
        if (colonIdx != -1) {
            val cw = sharedTextPaint.measureText(":")
            totalHoriz += cw + cw * 0.5f
        }
        sharedTextPaint.textSize = minSizePx
        for (c in mins) {
            val w = sharedTextPaint.measureText(c.toString())
            totalHoriz += w + w * 0.15f
        }
        
        sharedTextPaint.textSize = hourSizePx
        val wH0 = sharedTextPaint.measureText(hStr[0].toString())
        val wH1 = sharedTextPaint.measureText(hStr[1].toString())
        sharedTextPaint.textSize = minSizePx
        val wM0 = sharedTextPaint.measureText(mStr[0].toString())
        val wM1 = sharedTextPaint.measureText(mStr[1].toString())

        val col0Width = max(wH0, wM0)
        val col1Width = max(wH1, wM1)
        val gapX = hourSizePx * 0.1f
        val totalVert = col0Width + gapX + col1Width

        return totalHoriz + (totalVert - totalHoriz) * layoutProgress.coerceIn(0f, 1f)
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
        return buildPaths(
            timeString, startX, startY, absoluteClockX, absoluteClockY,
            hourSizePx, minSizePx, typeface, screenW, screenH, isStretchEnabled,
            if (isVertical) 1f else 0f, collisionMap, density, strokeWidth, stretchProgress
        )
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
        layoutProgress: Float,
        collisionMap: FloatArray?,
        density: Float,
        strokeWidth: Float,
        stretchProgress: Float = 1f
    ): ClockPaths {
        sharedPaths.hours.rewind()
        sharedPaths.colon.rewind()
        sharedPaths.mins.rewind()
        
        sharedTextPaint.typeface = typeface

        val colonIdx = timeString.indexOf(':')
        val hours = if (colonIdx != -1) timeString.substring(0, colonIdx) else "00"
        val mins = if (colonIdx != -1) timeString.substring(colonIdx + 1) else "00"
        val hStr = hours.padStart(2, '0')
        val mStr = mins.padStart(2, '0')

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

        sharedTextPaint.textSize = hourSizePx
        val wH0 = sharedTextPaint.measureText(hStr[0].toString())
        val wH1 = sharedTextPaint.measureText(hStr[1].toString())
        val wColon = if (colonIdx != -1) sharedTextPaint.measureText(":") else 0f
        
        sharedTextPaint.textSize = minSizePx
        val wM0 = sharedTextPaint.measureText(mStr[0].toString())
        val wM1 = sharedTextPaint.measureText(mStr[1].toString())

        fun getCharActualHeight(charStr: String, sizePx: Float): Float {
            sharedTextPaint.textSize = sizePx
            sharedCharPath.rewind()
            sharedTextPaint.getTextPath(charStr, 0, 1, 0f, 0f, sharedCharPath)
            sharedCharPath.computeBounds(sharedCharBounds, true)
            return if (sharedCharBounds.height() > 0) sharedCharBounds.height() else sizePx
        }

        val h0_actualH = getCharActualHeight(hStr[0].toString(), hourSizePx)
        val h1_actualH = getCharActualHeight(hStr[1].toString(), hourSizePx)
        val m0_actualH = getCharActualHeight(mStr[0].toString(), minSizePx)
        val m1_actualH = getCharActualHeight(mStr[1].toString(), minSizePx)
        
        var curX = startX
        val h0_X_horiz = curX
        val h0_Y_horiz = startY
        curX += wH0 + wH0 * 0.15f

        val h1_X_horiz = curX
        val h1_Y_horiz = startY
        curX += wH1 + (if (colonIdx != -1) wColon * 0.5f else wH1 * 0.15f)

        val colon_X_horiz = curX
        val colon_Y_horiz = startY
        if (colonIdx != -1) {
            curX += wColon + wColon * 0.5f
        }

        val m0_X_horiz = curX
        val m0_Y_horiz = startY
        curX += wM0 + wM0 * 0.15f

        val m1_X_horiz = curX
        val m1_Y_horiz = startY

        val h0_scaleY_horiz = if (isStretchEnabled) {
            val targetH = getDropY(h0_X_horiz, wH0, hourSizePx) - startY
            if (targetH > h0_actualH) targetH / h0_actualH else 1f
        } else 1f

        val h1_scaleY_horiz = if (isStretchEnabled) {
            val targetH = getDropY(h1_X_horiz, wH1, hourSizePx) - startY
            if (targetH > h1_actualH) targetH / h1_actualH else 1f
        } else 1f

        val m0_scaleY_horiz = if (isStretchEnabled) {
            val targetH = getDropY(m0_X_horiz, wM0, minSizePx) - startY
            if (targetH > m0_actualH) targetH / m0_actualH else 1f
        } else 1f

        val m1_scaleY_horiz = if (isStretchEnabled) {
            val targetH = getDropY(m1_X_horiz, wM1, minSizePx) - startY
            if (targetH > m1_actualH) targetH / m1_actualH else 1f
        } else 1f
        
        val gapX = hourSizePx * 0.1f
        val gapY = hourSizePx * 0.05f
        val col0Width = max(wH0, wM0)
        val col1Width = max(wH1, wM1)
        val col0_X = startX
        val col1_X = startX + col0Width + gapX

        val defaultTotalHeight = hourSizePx + minSizePx + gapY
        val dropY_col0 = getDropY(col0_X, col0Width, defaultTotalHeight)
        val dropY_col1 = getDropY(col1_X, col1Width, defaultTotalHeight)

        val targetCharHeight0 = if (isStretchEnabled) ((dropY_col0 - startY - gapY) / 2f).coerceAtLeast(hourSizePx * 0.4f) else hourSizePx
        val targetCharHeight1 = if (isStretchEnabled) ((dropY_col1 - startY - gapY) / 2f).coerceAtLeast(hourSizePx * 0.4f) else hourSizePx

        val h0_X_vert = col0_X + (col0Width - wH0) / 2f
        val h0_Y_vert = startY
        val h0_scaleY_vert = if (isStretchEnabled) targetCharHeight0 / h0_actualH else 1f

        val m0_X_vert = col0_X + (col0Width - wM0) / 2f
        val m0_Y_vert = if (isStretchEnabled) startY + targetCharHeight0 + gapY else startY + hourSizePx + gapY
        val m0_scaleY_vert = if (isStretchEnabled) targetCharHeight0 / m0_actualH else 1f

        val h1_X_vert = col1_X + (col1Width - wH1) / 2f
        val h1_Y_vert = startY
        val h1_scaleY_vert = if (isStretchEnabled) targetCharHeight1 / h1_actualH else 1f

        val m1_X_vert = col1_X + (col1Width - wM1) / 2f
        val m1_Y_vert = if (isStretchEnabled) startY + targetCharHeight1 + gapY else startY + hourSizePx + gapY
        val m1_scaleY_vert = if (isStretchEnabled) targetCharHeight1 / m1_actualH else 1f

        val colon_X_vert = (h0_X_vert + h1_X_vert) / 2f
        val colon_Y_vert = startY
        
        val p = layoutProgress.coerceIn(0f, 1f)
        fun lerp(s: Float, e: Float): Float = s + (e - s) * p

        val h0_X = lerp(h0_X_horiz, h0_X_vert)
        val h0_Y = lerp(h0_Y_horiz, h0_Y_vert)
        val h0_sY = lerp(h0_scaleY_horiz, h0_scaleY_vert)

        val h1_X = lerp(h1_X_horiz, h1_X_vert)
        val h1_Y = lerp(h1_Y_horiz, h1_Y_vert)
        val h1_sY = lerp(h1_scaleY_horiz, h1_scaleY_vert)

        val m0_X = lerp(m0_X_horiz, m0_X_vert)
        val m0_Y = lerp(m0_Y_horiz, m0_Y_vert)
        val m0_sY = lerp(m0_scaleY_horiz, m0_scaleY_vert)

        val m1_X = lerp(m1_X_horiz, m1_X_vert)
        val m1_Y = lerp(m1_Y_horiz, m1_Y_vert)
        val m1_sY = lerp(m1_scaleY_horiz, m1_scaleY_vert)

        val colon_X = lerp(colon_X_horiz, colon_X_vert)
        val colon_Y = lerp(colon_Y_horiz, colon_Y_vert)
        val colon_sY = lerp(1f, 0f)
        val colon_sX = lerp(1f, 0f)
        
        fun appendPath(charStr: String, sizePx: Float, tX: Float, tY: Float, sX: Float, sY: Float, targetPath: Path) {
            if (sX <= 0f || sY <= 0f) return
            sharedTextPaint.textSize = sizePx
            sharedCharPath.rewind()
            sharedTextPaint.getTextPath(charStr, 0, 1, 0f, 0f, sharedCharPath)
            sharedCharPath.computeBounds(sharedCharBounds, true)
            if (sharedCharBounds.height() > 0) {
                val offsetY = tY - sharedCharBounds.top
                sharedTransformMatrix.reset()
                sharedTransformMatrix.postTranslate(tX, offsetY)
                sharedTransformMatrix.postScale(sX, sY, tX, tY)
                sharedCharPath.transform(sharedTransformMatrix)
                targetPath.addPath(sharedCharPath)
            }
        }

        appendPath(hStr[0].toString(), hourSizePx, h0_X, h0_Y, 1f, h0_sY, sharedPaths.hours)
        appendPath(hStr[1].toString(), hourSizePx, h1_X, h1_Y, 1f, h1_sY, sharedPaths.hours)
        if (colonIdx != -1) {
            appendPath(":", hourSizePx, colon_X, colon_Y, colon_sX, colon_sY, sharedPaths.colon)
        }
        appendPath(mStr[0].toString(), minSizePx, m0_X, m0_Y, 1f, m0_sY, sharedPaths.mins)
        appendPath(mStr[1].toString(), minSizePx, m1_X, m1_Y, 1f, m1_sY, sharedPaths.mins)

        return sharedPaths
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
            if (currentMaxPx > availableHeightPx) scaleRatio = availableHeightPx / currentMaxPx
            
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
