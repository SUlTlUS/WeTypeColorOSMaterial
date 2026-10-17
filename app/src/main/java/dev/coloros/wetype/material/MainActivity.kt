package dev.coloros.wetype.material

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.viewinterop.AndroidView
import com.zeaze.tianyinwallpaper.backdrop.Backdrop
import com.zeaze.tianyinwallpaper.backdrop.backdrops.layerBackdrop
import com.zeaze.tianyinwallpaper.backdrop.backdrops.rememberCanvasBackdrop
import com.zeaze.tianyinwallpaper.backdrop.backdrops.rememberLayerBackdrop
import com.zeaze.tianyinwallpaper.catalog.components.*
import com.zeaze.tianyinwallpaper.ui.commom.ProgressiveBlurContent
import dev.liquidglass.coloros17.ColorOs17SettingsComponents
import dev.liquidglass.ios27.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ModulePage(this) }
    }

    override fun onResume() {
        super.onResume()
        ModuleFramework.refresh()
    }

}

@Composable
private fun ModulePage(activity: MainActivity) {
    val preferences = remember { activity.getSharedPreferences("appearance", 0) }
    var glass by remember { mutableStateOf(preferences.getBoolean("liquidGlass", true)) }
    var launcher by remember { mutableStateOf(launcherVisible(activity)) }
    val dark = isSystemInDarkTheme()
    val configuration = LocalConfiguration.current
    var nativeResult by remember(activity, glass, configuration) {
        mutableStateOf<Result<ColorOs17SettingsComponents>?>(null)
    }
    LaunchedEffect(activity, glass, configuration) {
        if (!glass) nativeResult = ColorOs17SettingsComponents.open(activity, dark)
    }
    val native = nativeResult?.getOrNull()
    DisposableEffect(native) { onDispose { native?.close() } }
    val changeAppearance: (Boolean) -> Unit = {
        preferences.edit().putBoolean("liquidGlass", it).apply()
        glass = it
    }
    LaunchedEffect(dark) {
        val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        activity.window.insetsController?.setSystemBarsAppearance(if (dark) 0 else mask, mask)
    }
    Ios27PageTheme(darkTheme = dark) {
        CompositionLocalProvider(LocalIos27ListTypography provides Ios27ListTypography(
            title = Ios27Tokens.Type.Body.copy(fontWeight = FontWeight.Medium), sectionWeight = FontWeight.Medium)) {
            val colors = LocalIos27PageColors.current
            if (!glass && native == null) {
                Column(Modifier.fillMaxSize().background(colors.page).safeDrawingPadding().padding(16.dp)) {
                    Ios27PageHeader("界面")
                    Text(if (nativeResult == null) "正在加载 ColorOS 17 原组件…" else
                        "无法调用 ColorOS 17 原组件：${ColorOs17SettingsComponents.failure(nativeResult!!.exceptionOrNull()!!)}",
                        color = colors.label)
                    Ios27ListSection { Ios27ListRow("使用液态玻璃", onClick = { changeAppearance(true) }) }
                }
                return@CompositionLocalProvider
            }
            val scroll = rememberScrollState()
            // Only the page records into this backdrop. Glass chrome is a sibling.
            val backdrop = rememberLayerBackdrop { drawRect(colors.page); drawContent() }
            val systemDensity = LocalDensity.current
            val topInsetPx = with(systemDensity) {
                WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding().toPx()
            }
            BoxWithConstraints(Modifier.fillMaxSize().background(colors.page)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
                // Match soundbar's design-density host; COUI keeps system density.
                val scale = if (native != null) 1f else
                    (minOf(maxWidth.value, maxHeight.value) / 402f).coerceIn(0.85f, 1f)
                val pageDensity = Density(systemDensity.density * scale, systemDensity.fontScale)
                Ios27AndroidDesignBehavior(pageDensity) {
                    val topInset = with(pageDensity) { topInsetPx.toDp() }
                    Box(Modifier.fillMaxSize()) {
                if (native != null) Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(colors.page))
                Box(Modifier.fillMaxSize().then(if (native == null) Modifier.layerBackdrop(backdrop) else Modifier)) {
                    Ios27PageContent(Modifier.fillMaxSize().verticalScroll(scroll)
                        .padding(top = topInset + Ios27Tokens.CompactHeaderHeight.dp, bottom = 112.dp)) {
                        Ios27PageHeader("设置")
                                Ios27ListSection(title = "模块") {
                                    Ios27ListRows(listOf(false, true)) { icon ->
                                        if (!icon) Ios27ListRow("LSPosed", detail = ModuleFramework.status,
                                            onClick = { ModuleFramework.configure(activity) })
                                        else SwitchRow("显示桌面图标", launcher, native) {
                                            activity.packageManager.setComponentEnabledSetting(
                                                ComponentName(activity, "${activity.packageName}.Launcher"),
                                                if (it) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                                PackageManager.DONT_KILL_APP)
                                            launcher = launcherVisible(activity)
                                        }
                                    }
                                }
                                Ios27ListSection(title = "界面", footer = "仅控制模块设置页的界面，键盘材质继续跟随系统。") {
                                    SwitchRow("液态玻璃", glass, native, changeAppearance)
                                }
                                Ios27ListSection(title = "关于") {
                                    Ios27ListRows(listOf(
                                        "模块版本" to BuildConfig.VERSION_NAME,
                                        "API" to "libxposed 102",
                                        "系统" to "Android ${Build.VERSION.RELEASE}",
                                        "系统版本" to Build.DISPLAY.removePrefix("${Build.MODEL}_"),
                                        "设备型号" to "${Build.MANUFACTURER} ${Build.MODEL}")) { (title, value) ->
                                        Ios27ListRow(title, trailing = {
                                            Text(value, color = colors.secondaryLabel, style = Ios27Tokens.Type.Body,
                                                modifier = Modifier.widthIn(max = 200.dp))
                                        })
                                    }
                                }
                    }
                }
                ProgressiveBlurContent(Modifier.align(Alignment.TopCenter).fillMaxWidth(), backdrop,
                    isLightTheme = !dark, strength = 1f, surfaceColor = colors.page, appBarHeight = topInset,
                    visibility = (scroll.value / with(pageDensity) { 30.dp.toPx() }).coerceIn(0f, 1f), scrollDriven = true)
                RestartAction({ ModuleFramework.restart(activity) }, backdrop, dark, native,
                    Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp))
                    }
                }
            }
        }
    }
}

