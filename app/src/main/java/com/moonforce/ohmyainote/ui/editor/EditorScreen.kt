package com.moonforce.ohmyainote.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moonforce.ohmyainote.document.model.NotebookKind
import com.moonforce.ohmyainote.document.model.PagePoint
import com.moonforce.ohmyainote.document.model.PageRect
import com.moonforce.ohmyainote.ink.AuthoringSurface
import com.moonforce.ohmyainote.ink.FinishedStrokesLayer
import com.moonforce.ohmyainote.ink.Tool
import com.moonforce.ohmyainote.ink.ViewportState
import com.moonforce.ohmyainote.ink.routeEditorPointers
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val manifest = state.manifest
    val snapshot = state.snapshot
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { manifest?.pageCount ?: 1 }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val densityDpi = density.density * 160f
    var viewport by remember { mutableStateOf(ViewportState(densityDpi = densityDpi)) }
    var fitScale by remember { mutableFloatStateOf(1f) }
    var stylusActive by remember { mutableStateOf(false) }
    var eraserPrevious by remember { mutableStateOf<PagePoint?>(null) }
    var boxStart by remember { mutableStateOf<PagePoint?>(null) }
    var boxEnd by remember { mutableStateOf<PagePoint?>(null) }
    var touchStartX by remember { mutableStateOf<Float?>(null) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var showPerformance by remember { mutableStateOf(false) }
    val pdfExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let(viewModel::exportPdf)
    }
    val packageExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(viewModel::exportPackage)
    }
    val appendImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::appendImagePage)
    }
    BackHandler { viewModel.close(onBack) }

    LaunchedEffect(pagerState.currentPage, manifest?.pageCount) {
        if (manifest != null) viewModel.loadPage(pagerState.currentPage)
    }
    LaunchedEffect(state.pageIndex) {
        if (pagerState.currentPage != state.pageIndex) pagerState.scrollToPage(state.pageIndex)
    }
    LaunchedEffect(snapshot?.page?.id, viewSize) {
        val page = snapshot?.page ?: return@LaunchedEffect
        if (viewSize.width <= 0 || viewSize.height <= 0) return@LaunchedEffect
        val pxPerPoint = densityDpi / 72f
        val scale = (viewSize.width * 0.94f / (page.widthPt * pxPerPoint)).coerceIn(0.25f, 8f)
        fitScale = scale
        val pageWidth = page.widthPt * pxPerPoint * scale
        val pageHeight = page.heightPt * pxPerPoint * scale
        viewport = ViewportState(
            scale = scale,
            pageOriginX = (viewSize.width - pageWidth) / 2f,
            pageOriginY = (viewSize.height - pageHeight) / 2f,
            densityDpi = densityDpi,
        )
    }

    fun pagePoint(offset: Offset): PagePoint = viewport.pageToView.invert().transform(PagePoint(offset.x, offset.y))
    fun handleEraser(event: PointerEvent) {
        val change = event.changes.firstOrNull { it.type == PointerType.Eraser || it.type == PointerType.Stylus } ?: return
        if (!change.pressed) {
            eraserPrevious = null
            return
        }
        val current = pagePoint(change.position)
        eraserPrevious?.let { viewModel.eraseSegment(it.x, it.y, current.x, current.y) }
        eraserPrevious = current
    }
    fun handleInterceptedStylus(event: PointerEvent) {
        val change = event.changes.firstOrNull { it.type == PointerType.Stylus } ?: return
        when (state.tool) {
            Tool.ERASER -> handleEraser(event)
            Tool.BOX_ASK -> {
                val point = pagePoint(change.position)
                if (change.pressed && !change.previousPressed) {
                    boxStart = point
                    boxEnd = point
                } else if (change.pressed) {
                    boxEnd = point
                } else if (change.previousPressed) {
                    val start = boxStart
                    val end = boxEnd ?: point
                    boxStart = null
                    boxEnd = null
                    if (start != null) {
                        val rect = PageRect(minOf(start.x, end.x), minOf(start.y, end.y), maxOf(start.x, end.x), maxOf(start.y, end.y))
                        if (rect.width >= 24f && rect.height >= 24f) viewModel.selectForAsk(rect)
                    }
                }
            }
            Tool.PAN -> if (change.pressed) {
                val delta = change.position - change.previousPosition
                viewport = viewport.copy(panX = viewport.panX + delta.x, panY = viewport.panY + delta.y)
            }
            else -> Unit
        }
    }
    fun handleTouch(event: PointerEvent) {
        if (stylusActive) return
        val pressed = event.changes.filter { it.pressed }
        val down = event.changes.firstOrNull { it.pressed && !it.previousPressed }
        if (down != null) touchStartX = down.position.x
        if (pressed.size == 1) {
            val change = pressed.single()
            val delta = change.position - change.previousPosition
            viewport = viewport.copy(panX = viewport.panX + delta.x, panY = viewport.panY + delta.y)
        } else if (pressed.size >= 2) {
            val first = pressed[0]
            val second = pressed[1]
            val oldDistance = hypot(
                (first.previousPosition.x - second.previousPosition.x).toDouble(),
                (first.previousPosition.y - second.previousPosition.y).toDouble(),
            ).toFloat()
            val newDistance = hypot(
                (first.position.x - second.position.x).toDouble(),
                (first.position.y - second.position.y).toDouble(),
            ).toFloat()
            if (oldDistance > 0f) viewport = viewport.copy(scale = (viewport.scale * newDistance / oldDistance).coerceIn(0.25f, 8f))
        }
        val up = event.changes.firstOrNull { !it.pressed && it.previousPressed }
        if (up != null && pressed.isEmpty()) {
            val start = touchStartX
            touchStartX = null
            if (start != null && abs(up.position.x - start) > 160f && abs(viewport.scale - fitScale) < 0.05f) {
                val target = if (up.position.x < start) pagerState.currentPage + 1 else pagerState.currentPage - 1
                if (target in 0 until pagerState.pageCount) scope.launch { pagerState.animateScrollToPage(target) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(manifest?.title ?: "正在打开…") },
                navigationIcon = { TextButton(onClick = { viewModel.close(onBack) }) { Text("返回") } },
                actions = {
                    Text("${(state.pageIndex + 1).coerceAtMost(manifest?.pageCount ?: 1)}/${manifest?.pageCount ?: 1}")
                    if (com.moonforce.ohmyainote.BuildConfig.DEBUG) {
                        TextButton(onClick = { showPerformance = !showPerformance }) { Text("Perf") }
                    }
                    TextButton(onClick = { pendingExport = "pdf" }) { Text("导出 PDF") }
                    TextButton(onClick = { pendingExport = "ainote" }) { Text("导出 .ainote") }
                },
            )
        },
        bottomBar = {
            EditorToolbar(
                state = state,
                onTool = viewModel::setTool,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onPrevious = {
                    if (pagerState.currentPage > 0) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                },
                onNext = {
                    if (pagerState.currentPage + 1 < pagerState.pageCount) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                onAddPage = when (manifest?.kind) {
                    NotebookKind.TEMPLATE -> viewModel::addTemplatePage
                    NotebookKind.IMAGE -> ({ appendImage.launch("image/*") })
                    else -> null
                },
                onFit = { viewport = viewport.copy(scale = fitScale, panX = 0f, panY = 0f) },
            )
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).onSizeChanged { viewSize = it }
                .routeEditorPointers(
                    interceptStylus = state.tool == Tool.ERASER || state.tool == Tool.BOX_ASK || state.tool == Tool.PAN,
                    onTouch = ::handleTouch,
                    onEraser = ::handleEraser,
                    onInterceptedStylus = ::handleInterceptedStylus,
                    onStylusActiveChanged = { stylusActive = it },
                ),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = false,
                beyondViewportPageCount = 1,
            ) { pageIndex ->
                if (snapshot != null && pageIndex == state.pageIndex && manifest != null) {
                    Box(Modifier.fillMaxSize()) {
                        BackgroundLayer(manifest, snapshot, notebookDir = viewModel.notebookDirectory, viewport = viewport)
                        FinishedStrokesLayer(state.finishedStrokes, viewport)
                        AiCardLayer(snapshot, viewModel.notebookDirectory, viewport)
                    }
                }
            }
            if (snapshot != null) {
                AuthoringSurface(
                    viewport = viewport,
                    tool = state.tool,
                    colorArgb = state.colorArgb,
                    modifier = Modifier.fillMaxSize(),
                    onStrokesFinished = viewModel::onStrokesFinished,
                )
            }
            if (boxStart != null && boxEnd != null) {
                Canvas(Modifier.fillMaxSize()) {
                    val a = viewport.pageToView.transform(boxStart!!)
                    val b = viewport.pageToView.transform(boxEnd!!)
                    drawRect(
                        Color(0x334285F4),
                        topLeft = Offset(minOf(a.x, b.x), minOf(a.y, b.y)),
                        size = androidx.compose.ui.geometry.Size(abs(a.x - b.x), abs(a.y - b.y)),
                    )
                }
            }
            state.busyMessage?.let { message ->
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(message, modifier = Modifier.padding(8.dp))
                }
            }
            if (showPerformance) {
                val handoff = state.lastDryHandoffMs?.let { "%.2f".format(it) } ?: "—"
                Text(
                    "dry handoff ${handoff} ms · scale ${"%.2f".format(viewport.scale)} · meshes ${state.finishedStrokes.size} · page bitmaps ≤1",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(Color(0xB0000000)).padding(6.dp),
                )
            }
        }
    }

    state.overlay?.let { overlay ->
        AiOverlaySheet(
            overlay = overlay,
            loading = state.aiLoading,
            onAsk = viewModel::ask,
            onInsert = viewModel::insertCurrentCard,
            onDismiss = viewModel::closeOverlay,
        )
    }
    state.confirmationUrl?.let { url ->
        AlertDialog(
            onDismissRequest = viewModel::cancelConfirmation,
            title = { Text(if (url.startsWith("http://")) "未加密连接" else "确认 AI 目标") },
            text = { Text("API Key、选区 JPEG 与问题将发送到：\n$url${if (url.startsWith("http://")) "\n\n该连接未加密。" else ""}") },
            confirmButton = { TextButton(onClick = viewModel::confirmAndAsk) { Text("确认发送") } },
            dismissButton = { TextButton(onClick = viewModel::cancelConfirmation) { Text("取消") } },
        )
    }
    if (state.settingsRequired) {
        AlertDialog(
            onDismissRequest = viewModel::consumeSettingsRequest,
            title = { Text("AI 尚未配置") },
            text = { Text("请先填写 Base URL、Model 和 API Key。书写与导出不受影响。") },
            confirmButton = {
                TextButton(onClick = { viewModel.consumeSettingsRequest(); onOpenSettings() }) { Text("去设置") }
            },
            dismissButton = { TextButton(onClick = viewModel::consumeSettingsRequest) { Text("稍后") } },
        )
    }
    pendingExport?.let { kind ->
        AlertDialog(
            onDismissRequest = { pendingExport = null },
            title = { Text(if (kind == "pdf") "导出扁平 PDF" else "导出 .ainote") },
            text = { Text("已插入纸面的 AI 卡片（问题、回答、选区图片和模型名）会进入导出文件；未插入的浮层对话不会导出。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingExport = null
                    if (kind == "pdf") pdfExport.launch("${manifest?.title ?: "note"}.pdf")
                    else packageExport.launch("${manifest?.title ?: "note"}.ainote")
                }) { Text("继续导出") }
            },
            dismissButton = { TextButton(onClick = { pendingExport = null }) { Text("取消") } },
        )
    }
    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("操作失败") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("确定") } },
        )
    }
}

