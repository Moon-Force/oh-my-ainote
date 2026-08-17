package com.moonforce.ohmyainote.ink

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.ink.authoring.compose.InProgressStrokes
import androidx.ink.strokes.Stroke

/** Must be instantiated once at editor scope, never inside a pager item. */
@Composable
fun AuthoringSurface(
    viewport: ViewportState,
    tool: Tool,
    colorArgb: Int = BrushCatalog.defaultColor(tool),
    maskPath: Path? = null,
    modifier: Modifier = Modifier,
    onStrokesFinished: (List<Stroke>) -> Unit,
) {
    Box(modifier = modifier) {
        InProgressStrokes(
            defaultBrush = BrushCatalog.create(tool, colorArgb),
            pointerEventToWorldTransform = viewport.viewToPageCompose(),
            maskPath = maskPath,
            onStrokesFinished = onStrokesFinished,
        )
    }
}
