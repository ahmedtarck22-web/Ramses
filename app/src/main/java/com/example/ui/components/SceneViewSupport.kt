package com.example.ui.components

import android.app.ActivityManager
import android.content.Context
import android.opengl.EGLContext
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.model.AppLanguage
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.RoyalGoldLight
import com.example.ui.theme.TextSecondary
import com.google.android.filament.Engine
import com.google.android.filament.Filament
import com.google.android.filament.Skybox
import io.github.sceneview.SceneView
import io.github.sceneview.loaders.EnvironmentLoader
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import java.nio.ByteBuffer
import java.util.WeakHashMap

private const val TAG = "SceneViewSupport"

@Volatile
private var cachedFilamentSupport: Boolean? = null

@Volatile
private var lastFilamentDisposeUptimeMs: Long = 0L

@Volatile
private var sharedFilamentResourcesInstance: SharedFilamentResources? = null

private val nodeOwnerSceneMap = WeakHashMap<Node, SceneView>()

/**
 * Process-scoped shared Filament Engine & Loaders.
 *
 * Keeping the core `Engine` and `ModelLoader` alive across screen switches prevents
 * SceneView's `rememberEngine()` / `Scene` teardown race (`PANIC in filament::FEngine::shutdown`
 * when `Engine.destroy()` runs before `SurfaceView`'s `Choreographer` and `SwapChain` detach)
 * and makes switching between `Ramses` and `Gemini` instantaneous and 100% crash-free.
 */
class SharedFilamentResources(
    val eglContext: EGLContext,
    val engine: Engine,
    val modelLoader: ModelLoader,
    val materialLoader: MaterialLoader,
    val environmentLoader: EnvironmentLoader,
    val blackSkybox: Skybox
) {
    private val cachedNodes = LinkedHashMap<String, ModelNode>()

    fun getOrLoadAssetNode(
        assetPath: String,
        initialScale: Float,
        slotKey: String = "default"
    ): ModelNode? {
        val cacheKey = "$slotKey:asset:$assetPath"
        cachedNodes[cacheKey]?.let { existing ->
            return existing
        }
        return try {
            val instance = modelLoader.createModelInstance(assetFileLocation = assetPath)
            val node = ModelNode(
                modelInstance = instance,
                scaleToUnits = initialScale
            )
            node.centerOrigin(Position(0f, 0f, 0f))
            if (node.animationCount > 0) {
                runCatching { node.playAnimation(0, loop = true) }
            }
            cachedNodes[cacheKey] = node
            node
        } catch (t: Throwable) {
            Log.w(TAG, "Could not load 3D asset '$assetPath': ${t.message}")
            null
        }
    }

    fun getOrLoadCustomBufferNode(
        customPath: String,
        lastModified: Long,
        byteBuffer: ByteBuffer,
        initialScale: Float,
        slotKey: String = "default"
    ): ModelNode? {
        val cacheKey = "$slotKey:file:$customPath@$lastModified"
        cachedNodes[cacheKey]?.let { existing ->
            return existing
        }
        return try {
            val instance = modelLoader.createModelInstance(byteBuffer)
            val node = ModelNode(
                modelInstance = instance,
                scaleToUnits = initialScale
            )
            node.centerOrigin(Position(0f, 0f, 0f))
            if (node.animationCount > 0) {
                runCatching { node.playAnimation(0, loop = true) }
            }
            cachedNodes[cacheKey] = node
            node
        } catch (t: Throwable) {
            Log.w(TAG, "Could not load custom .glb '$customPath': ${t.message}")
            null
        }
    }
}

@Synchronized
fun getOrCreateSharedFilamentResources(context: Context): SharedFilamentResources? {
    if (!isFilament3DSupported(context)) return null
    sharedFilamentResourcesInstance?.let { return it }

    return try {
        val appContext = context.applicationContext
        val eglContext = SceneView.createEglContext()
        val engine = SceneView.createEngine(eglContext)
        val modelLoader = SceneView.createModelLoader(engine, appContext)
        val materialLoader = SceneView.createMaterialLoader(engine, appContext)
        val environmentLoader = SceneView.createEnvironmentLoader(engine, appContext)
        val blackSkybox = Skybox.Builder()
            .color(0f, 0f, 0f, 1f)
            .build(engine)

        SharedFilamentResources(
            eglContext = eglContext,
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            environmentLoader = environmentLoader,
            blackSkybox = blackSkybox
        ).also { created ->
            sharedFilamentResourcesInstance = created
        }
    } catch (t: Throwable) {
        Log.e(TAG, "Failed initializing shared Filament resources; falling back to 2D view: ${t.message}")
        cachedFilamentSupport = false
        null
    }
}

private class SafeSceneViewHost(context: Context) : FrameLayout(context) {
    var sceneView: SceneView? = null
}

/**
 * Lifecycle-safe Compose wrapper around `SceneView` backed by [SharedFilamentResources].
 *
 * Unlike `io.github.sceneview.Scene`, this composable explicitly implements `AndroidView.onRelease`
 * to detach child nodes, stop `Choreographer` frame callbacks, and release only the per-view
 * `SwapChain`/`Renderer`/`View`/`Scene` via `sceneView.destroy()`—never destroying the shared
 * `Engine` or double-freeing `ModelLoader` assets.
 */
