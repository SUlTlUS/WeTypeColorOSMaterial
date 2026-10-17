package dev.liquidglass.ios27

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.zeaze.tianyinwallpaper.backdrop.backdrops.rememberCanvasBackdrop
import com.zeaze.tianyinwallpaper.catalog.components.LiquidButton
import com.zeaze.tianyinwallpaper.catalog.components.LiquidSlider
import com.zeaze.tianyinwallpaper.catalog.components.LiquidToggle

/** Inline controls sample an independent solid surface, never the containing page capture. */
@Composable
fun Ios27SliderRow(label: String, value: Float, onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier, showLabel: Boolean = true,
    leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null,
    ticks: Boolean = false,
    onValueChangeFinished: () -> Unit = {}) {
    val colors = LocalIos27PageColors.current
    val backdrop = rememberCanvasBackdrop { drawRect(colors.group) }
    Column(modifier.padding(horizontal = 16.dp, vertical = if (showLabel) 10.dp else 0.dp)) {
        if (showLabel) Text("$label · ${(value * 100).toInt()}%", color = colors.label, style = Ios27Tokens.Type.Footnote)
        Row(Modifier.fillMaxWidth().heightIn(min = Ios27Tokens.RowHeight.dp), verticalAlignment = Alignment.CenterVertically) {
            // Original slider rows sit between two 32pt symbol zones with 12pt gaps.
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Box(Modifier.weight(1f).ios27AndroidProgress(
                label = label, value = value, onFinished = onValueChangeFinished,
                onValueChange = onValueChange,
            ), Alignment.Center) {
                // Reference ticks: five 4pt dots, centers at 18+53.5i pt along a 250pt track,
                // 9pt below the track center, behind the knob. Drawn first so the knob covers them.
                if (ticks) androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                    val y = size.height / 2f + 9.dp.toPx()
                    val radius = 2.dp.toPx()
                    val tickColor = Ios27Tokens.SecondaryLabelLight.copy(alpha = 0.18f)
                    floatArrayOf(18f, 71.5f, 125f, 178.5f, 232f).forEach {
                        drawCircle(tickColor, radius, androidx.compose.ui.geometry.Offset(size.width * it / 250f, y))
                    }
                }
                if (Build.VERSION.SDK_INT >= 33) LiquidSlider(value = { value }, onValueChange = onValueChange,
                    valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop,
                    isLightTheme = colors.page != Ios27Tokens.PageDark, dragWholeTrack = true,
                    onValueChangeFinished = onValueChangeFinished)
                else Slider(value, onValueChange, onValueChangeFinished = onValueChangeFinished)
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
    }
}

@Composable
fun Ios27SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Ios27SwitchRow(label, checked, showStateText = true, onCheckedChange = onCheckedChange)
}

@Composable
fun Ios27SwitchRow(label: String, checked: Boolean, showStateText: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = LocalIos27PageColors.current
    val backdrop = rememberCanvasBackdrop { drawRect(colors.group) }
    Ios27ListRow(label, detail = if (showStateText) { if (checked) "已开启" else "已关闭" } else null, trailing = {
        Box(Modifier.semantics {
            contentDescription = label
            role = Role.Switch
            toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
            onClick { onCheckedChange(!checked); true }
        }, Alignment.Center) {
            if (Build.VERSION.SDK_INT >= 33) LiquidToggle(selected = { checked }, onSelect = onCheckedChange,
                backdrop = backdrop, isLightTheme = colors.page != Ios27Tokens.PageDark)
            else Switch(checked, onCheckedChange)
        }
    })
}

@Composable
fun Ios27StepperRow(label: String, value: Int, onValueChange: (Int) -> Unit, range: IntRange = 0..99) {
    Ios27ListRow(label, detail = value.toString(), trailing = {
        Row(Modifier.height(28.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onValueChange((value - 1).coerceIn(range)) }, enabled = value > range.first,
                modifier = Modifier.semantics { contentDescription = "$label 减少" }) { Text("−") }
            TextButton(onClick = { onValueChange((value + 1).coerceIn(range)) }, enabled = value < range.last,
                modifier = Modifier.semantics { contentDescription = "$label 增加" }) { Text("＋") }
        }
    })
}

@Composable
fun Ios27TextFieldRow(label: String, value: String, onValueChange: (String) -> Unit,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default, singleLine: Boolean = true) {
    val colors = LocalIos27PageColors.current
    TextField(value, onValueChange, modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        label = { Text(label) }, singleLine = singleLine, keyboardOptions = keyboardOptions,
        textStyle = Ios27Tokens.Type.Body, colors = TextFieldDefaults.textFieldColors(
            textColor = colors.label, backgroundColor = colors.group, cursorColor = colors.accent,
            focusedLabelColor = colors.accent, unfocusedLabelColor = colors.secondaryLabel,
            focusedIndicatorColor = colors.accent, unfocusedIndicatorColor = colors.separator))
}

@Composable
fun Ios27ActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalIos27PageColors.current
    val backdrop = rememberCanvasBackdrop { drawRect(colors.group) }
    if (Build.VERSION.SDK_INT >= 33) LiquidButton(onClick, backdrop,
        modifier = modifier.semantics { contentDescription = label }) { Text(label, color = colors.label) }
    else TextButton(onClick, modifier) { Text(label, color = colors.accent) }
}
