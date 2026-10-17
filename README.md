# 微信输入法 ColorOS 17 材质模块

新增 **直接修改微信输入法 APK 的独立版**：当前修正版为 `dist/WeType-4.0.0-ColorOSMaterial-1.0.0-direct-r2.apk`，针对表情、键盘选择和定制工具栏将绘制接入改为与模块一致的实际 `drawChild` 回调。无需启用 LSPosed 模块，构建及安装差异见 [direct/README.md](direct/README.md)。该 APK 已重新签名；当前仅有构建、静态和主机 JVM 验证，尚未实机验收。以下安装步骤仍对应原有模块版。

参照本机的小布输入法 `1.8.33.17-mkt` APK，给微信输入法 `3.5.5 / 4.0.0` 的键盘面板接入 ColorOS 17 系统材质。模块包名为 `dev.coloros.wetype.material`，作用域仅为 `com.tencent.wetype`。

## 从仓库构建

需要 JDK 21、Android SDK 37.0 和 Python 3。配置 `JAVA_HOME`，并在本地 `local.properties` 写入自己的 `sdk.dir`（该文件不提交）。

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:lintDebug
```

模块 APK 输出至 `app/build/outputs/apk/`。当前发布构建沿用本地 Android debug 证书，正式分发时请自行配置并妥善保存签名。

仓库包含 `app/`、libxposed API 102 编译接口 `api-102/`、设置页使用的 `liquidglass/` 源码快照，以及 `direct/` 和 `tools/` 中的直接改包工具。原版微信输入法 APK、已构建 APK、本机配置、设备截图/日志和构建缓存不随源码上传。直接改包需要自行提供匹配 SHA-256 的原版输入包，见 [直接改包说明](direct/README.md)。

## 安装

1. 安装 `dist/WeTypeColorOSMaterial-1.0.0-release.apk`。
2. 在支持 **libxposed API 102** 的 LSPosed 中启用模块，勾选微信输入法。
3. 重启微信输入法进程，重新打开键盘。模块无需系统框架或 SystemUI 作用域。

要求 ColorOS 17、系统允许跨窗口模糊、键盘窗口硬件加速。API 102 是最低要求；采用旧版 API 100/101 的框架无法加载该模块。安装包目前使用 Android debug 证书签名，适合本地测试。

## 实现

### 模块设置页共享 UI 框架（1.4.2）

设置页依赖仓库内的 `liquidglass/` 源码快照，通过相对路径构建，无需另一个本机工程。使用原有的 `Ios27PageTheme / Ios27ListSection / Ios27ListRows`、`LiquidToggle`、`LiquidButton` 和顶部渐进模糊；玻璃渲染源码保持原样。玻璃控件与页面采样层为独立兄弟节点，避免录制自身。只保留设置页，删除首页、预览页、旧预览 Activity、Tab 栏及对应图标资源；右下角单独保留“重启作用域”按钮。

“液态玻璃”是模块设置页的持久外观选项。关闭后通过同库的 `ColorOs17SettingsComponents` 加载已安装系统设置中的 COUI 开关和浮动按钮；组件不可用时显示明确原因并提供返回液态玻璃的入口。重启按钮保留 62dp 玻璃样式。设置页显示 API 102 服务返回的作用域状态，不将作用域已配置等同于输入法进程已加载；桌面入口使用独立 Activity alias，可隐藏后从 LSPosed 模块设置重新进入。重启按钮仅重启 `com.tencent.wetype:hld`，需要 Root；不涉及 SystemUI 或系统重启。

本轮按用户要求仅编译交付，不继续真机或额外验证。构建机需保留上述共享库路径。输入法 Hook 保留 1.3.20 的材质实现。

1.4.2 补齐 soundbar 的页面密度规则：以安全区域较短边除以 402dp，将玻璃界面的缩放限制为 0.85—1，统一通过 `Ios27AndroidDesignBehavior` 应用到字体、列表、间距与独立重启按钮；保留系统字体缩放和最小触摸区域。COUI 原生模式缩放为 1。顶部安全区先按系统密度换算为像素，再转为页面 dp，避免缩放导致状态栏留白错误；渐进模糊也使用页面密度的 30dp 滚动距离。

### 悬浮材质恢复和缩放弧线（1.3.16—1.3.20）

微信调整边框图片包含不透明中心与大块绿色角区。材质启用时滤掉中性实色，并根据实际内容面板边界限制四角绘制；绿色标记收为 2dp、圆端点的四段圆弧，保留微信原来的缩放触摸区域及显示时机。面板移动与尺寸变化分别检测，并在最后一次遍历后延迟提交一次原生材质参数，避免拖动结束时只剩透明底；关闭时取消该回调。

定制工具栏沿用两行四列分页，根据实际行高同步缩小图标、内部 padding、文字和图文间隔，横屏不再裁掉图标和标题。此前已在 1.3.17 检查横屏前两页，在 1.3.20 检查悬浮拖动、缩放及绿色弧线；这不代表后续 1.4.0 设置页已完成真机验收。

### 悬浮拖动后的调整预览（1.3.15）

拖动结束进入微信原生调整状态时，4.0.0 会创建另一个 `float.e` PopupWindow，显示通过 `float.f$g.g()` 的软件 Canvas 生成的截图。模块原先跳过没有实时键盘容器的截图预览；软件绘制也会回退原按键外观，`Z()/l0()` 又将截图强制设为不透明，所以该页恢复成纯色。

原生材质可用时，定向接管这一个截图生成方法，通过公开的 `RenderNode / HardwareRenderer / ImageReader` 生成透明硬件截图；临时排除截图根的面板背景并在同一次调用中恢复，按键仍经过硬件材质路径。缩放预览使用自己的 ColorOS 材质实例，恢复由模块创建截图的 alpha 标记，只隐藏普通背景图；微信原有调整边框、拖动与缩放事件继续保留。多个弹窗分别释放，截图复用、隐藏和重新显示均检查所有权；截图或材质失败时保留原预览。

未采用受目标 SDK 限制的隐藏 `createHardwareBitmap` 接口（参见 [Android HWUI 源码](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/HardwareRenderer.java)），也没有修改微信原 APK。`tools/test_floating_preview.py` 的 18 项主机检查验证截图选择、资源释放、背景所有权、复用、隐藏恢复和失败回退；真实 GPU 渲染与拖动手感仍需解锁手机后验收。

### 键盘选择卡片间距（1.3.14）

键盘选择项目的 View 按权重紧邻排布，1.3.13 的材质直接铺满整个项目，导致左右卡片挨在一起。本版只将每项圆角矩形的绘制范围左右各内缩 4dp，相邻两项形成 8dp 间距；极窄项目限制内缩量，避免绘制范围退化。图标、文字、点击区域、选中反馈和布局尺寸保持原样，外层大底板不内缩。已完成构建、Lint、静态及签名核验，实机视觉效果尚未验收。

### 表情分类精简、设置栏键盘选择入口（1.3.13）

去掉表情页底部 `emoji_type_root` 的额外 NEW_G2 圆角矩形，保留内部 `emoji_item_container_rl` 的原圆形选中提示、图标及点击区域；返回和删除键不受影响。

此前扫描只将 S30 视为键盘选择页入口，遗漏设置栏在 `S10SettingsKeyboard` 下动态添加的独立 `S10SettingKeyboardTypeView`。现在两种入口都处理底层皮肤、网格底板、项目卡与图标背景；显示和隐藏时同步刷新原生材质及该页 Tint，关闭后恢复普通键盘参数。继续使用现有绘制遮挡规则避免旧设置网格从透明面板后透出，不改页面的点击、选择、尺寸或动画。

本次直接核对所提供 4.0.0 APK 的二进制 XML：真实布局名为 `dz.xml`（键盘类型）、`eo.xml`（S30）、`e4.xml`（表情），更正下文历史说明中的旧版文件名；未依赖这些混淆文件名进行运行时匹配。构建、Lint、DEX/XML 和签名核验完成后交付测试包；当前无 ADB 设备，实机视觉效果仍需验收。

### 键盘选择、表情与定制工具栏（1.3.9—1.3.12，迭代适配）

根据 4.0.0 的真实布局与 View 类接入三个输入法内页面：键盘选择 `S30KeyboardChooseKeyboard`（`em.xml` 内含 `dx.xml`）、表情 `S11EmojiKeyboard`（`e2.xml`）、定制工具栏 `S10SettingCustomToolbarView`（`dt.xml`）。新增 `AuxiliaryPagesMaterial`，只处理输入法根 View 下这些页面；独立的设置 Activity、文字/表情图片绘制、数据和点击事件不接管。

原生面板材质就绪时，把三个页面的全屏旧皮肤底层让出；键盘选择和定制工具栏的网格底卡、动态项目卡、图标底板使用既有小布 `NEW_G2`，保留选择状态、原圆角、按压及微信输入法的增删、重排和页码行为。表情页保持内容 RecyclerView 与 emoji 绘制不变，只对已核实的分类项容器做局部表面处理，避免把整个分类 RecyclerView 误画成大卡片。

页面 `f0/g0`、定制工具栏 `setVisibility`、全局布局及每帧轻量所有权校验处理切换和回收；原背景与 Tint 在退出和原生材质不可用时恢复。仅当原生材质准备完成时，把 `KeyboardChoose`/`Emoji` 加入微信磨砂控制器的支持类型，未扩大到语音或手写等页面。着色器失败回退微信原背景，三页不依赖整个模块重新加载。源码已编译、历史材质生命周期回归已通过；由于 ADB 未连接，**三页尚未实机视觉和交互验收**。1.3.9 的实机截图反馈指出表情页有原设置项透出、定制工具栏页出现双层卡片及文字重影，键盘选择网格底板偏实白。原因是 S10 页面内层的旧设置网格仍保留在编辑层下方，而主键盘容器也会保留其他页面的绘制分支。1.3.10 在绘制阶段按当前最上层面板计算祖先路径，只跳过被覆盖、确实相交的兄弟 View 的 `drawChild`，并在表情模式下排除键盘容器的其他槽位。保留原 `visibility`、布局、点击区域、动画状态，退出面板立即恢复对应父容器显示列表。代码还限制两种网格大底板的白色不透明度、加入一次性的遮挡绘制日志 `auxiliary covered old-page drawing omitted`。

1.3.10 测试包为 `dist/WeTypeColorOSMaterial-1.3.10-debug.apk`；`dist/WeTypeColorOSMaterial-1.3.8.apk` 保留作为先前验收的稳定版本。1.3.10 已完成 Debug 编译、Lint 和 12 项历史生命周期测试。

**1.3.11（针对第二轮真机截图）**：键盘选择页仍近乎纯色，原因之一是普通面板的浅色 Oplus Tint 为约 80% 不透明度。本版保留原生模糊和 NEW_G2 边缘效果，仅在键盘选择页显示时将主输入窗口浅色 Tint 降至约 50%（暗色相应降低），退出后恢复普通键盘参数，悬浮键盘不受影响。

表情页底部黑边与类别栏缺失，收窄 `drawChild` 抑制范围：**只抑制当前前景面板之前且被完整覆盖的兄弟 View**，不再跳过它之后的键盘底栏、表情分类及其他层；避免只相交了一部分的节点被整个丢弃。仍不改原 View 的可见性、位置、点击与测量属性。

定制工具栏每页应有 **2 行 × 4 列 = 8 项**。微信输入法 4.0.0 的 `S10SettingCustomToolbarView.getItemWidth()` 在多页时采用除数 4.38，会让第五列露出一部分。本版根据 `custom_tool_setting_base_grid_view_container` 实际测量宽度，减去 `custom_tool_setting_base_grid_view_inner_container` 左右原生 padding 后每列分配四分之一；重排现有两行的 LayoutParams，并调用已验证的 `S10CustomToolbarHorizontalScrollView.a(IIII)` 同步分页滑动距离及最后一页定位。不改项目列表、勾选状态、按钮监听或微信原有分页数量计算。布局变更后按实际宽度重新校正，无硬编码屏幕尺寸。

**1.3.12（定制工具栏外观精简）**：只将 `S10SettingCustomToolbarItemView`（实际 DEX 类 `view.settingkeyboard.c`）原来额外画的每项 `NEW_G2` 圆角矩形卡片设为透明。保留外层网格大面板与图标后面的圆形底板，以及 2×4 分页、选中/禁用反馈、文字、拖动和点击逻辑。键盘选择页的 `S10SettingItemView` 卡片不受影响。

该轮安装测试包 `dist/WeTypeColorOSMaterial-1.3.12-debug.apk`；`./gradlew.bat :app:stageDebugApk :app:lintDebug --offline` 生成/检查当前版本 APK。`python tools/test_material_lifecycle.py` 已通过 14 项 JVM 测试，结果输出到 `evidence/material-lifecycle-validation-1.3.11.json`。由于当前 ADB 没连接设备，**1.3.11 的三页运行时布局和底栏是否完全恢复仍需真机截图验收**。

接入微信输入法自带的 `FrostedKeyboardController`。在原控制器的更新调用期间、且原生材质准备成功时，启用它的磨砂功能开关。原有窗口显示、调整模式、全屏手写、跨窗口模糊状态等条件继续由原控制器处理。

原生后端来自小布键盘的真实调用：

```java
ViewRootManager manager = new ViewRootManager(inputView);
Drawable background = manager.getBackgroundBlurDrawable();
OplusBlurParam params = new OplusBlurParam();
params.setBlurType(2);
params.setMaterialParams(1, new float[]{1f, 1f, 1f, 1f}, rgba);
params.setSmoothCornerType(1);
params.setSmoothCornerWeight(3f);
manager.setBlurParams(params);
manager.setBlurRadius(150);
manager.setCornerRadius(topLeftPx, topRightPx, 0f, 0f);
inputView.setBackground(background);
```

材质的浅色 `#CCE1E1E1`、深色 `#990D0D0D` 和顶部 28dp 圆角来自小布资源和键盘代理。原控制器负责键盘内容背景透明化，模块移除叠加在系统材质上的原磨砂渐变色。模块关闭原有普通窗口模糊半径，让 Oplus 原生背景 Drawable 负责渲染。

