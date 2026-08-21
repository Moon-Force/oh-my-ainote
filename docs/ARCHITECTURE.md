# Architecture

## Modules

| Module | Responsibility | Android dependency |
| --- | --- | --- |
| `:app` | Compose UI、导航、ViewModel、手写 `AppContainer` | Yes |
| `:document` | Oma v1 模型、版本化笔刷语义、编解码、目录存储、撤销、坐标变换 | No |
| `:ink` | AndroidX Ink 1.1.0-alpha07 作者层、官方笔刷扩展、渲染桥与命中 | Yes |
| `:pdf` | `PdfRenderer` 双实例、位图/瓦片缓存与预取 | Yes |
| `:ai-api` | OpenAI 兼容请求模型、prompt 与 OkHttp client | No |
| `:ai` | Keystore/DataStore、区域栅格化、浮层会话 | Yes |
| `:hwr` | ML Kit Digital Ink 中文手写识别、模型下载、转写开关持久化 | Yes |
| `:export` | PdfBox-Android 扁平 PDF 写出 | Yes |

`:app` 依赖全部模块；`:ink`、`:pdf`、`:ai`、`:hwr`、`:export` 依赖 `:document`；`:ai` 依赖 `:ai-api`。模块之间不得形成环。`:hwr` 不得进入 `:ink`（湿墨路径）或 JVM 模块。

## Runtime flows

- 导入：SAF `Uri` → `ImportProcessors` 临时文件/EXIF 烘焙 → PDF 检查或图片页描述 → `LocalNotebookStore`。
- 书写：唯一 `InProgressStrokes` → `OMA_PRESSURE_INK_V1` → page-space `Stroke` → 同步湿墨/干墨交接 → 串行页面提交。官方交接（`InProgressStrokesFinishedListener`）要求在同一 HWUI 帧里对干墨 `View.invalidate()`，库才会卸湿墨。干墨因此是 pager 外、与湿墨同层的 `FinishedStrokesView`（`ViewStrokeRenderer`）；`onStrokesFinished` 里直接 `present()` / `invalidate()`。`StateFlow` 的页快照与 undo 旗标推迟到下一帧，避免 pager 重绘抢走这一帧。
- 手写转写：撤销旁独立图标开关（全局 DataStore）。打开后仅对新钢笔在抬笔 **2s** 无续笔时，用已落盘的 `StrokeRecord` 调用 ML Kit Digital Ink（`zh-CN` / `zh-Hani-CN`），立刻用 `TextRecord` 替换该串墨水。识别失败不删墨。翻页或关开关取消待转队列。第一次打开若模型未在本地则直接下载约 20MB（需 Google 服务）；下载期间新笔按普通墨水留下。湿墨交接不参与识别。
- 提问：框选 page-space 矩形 → 背景、干墨和卡片合成 JPEG → 首次目标确认 → OpenAI 兼容 `/chat/completions` → 内存浮层；只有 Insert 才写页目录。
- 导出：等待当前写队列 → PdfBox-Android 按版本化笔刷曲线重放线宽/透明度并逐页追加或生成内容 → 临时 PDF 原子提升 → SAF 输出流。
- 分享：隐私确认 → 导出到 `cacheDir/exports/`（已映射 FileProvider）→ `ACTION_SEND` + 读权限 grant 送出 PDF / `.ainote`；不注册 MIME/`SEND` intent-filter。
- 删页：仅模板本空白页、至少保留 1 页；重排剩余页 `index` → 原子写 manifest → 删页目录与卡片媒体，`open` 时清孤儿并修复陈旧索引。
- 卡片展开：手指轻点卡片锚点（≤16 px 位移）弹出只读全文；不新增 pointer 层，复用 `routeEditorPointers` 触摸通道。

## Editor layers

