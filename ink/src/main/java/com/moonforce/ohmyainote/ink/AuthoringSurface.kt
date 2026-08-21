package com.moonforce.ohmyainote.ink

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
    sizePt: Float = BrushCatalog.defaultSize(tool),
    maskPath: Path? = null,
    modifier: Modifier = Modifier,
    onStrokesFinished: (List<Stroke>) -> Unit,
) {
    val brush = remember(tool, colorArgb, sizePt) { BrushCatalog.create(tool, colorArgb, sizePt) }
    val currentBrush = rememberUpdatedState(brush)
    // InProgressShapes keys pointerInput on the transform lambdas, not nextBrush. A stable
    // viewToPage therefore keeps the first `{ defaultBrush }` forever unless nextBrush reads
    // latest state at pointer-down.
    val nextBrush = remember { { currentBrush.value } }
    val viewToPage = remember(viewport) { viewport.viewToPageCompose() }
    Box(modifier = modifier) {
        InProgressStrokes(
            defaultBrush = brush,
            nextBrush = nextBrush,
            pointerEventToWorldTransform = viewToPage,
            maskPath = maskPath,
            onStrokesFinished = onStrokesFinished,
        )
    }
}
