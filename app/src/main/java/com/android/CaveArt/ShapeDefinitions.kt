package com.android.CaveArt

import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

enum class MagicShape(val label: String) {
    CIRCLE("Circle"),
    SQUIRCLE("Square"),
    ARCH("Arch"),
    CLOVER("Clover"),
    BADGE("Badge")
}

object ShapePathProvider {
    fun getPathForShape(shape: MagicShape, bounds: RectF): Path {
        val path = Path()
        val width = bounds.width()
        val height = bounds.height()
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()

        when (shape) {
            MagicShape.CIRCLE -> {
                path.addOval(bounds, Path.Direction.CW)
            }
            
            MagicShape.SQUIRCLE -> {
                val radius = width * 0.22f
                path.addRoundRect(bounds, radius, radius, Path.Direction.CW)
            }
            
            MagicShape.ARCH -> {
                val cornerRadii = floatArrayOf(
                    width / 2f, width / 2f,
                    width / 2f, width / 2f,
                    0f, 0f,
                    0f, 0f
                )
                path.addRoundRect(bounds, cornerRadii, Path.Direction.CW)
            }
            
            MagicShape.CLOVER -> {
                val lobeRadius = width / 3.4f
                val offset = width / 6.5f
                
                path.addCircle(centerX - offset, centerY - offset, lobeRadius, Path.Direction.CW)
                path.addCircle(centerX + offset, centerY - offset, lobeRadius, Path.Direction.CW)
                path.addCircle(centerX - offset, centerY + offset, lobeRadius, Path.Direction.CW)
                path.addCircle(centerX + offset, centerY + offset, lobeRadius, Path.Direction.CW)
                
                path.addCircle(centerX, centerY, width / 5f, Path.Direction.CW)
            }
            
            MagicShape.BADGE -> {
                
                val points = 360
                val outerRadius = width / 2f
                val amplitude = width * 0.06f 
                val frequency = 8.0
                
                val startR = outerRadius - amplitude + amplitude * cos(0.0)
                path.moveTo(
                    (centerX + startR * cos(0.0)).toFloat(), 
                    (centerY + startR * sin(0.0)).toFloat()
                )
                
                for (i in 1..points) {
                    val angle = (2.0 * PI * i) / points
                    val r = (outerRadius - amplitude) + amplitude * cos(frequency * angle)
                    
                    val x = centerX + r * cos(angle)
                    val y = centerY + r * sin(angle)
                    path.lineTo(x.toFloat(), y.toFloat())
                }
                path.close()
            }
        }
        return path
    }
}
