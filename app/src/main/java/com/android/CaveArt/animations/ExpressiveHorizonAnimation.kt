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

class ExpressiveHorizonAnimation : WallpaperAnimation {

    override fun needsSegmentationMask(): Boolean = true
    
    override fun supportsCenter(): Boolean = false
    override fun supportsScale(): Boolean = false 
    override fun supports3DPop(): Boolean = true

    override fun getCustomSettings(): List<AnimSetting> = listOf(
        AnimSetting.Slider("glow_intensity", "Material You Bloom", 0.0f, 1.5f, 0.8f),
        AnimSetting.Slider("parallax_strength", "Parallax Depth", 0.0f, 1.5f, 0.7f),
        AnimSetting.Slider("bloom_scale", "Wake Dilation Scale", 1.0f, 1.3f, 1.08f)
    )

    companion object {
        
        private val AGSL_SRC = """
            uniform shader image;
            uniform float2 resolution;
            uniform float time;
            uniform float progress;
            uniform float3 accentColor;
            uniform float glowIntensity;

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / resolution;
                
                // Fetch base background pixel
                half4 base = image.eval(fragCoord);
                
                // organic radial glow centered near the focal vertical axis (0.55)
                float2 glowCenter = float2(0.5, 0.55);
                float dist = distance(uv, glowCenter);
                
                // Gentle breathing pulsation for a living universe aesthetic
                float pulse = sin(time * 0.7) * 0.04 + 0.96;
                float glow = smoothstep(0.85, 0.15, dist) * glowIntensity * pulse * progress;
                
                // Dynamically blend system Dynamic Palette color (Material You) behind the subject
                half3 dynamicBloom = accentColor * glow * 0.42;
                
                // Volumetric horizon light sweep (ultra-subtle slow diagonal waves)
                float sweep = sin((uv.x - uv.y) * 2.0 + time * 0.3) * 0.02 * progress;
                
                base.rgb += dynamicBloom;
                base.rgb += half3(sweep);
                
                // Smooth hardware lens-wake exposure adjustment (Pixel lockscreen wake-up)
                base.rgb *= mix(0.48, 1.0, progress);
                
                return base;
            }
        """.trimIndent()
    }

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

        Text("Expressive Engine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
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

    override fun getPreviewScaleTarget(): Float = 1.0f
    override fun getPreviewDuration(): Int = 4000

    private var timeSeconds = 0f
    private var currentProgress = 0f
    private var targetProgress = 0f
    private var velocity = 0f
    private val SPRING_TENSION = 110f
    private val SPRING_FRICTION = 21f
    private val _bgMatrix = Matrix()
    private val _fgMatrix = Matrix()
    
    private val _shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
    }

    private var _runtimeShader: RuntimeShader? = null
    private val _bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)

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
        val screenW = canvas.width.toFloat()
        val screenH = canvas.height.toFloat()

        val safeProgress = currentProgress.coerceIn(0f, 1f)

        val glowIntensity = config.animParams["glow_intensity"] ?: 0.8f
        val parallaxStrength = config.animParams["parallax_strength"] ?: 0.7f
        val bloomScale = config.animParams["bloom_scale"] ?: 1.08f
        
        val redFactor = Color.red(config.backgroundColor) / 255f
        val greenFactor = Color.green(config.backgroundColor) / 255f
        val blueFactor = Color.blue(config.backgroundColor) / 255f

        val anchorX = geo.subjectCenterX
        val anchorY = geo.subjectCenterY
        val currentScale = lerp(geo.baseScale * config.scale, geo.baseScale * config.scale * bloomScale, safeProgress)
        
        val floatX = sin(timeSeconds * 0.35f) * 20f * parallaxStrength
        val floatY = cos(timeSeconds * 0.28f) * 12f * parallaxStrength
        
        _bgMatrix.reset()
        _bgMatrix.postTranslate(-anchorX, -anchorY)
        _bgMatrix.postScale(currentScale, currentScale)
        _bgMatrix.postTranslate(screenW / 2f + floatX, screenH / 2f + floatY)
        
        val fgFloatX = floatX * 1.35f
        val fgFloatY = floatY * 1.35f
        
        _fgMatrix.reset()
        _fgMatrix.postTranslate(-anchorX, -anchorY)
        _fgMatrix.postScale(currentScale, currentScale)
        _fgMatrix.postTranslate(screenW / 2f + fgFloatX, screenH / 2f + fgFloatY)
        
        canvas.drawColor(config.backgroundColor)
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (_runtimeShader == null) _runtimeShader = RuntimeShader(AGSL_SRC)
            
            val bitmapShader = BitmapShader(originalBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            bitmapShader.setLocalMatrix(_bgMatrix)
            
            _runtimeShader?.let { shader ->
                shader.setInputShader("image", bitmapShader)
                shader.setFloatUniform("resolution", screenW, screenH)
                shader.setFloatUniform("time", timeSeconds)
                shader.setFloatUniform("progress", safeProgress)
                shader.setFloatUniform("accentColor", redFactor, greenFactor, blueFactor)
                shader.setFloatUniform("glowIntensity", glowIntensity)
                _bgPaint.shader = shader
            }
            canvas.drawRect(0f, 0f, screenW, screenH, _bgPaint)
        } else {
            
            canvas.drawBitmap(originalBitmap, _bgMatrix, paint)
        }
        
        if (config.is3DPopEnabled && maskBitmap != null) {
        	
            val shadowMatrix = Matrix(_fgMatrix)
            val shadowOffset = lerp(4f, 32f, safeProgress)
            shadowMatrix.postTranslate(shadowOffset, shadowOffset * 1.25f)
            
            _shadowPaint.alpha = (100 * safeProgress).toInt().coerceIn(0, 255)
            canvas.drawBitmap(maskBitmap, shadowMatrix, _shadowPaint)
            
            paint.alpha = (255 * lerp(0.6f, 1.0f, safeProgress)).toInt().coerceIn(0, 255)
            val layerId = canvas.saveLayer(0f, 0f, screenW, screenH, null)
            canvas.drawBitmap(maskBitmap, _fgMatrix, paint)
            canvas.drawBitmap(originalBitmap, _fgMatrix, maskXferPaint)
            canvas.restoreToCount(layerId)
            
            paint.alpha = 255
        } else if (!config.is3DPopEnabled) {
            canvas.drawBitmap(originalBitmap, _fgMatrix, paint)
        }
    }

    private fun lerp(start: Float, end: Float, t: Float): Float = start + (end - start) * t
}
