package com.android.CaveArt

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import kotlin.math.min

enum class MagicShape(val label: String) {
    CIRCLE("Circle"),
    SQUIRCLE("Square"),
    ARCH("Arch"),
    PILL("Pill"),
    COOKIE_4("4-sided cookie"),
    COOKIE_9("9-sided cookie");

    companion object {
        fun fromName(name: String): MagicShape {
            return try {
                when (name) {
                    "SQUARE" -> SQUIRCLE
                    "CLOVER", "COOKIE_6" -> COOKIE_4
                    "BADGE", "SUNNY" -> COOKIE_9
                    else -> valueOf(name)
                }
            } catch (e: Exception) {
                CIRCLE
            }
        }
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object PixelShapeMorpher {

    private val morphMatrix = Matrix()
    private val pathBounds = RectF()

   
    fun getPolygon(shape: MagicShape): RoundedPolygon {
        return when (shape) {
            MagicShape.CIRCLE -> MaterialShapes.Circle
            MagicShape.SQUIRCLE -> MaterialShapes.Square
            MagicShape.ARCH -> MaterialShapes.Arch
            MagicShape.PILL -> MaterialShapes.Pill
            MagicShape.COOKIE_4 -> MaterialShapes.Cookie4Sided
            MagicShape.COOKIE_9 -> MaterialShapes.Cookie9Sided
        }
    }
    
    fun buildMorphedPath(
        fromShape: MagicShape,
        toShape: MagicShape,
        progress: Float,
        bounds: RectF,
        targetPath: Path
    ) {
        val polyStart = getPolygon(fromShape)
        val polyEnd = getPolygon(toShape)
        val morph = Morph(polyStart, polyEnd)

        targetPath.rewind()
        morph.toPath(progress.coerceIn(0f, 1f), targetPath)
        
        targetPath.computeBounds(pathBounds, true)
        val pW = pathBounds.width().coerceAtLeast(1e-3f)
        val pH = pathBounds.height().coerceAtLeast(1e-3f)
        
        val scale = min(bounds.width() / pW, bounds.height() / pH)

        morphMatrix.reset()
        morphMatrix.postTranslate(-pathBounds.centerX(), -pathBounds.centerY())
        morphMatrix.postScale(scale, scale)
        morphMatrix.postTranslate(bounds.centerX(), bounds.centerY())
        targetPath.transform(morphMatrix)
    }
}

object ShapePathProvider {
    fun updatePathForShape(path: Path, shape: MagicShape, bounds: RectF) {
        PixelShapeMorpher.buildMorphedPath(shape, shape, 1.0f, bounds, path)
    }

    fun getPathForShape(shape: MagicShape, bounds: RectF): Path {
        val path = Path()
        updatePathForShape(path, shape, bounds)
        return path
    }
}
