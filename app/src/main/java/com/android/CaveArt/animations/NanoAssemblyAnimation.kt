package com.android.CaveArt.animations

import android.graphics.*
import com.android.CaveArt.UnifiedGeometry
import kotlin.math.*

class NanoAssemblyAnimation : WallpaperAnimation {

    companion object {
        private val AGSL_SRC = """
            uniform shader image;
            uniform float progress;
            uniform float2 resolution;
            uniform float time;
            uniform float rotationAngle;
            
            float sdRoundedRect(float2 p, float2 b, float r) {
                float2 q = abs(p) - b + r;
                return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
            }

            half4 main(float2 fragCoord) {
                float intensity = 1.0 - progress;
                float2 center = resolution * 0.5;
                float2 p = fragCoord - center;
                float rad = -rotationAngle; 
                float cosA = cos(rad), sinA = sin(rad);
                float2 rotatedP = float2(p.x * cosA - p.y * sinA, p.x * sinA + p.y * cosA);
                float currentScale = mix(1.0, 0.45, intensity);
                float cardW = resolution.x * currentScale;
                float cardH = (resolution.x * 1.77) * currentScale;
                float2 cardHalfSize = float2(cardW, cardH) * 0.5;
                float cornerRad = 60.0 * currentScale;
                
                float dist = sdRoundedRect(rotatedP, cardHalfSize, cornerRad);

                float2 auraUV = center + (p * 0.25); 
                half4 aura = image.eval(auraUV);
               
                aura.rgb *= 0.18 * intensity;

                float2 parallax = float2(sin(time * 0.4), cos(time * 0.4)) * 12.0 * intensity;
                half4 color = image.eval(fragCoord + parallax);

                float cardAlpha = smoothstep(1.0, -1.0, dist);
                
                float border = smoothstep(2.5, 0.0, abs(dist)) * intensity;

                half4 finalOutput = mix(aura, color, cardAlpha);
                finalOutput.rgb += half3(0.4) * border; 

                float sweepPos = fract(time * 0.12 + progress * 0.4) * 4.0 - 2.0;
                float beamDist = (rotatedP.x + rotatedP.y) / length(resolution);
                float beam = smoothstep(sweepPos - 0.04, sweepPos, beamDist) * 
                             smoothstep(sweepPos + 0.04, sweepPos, beamDist);
                
                finalOutput.rgb += half3(0.1) * beam * intensity;
               
                float luma = dot(color.rgb, half3(0.299, 0.587, 0.114));
                float pulse = (sin(time * 1.8) * 0.5 + 0.5) * intensity;
                finalOutput.rgb += color.rgb * smoothstep(0.5, 0.9, luma) * pulse * 0.15 * cardAlpha;

                return finalOutput;
            }
        """.trimIndent()
    }

    private var currentProgress = 0f    
    private var velocity = 0f           
    private var targetProgress = 0f     
    private var timeSeconds = 0f
    private var currentRotation = 0f
    
    private val SPRING_TENSION = 340f  
    private val SPRING_FRICTION = 32f  
    private val LEVITATION_SPEED = 0.7f
    private val LEVITATION_AMP = 8f

    private val _bodyMatrix = Matrix()
    private val _shapeMatrix = Matrix()
    private val _popMatrix = Matrix()
    private var runtimeShader: RuntimeShader? = null

    override fun update(deltaTime: Float) {
        timeSeconds += deltaTime
        val displacement = currentProgress - targetProgress
        val force = -SPRING_TENSION * displacement - SPRING_FRICTION * velocity
        velocity += force * deltaTime
        currentProgress += velocity * deltaTime

        if (abs(displacement) < 0.0001f && abs(velocity) < 0.0001f) {
            currentProgress = targetProgress
            velocity = 0f
        }
    }

    override fun onUnlock() { targetProgress = 1f }
    override fun onLock() { targetProgress = 0f }

    override fun calculateState(geo: UnifiedGeometry, screenW: Float, screenH: Float, imgW: Int, imgH: Int, is3DPopEnabled: Boolean): AnimationState {
        val fillScale = max(screenW / imgW, screenH / imgH)
        
        val currentImgScale = lerp(fillScale * 0.45f, geo.baseScale, currentProgress.coerceIn(0f, 1f))

        val anchorX = if (currentProgress > 0.05f) geo.subjectCenterX else imgW / 2f
        val anchorY = if (currentProgress > 0.05f) geo.subjectCenterY else imgH / 2f
        
        val finalAnchorX = lerp(imgW / 2f, anchorX, currentProgress.coerceIn(0f, 1f))
        val finalAnchorY = lerp(imgH / 2f, anchorY, currentProgress.coerceIn(0f, 1f))

        val idleY = sin(timeSeconds * LEVITATION_SPEED) * LEVITATION_AMP * (1f - currentProgress.coerceIn(0f, 1f))
        currentRotation = (1f - currentProgress) * 5f 

        _bodyMatrix.reset()
        _bodyMatrix.postTranslate(-finalAnchorX, -finalAnchorY)
        _bodyMatrix.postScale(currentImgScale, currentImgScale)
        _bodyMatrix.postRotate(currentRotation)
        _bodyMatrix.postTranslate(screenW / 2f, (screenH / 2f) + idleY)

        _popMatrix.set(_bodyMatrix)
        if (is3DPopEnabled) {
           
            val popLag = (1f - currentProgress.coerceIn(0f, 1.2f)) * 180f 
            _popMatrix.postTranslate(0f, -popLag)
        }

        _shapeMatrix.set(_bodyMatrix)
        val expansion = lerp(6.0f, 1.0f, currentProgress.coerceIn(0f, 1f))
        _shapeMatrix.postScale(expansion, expansion, screenW / 2f, (screenH / 2f) + idleY)

        return AnimationState(
            bodyMatrix = _bodyMatrix,
            shapeMatrix = _shapeMatrix,
            popMatrix = _popMatrix,
            popAlpha = (currentProgress * 255).toInt().coerceIn(0, 255),
            vShift = if (is3DPopEnabled) geo.shapeBoundsRel.height() * geo.baseScale * 0.1f * currentProgress else 0f,
            progress = currentProgress.coerceIn(0f, 1f),
            time = timeSeconds
        )
    }

    override fun applyShader(paint: Paint, bitmap: Bitmap, state: AnimationState, canvasWidth: Float, canvasHeight: Float): Boolean {
        if (runtimeShader == null) runtimeShader = RuntimeShader(AGSL_SRC)
        
        val bShader = BitmapShader(bitmap, Shader.TileMode.DECAL, Shader.TileMode.DECAL)
        bShader.setLocalMatrix(state.bodyMatrix)
        
        runtimeShader?.let { shader ->
            shader.setInputShader("image", bShader)
            shader.setFloatUniform("resolution", canvasWidth, canvasHeight)
            shader.setFloatUniform("progress", state.progress)
            shader.setFloatUniform("time", state.time)
            shader.setFloatUniform("rotationAngle", (currentRotation * PI / 180f).toFloat())
            paint.shader = shader
            return true
        }
        return false
    }

    private fun lerp(start: Float, end: Float, t: Float): Float = start + (end - start) * t
}