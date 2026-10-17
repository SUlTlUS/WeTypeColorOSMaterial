package dev.liquidglass.coloros17

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.content.DialogInterface
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.AssetManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Looper
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import dev.liquidglass.components.ColorOs17BlurPolicy
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Invocation only: no copied COUI renderer, resources, or approximation fallback. */
class ColorOs17SettingsComponents private constructor(
    private val activity: Activity,
    val context: Context,
    val settingsVersion: String,
) : AutoCloseable {
    private val closers = mutableListOf<() -> Unit>()
    private var closed = false
    private var materialManager: Any? = null

    /** Stateful host controls, separate from the catalog's demonstration values. */
    fun createSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit): Result<CompoundButton> = attempt {
        requireUiThread()
        (construct("com.coui.appcompat.couiswitch.COUISwitch") as CompoundButton).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, value -> onCheckedChange(value) }
        }
    }

    fun createSlider(max: Int, progress: Int, onProgress: (Int) -> Unit,
        onFinished: () -> Unit): Result<View> = attempt {
        requireUiThread()
        val view = construct("com.coui.appcompat.seekbar.COUISeekBar") as View
        call(view, "setMax", max)
        call(view, "setProgress", progress)
        val binding = ColorOs17SliderBinding(onProgress, onFinished)
        listener(view, "setOnSeekBarChangeListener") { method, args ->
            when (method) {
                "onStartTrackingTouch" -> binding.start()
                "onProgressChanged" -> binding.change(args!![1] as Int, args[2] == true)
                "onStopTrackingTouch" -> binding.stop()
            }
        }
        view
    }

    fun updateSlider(view: View, progress: Int): Result<Unit> = attempt {
        requireUiThread()
        if (call(view, "getProgress") != progress) call(view, "setProgress", progress)
    }

    fun createNavigation(items: List<ColorOs17NavigationItem>, selectedId: Int,
        onSelected: (Int) -> Unit): Result<View> = attempt {
        requireUiThread()
        val view = construct("com.coui.appcompat.bottomfloatingnavigationbar.COUIBottomFloatingNavigationBar") as View
        val builderClass = load("com.coui.appcompat.bottomfloatingnavigationbar.COUIBottomFloatingNavigationBarItem\$Builder")
        val nativeItems = items.map { item ->
            val builder = builderClass.getConstructor().newInstance()
            call(builder, "setId", item.id)
            call(builder, "setTitle", item.title)
            call(builder, "setIcon", item.icon)
            call(builder, "build")!!
        }
        call(view, "addItems", nativeItems)
        // false selects TYPE_FRAMEWORK_BOTTOM_BAR; true clears native blur and draws a panel background.
        call(view, "setIsDrawableMaterialEffect", false)
        Log.i(TAG, "navigation material=framework-bottom-bar; version=$settingsVersion")
        call(view, "setSelectedItemId", selectedId)
        listener(view, "setOnItemSelectedListener") { _, args ->
            onSelected(call(args!![1]!!, "getId") as Int)
        }
        view
    }

    fun updateNavigation(view: View, selectedId: Int): Result<Unit> = attempt {
        requireUiThread()
        if (call(view, "getSelectedItemId") != selectedId) call(view, "setSelectedItemId", selectedId)
    }

    fun createActionButton(label: String, icon: Drawable?, onClick: () -> Unit): Result<TextView> = attempt {
        requireUiThread()
        (construct("com.coui.appcompat.button.COUIButton") as TextView).apply {
            contentDescription = label
            if (icon == null) text = label else {
                icon.mutate().setTint(currentTextColor)
                setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
            }
            setOnClickListener { onClick() }
        }
    }

    fun createFloatingActionButton(label: String, icon: Drawable, onClick: () -> Unit): Result<View> = attempt {
        requireUiThread()
        val view = construct("com.coui.appcompat.floatingactionbutton.COUIFloatingButton") as View
        call(view, "setIsFloatingButtonExpandEnable", false)
        call(view, "setMainFabDrawable", icon)
        (call(view, "getMainFloatingButton") as View).contentDescription = label
        listener(view, "setFloatingButtonClickListener") { _, _ -> onClick() }
        view
    }

    fun showSingleChoiceDialog(title: String, choices: List<String>, selected: Int,
        onSelected: (Int) -> Unit, onDismiss: () -> Unit): Result<Dialog> = attempt {
        requireUiThread()
        val builder = construct("com.coui.appcompat.dialog.COUIAlertDialogBuilder")
        call(builder, "setTitle", title as CharSequence)
        val selection = DialogInterface.OnClickListener { dialog, index ->
            onSelected(index)
            dialog.dismiss()
        }
        call(builder, "setSingleChoiceItems", choices.toTypedArray<CharSequence>(), selected, selection)
        (call(builder, "create") as Dialog).also { dialog ->
            dialog.setOnDismissListener { onDismiss() }
            showDialog(dialog)
        }
    }

    fun createView(spec: ColorOs17ComponentSpec, onValue: (String) -> Unit = {}): Result<View> = attempt {
        requireUiThread()
        require(ColorOs17ComponentCatalog.find(spec.key) == spec) { "Unknown component" }
        when (spec.kind) {
            ColorOs17ComponentKind.View -> (construct(spec.className) as View).also {
                configure(it, spec.key, onValue)
            }
            ColorOs17ComponentKind.Preference -> createPreference(spec, onValue)
            else -> error("Use showPopup for ${spec.kind}")
        }
    }

    fun showPopup(spec: ColorOs17ComponentSpec, anchor: View,
        onValue: (String) -> Unit = {}): Result<Unit> = attempt {
        requireUiThread()
        require(anchor.isAttachedToWindow) { "Anchor must be attached" }
        require(ColorOs17ComponentCatalog.find(spec.key) == spec) { "Unknown component" }
        when (spec.kind) {
            ColorOs17ComponentKind.Dialog -> {
                val builder = construct(spec.className)
                call(builder, "setTitle", "ColorOS 17" as CharSequence)
                call(builder, "setMessage", "直接调用设置 APK 的 COUIAlertDialogBuilder。" as CharSequence)
                val listener = DialogInterface.OnClickListener { _, which -> onValue("按钮：$which") }
                call(builder, "setPositiveButton", "确认" as CharSequence, listener)
                call(builder, "setNegativeButton", "取消" as CharSequence, listener)
                showDialog(call(builder, "create") as Dialog)
            }
            ColorOs17ComponentKind.Sheet -> {
                val dialog = construct(spec.className) as Dialog
                val input = construct("com.coui.appcompat.edittext.COUIInputView") as View
                call(input, "setHint", "输入示例" as CharSequence)
                call(dialog, "setContentView", input)
                showDialog(dialog)
            }
            ColorOs17ComponentKind.Menu -> {
                val popup = construct(spec.className)
                call(popup, "setAnchorView", anchor)
                val menu = call(popup, "getMenu") as Menu
                listOf("复制", "分享", "取消").forEachIndexed { index, title ->
                    menu.add(0, index + 1, index, title)
                }
                listener(popup, "setOnMenuItemClickListener") { _, args ->
                    onValue((args?.firstOrNull() as? android.view.MenuItem)?.title.toString())
                }
                closers.add { call(popup, "dismiss") }
                call(popup, "show")
            }
            else -> error("Not a popup")
        }
        Log.i(TAG, "invoked=${spec.className}; version=$settingsVersion")
    }

    private fun configure(view: View, key: String, onValue: (String) -> Unit) {
        // Only demo data and callback wiring. Geometry/material/animation stay in native code.
        when (key) {
            "coloros17-button" -> {
                (view as TextView).text = "Button"
                view.setOnClickListener { onValue("已点击") }
            }
            "coloros17-loading-button" -> {
                (view as TextView).text = "加载"
                view.setOnClickListener {
                    attempt {
                        val loading = call(view, "getButtonState") == 1
                        call(view, if (loading) "resetButtonState" else "switchToLoadingState")
                        onValue(if (loading) "已停止加载" else "加载中")
                    }.onFailure { onValue(failure(it)) }
                }
            }
            "coloros17-switch" -> (view as CompoundButton).apply {
                isChecked = true
                setOnCheckedChangeListener { _, checked -> onValue("$checked") }
            }
            "coloros17-checkbox" -> {
                call(view, "setState", 0)
                listener(view, "setOnStateChangeListener") { _, args -> onValue("${args?.getOrNull(1)}") }
            }
            "coloros17-slider", "coloros17-section-slider", "coloros17-intent-slider" -> {
                val max = if (key == "coloros17-section-slider") 5 else 100
                call(view, "setMax", max)
                call(view, "setProgress", max / 2)
                listener(view, "setOnSeekBarChangeListener") { method, args ->
                    if (method == "onProgressChanged") onValue("${args?.getOrNull(1)}")
                }
            }
            "coloros17-stepper" -> {
                call(view, "setMinimum", 0)
                call(view, "setMaximum", 10)
                call(view, "setCurStep", 5)
                listener(view, "setOnStepChangeListener") { _, args -> onValue(args?.joinToString().orEmpty()) }
                closers.add { call(view, "release") }
            }
            "coloros17-edit" -> (view as EditText).hint = "Placeholder"
            "coloros17-input" -> call(view, "setHint", "Placeholder" as CharSequence)
            "coloros17-toolbar" -> call(view, "setTitle", "Title" as CharSequence)
            "coloros17-segment" -> {
                call(view, "setSegmentButtons", arrayOf("首页", "发现", "设置"))
                listener(view, "setOnSegmentSelectedListener") { _, args -> onValue(args?.joinToString().orEmpty()) }
            }
            "coloros17-tabs" -> {
                listOf("首页", "发现", "设置").forEach { title ->
                    val tab = call(view, "newTab")!!
                    call(tab, "setText", title as CharSequence)
                    call(view, "addTab", tab)
                }
                listener(view, "addOnTabSelectedListener") { method, args ->
                    if (method == "onTabSelected") onValue(call(args!!.first()!!, "getText").toString())
                }
            }
            "coloros17-navigation" -> {
                val menu = call(view, "getMenu") as Menu
                listOf("首页", "发现", "设置").forEachIndexed { index, title ->
                    menu.add(0, index + 1, index, title).setIcon(android.R.drawable.ic_menu_compass)
                }
                listener(view, "setOnItemSelectedListener") { _, args ->
                    onValue((args?.firstOrNull() as? android.view.MenuItem)?.title.toString())
                }
            }
            "coloros17-floating-navigation", "coloros17-floating-toolbar" -> {
                val navigation = key == "coloros17-floating-navigation"
                val itemClass = if (navigation)
                    "com.coui.appcompat.bottomfloatingnavigationbar.COUIBottomFloatingNavigationBarItem"
                else "com.coui.appcompat.bottomfloatingtoolbar.COUIBottomFloatingToolbarItem"
                val items = listOf("首页", "发现", "设置").mapIndexed { index, title ->
                    val builder = load("$itemClass\$Builder").getConstructor().newInstance()
                    call(builder, "setId", index + 1)
                    call(builder, "setTitle", title)
                    call(builder, "setIcon", context.getDrawable(android.R.drawable.ic_menu_compass))
                    call(builder, "build")!!
                }
                call(view, if (navigation) "addItems" else "setItemList", items)
                if (navigation) {
                    call(view, "setSelectedItemId", 1)
                    listener(view, "setOnItemSelectedListener") { _, args ->
                        onValue(args?.firstOrNull()?.toString().orEmpty())
                    }
                } else {
                    listener(view, "registerItemClick") { _, args -> onValue(args?.joinToString().orEmpty()) }
                    closers.add { call(view, "unRegisterItemClick") }
                }
            }
            "coloros17-number" -> {
                call(view, "setMinValue", 0)
                call(view, "setMaxValue", 59)
                call(view, "setValue", 30)
                listener(view, "setOnValueChangedListener") { _, args -> onValue("${args?.getOrNull(2)}") }
            }
            "coloros17-date" -> listener(view, "setOnDateChangedListener") { _, args ->
                onValue(args?.drop(1)?.joinToString(" / ").orEmpty())
            }
            "coloros17-time" -> {
                call(view, "setTimePicker", java.util.Calendar.getInstance())
                listener(view, "setOnTimeChangeListener") { _, args ->
                    onValue(args?.lastOrNull()?.toString().orEmpty())
                }
            }
            "coloros17-progress", "coloros17-circle-progress" -> {
                call(view, "setMax", 100)
                call(view, "setProgress", 60)
            }
        }
        Log.i(TAG, "created=${view.javaClass.name}; version=$settingsVersion")
    }

    private fun createPreference(spec: ColorOs17ComponentSpec, onValue: (String) -> Unit): View {
        val preference = construct(spec.className)
        call(preference, "setTitle", "Title" as CharSequence)
        call(preference, "setSummary", "Subtitle" as CharSequence)
        // Never persist demo state into the Settings package's preferences.
        call(preference, "setPersistent", false)
        if (spec.key == "coloros17-switch-preference") call(preference, "setChecked", true)
        val layout = call(preference, "getLayoutResource") as Int
        val parent = FrameLayout(context)
        val view = LayoutInflater.from(context).inflate(layout, parent, false)
        val widget = call(preference, "getWidgetLayoutResource") as Int
        val frame = view.findViewById<ViewGroup>(android.R.id.widget_frame)
        if (widget != 0 && frame != null) LayoutInflater.from(context).inflate(widget, frame)
        val holderClass = load("androidx.preference.PreferenceViewHolder")
        val holder = holderClass.getMethod("createInstanceForTests", View::class.java).invoke(null, view)
        call(preference, "onBindViewHolder", holder)
        view.setOnClickListener {
            attempt {
                call(preference, "performClick")
                call(preference, "onBindViewHolder", holder)
                onValue(if (spec.key == "coloros17-switch-preference")
                    call(preference, "isChecked").toString() else "已点击")
            }.onFailure { onValue(failure(it)) }
        }
        return view
    }

    private fun showDialog(dialog: Dialog) {
        // WindowManager comes from the host Activity, not the foreign package context.
        dialog.setOwnerActivity(activity)
        closers.add { dialog.dismiss() }
        dialog.show()
    }

    private fun load(name: String): Class<*> = Class.forName(name, false, context.classLoader)
    private fun construct(name: String): Any = load(name).getConstructor(Context::class.java).newInstance(context)

    private fun call(target: Any, name: String, vararg args: Any?): Any? {
        val candidates = target.javaClass.methods.filter { method ->
            method.name == name && method.parameterTypes.size == args.size &&
                method.parameterTypes.indices.all { index -> matches(method.parameterTypes[index], args[index]) }
        }
        val method = candidates.firstOrNull { !it.isBridge && !it.isSynthetic } ?: candidates.firstOrNull()
            ?: error("Missing API: ${target.javaClass.name}.$name/${args.size}")
        return method.invoke(target, *args)
    }

    private fun listener(target: Any, setter: String, callback: (String, Array<out Any?>?) -> Unit) {
        val method = target.javaClass.methods.firstOrNull {
            it.name == setter && it.parameterCount == 1 && it.parameterTypes[0].isInterface
        } ?: error("Missing listener: $setter")
        val type = method.parameterTypes[0]
        val proxy = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { obj, invoked, args ->
            when (invoked.name) {
                "equals" -> obj === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(obj)
                "toString" -> "ColorOS17ExampleListener"
                else -> {
                    callback(invoked.name, args)
                    when (invoked.returnType) {
                        java.lang.Boolean.TYPE -> true
                        java.lang.Integer.TYPE -> 0
                        java.lang.Float.TYPE -> 0f
                        else -> null
                    }
                }
            }
        }
        method.invoke(target, proxy)
    }

    private fun requireUiThread() {
        check(!closed) { "Session is closed" }
        check(Looper.myLooper() == Looper.getMainLooper()) { "UI calls require main thread" }
        check(!activity.isFinishing && !activity.isDestroyed) { "Activity is no longer active" }
        if (materialManager == null) {
            val type = load("com.coui.appcompat.COUIMaterialStateManager")
            val manager = type.getField("INSTANCE").get(null)!!
            val flags = type.getField("FLAG_ALL_EFFECT").getInt(null)
            materialOwners.acquire(manager) { call(manager, "registerMaterialStateObserver", context, flags) }
            materialManager = manager
            Log.i(TAG, "material initialized; blur=${call(manager, "isBlurEffectEnable")}; stroke=${call(manager, "isStrokeEffectEnable")}")
        }
    }

    override fun close() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Close on main thread" }
        if (closed) return
        closed = true
        closers.asReversed().forEach { closer -> attempt { closer() } }
        closers.clear()
        materialManager?.let { manager ->
            attempt {
                val flags = manager.javaClass.getField("FLAG_ALL_EFFECT").getInt(null)
                materialOwners.release(manager) { call(manager, "unregisterMaterialStateObserver", flags) }
            }
            materialManager = null
        }
    }

    companion object {
        private const val TAG = "ColorOs17Components"
        const val SETTINGS_PACKAGE = "com.android.settings"
        private val materialOwners = ColorOs17MaterialOwners()

        /** Only the installed, platform-signed system Settings is permitted as a code source. */
        suspend fun open(host: Context, dark: Boolean): Result<ColorOs17SettingsComponents> =
            withContext(Dispatchers.IO) {
                attempt {
                    check(Build.VERSION.SDK_INT >= 33) { "Settings APK requires Android API 33+" }
                    val rom = readProperty("ro.build.version.oplusrom")
                    val oldRom = if (rom.isBlank()) readProperty("ro.build.version.opporom") else ""
                    check(ColorOs17BlurPolicy.isColorOs17(rom, oldRom)) { "Only ColorOS 17: $rom $oldRom" }
                    val activity = findActivity(host) ?: error("An Activity context is required")
                    val pm = host.packageManager
                    @Suppress("DEPRECATION")
                    val info = pm.getPackageInfo(SETTINGS_PACKAGE, 0)
                    val app = info.applicationInfo ?: error("No Settings ApplicationInfo")
                    check(app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                        "Settings must be a system package"
                    }
                    check(pm.checkSignatures("android", SETTINGS_PACKAGE) == PackageManager.SIGNATURE_MATCH) {
                        "Settings is not platform-signed"
                    }
                    check(info.versionName?.startsWith("17.") == true) { "Unsupported Settings ${info.versionName}" }
                    // Documented cross-package loading API. Does not grant the Settings UID/permissions.
                    val source = host.createPackageContext(SETTINGS_PACKAGE,
                        Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY)
                    val config = Configuration(host.resources.configuration).apply {
                        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                            if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                    }
                    val resourceContext = source.createConfigurationContext(config)
                    val themeName = if (dark) "Theme.COUI.Dark" else "Theme.COUI"
                    val themeId = resourceContext.resources.getIdentifier(themeName, "style", SETTINGS_PACKAGE)
                    check(themeId != 0) { "Missing native theme: $themeName" }
                    val context = NativeUiContext(resourceContext, activity, source.classLoader, themeId)
                    ColorOs17SettingsComponents(activity, context, info.versionName.orEmpty())
                }
            }

        private fun findActivity(context: Context): Activity? = when (context) {
            is Activity -> context
            is ContextWrapper -> if (context.baseContext !== context) findActivity(context.baseContext) else null
            else -> null
        }

        private fun readProperty(key: String): String {
            val reflected = attempt {
                Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, key) as String
            }.getOrNull()
            if (!reflected.isNullOrBlank()) return reflected
            return attempt {
                val process = ProcessBuilder("/system/bin/getprop", key).start()
                try {
                    if (process.waitFor(200, TimeUnit.MILLISECONDS))
                        process.inputStream.bufferedReader().use { it.readText().trim() } else ""
                } finally { process.destroy() }
            }.getOrDefault("")
        }

        private fun matches(type: Class<*>, value: Any?): Boolean = when {
            value == null -> !type.isPrimitive
            type.isInstance(value) -> true
            type == java.lang.Integer.TYPE -> value is Int
            type == java.lang.Boolean.TYPE -> value is Boolean
            type == java.lang.Float.TYPE -> value is Float
            type == java.lang.Long.TYPE -> value is Long
            else -> false
        }

        private fun <T> attempt(block: () -> T): Result<T> = try {
            Result.success(block())
        } catch (error: Exception) {
            val cause = if (error is InvocationTargetException) error.targetException else error
            if (cause is VirtualMachineError || cause is ThreadDeath) throw cause
            Log.w(TAG, failure(cause))
            Result.failure(cause)
        } catch (error: LinkageError) {
            Log.w(TAG, failure(error))
            Result.failure(error)
        }

        fun failure(error: Throwable): String = "${error.javaClass.simpleName}: ${error.message.orEmpty()}"
    }
}

data class ColorOs17NavigationItem(val id: Int, val title: String, val icon: Drawable?)

/** Keep foreign resource IDs and class identity, but use the host window/input services. */
private class NativeUiContext(private val source: Context, private val host: Activity,
    private val loader: ClassLoader, theme: Int) : ContextThemeWrapper(host, theme) {
    override fun getResources(): Resources = source.resources
    override fun getAssets(): AssetManager = source.assets
    override fun getPackageName(): String = ColorOs17SettingsComponents.SETTINGS_PACKAGE
    override fun getClassLoader(): ClassLoader = loader
    override fun getSystemService(name: String): Any? = when (name) {
        Context.WINDOW_SERVICE, Context.INPUT_METHOD_SERVICE -> host.getSystemService(name)
        else -> super.getSystemService(name)
    }
}
