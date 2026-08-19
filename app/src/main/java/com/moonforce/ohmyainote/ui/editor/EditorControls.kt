package com.moonforce.ohmyainote.ui.editor

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonforce.ohmyainote.R
import com.moonforce.ohmyainote.ink.Tool
import com.moonforce.ohmyainote.ink.isWriting

private data class ToolItem(val tool: Tool, val label: String, @DrawableRes val icon: Int)

private val toolItems = listOf(
    ToolItem(Tool.PEN, "钢笔", R.drawable.ic_ink_pen),
    ToolItem(Tool.HIGHLIGHTER, "荧光笔", R.drawable.ic_ink_highlighter),
    ToolItem(Tool.ERASER, "橡皮", R.drawable.ic_ink_eraser),
    ToolItem(Tool.BOX_ASK, "框选", R.drawable.ic_select),
    ToolItem(Tool.PAN, "手形", R.drawable.ic_pan_tool),
)

@Composable
internal fun EditorStudioToolbar(
    state: EditorUiState,
    onTool: (Tool) -> Unit,
    onColor: (Int) -> Unit,
    onSize: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.padding(top = 10.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 2.dp,
                    shadowElevation = 3.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(5.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        toolItems.forEach { item ->
                            ToolModeButton(
                                item = item,
                                selected = state.tool == item.tool,
                                colorDot = if (selectedWritingTool(state.tool, item.tool)) state.colorArgb else null,
                                onClick = { onTool(item.tool) },
                            )
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 2.dp,
                    shadowElevation = 3.dp,
                ) {
                    Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp)) {
                        HistoryButton(R.drawable.ic_undo, "撤销", state.canUndo, onUndo)
                        HistoryButton(R.drawable.ic_redo, "重做", state.canRedo, onRedo)
                    }
                }
            }
            AnimatedVisibility(
                visible = state.tool.isWriting,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                BrushProperties(
                    tool = state.tool,
                    colorArgb = state.colorArgb,
                    sizePt = state.brushSizePt,
                    onColor = onColor,
                    onSize = onSize,
                )
            }
        }
    }
}