@Composable
private fun EditorToolbar(
    state: EditorUiState,
    onTool: (Tool) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onAddPage: (() -> Unit)?,
    onFit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(Tool.PEN to "钢笔", Tool.HIGHLIGHTER to "荧光笔", Tool.ERASER to "整笔擦", Tool.BOX_ASK to "框选问", Tool.PAN to "平移").forEach { (tool, label) ->
            FilterChip(selected = state.tool == tool, onClick = { onTool(tool) }, label = { Text(label) })
        }
        TextButton(enabled = state.canUndo, onClick = onUndo) { Text("撤销") }
        TextButton(enabled = state.canRedo, onClick = onRedo) { Text("重做") }
        TextButton(onClick = onPrevious) { Text("上一页") }
        TextButton(onClick = onNext) { Text("下一页") }
        TextButton(onClick = onFit) { Text("适合宽度") }
        onAddPage?.let { TextButton(onClick = it) { Text("加页") } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiOverlaySheet(
    overlay: com.moonforce.ohmyainote.ai.OverlaySession,
    loading: Boolean,
    onAsk: (String) -> Unit,
    onInsert: () -> Unit,
    onDismiss: () -> Unit,
) {
    var question by remember(overlay) { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp).widthIn(max = 900.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("询问这个选区", style = MaterialTheme.typography.titleLarge)
            overlay.turns.forEach { turn ->
                Text("问：${turn.question}", style = MaterialTheme.typography.titleSmall)
                Text(turn.answer.text)
            }
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("问题") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(enabled = question.isNotBlank() && !loading, onClick = { onAsk(question); question = "" }) {
                    Text(if (loading) "思考中…" else "提问")
                }
                Button(enabled = overlay.turns.isNotEmpty() && !loading, onClick = onInsert) { Text("Insert 到纸面") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    }
}