### 剪贴板与常用语（1.3.6）

剪贴板是独立的 `S15CustomPhraseAndClipboardKeyboard`，原 `keyboard.u.b(int)` 没有将其列入磨砂内容支持范围。模块只在原生材质准备完成或已启用时，将该类型及 `S29ClipboardCleanRecordKeyboard` 加入内容透明化路径，保留原控制器其余窗口条件。

`ClipboardMaterial` 接入这两个面板的真实 `f0()/g0()` 显示和隐藏回调，直接跟踪当前面板，补充根 View 扫描。原生模糊可用时清除面板和侧栏祖先容器的独立实色背景；内容卡片、折叠叠层、关键内容标签、返回/设置/清空/展开按钮、添加/拆词/删除等操作使用已有 `NEW_G2` 材质与按压反馈。常用语共用的卡片、编辑/置顶/删除按钮一起处理。卡片沿用已识别的原圆角，折叠叠层保留原透明度；原边框/投影暂时关闭，防止和 Shader 重叠。列表、文字、图片、点击处理、内容数据及操作权限仍由微信输入法管理。

中央切换标签的选中底色由 `TabContainerView.onDraw(Canvas)` 自绘，因此另行接管该层，并保留实际选中区域、圆角和动画位置。绑定已核对的 DEX 字段 `f:RectF / g:float / h:Paint / k:boolean`，其中 `k` 是覆盖模式，不能误用动画状态字段 `i`。

