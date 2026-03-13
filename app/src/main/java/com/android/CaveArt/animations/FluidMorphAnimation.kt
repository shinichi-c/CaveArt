package com.android.CaveArt.animations

import android.graphics.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.CaveArt.LiveWallpaperConfig
import com.android.CaveArt.UnifiedGeometry
import kotlin.math.*

class FluidMorphAnimation : WallpaperAnimation {

    override fun needsSegmentationMask(): Boolean = true
    
    override fun supportsCenter(): Boolean = false
    override fun supportsScale(): Boolean = false 
    override fun supports3DPop(): Boolean = true

    override fun getCustomSettings(): List<AnimSetting> = listOf(
        AnimSetting.Slider("effect_scale", "Effect Size", 0.5f, 1.5f, 1.0f),
        AnimSetting.Slider("morph_speed", "Shape Morph Speed", 0.1f, 3.0f, 0.8f),
        AnimSetting.Slider("rot_speed", "Rotation Speed", -40.0f, 40.0f, 15.0f),
        AnimSetting.Slider("wander_radius", "Wander Distance", 0f, 100f, 40f),
        AnimSetting.Slider("layer_count", "Background Layers", 1f, 5f, 3f),
        AnimSetting.Slider("layer_spread", "Layer Spacing", 0.05f, 0.4f, 0.15f),
        AnimSetting.Slider("bounce_delay", "Bounce Delay", 0.0f, 1.0f, 0.2f)
    )

    @Composable
    override fun CustomUI(
        params: Map<String, Float>,
        onUpdateParam: (String, Float) -> Unit
    ) {
        val settings = getCustomSettings().filterIsInstance<AnimSetting.Slider>()
        if (settings.isEmpty()) return

        var selectedIndex by remember { mutableIntStateOf(0) }
        var expanded by remember { mutableStateOf(false) }

        val activeSetting = settings[selectedIndex]
        val currentValue = params[activeSetting.id] ?: activeSetting.defaultValue

        Text("Customize Effect", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(16.dp))

        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { expanded = true }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = activeSetting.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Parameter",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            settings.forEachIndexed { index, setting ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = setting.title,
                                            color = if (index == selectedIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        selectedIndex = index
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable {
                                settings.forEach { setting ->
                                    onUpdateParam(setting.id, setting.defaultValue)
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset All",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Text(
                    text = String.format("%.2f", currentValue),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))

            Slider(
                value = currentValue,
                onValueChange = { onUpdateParam(activeSetting.id, it) },
                valueRange = activeSetting.minValue..activeSetting.maxValue
            )
        }
    }

    override fun getPreviewScaleTarget(): Float = 1.06f
    override fun getPreviewDuration(): Int = 4000

    private var timeSeconds = 0f
    private var currentProgress = 0f
    private var targetProgress = 0f
    private var velocity = 0f

    private val SPRING_TENSION = 200f
    private val SPRING_FRICTION = 20f

    private val _bodyMatrix = Matrix()
    private val _blobPath = Path()
    private val _pathMatrix = Matrix()
    private val _shapeRect = RectF()
    
    private val _fadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private var _lastFadeStart = -1f
    private var _lastFadeEnd = -1f

    override fun update(deltaTime: Float) {
        timeSeconds += deltaTime
        val steps = 4
        val dt = deltaTime / steps
        for (i in 0 until steps) {
            val displacement = currentProgress - targetProgress
            val force = -SPRING_TENSION * displacement - SPRING_FRICTION * velocity
            velocity += force * dt
            currentProgress += velocity * dt
        }
        if (abs(currentProgress - targetProgress) < 0.001f && abs(velocity) < 0.001f) {
            currentProgress = targetProgress
            velocity = 0f
        }
    }

    override fun onUnlock() { targetProgress = 1f }
    override fun onLock() { targetProgress = 0f }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun getShapeRadius(angle: Float, r: Float, type: Int): Float {
        return when (type % 4) {
            0 -> r + sin(angle * 3f) * (r * 0.06f) + cos(angle * 2f) * (r * 0.04f) 
            1 -> r + sin(angle * 12f) * (r * 0.08f) 
            2 -> r + cos(angle * 4f) * (r * 0.12f)  
            3 -> r + cos(angle * 6f) * (r * 0.08f)  
            else -> r
        }
    }

