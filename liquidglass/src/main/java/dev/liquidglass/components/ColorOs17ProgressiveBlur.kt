package dev.liquidglass.components

import android.content.Context
import android.database.ContentObserver
import android.graphics.RenderEffect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "ColorOs17TopBlur"

/** Page hosts provide their actual recorded background, including ungrouped white pages. */
val LocalProgressiveBlurSurface = staticCompositionLocalOf { Color.Unspecified }

/** Internal bridge. Call ProgressiveBlurContent rather than managing a second overlay. */
@Composable
internal fun ColorOs17ProgressiveBlurOrFallback(
    modifier: Modifier,
    isLightTheme: Boolean,
    strength: Float,
    visibility: Float,
    overlayHeight: Dp,
    surfaceColor: Color,
    fallback: @Composable () -> Unit,
) {
    val properties by produceState<Map<String, String>?>(null) {
        value = if (Build.VERSION.SDK_INT >= 31) {
            withContext(Dispatchers.IO) { readBlurProperties() }
        } else emptyMap()
    }
    var failed by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(false) }
    // Keep RenderEffect-bearing classes off the pre-31 execution path.
    val native = Build.VERSION.SDK_INT >= 31 && properties?.let {
        ColorOs17BlurPolicy.isColorOs17(it["ro.build.version.oplusrom"].orEmpty(),
            it["ro.build.version.opporom"].orEmpty())
    } == true
    val decision = when {
        properties == null -> "pending"
        !native -> "compose-fallback: rom=${properties?.get("ro.build.version.oplusrom")}, api=${Build.VERSION.SDK_INT}"
        failed -> "compose-fallback: native failure"
        active -> "coloros17-system"
        else -> "native pending/disabled"
    }
    var lastDecision by remember { mutableStateOf("") }
    SideEffect {
        if (decision != lastDecision) {
            Log.i(TAG, "decision=$decision")
            lastDecision = decision
        }
    }
    Box(modifier) {
        if (native && !failed) {
            if (!active) fallback()
            NativeBlurView(
                Modifier.height(overlayHeight).fillMaxWidth(), properties.orEmpty(),
                isLightTheme, strength, visibility, surfaceColor,
                onFailure = { failed = true },
                onActive = { active = it },
            )
        } else {
            fallback()
        }
    }
}

/** Read once off the UI thread. No hidden-API exemptions, root, or settings writes. */
private fun readBlurProperties(): Map<String, String> {
    val keys = listOf("ro.build.version.oplusrom", "ro.build.version.opporom",
        "persist.sys.oplus.anim_level")
    val result = mutableMapOf<String, String>()
    val get = try {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
    } catch (_: Exception) { null }
    for (key in keys) {
        val value = try { get?.invoke(null, key) as? String } catch (_: Exception) { null }
        if (!value.isNullOrBlank()) {
            result[key] = value
            continue
        }
        // Public process execution is a fallback for property reads only. Timeout is bounded.
        val process = try { ProcessBuilder("/system/bin/getprop", key).start() }
            catch (_: Exception) { null }
        if (process != null) {
            try {
                if (process.waitFor(200, TimeUnit.MILLISECONDS)) {
                    result[key] = process.inputStream.bufferedReader().use { it.readText().trim() }
                }
            } catch (_: Exception) {
                // Unknown ROM means fallback, not a guessed ColorOS version.
            } finally { process.destroy() }
        }
    }
    return result
}

@RequiresApi(31)
@Composable
private fun NativeBlurView(
    modifier: Modifier,
    properties: Map<String, String>,
    light: Boolean,
    strength: Float,
    visibility: Float,
    surfaceColor: Color,
    onFailure: () -> Unit,
    onActive: (Boolean) -> Unit,
) {
    AndroidView(
        modifier = modifier,
        factory = { context -> ColorOsBlurOverlay(context, properties, onFailure, onActive) },
        update = { it.update(light, strength, visibility, surfaceColor.toArgb()) },
        onRelease = { it.release() },
    )
}