1.3.5 补充卡片的八值圆角数组识别，并展开 InsetDrawable 后读取原圆角；只读取 `GradientDrawable.getCornerRadius()` 会将采用 `setCornerRadii()` 的卡片误判成零圆角。圆角 getter 与边框/阴影 getter 分开解析，后两者缺失时仍保留圆角。切换条改为独立背景角色，保留垂直 padding 内缩，左右各收进 6dp；选中材质限制在内缩后的条内，避免背景铺满原 View 的高度。文字、点击区域与实际选中位置不变。

1.3.6 将剪贴板和常用语条目右侧的“更多”从整槽位圆形改为居中的 28dp 圆角方形，圆角为 9dp，普通状态降低底色浓度，按压仍使用原材质反馈。原图标、点击区域和菜单行为保留，顶部工具栏按钮继续使用圆形。已安装此版；用户反馈“可以了”，界面调整在此停止。

### 面板透明状态恢复（1.3.7）

用户反馈面板偶尔完全透明。代码检查发现，旧版仅在主题颜色变化时提交 Oplus 材质参数，布局刷新没有恢复可能被渲染器清空的模糊半径；窗口透明背景也没有随材质释放而恢复。1.3.7 缓存参数对象，但每次启用和布局刷新都重新提交材质参数、圆角和 150 模糊半径。渲染根节点更换时重新创建 ViewRootManager，窗口显示后的首帧完成后再进行一次重绑，避免只在 Surface 尚未就绪时配置。绘制前仅检查背景所有权、渲染根与窗口可见性；背景被替换但未发生布局时也会补绑，状态正常时不重新配置原生参数。

