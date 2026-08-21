package com.moonforce.ohmyainote.ink

import android.content.Context
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.rendering.android.view.ViewStrokeRenderer

/**
 * Dry-ink [View] that can be [invalidate]d in the same UI run loop as
 * [androidx.ink.authoring.compose.InProgressStrokes.onStrokesFinished].
 *
 * Official View handoff (`InProgressStrokesFinishedListener`) requires the replacement surface to
 * have called [invalidate] before wet ink is removed; Compose `Canvas` recomposition is not that
 * same HWUI frame.
 */
class FinishedStrokesView(context: Context) : View(context) {
    private val canvasRenderer = CanvasStrokeRenderer.create()
    private val viewRenderer = ViewStrokeRenderer(canvasRenderer, this)
    private var strokes: List<FinishedStroke> = emptyList()
    private var viewport = ViewportState()

    init {
        setWillNotDraw(false)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun present(strokes: List<FinishedStroke>, viewport: ViewportState) {
        this.strokes = strokes
        this.viewport = viewport
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (strokes.isEmpty()) return
        val matrix = viewport.pageToViewAndroid()
        viewRenderer.drawWithStrokes(canvas) { scope ->
            canvas.concat(matrix)
            for (stroke in strokes) {
                scope.drawStroke(stroke.ink)
            }
        }
    }
}

@Composable
fun FinishedStrokesLayer(
    strokes: List<FinishedStroke>,
    viewport: ViewportState,
    modifier: Modifier = Modifier,
    onViewReady: (FinishedStrokesView) -> Unit = {},
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context -> FinishedStrokesView(context).also(onViewReady) },
        update = { it.present(strokes, viewport) },
    )
}
