# Implementation status

本清单追踪 `DESIGN.md` 的原始 PR Plan。代码位于 `codex/implement-design` 分支；每个阶段均已接线，最终仍以自动验证和真机门禁共同决定是否可发布。

- [x] PR-00：MIT、Gradle 七模块、Hello Activity、清单、CI、依赖红线
- [x] PR-01：PRODUCT、FORMAT、ARCHITECTURE、IMPLEMENTATION 文档
- [x] PR-02：Oma v1 codecs、目录 store、坐标与崩溃恢复
- [x] PR-03：书架、单层文件夹、模板本
- [x] PR-04a：唯一 `InProgressStrokes`、viewport、笔/手指合约
- [x] PR-04b：抬笔写 Store
- [x] PR-05：荧光笔、整笔橡皮、undo/redo
- [x] PR-06：PdfRenderer 背景与 PDF 导入
- [x] PR-07：图片导入、追加页与 EXIF 烘焙
- [x] PR-08：崩溃/撤销耐久与封面
- [x] PR-09：BYOK 设置与 OpenAI 兼容 client
- [x] PR-10：框选 AI 浮层
- [x] PR-11：插入 AI 卡片
- [x] PR-12：PdfBox 扁平 PDF 导出
- [x] PR-13：`.ainote` 打包与应用内 SAF 导入
- [x] PR-14：Release/F-Droid、debug 性能浮层、真机清单
- [x] PR-15：升级 AndroidX Ink 1.1.0-alpha07，基于官方 `pressurePen` 增加版本化线宽/浓淡压感并同步 PDF 导出
- [x] PR-16：修复 Compose 位图生命周期，避免图片本打开崩溃、背景/封面消失与重用已回收位图
- [x] PR-17：Material 3 编辑器工具区，接入钢笔/荧光笔颜色与粗细，固定视口避免属性栏导致纸面缩放或位移
- [x] PR-18：模板空白页删除（含崩溃恢复重排）、AI 卡片点按只读全文展开、FileProvider Sharesheet 分享 PDF / `.ainote`、封面 5 s 防抖、性能浮层 move→frame 测量
- [x] PR-19：ML Kit Digital Ink 手写转标准字（图标开关、2s 停笔提交、`TextRecord` 与 `minReaderVersion=2`）

代码阶段完成不代表真机验收完成。2026-08-19 已在小米平板（Android 16 / USI 笔）真机通过：模板删页一致性、分享面板拉起、封面 5 s 防抖、200 页内存、旋转 CropBox 导出对齐（结构级）。USI 湿墨延迟、掌拒、压感线宽浓淡、AI 卡片问答等需真笔 / API Key 的条目仍以 `MANUAL_TEST.md` 的未勾选项为发布门禁。

## Verification snapshot

验证日期：2026-08-19（Windows 11，JVM 目标 17，Android SDK 36）。

| 检查 | 命令 / 结果 |
| --- | --- |
| Oma/Store/坐标/压感曲线 | 已编译测试 class 后直接运行 JUnit，7 tests，0 failures；中文仓库路径下 Gradle Test Worker 的 classpath 问题见 `BUILDING.md` |
| OpenAI 请求形状 | `:ai-api:test`，1 test，0 failures |
| Debug APK | Ink 1.1.0-alpha07 下 `:app:assembleDebug` 成功；56,869,917 bytes（debug 不限体积）；四种 ABI 的 `libink.so` 已打包 |
| Release/R8 | Ink 1.1.0-alpha07 下 `:app:assembleRelease` 成功；未签名 APK 16,823,214 bytes；R8/lintVital 通过，四种 ABI 的 `libink.so` 已打包 |
| 原生符号 | `stripReleaseDebugSymbols` 对 `libink.so` 等 AndroidX 原生库提示无法再 strip，随后按原样打包且整体构建成功；该警告不等于 APK 失败 |
| 许可红线 | `releaseRuntimeClasspath` 无 iText |

自动测试覆盖 OmaInputsV1 变长 flags、PDF 四种旋转共 16 个角点、Affine 往返、页目录备份/提升崩溃恢复、重复 `.ainote` 导入新 UUID、`OMA_PRESSURE_INK_V1` 的线宽/不透明度边界，以及 MockWebServer 的路径、Authorization、JSON 与响应解析。

## Remaining release gates