窗口隐藏、View 脱离、硬件加速或系统模糊不可用时释放材质，恢复仍由模块持有的根背景和窗口背景。原生配置失败时，通过原控制器强制关闭磨砂内容一次，恢复微信自身的实色背景；随后显示周期仍可重新尝试启用。释放不覆盖其他组件后来设置的新窗口背景。首帧重绑是一次性回调，隐藏、销毁和换根时移除；没有按帧重复配置材质或进行无限重试。

`python tools/test_material_lifecycle.py` 将实际 `ColorOsMaterial.java` 与可模拟状态丢失的 OEM 边界在主机 JVM 编译运行。10 项场景覆盖渲染参数清空、双层背景恢复、反复显示/隐藏、系统关闭模糊、渲染根更换、原生异常、隐藏窗口、新背景所有权，以及未触发布局时根背景/窗口背景替换后的修复。结果见 `evidence/material-lifecycle-validation-1.3.7.json`。这验证状态管理，不证明 Android 真机渲染正常；此次 ADB 已断开，1.3.7 尚未安装和复测偶发透明。

### 悬浮模式（1.3.8）

微信悬浮模式的主输入法窗口只承载底部占位视图，实际键盘位于独立 PopupWindow。`FloatingMaterial` 接入真实 `float.e.onAttachedToWindow/onDetachedFromWindow`，通过其公开的 `getRootView()/getContent()` 找到圆角面板和键盘内容；包含实时键盘容器时才启用，缩放预览中的截图副本继续由微信管理。4.0.0 的方法签名已从 DEX 核对，构造时普通背景与编辑背景的子 View 顺序已在 `evidence/wetype4-floating-bytecode.txt` 核实。

