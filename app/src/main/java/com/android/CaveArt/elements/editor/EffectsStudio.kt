package com.android.CaveArt

import android.graphics.Bitmap
import android.graphics.RectF
import android.view.TextureView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.CaveArt.animations.AnimationFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffectsStudio(
    wallpaper: Wallpaper,
    viewModel: WallpaperViewModel,
    onBack: () -> Unit,
    onApplyRequested: (Int) -> Unit
) {
    val context = LocalContext.current
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewOriginal by remember { mutableStateOf<Bitmap?>(null) }
    var previewMask by remember { mutableStateOf<Bitmap?>(null) }
    
    var extractedColors by remember { mutableStateOf(listOf<Int>()) }
    var showCustomizeSheet by remember { mutableStateOf(false) }
    var showApplyOptions by remember { mutableStateOf(false) }
    
    var isExtractingColors by remember { mutableStateOf(true) }
    
    LaunchedEffect(wallpaper) {
        isExtractingColors = true
        previewMask = viewModel.getMaskForClock(context, wallpaper)
        withContext(Dispatchers.IO) {
            try {
                val bitmap = if(wallpaper.uri != null) BitmapHelper.decodeSampledBitmapFromUri(context, wallpaper.uri, 112) else BitmapHelper.decodeSampledBitmapFromResource(context.resources, wallpaper.resourceId, 112)
                if (bitmap != null) {
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    var finalColors = com.materialkolor.score.Score.score(com.materialkolor.quantize.QuantizerCelebi.quantize(pixels, 128)).distinct().take(5)
                    if (finalColors.size < 5 && finalColors.isNotEmpty()) {
                        val hct = com.materialkolor.hct.Hct.fromInt(finalColors.first())
                        finalColors = (finalColors + listOf(com.materialkolor.hct.Hct.from(hct.hue + 60.0, hct.chroma, hct.tone).toInt(), com.materialkolor.hct.Hct.from(hct.hue - 60.0, hct.chroma, hct.tone).toInt(), com.materialkolor.hct.Hct.from(hct.hue + 180.0, hct.chroma, hct.tone).toInt(), com.materialkolor.hct.Hct.from(hct.hue, hct.chroma, 30.0).toInt(), com.materialkolor.hct.Hct.from(hct.hue, hct.chroma, 80.0).toInt())).distinct().take(5)
                    }
                    withContext(Dispatchers.Main) { 
                        extractedColors = finalColors
                        
                        if (finalColors.isNotEmpty()) {
                            viewModel.updateMagicConfig(viewModel.currentMagicShape, finalColors.first())
                        }
                    }
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    isExtractingColors = false
                }
            }
        }
    }

    LaunchedEffect(
        wallpaper, viewModel.isAnimationEnabled, viewModel.isMagicShapeEnabled,
        viewModel.isFilamentEnabled, viewModel.currentMagicShape, viewModel.currentBackgroundColor, 
        viewModel.is3DPopEnabled, viewModel.magicScale, viewModel.isCentered, viewModel.currentAnimationStyle
    ) {
        if (viewModel.isFilamentEnabled) {
            currentBitmap = null
        } else if (viewModel.isAnimationEnabled) {
            val components = viewModel.getPreviewAnimationComponents(context, wallpaper)
            previewOriginal = components.first
            if (components.first != null) currentBitmap = components.first 
        } else {
            val useMagic = viewModel.isMagicShapeEnabled
            val newBitmap = viewModel.getOrCreateProcessedBitmap(context, wallpaper, allowMagic = useMagic)
            if (newBitmap != null) currentBitmap = newBitmap
        }
    }

    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Effects Studio", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            Row(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp, top = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                ExtendedFloatingActionButton(onClick = { showCustomizeSheet = true }, icon = { Icon(Icons.Default.Palette, null) }, text = { Text("Customize") }, containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, shape = RoundedCornerShape(24.dp))
                Spacer(Modifier.width(16.dp))
                ExtendedFloatingActionButton(onClick = { showApplyOptions = true }, icon = { Icon(Icons.Default.Check, null) }, text = { Text("Done") }, containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = RoundedCornerShape(24.dp))
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            val metrics = context.resources.displayMetrics
            val screenAspectRatio = metrics.widthPixels.toFloat() / metrics.heightPixels.toFloat()
            
            Card(modifier = Modifier.fillMaxHeight().aspectRatio(screenAspectRatio, matchHeightConstraintsFirst = true), shape = RoundedCornerShape(28.dp), elevation = CardDefaults.cardElevation(12.dp)) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    
                    if (isExtractingColors || (currentBitmap == null && !viewModel.isFilamentEnabled)) {
                        AsyncWallpaperImage(wallpaper, null, viewModel, Modifier.fillMaxSize().blur(25.dp), allowMagic = false)
                        Spacer(modifier = Modifier.fillMaxSize().background(overlayColor))
                        ParticleLoadingOverlay(Color.White)
                    } else if (currentBitmap == null && viewModel.isFilamentEnabled) {
                        AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx -> TextureView(ctx).apply { FilamentTextureController(this) } })
                    } else if (currentBitmap != null) {
                        if (viewModel.isAnimationEnabled && previewOriginal != null) {
                            val currentAnim = remember(viewModel.currentAnimationStyle) { AnimationFactory.getAnimation(viewModel.currentAnimationStyle).apply { onUnlock() } }
                            var frameTimeNanos by remember { mutableLongStateOf(0L) }
                            LaunchedEffect(currentAnim) { var lastTime = withFrameNanos { it }; currentAnim.onUnlock(); while (true) { frameTimeNanos = withFrameNanos { it }; val dt = (frameTimeNanos - lastTime) / 1_000_000_000f; lastTime = frameTimeNanos; currentAnim.update(dt.coerceAtMost(0.1f)) } }
                            val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG) }
                            val maskXferPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN) } }
                            val clipPath = remember { android.graphics.Path() }; val screenShapeRect = remember { RectF() }
                            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().pointerInput(currentAnim) { detectTapGestures(onPress = { currentAnim.onLock(); tryAwaitRelease(); currentAnim.onUnlock() }) }) {
                                frameTimeNanos.let {} 
                                val config = LiveWallpaperConfig(shapeName = viewModel.currentMagicShape.name, backgroundColor = viewModel.currentBackgroundColor, is3DPopEnabled = viewModel.is3DPopEnabled, scale = viewModel.magicScale, isCentered = viewModel.isCentered, animationStyle = viewModel.currentAnimationStyle.name, isMagicShapeEnabled = false, isAnimationEnabled = true, isFilamentEnabled = false, animParams = viewModel.currentAnimParams)
                                val geo = ShapeEffectHelper.getUnifiedGeometry(previewOriginal!!.width, previewOriginal!!.height, size.width, size.height, previewMask, config)
                                drawIntoCanvas { canvas -> currentAnim.draw(canvas.nativeCanvas, previewOriginal!!, previewMask, geo, config, paint, maskXferPaint, clipPath, screenShapeRect) }
                            }
                        } else {
                            val infiniteTransition = rememberInfiniteTransition(label = "LivePreview")
                            val finalScale by infiniteTransition.animateFloat(initialValue = 1f, targetValue = 1.03f, animationSpec = infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "animScale")
                            AnimatedContent(targetState = currentBitmap, label = "LiveDepthAnim", transitionSpec = { (fadeIn(animationSpec = tween(500)) + scaleIn(initialScale = 0.95f, animationSpec = tween(500, easing = LinearOutSlowInEasing))).togetherWith(fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 1.15f, animationSpec = tween(400, easing = FastOutSlowInEasing))) }) { bitmap ->
                                if (bitmap != null) androidx.compose.foundation.Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize().scale(finalScale), contentScale = ContentScale.Crop)
                            }
                        }
                    }
                }
            }
        }

        if (showCustomizeSheet) EffectsControlsSheet(viewModel, wallpaper, previewMask, extractedColors) { showCustomizeSheet = false }
        
        if (showApplyOptions) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            AmbientBottomSheet(onDismissRequest = { showApplyOptions = false }, sheetState = sheetState, viewModel = viewModel, currentWallpaper = wallpaper) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).navigationBarsPadding()) {
                    Text("Apply Live Effect", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 24.dp))
                    DestinationButton(Icons.Default.AutoAwesome, "Live Wallpaper", "Animated interactive wallpaper", false) {
                        onApplyRequested(0) 
                        showApplyOptions = false
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

class FilamentTextureController(val textureView: android.view.TextureView) : android.view.TextureView.SurfaceTextureListener, android.view.Choreographer.FrameCallback {
    private var filamentEngine: com.google.android.filament.Engine? = null; private var renderer: com.google.android.filament.Renderer? = null; private var scene: com.google.android.filament.Scene? = null; private var camera: com.google.android.filament.Camera? = null; private var view: com.google.android.filament.View? = null; private var swapChain: com.google.android.filament.SwapChain? = null; private var assetLoader: com.google.android.filament.gltfio.AssetLoader? = null; private var resourceLoader: com.google.android.filament.gltfio.ResourceLoader? = null; private var filamentAsset: com.google.android.filament.gltfio.FilamentAsset? = null; private var light: Int = 0; private val choreographer = android.view.Choreographer.getInstance(); private var androidSurface: android.view.Surface? = null; private var rotationAngle = 0f
    init { runCatching { com.google.android.filament.Filament.init() }; runCatching { com.google.android.filament.gltfio.Gltfio.init() }; runCatching { System.loadLibrary("gltfio-jni") }; textureView.surfaceTextureListener = this; textureView.isOpaque = false }
    override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) { val engine = com.google.android.filament.Engine.create().also { filamentEngine = it }; renderer = engine.createRenderer(); scene = engine.createScene(); camera = engine.createCamera(engine.entityManager.create()); view = engine.createView(); view?.scene = scene; view?.camera = camera; scene?.skybox = com.google.android.filament.Skybox.Builder().color(0.1f, 0.12f, 0.15f, 1.0f).build(engine); light = com.google.android.filament.EntityManager.get().create(); com.google.android.filament.LightManager.Builder(com.google.android.filament.LightManager.Type.DIRECTIONAL).color(1.0f, 1.0f, 0.95f).intensity(50000.0f).direction(-1.0f, -1.0f, -1.0f).castShadows(true).build(engine, light); scene?.addEntity(light); assetLoader = com.google.android.filament.gltfio.AssetLoader(engine, com.google.android.filament.gltfio.UbershaderProvider(engine), com.google.android.filament.EntityManager.get()); resourceLoader = com.google.android.filament.gltfio.ResourceLoader(engine); try { val safeContext = if (android.os.Build.VERSION.SDK_INT >= 24) textureView.context.createDeviceProtectedStorageContext() else textureView.context; val customFile = java.io.File(safeContext.filesDir, "custom_model.glb"); if (customFile.exists()) { val bytes = customFile.readBytes(); val buffer = java.nio.ByteBuffer.allocateDirect(bytes.size); buffer.put(bytes); buffer.rewind(); filamentAsset = assetLoader?.createAsset(buffer); filamentAsset?.let { asset -> resourceLoader?.loadResources(asset); asset.releaseSourceData(); scene?.addEntities(asset.entities) } } } catch (e: Exception) { }; androidSurface = android.view.Surface(surface); swapChain = engine.createSwapChain(androidSurface!!); view?.viewport = com.google.android.filament.Viewport(0, 0, width, height); camera?.setProjection(45.0, width.toDouble() / height.toDouble(), 0.1, 100.0, com.google.android.filament.Camera.Fov.VERTICAL); camera?.lookAt(0.0, 0.0, 5.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0); choreographer.postFrameCallback(this) }
    override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) { view?.viewport = com.google.android.filament.Viewport(0, 0, width, height); camera?.setProjection(45.0, width.toDouble() / height.toDouble(), 0.1, 100.0, com.google.android.filament.Camera.Fov.VERTICAL) }
    override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean { choreographer.removeFrameCallback(this); filamentEngine?.let { engine -> filamentAsset?.let { assetLoader?.destroyAsset(it) }; assetLoader?.destroy(); resourceLoader?.destroy(); engine.destroyEntity(light); swapChain?.let { engine.destroySwapChain(it) }; renderer?.let { engine.destroyRenderer(it) }; view?.let { engine.destroyView(it) }; scene?.let { engine.destroyScene(it) }; camera?.let { engine.destroyCameraComponent(it.entity) }; engine.destroy() }; filamentEngine = null; androidSurface?.release(); return true }
    override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
    override fun doFrame(frameTimeNanos: Long) { if (filamentEngine != null && swapChain != null) { choreographer.postFrameCallback(this); filamentAsset?.let { asset -> val tm = filamentEngine?.transformManager; val instance = tm?.getInstance(asset.root); if (instance != null && instance != 0) { val halfExtent = asset.boundingBox.halfExtent; val maxExtent = kotlin.math.max(halfExtent[0], kotlin.math.max(halfExtent[1], halfExtent[2])); val scaleFactor = if (maxExtent > 0f) 2.0f / maxExtent else 1.0f; val transform = FloatArray(16); android.opengl.Matrix.setIdentityM(transform, 0); android.opengl.Matrix.rotateM(transform, 0, rotationAngle, 0f, 1f, 0f); android.opengl.Matrix.scaleM(transform, 0, scaleFactor, scaleFactor, scaleFactor); android.opengl.Matrix.translateM(transform, 0, -asset.boundingBox.center[0], -asset.boundingBox.center[1], -asset.boundingBox.center[2]); tm.setTransform(instance, transform); rotationAngle += 0.3f } }; if (renderer?.beginFrame(swapChain!!, frameTimeNanos) == true) { renderer?.render(view!!); renderer?.endFrame() } } }
}
