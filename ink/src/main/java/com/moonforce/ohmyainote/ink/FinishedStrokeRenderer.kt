package com.moonforce.ohmyainote.ink

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer

@Composable
fun FinishedStrokesLayer(
    strokes: List<FinishedStroke>,
    viewport: ViewportState,
    modifier: Modifier = Modifier,
) {
    val renderer = remember { CanvasStrokeRenderer.create() }
    val matrix = viewport.pageToViewAndroid()
    Canvas(modifier.fillMaxSize()) {
        val canvas = drawContext.canvas.nativeCanvas
        val saveCount = canvas.save()
        try {
            canvas.concat(matrix)
            strokes.forEach { renderer.draw(canvas, it.ink, matrix) }
        } finally {
            canvas.restoreToCount(saveCount)
        }
    }
}
