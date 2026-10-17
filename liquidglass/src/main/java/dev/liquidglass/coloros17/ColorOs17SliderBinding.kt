package dev.liquidglass.coloros17

/** Native dragging commits on release; keyboard/accessibility edits commit immediately. */
internal class ColorOs17SliderBinding(
    private val onProgress: (Int) -> Unit,
    private val onFinished: () -> Unit,
) {
    private var tracking = false

    fun start() { tracking = true }

    fun change(progress: Int, fromUser: Boolean) {
        if (!fromUser) return
        onProgress(progress)
        if (!tracking) onFinished()
    }

    fun stop() {
        tracking = false
        onFinished()
    }
}
