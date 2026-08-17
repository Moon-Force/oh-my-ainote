package com.moonforce.ohmyainote.ink

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Apply to the editor ancestor containing [AuthoringSurface]. Touch and pen-tail events are
 * consumed during the Initial pass so they never become ink; stylus events fall through to Ink.
 */
fun Modifier.routeEditorPointers(
    interceptStylus: Boolean = false,
    onTouch: (PointerEvent) -> Unit = {},
    onEraser: (PointerEvent) -> Unit = {},
    onInterceptedStylus: (PointerEvent) -> Unit = {},
    onStylusActiveChanged: (Boolean) -> Unit = {},
): Modifier = pointerInput(interceptStylus, onTouch, onEraser, onInterceptedStylus, onStylusActiveChanged) {
    awaitPointerEventScope {
        var stylusActive = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val stylusChanges = event.changes.filter { it.type == PointerType.Stylus }
            val eraserChanges = event.changes.filter { it.type == PointerType.Eraser }
            val touchChanges = event.changes.filter { it.type != PointerType.Stylus && it.type != PointerType.Eraser }
            val nextActive = (stylusChanges + eraserChanges).any { it.pressed }
            if (nextActive != stylusActive) {
                stylusActive = nextActive
                onStylusActiveChanged(stylusActive)
            }
            if (eraserChanges.isNotEmpty()) {
                onEraser(event)
                eraserChanges.forEach { it.consume() }
            }
            if (touchChanges.isNotEmpty()) {
                onTouch(event)
                touchChanges.forEach { it.consume() }
            }
            if (interceptStylus && stylusChanges.isNotEmpty()) {
                onInterceptedStylus(event)
                stylusChanges.forEach { it.consume() }
            }
        }
    }
}