弹窗拥有独立的 ColorOS 材质实例，不要求主输入法 Window 开启磨砂，也不会铺满主窗口。保留原面板圆角，并给底部两角使用相同半径；前景 NEW_G2 高光绘制完整底边，停靠键盘仍保留原底边裁切。原面板的实色内容背景、普通背景图、旧边框及投影暂时让出；工具栏使用同一套按钮材质，按键和剪贴板的启用状态兼容独立弹窗。深浅色更新、首帧重绑、窗口隐藏与 Popup 脱离分别处理。拖动、缩放、键盘大小、点击区域和输入处理保持原实现。缩放编辑遮罩继续由微信控制。

弹窗关闭时恢复由它管理的背景及装饰，已转移回主窗口的键盘视图不会被旧弹窗覆盖皮肤。12 项主机生命周期回归包括独立弹窗启用、四角配置、主窗口释放不影响弹窗、弹窗渲染重置和原背景恢复；结果见 `evidence/material-lifecycle-validation-1.3.8.json`。`tools/render_panel_preview.py --floating` 验证深浅色四角、高光底边及透明中心，预览位于 `evidence/floating-panel-edge-preview.png`。这些是离线检查，1.3.8 未完成真机悬浮模式的拖动、缩放与视觉验收。

微信输入法以覆盖层显示剪贴板，主键盘与候选工具栏继续保留在更早的兄弟分支中。1.3.2 仅清除覆盖层背景，导致主键盘从半透明卡片后透出。1.3.3 只处理两个侧栏槽位，遗漏当前设备实际使用的 `input_upper_view`。1.3.4 在原生材质启用、实际剪贴板可见时，限定 `keyboard_container_rl` 中 `input_upper_view / input_left_view / input_left_view2` 之前的兄弟分支，跳过它们的 `ViewGroup.drawChild` 绘制。保留原 visibility、alpha、布局尺寸、动画和输入状态；进入/退出时使父容器重新记录显示列表，退出剪贴板、关闭材质或解绑根 View 后恢复绘制。当前覆盖层及其后的分支继续绘制。4.0.0 二进制布局证据为 `evidence/wetype4-root-layout.txt`；PLQ110 运行时层级在诊断版日志中确认 S15 位于 `input_upper_view`，根 View 外还有 ConstraintLayout 包装。最终版本已移除批量层级诊断日志。

