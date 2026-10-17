package dev.liquidglass.components

/** Marketing ROM version and Oplus SDK level are different version domains. */
internal object ColorOs17BlurPolicy {
    fun isColorOs17(oplusRom: String, oppoRom: String): Boolean {
        val version = oplusRom.trim().ifEmpty { oppoRom.trim() }
        return Regex("^(?:ColorOS\\s*)?[vV]?17(?:\\.|$)", RegexOption.IGNORE_CASE)
            .containsMatchIn(version)
    }

    fun supportedSdk(major: Int, minor: Int): Boolean =
        major > 33 || (major == 33 && minor >= 34)

    fun enabled(materialBlur: Int, disabledWindowBlur: Int, animationLevel: Int?): Boolean =
        materialBlur == 1 && disabledWindowBlur == 0 && animationLevel in 1..2

    fun strength(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

    // Settings APK AppBarBlurParamsExtKt, not dp values.
    const val START_RADIUS = 30f
    const val END_RADIUS = 0f
    const val SPEED = 3f
    const val FIXED_EXTRA_HEIGHT_DP = 8f
    const val SCROLL_EXTRA_HEIGHT_DP = 24f
    const val SCROLL_REVEAL_DISTANCE_DP = 30f

    fun overlayHeight(appBarHeight: Float?, scrollDriven: Boolean): Float =
        if (appBarHeight == null || !appBarHeight.isFinite()) 128f
        else appBarHeight.coerceAtLeast(0f) +
            if (scrollDriven) SCROLL_EXTRA_HEIGHT_DP else FIXED_EXTRA_HEIGHT_DP

    // GradientBackgroundDrawable uses bottom -> top coordinates.
    fun positions(light: Boolean): FloatArray = if (light) floatArrayOf(0f, .4f, 1f)
        else floatArrayOf(0f, 1f)
    fun alphas(light: Boolean): FloatArray = if (light) floatArrayOf(0f, .7f, 1f)
        else floatArrayOf(0f, .4f)
}