@RequiresApi(31)
private class OplusGradientApi private constructor(
    private val create: Method,
    private val setBackground: Method,
) {
    fun apply(view: View, strength: Float) {
        val effect = if (strength > 0f) create.invoke(null,
            ColorOs17BlurPolicy.START_RADIUS * strength,
            ColorOs17BlurPolicy.END_RADIUS * strength,
            true, ColorOs17BlurPolicy.SPEED, 0, 0, 0) as? RenderEffect
            ?: error("Oplus gradient effect returned null") else null
        setBackground.invoke(null, effect, view)
    }

    fun clear(view: View) { setBackground.invoke(null, null, view) }

    companion object {
        fun resolve(): OplusGradientApi {
            val version = Class.forName("com.oplus.os.OplusBuild\$VERSION")
            val major = version.getField("SDK_VERSION").getInt(null)
            val minor = version.getField("SDK_SUB_VERSION").getInt(null)
            check(ColorOs17BlurPolicy.supportedSdk(major, minor)) { "Unsupported Oplus view SDK $major.$minor" }
            val create = Class.forName("com.oplus.graphics.OplusRenderEffect").getMethod(
                "createGradientBlurEffect", Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
                Float::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            val set = Class.forName("com.oplus.view.OplusViewBackgroundRenderEffect").getMethod(
                "setBackgroundRenderEffect", RenderEffect::class.java, View::class.java)
            return OplusGradientApi(create, set)
        }
    }
}

@RequiresApi(31)
private class ColorOsBlurOverlay(
    context: Context,
    private val properties: Map<String, String>,
    private val onFailure: () -> Unit,
    private val onActive: (Boolean) -> Unit,
) : View(context) {
    private var api: OplusGradientApi? = null
    private var failed = false
    private var observing = false
    private var light = true
    private var strength = 1f
    private var visibility = 1f
    private var surface = android.graphics.Color.WHITE
    private var lastKey: List<Any>? = null
    private var active = false
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { refresh() }
    }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        // Gradient background supplies a real RenderNode; no captured parent or own-content blur.
        setWillNotDraw(false)
    }

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean = false

    fun update(light: Boolean, strength: Float, visibility: Float, surface: Int) {
        this.light = light
        this.strength = ColorOs17BlurPolicy.strength(strength)
        this.visibility = ColorOs17BlurPolicy.strength(visibility)
        this.surface = surface
        refresh()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        try {
            val resolver = context.contentResolver
            observing = true // Release partial registrations if the second one fails.
            resolver.registerContentObserver(Settings.System.getUriFor("system_material_blur_enable"), false, observer)
            resolver.registerContentObserver(Settings.Global.getUriFor("disable_window_blurs"), false, observer)
            refresh()
        } catch (error: Exception) { fail(error) }
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    private fun refresh() {
        if (!isAttachedToWindow || failed) return
        try {
            check(isHardwareAccelerated) { "Hardware acceleration unavailable" }
            val material = Settings.System.getInt(context.contentResolver, "system_material_blur_enable", 0)
            val disabled = Settings.Global.getInt(context.contentResolver, "disable_window_blurs", 0)
            if (!ColorOs17BlurPolicy.enabled(material, disabled,
                properties["persist.sys.oplus.anim_level"]?.trim()?.toIntOrNull())) {
                api?.clear(this)
                background = null
                lastKey = null
                reportActive(false)
                return
            }
            val current = api ?: OplusGradientApi.resolve().also { api = it }
            // Match AppBarBlurOverlayBehavior: scroll fades the View, not the blur kernel.
            alpha = visibility
            val key = listOf(light, strength, surface)
            if (key == lastKey) return
            // Equivalent to the APK's bottom -> top LinearGradient, expressed top -> bottom.
            val positions = ColorOs17BlurPolicy.positions(light)
            val alphas = ColorOs17BlurPolicy.alphas(light)
            val colors = alphas.reversedArray().map {
                ((it * 255).toInt() shl 24) or (surface and 0xFFFFFF)
            }.toIntArray()
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors).apply {
                setColors(colors, positions.reversedArray().map { 1f - it }.toFloatArray())
                alpha = (strength * 255).toInt()
            }
            current.apply(this, strength)
            if (lastKey == null) Log.i(TAG, "backend=coloros17-system surface=${Integer.toHexString(surface)} visibility=$visibility (visual validation required)")
            lastKey = key
            reportActive(true)
            invalidate()
        } catch (error: Exception) { fail(error) }
        catch (error: LinkageError) { fail(error) }
    }

    private fun reportActive(value: Boolean) {
        if (active == value) return
        active = value
        post { onActive(value) }
    }

    private fun fail(error: Throwable) {
        if (failed) return
        failed = true
        Log.w(TAG, "backend=compose-fallback: ${error.javaClass.simpleName}: ${error.message}")
        release()
        background = null
        // Do not write Compose state from inside an AndroidView update callback.
        post { onFailure() }
    }

    fun release() {
        if (observing) {
            try { context.contentResolver.unregisterContentObserver(observer) } catch (_: Exception) { }
            observing = false
        }
        try { api?.clear(this) } catch (_: Exception) { } catch (_: LinkageError) { }
        lastKey = null
    }
}