- USI 实机湿墨/干墨对齐、掌拒、笔尾橡皮和横向长笔画仲裁。（剩余）
- USI 实机轻/中/重压力的线宽与浓淡、快速转向、慢速收笔，以及保存重开和 PDF 导出后的视觉一致性。（剩余）
- 40/200 页 PDF 的翻页、缩放和内存稳定性。（200 页翻页无 OOM 已通过；高倍缩放与纸面写字剩余）
- `/Rotate 90` + 非零 CropBox 的屏幕、AI 裁切和导出视觉对齐。（导出对齐已通过；AI 裁切剩余）
- 真机 Keystore、HTTP 首次警告、错误 Key、超时与 SAF 阅读器互操作。（分享面板拉起已通过；SAF 阅读器打开与卡片可见剩余）

这些项目只在 `MANUAL_TEST.md` 记录结果，不因代码阶段完成而预先勾选；已验证部分标注于 `MANUAL_TEST.md` 的 2026-08-19 执行记录。

## Known implementation deltas

下表描述“原设计目标”与当前分支的差异；它们没有被自动构建结果掩盖。

| 项目 | 当前实现 | 后续条件 |
| --- | --- | --- |
| PDF 高倍瓦片 / ±1 预取 | 已接入：`PdfTileProvider` 按 scale bucket 渲染视口可见 512×512 瓦片（`TileCache` 96 MB LRU、按源文件复用 `PdfPageRenderer`），翻页同视口预热相邻 ±1 页；高倍缩放不再受单张 4096 上限约束 | 真机 40 页翻满无 OOM、内存收敛（Graphics ~340 MB / PSS ~450 MB）；4×/8× 清晰度与瓦片接缝待人工 pinch + 肉眼确认 |
| 模板页管理 | 支持追加与删除空白页（至少保留 1 页），删页走同一崩溃安全页提交并可在 `open` 时重排/清孤儿 | 已完成；真机验收通过（加页/删页重排/末页守卫/杀进程重开一致） |
| 封面刷新 | 首页变更 5 s 防抖生成最长边 512 JPEG，退出编辑器立即 flush | 已完成 |
| AI 卡片 | 可显示、持久化、导出和随包往返；点按卡片只读展开全文（问题、回答、model、时间） | 已完成 |
| 导出分享 | SAF `CreateDocument` 可导出，另有 FileProvider Sharesheet 分享 PDF / `.ainote`，均有隐私确认 | 已完成；真机分享面板拉起通过，目标 App 打开留待人工 |
| Debug 性能浮层 | 顶部更多菜单可开关 dry handoff、move→frame、scale、mesh 和位图上限 | move→frame 已测量；tile cache 显示 `TileCache` 真实瓦片数与缓存 MB（真机 `tiles 96 · tile cache 96.0 MB`） |
| Observability | 默认无远程日志/崩溃上报 | Timber 与可选 ACRA/Sentry 未接入；这不改变隐私边界 |

其中“模板删页”是功能差异；PDF 瓦片是否成为发布阻断由目标设备的长 PDF 验收决定。其余项目不影响 `.ainote` v1 兼容性。

## 2026-08-19 targeted regressions

- 图片导入后点击打开：已移除页面背景、AI 卡片缩略图和书架封面上的手动 `Bitmap.recycle()`，避免 Compose 仍在绘制时命中已回收位图。
- 写字后纸面消失/缩小：工具区固定为 `140dp`，笔刷属性行只在该区域内展开；画布使用 `clipToBounds()`，不再覆盖工具区或触发页面重测量。
- 抬笔闪一下：按官方 `InProgressStrokesFinishedListener` 在同一 HWUI 帧 `View.invalidate()`。干墨改为 pager 外的 `FinishedStrokesView` + `ViewStrokeRenderer`；`onStrokesFinished` 回调里直接 `present()`，页快照 / undo 的 `StateFlow` 推迟到下一帧。
- 切换笔型/颜色画出来仍是第一支笔：`InProgressStrokes` 的 `pointerInput` 只跟变换矩阵重启；`remember(viewport)` 之后默认 `{ defaultBrush }` 会一直抓着第一支笔。按下时通过 `nextBrush` 读 `rememberUpdatedState` 的最新 brush。
- 编辑器工具区：钢笔、荧光笔、橡皮、顶部导出菜单已完成真机触摸检查；视觉对比记录见 [`../design-qa.md`](../design-qa.md)。
- 手写转写：`:hwr` 使用 ML Kit Digital Ink（`zh-CN`），模型按需下载、不进 APK；识别只吃干墨 `StrokeRecord`，不碰 `InProgressStrokes`。无 Google 服务时下载失败并保持开关关闭。真机转写需有 GMS 的 USI 笔机。