布局与绘制前检查覆盖异步加载、展开和 View 回收，皮肤切换更新底色；退出剪贴板后恢复其祖先容器背景，收起窗口时恢复最新原背景、Tint、边框及投影。卡片材质不再依赖面板模糊已启用；软件 Canvas 或 Shader 失败时使用半透明底色与渐变边缘高光，面板模糊不可用时保留面板原背景。软件路径不具备原生模糊或完整 `NEW_G2` 内阴影。没有读取或记录剪贴板内容。独立设置 Activity、浮层详情编辑器及其他键盘类型未统一改造。

### 微信输入法 4.0.0 兼容（1.3.0）

用户提供的 4.0.0 APK SHA-256 为 `f7f21c78d293c0d144f52f461b9c75b2d631d23f2a37ee1107eb53b35d86df4c`，与此次连接设备上的已安装 APK 一致。虽然两个版本的 versionCode 都是 57206，但混淆符号不同，因此不能仅靠版本号选择 Hook。

| 路径 | 3.5.5 | 提供的 4.0.0 |
|---|---|---|
| 自绘键盘类 | `keyboard.selfdraw.n` | `keyboard.selfdraw.o` |
| T9 getter | `a2()` | `b2()` |
| 主题工具 | `utils.n1` | `utils.m1` |
| 原控制器的磨砂功能开关 | `feature.a.u2()` | `feature.a.x2()` |

通过 `drawmethod.a.c()` 的真实返回类型选择已核验映射，同时绑定 `onCreateInputView` 和框架 `setInputView(View)` 路径。工具栏容器 ID、皮肤引擎和其余按键 getter 已核对。未知键盘类型会停止安装 Hook，不猜测混淆方法。

### 面板边缘高光（1.2.1）

小布的背景模糊与边缘高光是两个独立绘制层。`C0879v` 在键盘 View 的前景安装 `C0880w` 包装的 `COUIShadowEdgeDrawable`；仅调用 `ViewRootManager` 不会生成这层高光。

`PanelEdgeMaterial` 在原生面板启用时安装同一原版 `NEW_G2` Shader 的独立前景层，使用 `EDGE_STYLE_2`、weight=3、4dp 线宽、浅色 lineAlpha=1 / 深色=0.4、`BlendMode.PLUS`；关闭内阴影，不填充面板中心。顶部圆角与原生面板的 28dp 对齐，底部圆角为零，并按 `C0880w` 裁去最后 4dp，避免停靠键盘出现多余底边。原前景存在时与其叠加；尺寸、深浅色、前景替换随原面板生命周期更新，收起/脱离/关闭材质时恢复原前景。

`tools/render_panel_preview.py` 离线渲染深浅色前后对比，并核验顶部高光非零、中心透明、底边裁切及顶部圆角区域。预览位于 `evidence/panel-edge-preview.png`；这是固定底色上的桌面 Shader 验证，真机前景绘制和窗口模糊仍需实际检查。日志 `panel edge attached` 与 `panel edge drawn on hardware canvas` 分别表示安装和进入硬件绘制路径。

### 系统主题与工具栏（1.2.0）

深浅色读取微信输入法的 `e7.u.j()` 未包装系统 Resources，调用实际皮肤引擎 `skin.e.c().j(1/2)` 切换白色/深色皮肤。原引擎通知其 View、候选栏、图标和按键观察者，避免仅修改底色。打开键盘、系统配置改变及 `n1.E2` 外观回调都会重新同步。**模块启用时系统深浅色优先于微信输入法手动外观**；模块不写入手动外观偏好。

回车等强调按键从已安装的 `com.oplus.keyboard` 读取 `Theme.COUI.Main.Light/Dark` 的 `couiColorContainerTheme`。`XiaobuThemeOverlay` 根据 ColorOS 配置中的 `mMaterialColor` 和原 COUI 数组选择主题叠加样式，参照小布 `InnerThemeWorker.J` 与 `COUIThemeOverlay.resolveThemeStyle`，支持系统主题色/壁纸配色路径。配置改变和重新显示键盘会刷新取色缓存。小布未安装、资源缺失或反射失败时保留微信输入法原强调色。具体颜色由设备当前 COUI 配置和小布资源决定，不固定为截图绿色。