    private fun buildMorphingPath(path: Path, r: Float, time: Float, morphSpeed: Float) {
        path.rewind()
        val pts = 120
        
        val cycle = abs(time * morphSpeed) % 4f
        val typeA = cycle.toInt()
        val typeB = (typeA + 1) % 4
        val fraction = cycle - typeA
        
        val blend = smoothstep(0.3f, 0.7f, fraction)
        
        for (i in 0..pts) {
            val a = (i.toFloat() / pts) * (PI.toFloat() * 2f)
            
            val rA = getShapeRadius(a, r, typeA)
            val rB = getShapeRadius(a, r, typeB)
            
            val finalR = rA + (rB - rA) * blend
            
            val x = finalR * cos(a)
            val y = finalR * sin(a)
            
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    override fun draw(
        canvas: Canvas,
        originalBitmap: Bitmap,
        maskBitmap: Bitmap?,
        geo: UnifiedGeometry,
        config: LiveWallpaperConfig,
        paint: Paint,
        maskXferPaint: Paint,
        clipPath: Path,
        screenShapeRect: RectF
    ) {
        val customScale = config.animParams["effect_scale"] ?: 1.0f 
        val morphSpeed = config.animParams["morph_speed"] ?: 0.8f
        val rotSpeed = config.animParams["rot_speed"] ?: 15.0f
        val wanderRadius = config.animParams["wander_radius"] ?: 40f
        val numLayers = (config.animParams["layer_count"] ?: 3f).toInt()
        val layerSpread = config.animParams["layer_spread"] ?: 0.15f
        val bounceDelay = config.animParams["bounce_delay"] ?: 0.2f

        val screenW = canvas.width.toFloat()
        val screenH = canvas.height.toFloat()

        val safeProgress = currentProgress.coerceIn(0f, 1f)
        val unlockScale = lerp(0.7f, 1.0f, safeProgress) 
        
        val currentImgScale = geo.baseScale * customScale * unlockScale

        val anchorX = geo.subjectCenterX
        val anchorY = geo.subjectCenterY

        _bodyMatrix.reset()
        _bodyMatrix.postTranslate(-anchorX, -anchorY)
        _bodyMatrix.postScale(currentImgScale, currentImgScale)
        _bodyMatrix.postTranslate(screenW / 2f, screenH / 2f)

        _shapeRect.set(geo.shapeBoundsRel)
        _bodyMatrix.mapRect(_shapeRect)
        
        val activeWanderRadius = lerp(wanderRadius * 0.3f, wanderRadius, safeProgress)
        val wanderX = sin(timeSeconds * 0.6f) * cos(timeSeconds * 0.4f) * activeWanderRadius
        val wanderY = sin(timeSeconds * 0.5f) * cos(timeSeconds * 0.7f) * activeWanderRadius
        val vShift = if (config.is3DPopEnabled) _shapeRect.height() * 0.15f else 0f
        
        val dynamicCX = _shapeRect.centerX() + wanderX
        val dynamicCY = _shapeRect.centerY() + vShift + wanderY
        val baseRadius = (min(_shapeRect.width(), _shapeRect.height()) / 2f) * 0.85f
        
        val currentRotation = timeSeconds * rotSpeed

        val hsv = FloatArray(3)
        Color.colorToHSV(config.backgroundColor, hsv)
        
        val bgHsv = floatArrayOf(hsv[0], hsv[1] * 0.3f, min(1f, hsv[2] * 1.3f))
        canvas.drawColor(Color.HSVToColor(bgHsv))

        val baseAlpha = 255
        paint.alpha = baseAlpha
        
        for (i in numLayers downTo 1) {
            val scale = 1f + (i * layerSpread)
            val phaseOffset = i * bounceDelay 

            val fraction = 1f - (i.toFloat() / (numLayers + 1f))
            val layerHsv = floatArrayOf(
                hsv[0],
                lerp(bgHsv[1], hsv[1], fraction),
                lerp(bgHsv[2], hsv[2], fraction)
            )
            paint.color = Color.HSVToColor(layerHsv)
            paint.alpha = baseAlpha 

            buildMorphingPath(_blobPath, baseRadius * scale, timeSeconds - phaseOffset, morphSpeed)
            
            _pathMatrix.reset()
            _pathMatrix.postRotate(currentRotation) 
            _pathMatrix.postTranslate(dynamicCX, dynamicCY)
            _blobPath.transform(_pathMatrix)
            
            canvas.drawPath(_blobPath, paint)
        }

        paint.color = config.backgroundColor
        paint.alpha = baseAlpha
        
        buildMorphingPath(_blobPath, baseRadius, timeSeconds, morphSpeed)
        
        _pathMatrix.reset()
        _pathMatrix.postRotate(currentRotation)
        _pathMatrix.postTranslate(dynamicCX, dynamicCY)
        _blobPath.transform(_pathMatrix)

        canvas.drawPath(_blobPath, paint)

        canvas.save()
        canvas.clipPath(_blobPath)
        paint.alpha = 255
        canvas.drawBitmap(originalBitmap, _bodyMatrix, paint)
        canvas.restore()

        val popAlpha = 255 
        
        if (config.is3DPopEnabled && maskBitmap != null) {
            val popMatrix = Matrix(_bodyMatrix)
            val layerId = canvas.saveLayer(0f, 0f, screenW, screenH, null)
            
            paint.alpha = popAlpha
            canvas.drawBitmap(maskBitmap, popMatrix, paint)
            canvas.drawBitmap(originalBitmap, popMatrix, maskXferPaint)
            
            val fadeStart = _shapeRect.bottom - _shapeRect.height() * 0.35f
            val fadeEnd = _shapeRect.bottom - _shapeRect.height() * 0.05f
            
            if (fadeStart != _lastFadeStart || fadeEnd != _lastFadeEnd) {
                _fadePaint.shader = LinearGradient(
                    0f, fadeStart, 0f, fadeEnd, 
                    Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP
                )
                _lastFadeStart = fadeStart
                _lastFadeEnd = fadeEnd
            }
            canvas.drawRect(0f, 0f, screenW, screenH, _fadePaint)
            
            paint.alpha = 255 
            canvas.restoreToCount(layerId)
        }
    }

    private fun lerp(start: Float, end: Float, t: Float): Float = start + (end - start) * t
}