## 2026-08-19 release-gate pass (PR-18)

- 模板删页：`NotebookSession.deleteTemplatePage` 只接受模板本空白页且至少保留 1 页；协议 = 重排剩余页 `index` → 原子写 manifest → 删页目录与卡片媒体。`open()` 增加孤儿页目录清理与陈旧 `index` 重排，两个崩溃窗口均可恢复。JVM 测试覆盖重排、拒绝末页/含墨页、崩溃后重开修复（5 个新用例全过）。
- AI 卡片：手指轻点卡片区域（≤16 px 位移判为 tap）弹出只读全文对话框；不新增 pointer 层，走既有 `routeEditorPointers` 触摸通道，不影响湿墨。
- 分享：更多菜单新增「分享 PDF / 分享 .ainote」，复用隐私确认文案，导出到 `cacheDir/exports/`（已映射 FileProvider），`ACTION_SEND` + `FLAG_GRANT_READ_URI_PERMISSION` 送出；未注册任何 MIME/`SEND` intent-filter。
- 封面：首页墨迹/卡片变更 5 s 防抖重新生成，`close()` 取消防抖并立即 flush，与 DESIGN.md:969 对齐。
- 性能浮层：`routeEditorPointers` 新增 `onStylusMove` 时间戳回调（仅未拦截的书写笔触），`withFrameNanos` 采样最后 move→frame 延迟；tile cache 列显示 `TileCache` 真实瓦片数与缓存 MB。
- PDF 瓦片：新建 `PdfTileProvider`（按源文件复用 `PdfPageRenderer` 池 + 共享 96 MB `TileCache` + per-page `pageInfo` per-axis 校正）；`PageLayers.BackgroundLayer` 的 PDF 分支由「整页单张位图（上限 4096）」改为按 scale bucket 渲染视口可见 512×512 瓦片 + 翻页同视口预热相邻 ±1 页；`EditorScreen` 以 `remember + DisposableEffect` 管理 provider 生命周期并在浮层显示 tile 统计。
- 验证：`:document:test`（12 例）与 `:ai-api:test`（1 例）以直接 JUnit 方式通过（中文路径 Test Worker 限制见 `BUILDING.md`）；`assembleDebug` 通过。

## 2026-08-19 device gate pass（真机，PR-18）

设备：小米平板 2410CRP4CC（Android 16, arm64）+ 小米 USI 笔，无线 ADB。方法：`uiautomator dump` 取坐标点击 + `dumpsys window` 取焦点 + `run-as` 读文件系统；本设备 `adb screencap` 一度返回纯黑帧缓冲，视觉判定改用节点 dump / 文件系统 / 用户肉眼。

- 模板删页：加页 1→2、删页 2→1（`pageOrder` 重排）、末页按钮 `clickable=false` 不可删、`am force-stop` 重开 manifest 一致。
- 分享面板：分享 PDF → 继续分享 → `mCurrentFocus=com.android.intentresolver`，`cache/exports/share-*.pdf` 生成。
- 封面 5 s 防抖：manifest `updatedAt` 15:30:45.605 → `cover.jpg` mtime 15:30:50.744，差 5.14 s。
- 200 页内存：翻满 200/200 无 OOM，进程存活，PSS 289→540MB。
- 旋转 CropBox 导出对齐：导出与源逐页几何一致，原始内容流逐字节内嵌（同对象哈希），仅加 `[q\n]`/`[Q\n]` 无缩放平移。

剩余人工（需真笔 / API Key / 目标 App）：USI 湿墨延迟、掌拒、压感线宽浓淡、AI 卡片问答与错误文案、双指 pinch、图片 EXIF、SAF 图片导入、`.ainote` 往返、目标 App 打开分享文件后卡片可见。详见 `MANUAL_TEST.md` 的 2026-08-19 执行记录。

## 2026-08-21 测试补全记录

**最终结果（可联网环境，纯 ASCII 工作路径 `D:\ainote-run` 物理副本）：8 个模块全绿，46 例，0 失败。**

