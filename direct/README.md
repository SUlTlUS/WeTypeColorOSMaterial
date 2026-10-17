# 微信输入法 4.0.0 直接改包版

将当前模块的 ColorOS 17 材质代码编入微信输入法 APK，无需启用 LSPosed 模块。输出包名仍为 `com.tencent.wetype`，采用本机 Android debug 证书重新签名。

修正版：[`../dist/WeType-4.0.0-ColorOSMaterial-1.0.0-direct-r2.apk`](../dist/WeType-4.0.0-ColorOSMaterial-1.0.0-direct-r2.apk)。模块当前版本号已调整为 1.0.0，直接版另用 `r2` 标识绘制接入修正；旧的 1.3.14、1.3.20 成品保留。

### r2：表情、键盘选择、定制工具栏

根据三页的异常反馈，修正直接版与 LSP 模块的接入差异：去掉绘制前用空 `clipBounds` 模拟 `drawChild` 的方式，改为在真实绘制回调中运行同一套模块判定。仅在原版 `r/a_/dg.xml` 和 `r/a_/du.xml` 的 9 个 FrameLayout 标签上使用其子类 `MaterialFrameLayout`，保留原构造参数、布局属性、ID、层级和触摸处理。其他布局、Manifest、资源表及 native 库保持原始内容。

为 `S10SettingKeyboardTypeView` 增加调用原父实现的 `setVisibility` 转发入口，按模块的实际调用时机处理显示和关闭；取消绘制前的显隐轮询。按压状态观察继续保留。模块 Java/Kotlin 源码不作改动。

本地分派回归现为 20 项，验证当次绘制抑制、底栏保留、原 Canvas/时间参数、多个回调、同次显示恢复及裁剪所有权等行为；另有悬浮预览 20 项、工具栏尺寸 8 项和材质生命周期 16 项。合计 64 项主机检查，仍需实际设备确认这三页的最终画面。

补充适配：悬浮表情页在独立 PopupWindow 内重新绑定 `AuxiliaryPagesMaterial`，避免沿用停靠键盘窗口的材质激活状态；返回停靠模式时恢复原根。首次开启“排版成图”等 Plus 功能的 `S10SettingPlusModeOpenView` 也加入材质扫描，其 `e2.xml` 包含 `e3.xml` 中的 `plus_mode_bg` 卡片：清除旧大底色、保留原圆角，用 `NEW_G2` 绘制卡片，不修改文字及两个操作按钮。新增 `d/e` 两个方法分派入口。以上修复已通过编译及主机检查，尚待真机逐页视觉验收。

本次同步悬浮调整预览的透明硬件截图、原生材质恢复、调整框圆角标记及横屏工具栏图标/文字尺寸适配。改包脚本自动从模块 Gradle 配置读取版本，并核对入口日志版本；输出文件和验证报告随版本命名。

普通安装不能用此签名覆盖腾讯官方签名版本。自行替换前先导出或同步需要保留的词库、常用语及设置；本构建脚本不会卸载或安装应用，也不会操作设备。

## 实现范围

- 复用原有普通键盘、悬浮键盘、按键、工具栏、剪贴板、表情和键盘选择页材质代码。
- 在原 APK 的 39 个方法中加入本地调用分派，原方法体改名保留；另外增加 `setInputView` 和键盘选择页 `setVisibility` 转发入口，共 41 处方法入口。包括悬浮调整截图入口 `float.f$g.g()`，无需注入原生 Hook 库。
- `MaterialFrameLayout.drawChild` 将原父对象、Canvas、子 View 及绘制时间交给模块回调；被遮挡的旧页返回 false，其余调用原 FrameLayout 实现。页面判断、布局、尺寸和触摸区域使用原实现。
- 通过原输入法服务和页面方法的直接调用初始化材质代码，不添加启动组件或权限；原 Manifest、资源表、词库和 native 库保持原始内容，仅替换两份 IME XML 的容器类标签。
- 继续要求 ColorOS 17 和系统跨窗口模糊可用，原生材质初始化失败时使用微信原背景。

## 构建

需要 JDK 21、本机 Android SDK 37.0 与 Build Tools 37.0.0、Python 3，以及 `.android/debug.keystore`。首次构建从 Maven Central 下载 dexlib2/smali 的固定版本，按 `tooling-lock.json` 校验 SHA-256。脚本支持 `--sdk` 和 `--java-home` 指定安装路径。

```powershell
python tools/build_direct_apk.py --input direct/inputs/WeType-4.0.0-original.apk
python tools/test_direct_runtime.py
python tools/test_floating_preview.py
python tools/test_toolbar_sizing.py
python tools/test_material_lifecycle.py
```

只支持本工程核对过且 SHA-256 为 `f7f21c78d293c0d144f52f461b9c75b2d631d23f2a37ee1107eb53b35d86df4c` 的 4.0.0 输入包，不能把此补丁直接用于其他版本。本次原路径已不存在，已从连接设备只读导出相同哈希的原版包，保存在被 Git 忽略的 `direct/inputs/`；来源记录在 `evidence/direct-apk-input-provenance.json`。

生成过程在 `direct/build/<模块版本>-r2/` 保存可检查的包装方法 smali、改写 DEX 和生成的材质 Java 源码，不重写 `app/src`。编译类目录每次独立创建并自动清理，防止旧类混入；若构建期间模块源码或着色器变化，脚本拒绝生成通过报告。

当前报告为 `evidence/direct-apk-verification-1.0.0-r2.json` 和 `evidence/direct-runtime-validation.json`，前者记录材质源文件与着色器的 SHA-256、两份布局中替换的节点及其 ID。构建检查所有原方法签名仍存在、改写方法保留原方法体、其他 ZIP 条目未改变、布局 ID 与层级未改变、无新增 Xposed 类、v2/v3 签名和 16KB 对齐。历史版本的验证报告保留。

当前未安装到设备。ColorOS 原生渲染、页面切换、悬浮拖动缩放、输入、登录/云同步及应用对重签名的兼容性仍需自行实机验证。

## 工具来源

改包工具使用 [smali/dexlib2 2.5.2](https://github.com/JesusFreke/smali/tree/v2.5.2)（BSD 3-Clause），以及其 Maven Central 依赖。工具 JAR 只在本地构建时使用，不编入输出 APK。输入 APK 及其资源权利归原作者所有。
