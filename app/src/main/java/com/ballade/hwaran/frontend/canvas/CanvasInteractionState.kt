package com.ballade.hwaran.frontend.canvas

/**
 * Shared touch/pointer state for interactive canvas backgrounds (IDs 8, 9, 10).
 * Plain mutable fields — written on the main thread from pointerInput,
 * read on the main thread from animation coroutine loops. No synchronisation needed.
 */
class CanvasInteractionState {
    var touchX: Float = -1f
    var touchY: Float = -1f
    var isTouching: Boolean = false

    /** Increments every time the finger first touches down — used by ripple to spawn a ring. */
    var tapVersion: Int = 0

    fun onTapDown(x: Float, y: Float) {
        touchX = x
        touchY = y
        isTouching = true
        tapVersion++
    }

    fun onMove(x: Float, y: Float) {
        touchX = x
        touchY = y
        isTouching = true
    }

    fun onRelease() {
        isTouching = false
    }
}
