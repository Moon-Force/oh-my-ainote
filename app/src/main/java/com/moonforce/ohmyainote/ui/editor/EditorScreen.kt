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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moonforce.ohmyainote.document.model.AiCardRecord
import com.moonforce.ohmyainote.document.model.NotebookKind
import com.moonforce.ohmyainote.R
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
    var touchStartY by remember { mutableStateOf<Float?>(null) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var pendingDeletePage by remember { mutableStateOf(false) }
    var expandedCard by remember { mutableStateOf<AiCardRecord?>(null) }
    var showPerformance by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var lastWetMoveNanos by remember { mutableLongStateOf(0L) }
    var lastMoveToFrameMs by remember { mutableFloatStateOf(0f) }
    val tileProvider = remember(viewModel) { PdfTileProvider() }
    DisposableEffect(tileProvider) { onDispose { tileProvider.close() } }
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

    val recordWetMove: (Long) -> Unit = remember(viewModel) {
        { nanos: Long ->
            val tool = viewModel.state.value.tool
            if (tool == Tool.PEN || tool == Tool.HIGHLIGHTER) lastWetMoveNanos = nanos
        }
    }
    LaunchedEffect(showPerformance, stylusActive) {
        if (!showPerformance || !stylusActive) return@LaunchedEffect
        while (stylusActive) {
            withFrameNanos { frameNanos ->
                val started = lastWetMoveNanos
                if (started > 0L) lastMoveToFrameMs = ((frameNanos - started) / 1_000_000.0).toFloat()
            }
        }
    }
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
        if (down != null) {
            touchStartX = down.position.x
            touchStartY = down.position.y
        }
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
            val startY = touchStartY
            touchStartX = null
            touchStartY = null
            if (start != null && startY != null) {
                if (abs(up.position.x - start) < 16f && abs(up.position.y - startY) < 16f) {
                    expandedCard = snapshot?.page?.cards?.firstOrNull { card ->
                        val point = pagePoint(up.position)
                        point.x in card.anchor.x..(card.anchor.x + card.anchor.w) &&
                            point.y in card.anchor.y..(card.anchor.y + card.anchor.h)
                    }
                } else if (abs(up.position.x - start) > 160f && abs(viewport.scale - fitScale) < 0.05f) {
                    val target = if (up.position.x < start) pagerState.currentPage + 1 else pagerState.currentPage - 1
                    if (target in 0 until pagerState.pageCount) scope.launch { pagerState.animateScrollToPage(target) }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        manifest?.title ?: "正在打开…",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.close(onBack) }) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "返回")
                    }
                },
                actions = {
                    Text(
                        "${(state.pageIndex + 1).coerceAtMost(manifest?.pageCount ?: 1)} / ${manifest?.pageCount ?: 1}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "更多操作")
                        }
                        DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("导出 PDF") },
                                onClick = { showOverflowMenu = false; pendingExport = "pdf" },
                            )
                            DropdownMenuItem(
                                text = { Text("导出 .ainote") },
                                onClick = { showOverflowMenu = false; pendingExport = "ainote" },
                            )
                            DropdownMenuItem(
                                text = { Text("分享 PDF") },
                                onClick = { showOverflowMenu = false; pendingExport = "share_pdf" },
                            )
                            DropdownMenuItem(
                                text = { Text("分享 .ainote") },
                                onClick = { showOverflowMenu = false; pendingExport = "share_ainote" },
                            )
                            if (com.moonforce.ohmyainote.BuildConfig.DEBUG) {
                                DropdownMenuItem(
                                    text = { Text(if (showPerformance) "关闭性能信息" else "显示性能信息") },
                                    onClick = { showOverflowMenu = false; showPerformance = !showPerformance },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            EditorStudioToolbar(
                state = state,
                onTool = viewModel::setTool,
                onColor = viewModel::setBrushColor,
                onSize = viewModel::setBrushSize,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                modifier = Modifier.fillMaxWidth().height(140.dp),
            )
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Box(
                    Modifier.fillMaxSize().clipToBounds().onSizeChanged { viewSize = it }
                        .routeEditorPointers(
                            interceptStylus = state.tool == Tool.ERASER || state.tool == Tool.BOX_ASK || state.tool == Tool.PAN,
                            onTouch = ::handleTouch,
                            onEraser = ::handleEraser,
                            onInterceptedStylus = ::handleInterceptedStylus,
                            onStylusActiveChanged = { stylusActive = it },
                            onStylusMove = recordWetMove,
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
                                BackgroundLayer(
                                    manifest,
                                    snapshot,
                                    notebookDir = viewModel.notebookDirectory,
                                    viewport = viewport,
                                    viewSize = viewSize,
                                    tileProvider = tileProvider,
                                    pageCount = manifest.pageCount,
                                )
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
                            sizePt = state.brushSizePt,
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
                        val moveFrame = if (lastWetMoveNanos > 0L) "%.2f".format(lastMoveToFrameMs) else "—"
                        Text(
                            "dry handoff ${handoff} ms · move→frame ${moveFrame} ms · scale ${"%.2f".format(viewport.scale)} · meshes ${state.finishedStrokes.size} · tiles ${tileProvider.tileCount} · tile cache ${"%.1f".format(tileProvider.cacheBytes / 1048576f)} MB",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(Color(0xB0000000)).padding(6.dp),
                        )
                    }
                }
                PageControls(
                    pageIndex = state.pageIndex,
                    pageCount = manifest?.pageCount ?: 1,
                    onPrevious = {
                        if (pagerState.currentPage > 0) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    },
                    onNext = {
                        if (pagerState.currentPage + 1 < pagerState.pageCount) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                    onFit = { viewport = viewport.copy(scale = fitScale, panX = 0f, panY = 0f) },
                    onAddPage = when (manifest?.kind) {
                        NotebookKind.TEMPLATE -> viewModel::addTemplatePage
                        NotebookKind.IMAGE -> ({ appendImage.launch("image/*") })
                        else -> null
                    },
                    onDeletePage = if (manifest?.kind == NotebookKind.TEMPLATE) {
                        { pendingDeletePage = true }
                    } else {
                        null
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
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
        val isShare = kind.startsWith("share_")
        AlertDialog(
            onDismissRequest = { pendingExport = null },
            title = {
                Text(
                    when (kind) {
                        "pdf" -> "导出扁平 PDF"
                        "ainote" -> "导出 .ainote"
                        "share_pdf" -> "分享扁平 PDF"
                        else -> "分享 .ainote"
                    },
                )
            },
            text = { Text("已插入纸面的 AI 卡片（问题、回答、选区图片和模型名）会进入文件并随分享离开本机；未插入的浮层对话不会。") },
            confirmButton = {
                TextButton(onClick = {
                    val base = manifest?.title ?: "note"
                    pendingExport = null
                    when (kind) {
                        "pdf" -> pdfExport.launch("$base.pdf")
                        "ainote" -> packageExport.launch("$base.ainote")
                        "share_pdf" -> viewModel.sharePdf()
                        else -> viewModel.sharePackage()
                    }
                }) { Text(if (isShare) "继续分享" else "继续导出") }
            },
            dismissButton = { TextButton(onClick = { pendingExport = null }) { Text("取消") } },
        )
    }
    if (pendingDeletePage) {
        AlertDialog(
            onDismissRequest = { pendingDeletePage = false },
            title = { Text("删除当前页") },
            text = { Text("仅空白页可删除，删除后不可撤销，且至少保留一页。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeletePage = false
                    viewModel.deleteTemplatePage()
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDeletePage = false }) { Text("取消") } },
        )
    }
    expandedCard?.let { card ->
        AlertDialog(
            onDismissRequest = { expandedCard = null },
            title = { Text("AI 卡片") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("问：${card.question}", style = MaterialTheme.typography.titleSmall)
                    Text(card.answer)
                    Text("${card.model} · ${card.createdAt}", style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = { TextButton(onClick = { expandedCard = null }) { Text("关闭") } },
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
