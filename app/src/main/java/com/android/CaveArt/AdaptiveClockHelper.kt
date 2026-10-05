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

class ClockPaths(
    val hours: Path = Path(),
    val colon: Path = Path(),
    val mins: Path = Path()
)

object AdaptiveClockHelper {

    private class ThreadScratchpad {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val transformMatrix = Matrix()
        val charPath = Path()
        val charBounds = RectF()
        val clockPaths = ClockPaths()
        val pts = FloatArray(2)
    }

    private val scratchpad = ThreadLocal.withInitial { ThreadScratchpad() }

    fun measureClockWidth(
        timeString: String,
        hourSizePx: Float,
        minSizePx: Float,
        typeface: Typeface,
        isVertical: Boolean
    ): Float {
        return measureClockWidth(timeString, hourSizePx, minSizePx, typeface, if (isVertical) 1f else 0f)
    }

    fun measureClockWidth(
        timeString: String,
        hourSizePx: Float,
        minSizePx: Float,
        typeface: Typeface,
        layoutProgress: Float
    ): Float {
        val scratch = scratchpad.get() ?: ThreadScratchpad()
        val paint = scratch.textPaint
        paint.typeface = typeface

        val colonIdx = timeString.indexOf(':')
        val hours = if (colonIdx != -1) timeString.substring(0, colonIdx) else "00"
        val mins = if (colonIdx != -1) timeString.substring(colonIdx + 1) else "00"
        val hStr = hours.padStart(2, '0')
        val mStr = mins.padStart(2, '0')

        var totalHoriz = 0f
        paint.textSize = hourSizePx
        for (c in hours) {
            val w = paint.measureText(c.toString())
            totalHoriz += w + w * 0.15f
        }
        if (colonIdx != -1) {
            val cw = paint.measureText(":")
            totalHoriz += cw + cw * 0.5f
        }
        paint.textSize = minSizePx
        for (c in mins) {
            val w = paint.measureText(c.toString())
            totalHoriz += w + w * 0.15f
        }

        paint.textSize = hourSizePx
        val wH0 = paint.measureText(hStr[0].toString())
        val wH1 = paint.measureText(hStr[1].toString())

        paint.textSize = minSizePx
        val wM0 = paint.measureText(mStr[0].toString())
        val wM1 = paint.measureText(mStr[1].toString())

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
            timeString = timeString,
            startX = startX,
            startY = startY,
            absoluteClockX = absoluteClockX,
            absoluteClockY = absoluteClockY,
            hourSizePx = hourSizePx,
            minSizePx = minSizePx,
            typeface = typeface,
            screenW = screenW,
            screenH = screenH,
            isStretchEnabled = isStretchEnabled,
            layoutProgress = if (isVertical) 1f else 0f,
            collisionMap = collisionMap,
            density = density,
            strokeWidth = strokeWidth,
            stretchProgress = stretchProgress
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
        val scratch = scratchpad.get() ?: ThreadScratchpad()
        val paths = scratch.clockPaths
        paths.hours.rewind()
        paths.colon.rewind()
        paths.mins.rewind()

        val paint = scratch.textPaint
        paint.typeface = typeface

        val colonIdx = timeString.indexOf(':')
        val hours = if (colonIdx != -1) timeString.substring(0, colonIdx) else "00"
        val mins = if (colonIdx != -1) timeString.substring(colonIdx + 1) else "00"
        val hStr = hours.padStart(2, '0')
        val mStr = mins.padStart(2, '0')

        fun getDropY(cx: Float, cw: Float, defaultH: Float): Float {
            val normalY = startY + defaultH
            if (!isStretchEnabled || collisionMap == null || collisionMap.isEmpty() || screenW <= 0f) {
                return normalY
            }

            val absoluteX = cx + absoluteClockX
            val idxLeft = ((absoluteX / screenW) * 100f).toInt().coerceIn(0, 99)
            val idxRight = (((absoluteX + cw) / screenW) * 100f).toInt().coerceIn(0, 99)

            var minHitY = Float.MAX_VALUE
            for (i in idxLeft..idxRight) {
                if (collisionMap[i] < minHitY) {
                    minHitY = collisionMap[i]
                }
            }

            val padding = (14f * density) + ((strokeWidth * density) / 2f)
            val exactSurfaceY = minHitY - absoluteClockY - padding
            val maxStretchLimit = startY + (defaultH * 2.8f)
            val minCharHeight = startY + (defaultH * 0.4f)

            var finalY = exactSurfaceY
            if (minHitY >= screenH * 0.98f) {
                finalY = maxStretchLimit
            }

            val fullyStretchedY = finalY.coerceIn(minCharHeight, maxStretchLimit)
            return normalY + (fullyStretchedY - normalY) * stretchProgress
        }

        paint.textSize = hourSizePx
        val wH0 = paint.measureText(hStr[0].toString())
        val wH1 = paint.measureText(hStr[1].toString())
        val wColon = if (colonIdx != -1) paint.measureText(":") else 0f

        paint.textSize = minSizePx
        val wM0 = paint.measureText(mStr[0].toString())
        val wM1 = paint.measureText(mStr[1].toString())

        fun getCharActualHeight(charStr: String, sizePx: Float): Float {
            paint.textSize = sizePx
            scratch.charPath.rewind()
            paint.getTextPath(charStr, 0, 1, 0f, 0f, scratch.charPath)
            scratch.charPath.computeBounds(scratch.charBounds, true)
            return if (scratch.charBounds.height() > 0) scratch.charBounds.height() else sizePx
        }

        val h0ActualH = getCharActualHeight(hStr[0].toString(), hourSizePx)
        val h1ActualH = getCharActualHeight(hStr[1].toString(), hourSizePx)
        val m0ActualH = getCharActualHeight(mStr[0].toString(), minSizePx)
        val m1ActualH = getCharActualHeight(mStr[1].toString(), minSizePx)

        var curX = startX
        val h0XHoriz = curX
        val h0YHoriz = startY
        curX += wH0 + wH0 * 0.15f

        val h1XHoriz = curX
        val h1YHoriz = startY
        curX += wH1 + (if (colonIdx != -1) wColon * 0.5f else wH1 * 0.15f)

        val colonXHoriz = curX
        val colonYHoriz = startY
        if (colonIdx != -1) {
            curX += wColon + wColon * 0.5f
        }

        val m0XHoriz = curX
        val m0YHoriz = startY
        curX += wM0 + wM0 * 0.15f

        val m1XHoriz = curX
        val m1YHoriz = startY

        val h0ScaleYHoriz = if (isStretchEnabled) {
            val targetH = getDropY(h0XHoriz, wH0, hourSizePx) - startY
            if (targetH > h0ActualH) targetH / h0ActualH else 1f
        } else 1f

        val h1ScaleYHoriz = if (isStretchEnabled) {
            val targetH = getDropY(h1XHoriz, wH1, hourSizePx) - startY
            if (targetH > h1ActualH) targetH / h1ActualH else 1f
        } else 1f

        val m0ScaleYHoriz = if (isStretchEnabled) {
            val targetH = getDropY(m0XHoriz, wM0, minSizePx) - startY
            if (targetH > m0ActualH) targetH / m0ActualH else 1f
        } else 1f

        val m1ScaleYHoriz = if (isStretchEnabled) {
            val targetH = getDropY(m1XHoriz, wM1, minSizePx) - startY
            if (targetH > m1ActualH) targetH / m1ActualH else 1f
        } else 1f

        val gapX = hourSizePx * 0.1f
        val gapY = hourSizePx * 0.05f
        val col0Width = max(wH0, wM0)
        val col1Width = max(wH1, wM1)
        val col0X = startX
        val col1X = startX + col0Width + gapX

        val defaultTotalHeight = hourSizePx + minSizePx + gapY
        val dropYCol0 = getDropY(col0X, col0Width, defaultTotalHeight)
        val dropYCol1 = getDropY(col1X, col1Width, defaultTotalHeight)

        val targetCharHeight0 = if (isStretchEnabled) {
            ((dropYCol0 - startY - gapY) / 2f).coerceAtLeast(hourSizePx * 0.4f)
        } else hourSizePx

        val targetCharHeight1 = if (isStretchEnabled) {
            ((dropYCol1 - startY - gapY) / 2f).coerceAtLeast(hourSizePx * 0.4f)
        } else hourSizePx

        val h0XVert = col0X + (col0Width - wH0) / 2f
        val h0YVert = startY
        val h0ScaleYVert = if (isStretchEnabled) targetCharHeight0 / h0ActualH else 1f

        val m0XVert = col0X + (col0Width - wM0) / 2f
        val m0YVert = if (isStretchEnabled) startY + targetCharHeight0 + gapY else startY + hourSizePx + gapY
        val m0ScaleYVert = if (isStretchEnabled) targetCharHeight0 / m0ActualH else 1f

        val h1XVert = col1X + (col1Width - wH1) / 2f
        val h1YVert = startY
        val h1ScaleYVert = if (isStretchEnabled) targetCharHeight1 / h1ActualH else 1f

        val m1XVert = col1X + (col1Width - wM1) / 2f
        val m1YVert = if (isStretchEnabled) startY + targetCharHeight1 + gapY else startY + hourSizePx + gapY
        val m1ScaleYVert = if (isStretchEnabled) targetCharHeight1 / m1ActualH else 1f

        val colonXVert = (h0XVert + h1XVert) / 2f
        val colonYVert = startY

        val p = layoutProgress.coerceIn(0f, 1f)
        fun lerp(s: Float, e: Float): Float = s + (e - s) * p

        val h0X = lerp(h0XHoriz, h0XVert)
        val h0Y = lerp(h0YHoriz, h0YVert)
        val h0SY = lerp(h0ScaleYHoriz, h0ScaleYVert)

        val h1X = lerp(h1XHoriz, h1XVert)
        val h1Y = lerp(h1YHoriz, h1YVert)
        val h1SY = lerp(h1ScaleYHoriz, h1ScaleYVert)

        val m0X = lerp(m0XHoriz, m0XVert)
        val m0Y = lerp(m0YHoriz, m0YVert)
        val m0SY = lerp(m0ScaleYHoriz, m0ScaleYVert)

        val m1X = lerp(m1XHoriz, m1XVert)
        val m1Y = lerp(m1YHoriz, m1YVert)
        val m1SY = lerp(m1ScaleYHoriz, m1ScaleYVert)

        val colonX = lerp(colonXHoriz, colonXVert)
        val colonY = lerp(colonYHoriz, colonYVert)
        val colonSY = lerp(1f, 0f)
        val colonSX = lerp(1f, 0f)

        fun appendPath(charStr: String, sizePx: Float, tX: Float, tY: Float, sX: Float, sY: Float, targetPath: Path) {
            if (sX <= 0f || sY <= 0f) return
            paint.textSize = sizePx
            scratch.charPath.rewind()
            paint.getTextPath(charStr, 0, 1, 0f, 0f, scratch.charPath)
            scratch.charPath.computeBounds(scratch.charBounds, true)
            if (scratch.charBounds.height() > 0) {
                val offsetY = tY - scratch.charBounds.top
                scratch.transformMatrix.reset()
                scratch.transformMatrix.postTranslate(tX, offsetY)
                scratch.transformMatrix.postScale(sX, sY, tX, tY)
                scratch.charPath.transform(scratch.transformMatrix)
                targetPath.addPath(scratch.charPath)
            }
        }

        appendPath(hStr[0].toString(), hourSizePx, h0X, h0Y, 1f, h0SY, paths.hours)
        appendPath(hStr[1].toString(), hourSizePx, h1X, h1Y, 1f, h1SY, paths.hours)
        if (colonIdx != -1) {
            appendPath(":", hourSizePx, colonX, colonY, colonSX, colonSY, paths.colon)
        }
        appendPath(mStr[0].toString(), minSizePx, m0X, m0Y, 1f, m0SY, paths.mins)
        appendPath(mStr[1].toString(), minSizePx, m1X, m1Y, 1f, m1SY, paths.mins)

        return paths
    }