@Composable
private fun ToolModeButton(
    item: ToolItem,
    selected: Boolean,
    colorDot: Int?,
    onClick: () -> Unit,
) {
    val containerColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "toolContainer",
    )
    val contentColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "toolContent",
    )
    Surface(
        onClick = onClick,
        modifier = Modifier.height(48.dp).semantics { this.selected = selected; role = Role.RadioButton },
        shape = RoundedCornerShape(if (selected) 22.dp else 16.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Icon(painterResource(item.icon), contentDescription = null, modifier = Modifier.size(24.dp))
                colorDot?.let {
                    Box(
                        Modifier.size(8.dp).clip(CircleShape)
                            .background(Color(it).copy(alpha = 1f)),
                    )
                }
            }
            Text(item.label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun HistoryButton(@DrawableRes icon: Int, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(painterResource(icon), contentDescription = label)
    }
}

@Composable
private fun BrushProperties(
    tool: Tool,
    colorArgb: Int,
    sizePt: Float,
    onColor: (Int) -> Unit,
    onSize: (Float) -> Unit,
) {
    val colors = if (tool == Tool.HIGHLIGHTER) {
        listOf(0x66FFEB3BL, 0x6698D447L, 0x66F0708CL, 0x66A67AD5L, 0x666EA8E8L)
    } else {
        listOf(0xFF1A1A1AL, 0xFF5D4BA0L, 0xFFD84148L, 0xFF3975D3L, 0xFF2E9B55L)
    }.map { it.toInt() }
    val sizes = if (tool == Tool.HIGHLIGHTER) listOf(8f, 14f, 20f, 28f) else listOf(1.5f, 2.5f, 4f, 6f)
    var moreExpanded by remember(tool) { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StrokePreview(colorArgb, sizePt, Modifier.width(150.dp).height(48.dp))
            ToolbarDivider()
            colors.forEach { color -> ColorChoice(color, colorArgb == color, onColor) }
            ToolbarDivider()
            sizes.forEach { size -> SizeChoice(size, sizePt == size, colorArgb, onSize) }
            ToolbarDivider()
            Box {
                TextButton(onClick = { moreExpanded = true }, modifier = Modifier.height(48.dp)) {
                    Text("更多")
                }
                DropdownMenu(expanded = moreExpanded, onDismissRequest = { moreExpanded = false }) {
                    Column(Modifier.width(260.dp).padding(horizontal = 18.dp, vertical = 12.dp)) {
                        Text("笔画粗细  ${"%.1f".format(sizePt)} pt", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = sizePt,
                            onValueChange = onSize,
                            valueRange = if (tool == Tool.HIGHLIGHTER) 6f..32f else 1f..8f,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StrokePreview(colorArgb: Int, sizePt: Float, modifier: Modifier = Modifier) {
    val color = Color(colorArgb)
    Canvas(modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        val path = Path().apply {
            moveTo(0f, size.height * 0.62f)
            cubicTo(size.width * 0.22f, 0f, size.width * 0.42f, size.height, size.width * 0.62f, size.height * 0.42f)
            cubicTo(size.width * 0.76f, size.height * 0.12f, size.width * 0.88f, size.height * 0.72f, size.width, size.height * 0.35f)
        }
        drawPath(
            path,
            color = color,
            style = Stroke(width = sizePt.coerceIn(1f, 18f) * density * 0.7f, cap = StrokeCap.Round),
        )
    }
}

private fun selectedWritingTool(selectedTool: Tool, itemTool: Tool): Boolean =
    selectedTool == itemTool && itemTool.isWriting

@Composable
private fun ColorChoice(colorArgb: Int, selected: Boolean, onClick: (Int) -> Unit) {
    val display = Color(colorArgb).copy(alpha = 1f)
    val border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    Surface(
        onClick = { onClick(colorArgb) },
        modifier = Modifier.size(44.dp).semantics {
            this.selected = selected
            role = Role.RadioButton
            contentDescription = "颜色 #%08X".format(colorArgb)
        },
        shape = CircleShape,
        color = Color.Transparent,
        border = border,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(display)
                    .then(if (display.luminance() > 0.85f) Modifier.background(display) else Modifier),
            )
        }
    }
}

@Composable
private fun SizeChoice(sizePt: Float, selected: Boolean, colorArgb: Int, onClick: (Float) -> Unit) {
    val border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    Surface(
        onClick = { onClick(sizePt) },
        modifier = Modifier.size(44.dp).semantics {
            this.selected = selected
            role = Role.RadioButton
            contentDescription = "粗细 ${"%.1f".format(sizePt)} pt"
        },
        shape = CircleShape,
        color = Color.Transparent,
        border = border,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.size((6f + sizePt.coerceAtMost(18f) * 0.7f).dp)
                    .clip(CircleShape)
                    .background(Color(colorArgb).copy(alpha = 1f)),
            )
        }
    }
}

@Composable
private fun ToolbarDivider() {
    Spacer(
        Modifier.padding(horizontal = 4.dp).width(1.dp).height(32.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
internal fun PageControls(
    pageIndex: Int,
    pageCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFit: () -> Unit,
    onAddPage: (() -> Unit)?,
    onDeletePage: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(25.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious, enabled = pageIndex > 0) {
                Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "上一页")
            }
            Text("${pageIndex + 1} / $pageCount", style = MaterialTheme.typography.labelLarge, fontSize = 14.sp)
            IconButton(onClick = onNext, enabled = pageIndex + 1 < pageCount) {
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "下一页")
            }
            TextButton(onClick = onFit) { Text("适合宽度") }
            onAddPage?.let {
                IconButton(onClick = it) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = "加页")
                }
            }
            onDeletePage?.let {
                IconButton(onClick = it, enabled = pageCount > 1) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = "删除当前空白页")
                }
            }
        }
    }
}
