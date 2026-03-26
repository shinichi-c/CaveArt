package com.android.CaveArt

import android.content.Context
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Choreographer
import android.view.SurfaceHolder
import com.google.android.filament.*
import com.google.android.filament.Engine as FilamentEngineCore
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.gltfio.Gltfio
import java.io.File
import java.nio.ByteBuffer

class CaveArtFilamentService : WallpaperService() {

    companion object {
        init {
            runCatching { Filament.init() }
            runCatching { Gltfio.init() }
            runCatching { System.loadLibrary("gltfio-jni") }
        }
    }

    override fun onCreateEngine(): WallpaperService.Engine = FilamentEngine()

    inner class FilamentEngine : WallpaperService.Engine(), Choreographer.FrameCallback {
        private var filamentEngine: FilamentEngineCore? = null
        private var renderer: Renderer? = null
        private var scene: Scene? = null
        private var camera: Camera? = null
        private var filamentView: View? = null
        private var swapChain: SwapChain? = null
        
        private var assetLoader: AssetLoader? = null
        private var resourceLoader: ResourceLoader? = null
        private var filamentAsset: FilamentAsset? = null
        private var light: Int = 0

        private val choreographer = Choreographer.getInstance()
        private var isVisibleState = false
        private var rotationAngle = 0f

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            
            val engine = FilamentEngineCore.create().also { filamentEngine = it }
            
            renderer = engine.createRenderer()
            scene = engine.createScene()
            camera = engine.createCamera(engine.entityManager.create())
            filamentView = engine.createView()

            filamentView?.scene = scene
            filamentView?.camera = camera

            val skybox = Skybox.Builder().color(0.1f, 0.12f, 0.15f, 1.0f).build(engine)
            scene?.skybox = skybox

            light = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(1.0f, 1.0f, 0.95f)
                .intensity(50000.0f)
                .direction(-1.0f, -1.0f, -1.0f)
                .castShadows(true)
                .build(engine, light)
            scene?.addEntity(light)

            assetLoader = AssetLoader(engine, UbershaderProvider(engine), EntityManager.get())
            resourceLoader = ResourceLoader(engine)

            load3DModel(applicationContext)
        }

        private fun load3DModel(context: Context) {
            try {
                
                val customFile = File(context.filesDir, "custom_model.glb")
                if (!customFile.exists()) {
                    Log.w("CaveArt3D", "No custom model found. Waiting for user to import one.")
                    return
                }

                val bytes = customFile.readBytes()
                val buffer = ByteBuffer.allocateDirect(bytes.size)
                buffer.put(bytes)
                buffer.rewind()

                filamentAsset = assetLoader?.createAsset(buffer)
                filamentAsset?.let { asset ->
                    resourceLoader?.loadResources(asset)
                    asset.releaseSourceData() 
                    scene?.addEntities(asset.entities) 
                }
            } catch (e: Exception) {
                Log.e("CaveArt3D", "Failed to load 3D model.", e)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            val engine = filamentEngine ?: return
            
            swapChain?.let { engine.destroySwapChain(it) }
            swapChain = null
            
            if (holder?.surface != null && holder.surface.isValid) {
                swapChain = engine.createSwapChain(holder.surface)
                filamentView?.viewport = Viewport(0, 0, width, height)
                
                val aspect = width.toDouble() / height.toDouble()
                camera?.setProjection(45.0, aspect, 0.1, 100.0, Camera.Fov.VERTICAL)
                camera?.lookAt(0.0, 0.0, 5.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.isVisibleState = visible
            if (visible) {
                choreographer.postFrameCallback(this)
            } else {
                choreographer.removeFrameCallback(this)
            }
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (isVisibleState && filamentEngine != null && swapChain != null) {
                choreographer.postFrameCallback(this)

                filamentAsset?.let { asset ->
                    val tm = filamentEngine?.transformManager
                    val instance = tm?.getInstance(asset.root)
                    if (instance != null && instance != 0) {
                        val center = asset.boundingBox.center
                        val halfExtent = asset.boundingBox.halfExtent
                        val maxExtent = kotlin.math.max(halfExtent[0], kotlin.math.max(halfExtent[1], halfExtent[2]))
                        val scaleFactor = if (maxExtent > 0f) 2.0f / maxExtent else 1.0f

                        val transform = FloatArray(16)
                        android.opengl.Matrix.setIdentityM(transform, 0)
                        android.opengl.Matrix.rotateM(transform, 0, rotationAngle, 0f, 1f, 0f)
                        android.opengl.Matrix.scaleM(transform, 0, scaleFactor, scaleFactor, scaleFactor)
                        android.opengl.Matrix.translateM(transform, 0, -center[0], -center[1], -center[2])
                        
                        tm.setTransform(instance, transform)
                        rotationAngle += 0.3f 
                    }
                }

                if (renderer?.beginFrame(swapChain!!, frameTimeNanos) == true) {
                    renderer?.render(filamentView!!)
                    renderer?.endFrame()
                }
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            choreographer.removeFrameCallback(this)
            val engine = filamentEngine ?: return
            
            filamentAsset?.let { assetLoader?.destroyAsset(it) }
            assetLoader?.destroy()
            resourceLoader?.destroy()
            engine.destroyEntity(light)
            
            swapChain?.let { engine.destroySwapChain(it) }
            renderer?.let { engine.destroyRenderer(it) }
            filamentView?.let { engine.destroyView(it) }
            scene?.let { engine.destroyScene(it) }
            camera?.let { engine.destroyCameraComponent(it.entity) }
            
            engine.destroy()
            
            filamentEngine = null
            swapChain = null
            renderer = null
            filamentView = null
            scene = null
            camera = null
        }
    }
}
