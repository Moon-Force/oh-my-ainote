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
        strokes.forEach { renderer.draw(drawContext.canvas.nativeCanvas, it.ink, matrix) }
    }
}