@Composable
fun SafeFilamentScene(
    modifier: Modifier = Modifier,
    shared: SharedFilamentResources,
    childNodes: List<Node>,
    pureBlackBackground: Boolean = false
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SafeSceneViewHost(ctx).apply {
                try {
                    val sv = SceneView(
                        ctx,
                        null,
                        0,
                        0,
                        shared.engine,
                        shared.modelLoader,
                        shared.materialLoader,
                        shared.environmentLoader
                    ).apply {
                        cameraManipulator = null
                        onTouchEvent = { _, _ -> false }
                    }
                    sceneView = sv
                    addView(
                        sv,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                } catch (t: Throwable) {
                    Log.e(TAG, "SceneView surface creation failed safely: ${t.message}")
                }
            }
        },
        update = { host ->
            val sv = host.sceneView ?: return@AndroidView
            try {
                if (sv.childNodes != childNodes) {
                    // Detach any incoming node from a previous SceneView first so a node never belongs to two scenes
                    for (node in childNodes) {
                        val previousOwner = nodeOwnerSceneMap[node]
                        if (previousOwner != null && previousOwner !== sv) {
                            runCatching { previousOwner.removeChildNode(node) }
                        }
                        nodeOwnerSceneMap[node] = sv
                    }
                    sv.childNodes = childNodes
                }
            } catch (t: Throwable) {
                Log.w(TAG, "SafeFilamentScene update warning: ${t.message}")
            }
        },
        onRelease = { host ->
            val sv = host.sceneView
            host.sceneView = null
            if (sv != null) {
                runCatching {
                    val attached = sv.childNodes.toList()
                    sv.clearChildNodes()
                    for (node in attached) {
                        if (nodeOwnerSceneMap[node] === sv) {
                            nodeOwnerSceneMap.remove(node)
                        }
                    }
                }
                runCatching { host.removeAllViews() }
                runCatching { sv.destroy() }
                markFilamentEngineDisposed()
            }
        }
    )
}

fun markFilamentEngineDisposed() {
    lastFilamentDisposeUptimeMs = SystemClock.uptimeMillis()
}

suspend fun awaitSafeFilamentEngineMount(minCooldownMs: Long = 110L) {
    yield()
    val lastDispose = lastFilamentDisposeUptimeMs
    if (lastDispose > 0L) {
        val elapsed = SystemClock.uptimeMillis() - lastDispose
        if (elapsed in 0L until minCooldownMs) {
            delay(minCooldownMs - elapsed)
        }
    }
}

/**
 * Safely applies morph target weights only on Filament renderable entities that actually
 * declare morph targets (`getMorphTargetCount > 0`). Calling `setMorphWeights` on static
 * `.glb` meshes with 0 morph targets triggers an uncatchable native C++ `SIGABRT` panic.
 */
fun safeApplyMorphWeights(engine: Engine, node: ModelNode, weights: FloatArray) {
    if (weights.isEmpty()) return
    try {
        val rm = engine.renderableManager
        val entities = buildList {
            add(node.entity)
            node.renderableNodes.forEach { add(it.entity) }
        }.distinct()
        for (entity in entities) {
            if (entity == 0 || !rm.hasComponent(entity)) continue
            val inst = rm.getInstance(entity)
            if (inst == 0) continue
            val count = rm.getMorphTargetCount(inst)
            if (count > 0) {
                val safeWeights = if (weights.size <= count) weights else weights.copyOf(count)
                rm.setMorphWeights(inst, safeWeights, 0)
            }
        }
    } catch (_: Throwable) {
    }
}

fun isRobolectricRuntime(): Boolean {
    return Build.FINGERPRINT.equals("robolectric", ignoreCase = true) ||
        runCatching {
            Class.forName("org.robolectric.Robolectric")
            true
        }.getOrDefault(false)
}

/**
 * Safely verifies whether native Filament + OpenGL ES 3.0+ 3D rendering is supported
 * on the current runtime before initializing Filament.
 */
fun isFilament3DSupported(context: Context): Boolean {
    cachedFilamentSupport?.let { return it }

    val supported = try {
        if (isRobolectricRuntime()) {
            false
        } else {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val glEsVersion = activityManager?.deviceConfigurationInfo?.reqGlEsVersion ?: 0
            if (glEsVersion < 0x00030000) {
                Log.w(TAG, "Device OpenGL ES version (0x${glEsVersion.toString(16)}) < 3.0; using 2D royal fallback.")
                false
            } else {
                Filament.init()
                true
            }
        }
    } catch (t: Throwable) {
        Log.w(TAG, "Native Filament 3D engine unavailable (${t.javaClass.simpleName}: ${t.message}); using 2D royal fallback.")
        false
    }

    cachedFilamentSupport = supported
    return supported
}

@Composable
fun Pharaonic3DFallbackEmblem(
    modifier: Modifier = Modifier,
    appLanguage: AppLanguage = AppLanguage.ARABIC,
    titleOverride: String? = null,
    pureBlackBackground: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (pureBlackBackground) Modifier.background(Color.Black) else Modifier)
            .testTag("pharaonic_3d_fallback"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                RoyalGold.copy(alpha = 0.28f),
                                if (pureBlackBackground) Color.Black else Color(0xFF0A1428),
                                Color.Transparent
                            )
                        )
                    )
                    .border(2.dp, RoyalGold.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ramses_user_photo),
                    contentDescription = if (appLanguage == AppLanguage.ARABIC) "الملك رمسيس الثاني" else "King Rameses II",
                    modifier = Modifier
                        .size(104.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = titleOverride ?: if (appLanguage == AppLanguage.ARABIC) "الملك رمسيس الثاني" else "King Rameses II",
                fontFamily = AmiriFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = RoyalGoldLight,
                textAlign = TextAlign.Center
            )

            Text(
                text = if (appLanguage == AppLanguage.ARABIC) "العرض الملكي الثنائي الأبعاد" else "Royal Portrait View",
                fontFamily = AmiriFontFamily,
                fontSize = 11.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