`HorizontalPager` 页项只包含背景、纸面标准字和已插入卡片。干墨是 pager 外、与唯一 Compose `InProgressStrokes` 同层的 `FinishedStrokesView`（官方 `DrawingScreen` 的 Box 兄弟层结构）。再上方是框选矩形与 AI sheet。Material 3 工具区占用画布外固定 `140dp`，笔刷属性展开/收起不改变页面视口尺寸；画布在自身边界 `clipToBounds()`。湿墨不进入 pager item，也不被 viewport 的 `graphicsLayer` 二次变换。

`pageToView` 是干墨与背景共用的唯一正向矩阵；其逆矩阵交给 `InProgressStrokes.pointerEventToWorldTransform`。笔画出生即为 page space，书写期间冻结相机。

## Persistence

工作副本是 `{filesDir}/notebooks/{uuid}/` 目录。单页提交把 `page.json` 与 `strokes.bin` 写到同卷临时目录，再执行 `live -> .bak`、`tmp -> live`、删除 `.bak`。打开时先按有效性恢复 `.bak`，再读取页面。

页面变更、橡皮（笔画与标准字）、插入/删除卡片、手写转写以及 undo/redo 都必须走同一提交路径。undo 栈可在进程死亡时丢失，纸面状态不可回退。含 `texts` 的笔记把 `minReaderVersion` 升到 2。

`page.json.stockBrush` 是笔迹外观的持久化版本标识。旧 `PRESSURE_PEN` 继续按官方旧外观读取；新钢笔写入 `OMA_PRESSURE_INK_V1`。压力曲线是 `:document` 中的纯 Kotlin 语义，`:ink` 与 `:export` 共用同一组边界，避免编辑器和 PDF 导出各自解释。

编辑器 ViewModel 再用一个持久化互斥锁保持用户操作顺序，避免“抬笔尚未写完，紧接着 undo/擦除”在 I/O 线程上倒序提交。退出编辑器前等待队列并刷新首页 `media/cover.jpg`；封面是可重建缓存，不属于必需格式。

## Memory bounds

编辑器解码当前页的 Ink mesh 与 PDF 瓦片；`:pdf` 提供双渲染器、相邻页预取控制器与 96 MB tile LRU。`NotebookSession` 不常驻整本解码后的笔画，因此页数增长不会线性占用 mesh 内存。

PDF 背景已接入瓦片渲染：`PdfTileProvider` 持有按源文件复用的 `PdfPageRenderer` 与共享 96 MB `TileCache`，按 scale bucket 只渲染视口可见的 512×512 瓦片（外加 1 瓦片边距），并用 `pageInfo` 的渲染器页尺寸做 per-axis 校正，保证与整页渲染对齐；`close()` 关闭渲染器并清缓存。翻页时以同视口预热相邻 ±1 页瓦片。高倍缩放因此不再受单张 4096 位图上限约束而发糊。内存随翻页收敛（真机 40 页翻满 Graphics 稳定在 ~340 MB、TOTAL PSS ~450 MB），仍需 `MANUAL_TEST.md` 的 4×/8× 清晰度与瓦片接缝条目人工确认。

页面背景、AI 卡片缩略图和书架封面的解码位图由 Compose state 持有并随引用释放；UI 层不得在 `DisposableEffect` 中手动 `Bitmap.recycle()`，因为 Canvas / `BitmapPainter` 可能仍在当前帧使用该对象。导出器内部的短命位图不受此 UI 约束，仍在单页写出后立即释放。

## Release

tag `v*` 触发签名 APK workflow；签名材料只从 GitHub Actions secrets 注入。F-Droid/fastlane 元数据随仓库维护。release 启用 R8，并保留 AndroidX Ink JNI；PdfBox 的可选 JPX 引用只做 `dontwarn`，因为导入层已明确拒绝 JPEG2000 PDF。

本地无签名 secrets 时 `assembleRelease` 生成 `app-release-unsigned.apk`；GitHub tag workflow 注入 keystore 后生成签名 `app-release.apk`。当前签名 secret 名称见 `.github/workflows/release.yml`。