工具栏按实际自定义按钮 ID 覆盖：普通按钮容器 `2131296696`、最近使用按钮 `2131296695`、编辑完成按钮 `2131296687`，对应图标 ID 为 `2131296698/6699/6688`。折叠箭头的内部容器属于普通按钮路径。只扫描输入法根 View 下这些容器，不影响输入法设置 Activity。使用小布 `C0882y` 的圆形 `NEW_G2`、`EDGE_STYLE_1 / SHADOW_STYLE_1`、weight=2.3；深浅色底色及按压反馈与按键同步，图标分别使用灰色/浅色。长容器中的背景圆居中于图标，保留旁边文字、原 padding、布局和点击处理。

布局和绘制前回调补上新建或回收的工具栏按钮，并重新接管原皮肤引擎刷新的背景和图标颜色。窗口隐藏、View 脱离窗口、材质关闭和服务销毁时恢复最近的原背景及图标色。工具栏 Shader 失败会独立恢复原工具栏。工具栏功能、图标形状和排布仍来自微信输入法，因此不会变成小布的整套 UI。

### 按键材质与深色模式（1.1.0）

微信输入法用 Canvas 自绘按键。模块 Hook `drawmethod.c.a(Canvas, ImeButton)`，在原文字/图标绘制前画小布按键材质，并在这一次绘制期间抑制该按键的原底色、阴影、边框和按压蒙层。调用结束后立即清除范围标记，输入、布局、文字和图标仍由微信输入法绘制。普通绘制、图标绘制和 `NormalAlphaDrawer` 的 Bitmap 文字路径均经过同一入口；保留后者的渐隐透明度。

按键边缘和内阴影使用从小布 APK 提取的原版 `ShadowEdge.NEW_G2` AGSL，保留 `AbstractC0876s` 的 `EDGE_STYLE_2 / SHADOW_STYLE_2`、8dp 圆角、weight=2 和深浅色强度。通过 Android `RuntimeShader` 在硬件 Canvas 上绘制，方式与小布的 Canvas 按键一致；面板仍由 ColorOS 原生窗口材质负责。

| 状态 | 浅色 | 深色 |
|---|---|---|
| 普通按键底色 | `#8AFFFFFF` | `#26FFFFFF` |
| 按下底色 | `#85BBBEC2` | `#3DFFFFFF` |
| 内阴影强度 | fadeAlpha=0.4 | fadeAlpha=0.2 |
| 边缘强度 | lineAlpha=1.0 | lineAlpha=0.2 |

语义强调色（例如回车）使用上述 COUI 主题色并改为半透明材质；普通文字和图标同步深浅中性色，保留原透明度及非中性强调色。着色器失败或软件 Canvas 会恢复原按键绘制。模块说明页也提供 `values-night` 深色主题。

`tools/extract_xiaobu_shader.py` 根据原 `ShadowEdge.buildSourceInternal(NEW_G2)` 的七个字符串段生成原版 shader；来源及每段哈希记录在 `evidence/button-shader-provenance.json`。`evidence/key-material-preview.png` 是桌面 Skia 对原 AGSL 的 10 个按键/工具栏深浅色及按压样例；强调色为示例绿色、背景为固定底色，不能作为 Android 真机、动态主题色或窗口模糊验收。

原生 API 通过反射调用，不将 Oplus 桩类放入 APK。材质创建失败时恢复原背景；窗口隐藏、View 脱离窗口和服务销毁时释放材质。正常键盘、数字/符号键盘和原控制器支持的设置键盘采用此路径。剪贴板、表情、语音、全屏手写及调整模式仍按微信输入法原控制器的条件处理；没有声称这些独立面板已全部适配。

## API 102 源码

