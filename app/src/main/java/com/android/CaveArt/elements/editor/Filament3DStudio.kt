package com.android.CaveArt

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.Matrix as GlMatrix
import android.os.Build
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.filament.*
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Filament3DStudio(
    wallpaper: Wallpaper?,
    viewModel: WallpaperViewModel,
    onBack: () -> Unit,
    onApplyRequested: (Int) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val safeContext = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }
    }

    val modelFile = remember { File(safeContext.filesDir, "custom_model.glb") }
    var hasModel by remember { mutableStateOf(modelFile.exists()) }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        modelFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        hasModel = true
                        reloadTrigger++
                        Toast.makeText(context, "3D Model Loaded Successfully!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "3D Engine",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.padding(start = 6.dp)) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", modifier = Modifier.size(24.dp))
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxHeight(0.92f)
                            .aspectRatio(9f / 19.5f)
                            .shadow(28.dp, MaterialTheme.shapes.extraLarge, spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = Color.Black)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (hasModel) {
                                key(reloadTrigger) {
                                    AndroidView(
                                        modifier = Modifier.fillMaxSize(),
                                        factory = { ctx ->
                                            TextureView(ctx).apply {
                                                FilamentTextureController(this)
                                            }
                                        }
                                    )
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ViewInAr,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        text = "No 3D Model Loaded",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "Import a .GLB 3D model below to preview and set it as your wallpaper.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.7f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(24.dp, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)),
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        
                        FilledTonalButton(
                            onClick = { modelPickerLauncher.launch("*/*") },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (hasModel) "Replace .GLB Model" else "Import .GLB Model", fontWeight = FontWeight.Bold)
                        }
                        
                        Button(
                            onClick = {
                                if (hasModel) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    viewModel.setFilamentEnabled(true)
                                    onApplyRequested(WallpaperDestinations.FLAG_BOTH)
                                } else {
                                    Toast.makeText(context, "Please import a 3D model first!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = hasModel,
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Set 3D Wallpaper", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Embedded Filament 3D engine controller for real-time model preview
 */
class FilamentTextureController(val textureView: TextureView) : TextureView.SurfaceTextureListener, Choreographer.FrameCallback {
    private var filamentEngine: Engine? = null
    private var renderer: Renderer? = null
    private var scene: Scene? = null
    private var camera: Camera? = null
    private var view: View? = null
    private var swapChain: SwapChain? = null
    private var assetLoader: AssetLoader? = null
    private var resourceLoader: ResourceLoader? = null
    private var filamentAsset: FilamentAsset? = null
    private var light: Int = 0
    private val choreographer = Choreographer.getInstance()
    private var androidSurface: Surface? = null
    private var rotationAngle = 0f

    init {
        runCatching { Filament.init() }
        runCatching { Gltfio.init() }
        runCatching { System.loadLibrary("gltfio-jni") }
        textureView.surfaceTextureListener = this
        textureView.isOpaque = false
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        val engine = Engine.create().also { filamentEngine = it }
        renderer = engine.createRenderer()
        scene = engine.createScene()
        camera = engine.createCamera(engine.entityManager.create())
        view = engine.createView()
        view?.scene = scene
        view?.camera = camera

        scene?.skybox = Skybox.Builder().color(0.1f, 0.12f, 0.15f, 1.0f).build(engine)
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

        try {
            val safeContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                textureView.context.createDeviceProtectedStorageContext()
            } else {
                textureView.context
            }
            val customFile = File(safeContext.filesDir, "custom_model.glb")
            if (customFile.exists()) {
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
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        androidSurface = Surface(surface)
        swapChain = engine.createSwapChain(androidSurface!!)
        view?.viewport = Viewport(0, 0, width, height)
        camera?.setProjection(45.0, width.toDouble() / height.toDouble(), 0.1, 100.0, Camera.Fov.VERTICAL)
        camera?.lookAt(0.0, 0.0, 5.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        choreographer.postFrameCallback(this)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        view?.viewport = Viewport(0, 0, width, height)
        camera?.setProjection(45.0, width.toDouble() / height.toDouble(), 0.1, 100.0, Camera.Fov.VERTICAL)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        choreographer.removeFrameCallback(this)
        filamentEngine?.let { engine ->
            filamentAsset?.let { assetLoader?.destroyAsset(it) }
            assetLoader?.destroy()
            resourceLoader?.destroy()
            engine.destroyEntity(light)
            swapChain?.let { engine.destroySwapChain(it) }
            renderer?.let { engine.destroyRenderer(it) }
            view?.let { engine.destroyView(it) }
            scene?.let { engine.destroyScene(it) }
            camera?.let { engine.destroyCameraComponent(it.entity) }
            engine.destroy()
        }
        filamentEngine = null
        androidSurface?.release()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    override fun doFrame(frameTimeNanos: Long) {
        if (filamentEngine != null && swapChain != null) {
            choreographer.postFrameCallback(this)
            filamentAsset?.let { asset ->
                val tm = filamentEngine?.transformManager
                val instance = tm?.getInstance(asset.root)
                if (instance != null && instance != 0) {
                    val halfExtent = asset.boundingBox.halfExtent
                    val maxExtent = kotlin.math.max(halfExtent[0], kotlin.math.max(halfExtent[1], halfExtent[2]))
                    val scaleFactor = if (maxExtent > 0f) 2.0f / maxExtent else 1.0f
                    val transform = FloatArray(16)
                    GlMatrix.setIdentityM(transform, 0)
                    GlMatrix.rotateM(transform, 0, rotationAngle, 0f, 1f, 0f)
                    GlMatrix.scaleM(transform, 0, scaleFactor, scaleFactor, scaleFactor)
                    GlMatrix.translateM(transform, 0, -asset.boundingBox.center[0], -asset.boundingBox.center[1], -asset.boundingBox.center[2])
                    tm.setTransform(instance, transform)
                    rotationAngle += 0.3f
                }
            }
            if (renderer?.beginFrame(swapChain!!, frameTimeNanos) == true) {
                renderer?.render(view!!)
                renderer?.endFrame()
            }
        }
    }
}