| 模块 | 测试类 | 用例 | 结果 |
|---|---|---|---|
| `:document` | DigitalInkGeometry / Geometry / OmaInputsV1 / OmaPressureInkV1 / LocalNotebookStore 等 | 18 | ✅ 全过 |
| `:ai-api` | OpenAiCompatibleClientTest | 1 | ✅ 全过 |
| `:ink` | HitTestTest（文本擦除 AABB/越界/内边距/空输入） | 4 | ✅ 全过 |
| `:pdf` | TileKeyTest（键值相等/单坐标差异） | 2 | ✅ 全过 |
| `:ai` | OverlaySessionTest（加空校验/卡片锚定右侧/右侧满则下绕/底部满则钳制） | 5 | ✅ 全过 |
| `:export` | ExportGeometryTest（四旋转/裁剪/选项默认/进度单调）+ FlattenedPdfExporterTest（2 页压感笔+荧光笔导出、原子写无残留临时文件） | 11 | ✅ 全过 |
| `:hwr` | DigitalInkModelStoreTest（下载超时 `withTimeout` 取消并以 `IllegalStateException` 包装） | 1 | ✅ 全过 |
| `:app` | TilePlanTest（可见瓦片范围/外翻钳制/低缩放整页/密度×缩放桶） | 4 | ✅ 全过 |

### 关键诊断与处置

- **中文路径 Gradle Test Worker 问题**：`:ai-api` 等模块在 `D:\开源项目\ainote` 路径下，Gradle 8.11 测试 worker 抛 `ClassNotFoundException`（同一构建经 ASCII 路径通过）。判定为 Windows 中文工作目录与 worker 类路径编码交互的环境问题，**非测试代码缺陷**。后续 JVM 单测请在纯 ASCII 路径执行（本记录即在 `D:\ainote-run` 物理副本上跑通）；中文主仓库路径留作 IDE/`assembleDebug` 使用。
- **`android.graphics.Color` / `android.util.LruCache` / `android.text.TextUtils` 在纯 JVM 单测下是"not mocked"桩**：
  - `:export`：抽出 `ArgbColor` 接口（`AndroidArgbColor` 走真 `Color`、`PureJvmArgbColor` 纯 Kotlin 解码），`FlattenedPdfExporter` 构造注入，使模板/笔迹导出路径可离线跑。卡片/文本绘制仍走真 `Canvas`（仅真机可测）。
  - `:pdf`：`TileCache` 包 `LruCache`，纯 JVM 下 `size()/snapshot()` 均 not-mocked；测试只覆盖纯数据类 `TileKey`。
- **`androidx.ink` 含 JNI 原生库**（`StrokeInputBatchNative`）：`Stroke.shape` 需 on-device；`:ink` 的 `eraseIntersectingStrokes` 与 `:app` 的 `EditorViewModel.loadPage`（经 `StrokeBridge.load`）因此**只能在仪表化/真机测试**，JVM 侧仅测纯几何/文本分支。
- **ML Kit `Tasks` 调 `TextUtils`、`MlKitContext` 未初始化**：`:hwr` 成功/失败路径依赖 `Tasks.forResult/forException`（内部调 Android 桩），无法 JVM 跑；仅超时路径用手写 `mock(Task)` 旁路 `Tasks` 得以离线验证 `withTimeout`+取消+包装契约。
- **`:hwr` 生产修复**：`DigitalInkModelStore` 加 5 分钟 `withTimeout`（挂起不再永久阻塞）；`modelManager` 改为惰性、构造参数走 `internal` 次构造（保持 `RemoteModelManager` 不出现在公共 API，`:app` 无需 ML Kit 依赖即可编译）。
- **已删除不可 JVM 运行的投机测试**：`EditorViewModelTest`（强耦合 `AppContainer` 终态 val、`DigitalInkModelStore` 终类、`StrokeBridge` 原生、DataStore）、`HandwritingRecognizerTest`/`HwrSettingsStoreTest`（ML Kit/`Canvas`/DataStore 运行时）——这些属真机/仪表化测试范畴，留待 `connectedAndroidTest`。

- 清理未引用的 `local-m2-tmp/` 存根 POM。
- 运行方式：`./gradlew --continue :document:test :ai-api:test :ink:testDebugUnitTest :pdf:testDebugUnitTest :ai:testDebugUnitTest :export:testDebugUnitTest :hwr:testDebugUnitTest :app:testDebugUnitTest`（JVM 模块用 `:module:test`，Android 模块用 `:module:testDebugUnitTest`）。