private fun launcherVisible(activity: MainActivity): Boolean =
    activity.packageManager.getComponentEnabledSetting(ComponentName(activity, "${activity.packageName}.Launcher")) !=
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED

@Composable
private fun SwitchRow(title: String, checked: Boolean, native: ColorOs17SettingsComponents?, onChange: (Boolean) -> Unit) {
    if (native != null) {
        Ios27ListRow(title, onClick = { onChange(!checked) }, disclosure = false, trailing = {
            NativeControl(create = { native.createSwitch(checked, onChange).map { it as View } }, update = { view ->
                (view as CompoundButton).apply {
                    setOnCheckedChangeListener(null); isChecked = checked
                    setOnCheckedChangeListener { _, value -> onChange(value) }
                }
            })
        })
    } else {
        val colors = LocalIos27PageColors.current
        val backdrop = rememberCanvasBackdrop { drawRect(colors.group) }
        val dark = isSystemInDarkTheme()
        Ios27ListRow(title, trailing = {
            LiquidToggle(selected = { checked }, onSelect = onChange, backdrop = backdrop, isLightTheme = !dark,
                modifier = Modifier.semantics {
                    contentDescription = title; role = Role.Switch
                    toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                    onClick { onChange(!checked); true }
                })
        })
    }
}

@Composable
private fun RestartAction(restart: () -> Unit, backdrop: Backdrop, dark: Boolean,
                      native: ColorOs17SettingsComponents?, modifier: Modifier) {
    val colors = LocalIos27PageColors.current
    val host = LocalContext.current
    val resources = LocalResources.current
    val latestRestart = rememberUpdatedState(restart)
    if (native != null) {
        NativeControl(modifier.size(62.dp), create = {
            native.createFloatingActionButton("重启作用域", resources.getDrawable(R.drawable.ic_restart, host.theme)) { latestRestart.value() }
        })
        return
    }
    val base = if (dark) androidx.compose.ui.graphics.Color(0xff121212) else androidx.compose.ui.graphics.Color(0xfffafafa)
    LiquidButton(onClick = restart, backdrop = backdrop, surfaceColor = base.copy(alpha = .63f),
        style = LiquidButtonStyle.Default.copy(height = 62.dp, blurRadius = 2.dp, lensRadiusX = 7.dp, lensRadiusY = 7.dp),
        modifier = modifier.size(62.dp).semantics { contentDescription = "重启作用域" }, contentPadding = PaddingValues(0.dp)) {
        Icon(painterResource(R.drawable.ic_restart), null, Modifier.size(24.dp), tint = colors.label)
    }
}

@Composable
private fun NativeControl(modifier: Modifier = Modifier, create: () -> Result<View>, update: (View) -> Unit = {}) {
    AndroidView(modifier = modifier, factory = { host ->
        FrameLayout(host).apply {
            clipChildren = false; clipToPadding = false
            create().onSuccess { view ->
                tag = view
                addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.CENTER))
            }.onFailure { error -> addView(TextView(host).apply { text = "原组件不可用：${ColorOs17SettingsComponents.failure(error)}" }) }
        }
    }, update = { root -> (root.tag as? View)?.let { view ->
        runCatching { update(view) }.onFailure { error ->
            root.tag = null; root.removeAllViews()
            root.addView(TextView(root.context).apply { text = "原组件不可用：${ColorOs17SettingsComponents.failure(error)}" })
        }
    } }, onRelease = { it.removeAllViews() })
}