使用指定的 [JingMatrix/libxposed-api](https://github.com/JingMatrix/libxposed-api)，固定提交为 `39cac0845771547c9c67a3e3ce255af110a54a0e`。`api-102/src/main/java` 保存该提交的原始 API Java 源码，模块以 `compileOnly(project(":api-102"))` 编译依赖，不把 API 实现打入 APK。采用无参模块入口、`onPackageReady` 和 `hook(...).intercept(...)`；没有旧 XposedBridge 接口。

`api-102` 是精简的本地编译外壳，不需要重新克隆证据目录。编译用注解包 `io.github.libxposed:annotation:1.0.0` 来自 Maven Central，存放于 `api-102/libs`。源码与依赖来源、校验值见 `evidence/source-provenance.json`。

## 构建和核验

1.0.0 新增玻璃键盘桌面图标：默认浅玉色、夜间烟青色，前景和背景均随系统深浅色资源切换；Android 13+ 提供独立单色层用于启动器主题图标。图标由系统自适应遮罩裁切，应用入口和桌面 alias 共用同一图标。启动器的主题着色与夜间缓存刷新行为需在设备上确认。生成提示词及素材位置见 [evidence/launcher-icon/README.md](evidence/launcher-icon/README.md)。

模块 release 已启用 R8 代码裁剪、优化与混淆，以及资源压缩；保留 LSPosed 从 `META-INF/xposed/java_init.list` 加载的入口、无参构造与生命周期回调。运行 `.\gradlew.bat :app:stageReleaseApk` 可生成当前版本的 `dist/WeTypeColorOSMaterial-1.0.0-release.apk`，混淆映射和裁剪报告保存在 `app/build/outputs/mapping/release/`。release 沿用本机 Android debug 证书签名。

Windows 使用 JDK 21 与 Android SDK 37，编辑 `local.properties` 中的 SDK 路径后运行：

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:lintDebug
python tools/verify_apks.py --wetype '<原版微信输入法_4.0.0.apk>' --module dist/WeTypeColorOSMaterial-1.3.15-debug.apk --output evidence/static-verification-1.3.15-wetype4.json
python tools/test_material_lifecycle.py
```

`tools/verify_apks.py` 直接解析 DEX，检查真实类名、方法定义和签名，避免把 JADX 从 Kotlin 元数据恢复的别名当成 Hook 名称。它还验证剪贴板标签字段定义和类型唯一性、二进制 XML 中剪贴板覆盖层与主键盘的层级顺序、模块入口、API 102 元数据、作用域，以及 API/旧 XposedBridge 类未被打包。

如需重新核对小布原生 API 引用，可加 `--xiaobu '<原始小布 APK 路径>'`。原下载路径当前已不存在，因此 1.3.13 重新检查微信 APK、页面布局、模块及着色器哈希；未重新检查小布 APK 内的原生 API 引用，报告中明确标记该项未重跑。

反编译证据位于 `evidence/xiaobu`、`evidence/wetype`。小布窗口材质参数位于 `com/bumptech/glide/d.java:r()`，半径位于 `com/heytap/common/bean/d.java:i()`，材质颜色位于 `res/values/colors.xml`。小布代理存在 JADX 还原失败，补充输出 `evidence/xiaobu-blur-delegate.java` 和 `evidence/xiaobu-container-r.java` 仅用于分析，未将还原失败的代码复制进模块。

当前验证包括编译、Lint、APK 签名及 4.0.0 的 DEX 方法/字段、覆盖层布局和小布原生 API 静态核验。1.3.3 真机日志确认模块已正确加载，并复现剪贴板后露出主键盘；运行时层级证实遗漏上层槽位。PLQ110 / ColorOS V17.0.0 上 1.3.4 截图确认剪贴板后不再露出主键盘，且日志记录 `clipboard covered keyboard drawing omitted`。1.3.5 的逐项截图曾受锁屏阻碍；随后 1.3.6 已安装，用户明确反馈“可以了”，因此保留当前效果并停止进一步界面调整。本轮未额外完成深色模式和退出恢复的逐项截图检查，系统夜间模式保持 `custom_schedule`。先前核验范围见 `evidence/runtime-verification-1.3.5.json`，最终静态核验见 `evidence/static-verification-1.3.6-wetype4.json`。`backend=coloros17-system` 日志只表示调用成功，不能作为视觉验收。测试页面之外的设备界面不作为验收依据。

连接设备后可查看：

```powershell
adb logcat -s WeTypeColorOSMaterial
```

检查普通键盘、数字与符号切换、系统深浅色、COUI 主题色、工具栏按钮按压/增删/折叠、收起再打开键盘、旋转屏幕，以及系统关闭材质/模糊后的恢复表现。模块未提供热重载，更新 APK 后应重启微信输入法进程。