    @JvmOverloads
    fun generateCollisionMap(
        mask: Bitmap,
        screenW: Float,
        screenH: Float,
        zoomFactor: Float = 1.10f
    ): String {
        if (mask.isRecycled || screenW <= 0f || screenH <= 0f) return ""
        val w = mask.width
        val h = mask.height
        if (w <= 0 || h <= 0) return ""

        val pixels = IntArray(w * h)
        mask.getPixels(pixels, 0, w, 0, 0, w, h)

        val fillScale = max(screenW / w.toFloat(), screenH / h.toFloat()) * zoomFactor
        val matrix = Matrix().apply {
            postTranslate(-w / 2f, -h / 2f)
            postScale(fillScale, fillScale)
            postTranslate(screenW / 2f, screenH / 2f)
        }
        val inverse = Matrix()
        if (!matrix.invert(inverse)) return ""

        val buckets = FloatArray(100) { screenH }
        val stepY = 4
        val scratch = scratchpad.get() ?: ThreadScratchpad()
        val pts = scratch.pts
        val maxH = screenH.toInt()

        for (i in 0 until 100) {
            val screenX = (i / 100f) * screenW + (screenW / 200f)
            var hitY = screenH

            for (sy in 0 until maxH step stepY) {
                pts[0] = screenX
                pts[1] = sy.toFloat()
                inverse.mapPoints(pts)

                val mx = pts[0].toInt()
                val my = pts[1].toInt()

                if (mx in 0 until w && my in 0 until h) {
                    val alpha = (pixels[my * w + mx] ushr 24) and 0xFF
                    if (alpha > 45) {
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
        mask: Bitmap,
        screenW: Float,
        screenH: Float,
        currentHourSize: Float,
        currentMinSize: Float,
        density: Float,
        isMagicShapeEnabled: Boolean,
        magicScale: Float,
        isCentered: Boolean
    ): AutoFitResult? {
        if (mask.isRecycled || mask.width <= 0 || mask.height <= 0) return null
        val rawBounds = Geometric.getTightSubjectBounds(mask) ?: return null

        val fillScale = max(screenW / mask.width.toFloat(), screenH / mask.height.toFloat()) * 1.10f
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

        return if (availableHeightPx > 40f * density) {
            val currentMaxDp = max(currentHourSize, currentMinSize)
            val currentMaxPx = currentMaxDp * density
            var scaleRatio = 1.0f
            if (currentMaxPx > availableHeightPx) scaleRatio = availableHeightPx / currentMaxPx

            val newHourSize = (currentHourSize * scaleRatio).coerceIn(30f, 200f)
            val newMinSize = (currentMinSize * scaleRatio).coerceIn(30f, 200f)
            val newMaxPx = max(newHourSize, newMinSize) * density

            val newYPx = (topSpacePx / 2f) - (newMaxPx / 2f)
            val newYDp = (newYPx / density).coerceAtLeast(15f)

            AutoFitResult(newHourSize, newMinSize, newYDp)
        } else {
            AutoFitResult(50f, 35f, 25f)
        }
    }
}
