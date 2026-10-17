package dev.liquidglass.coloros17

/** Categories mirror the existing phone example library; implementations live in Settings. */
enum class ColorOs17ComponentKind { View, Preference, Dialog, Sheet, Menu }

data class ColorOs17ComponentSpec(
    val key: String,
    val category: String,
    val title: String,
    val className: String,
    val kind: ColorOs17ComponentKind = ColorOs17ComponentKind.View,
) {
    val sourcePath: String get() = className.replace('.', '/') + ".java"
}

object ColorOs17ComponentCatalog {
    private fun item(id: String, category: String, title: String, type: String,
        kind: ColorOs17ComponentKind = ColorOs17ComponentKind.View) =
        ColorOs17ComponentSpec("coloros17-$id", "ColorOS 17 / $category", title,
            "com.coui.appcompat.$type", kind)

    val entries: List<ColorOs17ComponentSpec> = listOf(
        item("toolbar", "顶部工具栏", "COUIToolbar", "toolbar.COUIToolbar"),
        item("floating-toolbar", "底部工具栏", "浮动工具栏", "bottomfloatingtoolbar.COUIBottomFloatingToolbar"),
        item("navigation", "标签栏", "底部导航", "bottomnavigation.COUINavigationView"),
        item("floating-navigation", "标签栏", "浮动底部导航", "bottomfloatingnavigationbar.COUIBottomFloatingNavigationBar"),
        item("tabs", "标签栏", "标签页", "tablayout.COUITabLayout"),
        item("segment", "标签栏", "分段选择", "segmentbutton.COUISegmentButtonLayout"),
        item("alert", "提示弹窗", "确认弹窗", "dialog.COUIAlertDialogBuilder", ColorOs17ComponentKind.Dialog),
        item("sheet", "模态页面", "底部面板", "panel.COUIBottomSheetDialog", ColorOs17ComponentKind.Sheet),
        item("menu", "菜单", "锚点菜单", "poplist.COUIPopupMenu", ColorOs17ComponentKind.Menu),
        item("button", "按钮", "普通按钮", "button.COUIButton"),
        item("loading-button", "按钮", "加载按钮", "button.COUILoadingButton"),
        item("switch", "开关", "COUISwitch", "couiswitch.COUISwitch"),
        item("checkbox", "开关", "复选框", "checkbox.COUICheckBox"),
        item("slider", "滑块", "连续滑块", "seekbar.COUISeekBar"),
        item("section-slider", "滑块", "分段滑块", "seekbar.COUISectionSeekBar"),
        item("intent-slider", "滑块", "意图滑块", "seekbar.COUIIntentSeekBar"),
        item("stepper", "步进器", "COUIStepperView", "stepper.COUIStepperView"),
        item("edit", "文本框", "快捷清除输入框", "edittext.COUIEditText"),
        item("input", "文本框", "带容器的输入框", "edittext.COUIInputView"),
        item("search", "文本框", "搜索框", "searchview.COUISearchBar"),
        item("date", "日期与时间", "日期选择", "picker.COUIDatePicker"),
        item("time", "日期与时间", "时间选择", "picker.COUITimePicker"),
        item("number", "日期与时间", "数字选择", "picker.COUINumberPicker"),
        item("preference", "列表", "标准列表项", "preference.COUIPreference", ColorOs17ComponentKind.Preference),
        item("switch-preference", "列表", "开关列表项", "preference.COUISwitchPreference", ColorOs17ComponentKind.Preference),
        item("jump-preference", "列表", "跳转列表项", "preference.COUIJumpPreference", ColorOs17ComponentKind.Preference),
        item("progress", "进度与反馈", "水平进度", "progressbar.COUIHorizontalProgressBar"),
        item("circle-progress", "进度与反馈", "圆形进度", "progressbar.COUICircularProgressBar"),
        item("loading", "进度与反馈", "加载指示", "progressbar.COUILoadingView"),
    )

    fun find(key: String): ColorOs17ComponentSpec? = entries.firstOrNull { it.key == key }
}
