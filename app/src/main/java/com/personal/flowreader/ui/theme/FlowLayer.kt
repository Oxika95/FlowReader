package com.personal.flowreader.ui.theme

/**
 * Draw order for everything on screen. Use `Modifier.zIndex(FlowLayer.X.z)` — never a literal.
 * Fullscreen cards are drawn by the overlay host at [Fullscreen] + stack depth.
 * See `docs/ui-system/layers.md`.
 */
enum class FlowLayer(val z: Float) {
    /** Screen content: lists, reading column, tab bars. */
    Content(0f),
    /** Non-interactive or tap-band layers over content (busy scrim, reader edge bands). */
    ContentScrim(10f),
    /** Top/bottom floating docks. */
    Dock(20f),
    /** FAB and snackbar. */
    Action(30f),
    /** Fullscreen card stack (host adds depth). */
    Fullscreen(40f),
    /** App-wide tools above everything (debug bubble). */
    System(90f),
}
