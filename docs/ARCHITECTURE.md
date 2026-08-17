# Architecture

## Modules

| Module | Responsibility | Android dependency |
| --- | --- | --- |
| `:app` | Compose UI、导航、ViewModel、手写 `AppContainer` | Yes |
| `:document` | Oma v1 模型、编解码、目录存储、撤销、坐标变换 | No |
| `:ink` | AndroidX Ink 作者层、渲染桥、笔刷与命中 | Yes |
| `:pdf` | `PdfRenderer` 双实例、位图/瓦片缓存与预取 | Yes |
| `:ai-api` | OpenAI 兼容请求模型、prompt 与 OkHttp client | No |
| `:ai` | Keystore/DataStore、区域栅格化、浮层会话 | Yes |
| `:export` | PdfBox-Android 扁平 PDF 写出 | Yes |

`:app` 依赖全部模块；`:ink`、`:pdf`、`:ai`、`:export` 依赖 `:document`；`:ai` 依赖 `:ai-api`。模块之间不得形成环。

## Runtime flows

- 导入：SAF `Uri` → `ImportProcessors` 临时文件/EXIF 烘焙 → PDF 检查或图片页描述 → `LocalNotebookStore`。
- 书写：唯一 `InProgressStrokes` → page-space `Stroke` → 同步湿墨/干墨交接 → 串行页面提交。
- 提问：框选 page-space 矩形 → 背景、干墨和卡片合成 JPEG → 首次目标确认 → OpenAI 兼容 `/chat/completions` → 内存浮层；只有 Insert 才写页目录。
- 导出：等待当前写队列 → PdfBox-Android 逐页追加/生成内容 → 临时 PDF 原子提升 → SAF 输出流。
- 分享包：工作目录 ZIP 为 `.ainote`；导入校验路径和格式后总是分配新 UUID，不覆盖现有笔记。

## Editor layers

`HorizontalPager` 页项只包含背景、干墨和已插入卡片。编辑器根上方存在唯一一个 Compose `InProgressStrokes`，再上方是工具栏、框选矩形与 AI sheet。湿墨不进入 pager item，也不被 viewport 的 `graphicsLayer` 二次变换。

`pageToView` 是干墨与背景共用的唯一正向矩阵；其逆矩阵交给 `InProgressStrokes.pointerEventToWorldTransform`。笔画出生即为 page space，书写期间冻结相机。

## Persistence

工作副本是 `{filesDir}/notebooks/{uuid}/` 目录。单页提交把 `page.json` 与 `strokes.bin` 写到同卷临时目录，再执行 `live -> .bak`、`tmp -> live`、删除 `.bak`。打开时先按有效性恢复 `.bak`，再读取页面。

页面变更、橡皮、插入/删除卡片以及 undo/redo 都必须走同一提交路径。undo 栈可在进程死亡时丢失，纸面状态不可回退。

编辑器 ViewModel 再用一个持久化互斥锁保持用户操作顺序，避免“抬笔尚未写完，紧接着 undo/擦除”在 I/O 线程上倒序提交。退出编辑器前等待队列并刷新首页 `media/cover.jpg`；封面是可重建缓存，不属于必需格式。

## Memory bounds

编辑器当前只解码当前页的 Ink mesh 和一个 fit 位图；`:pdf` 提供双渲染器、相邻页预取控制器与 96 MB tile LRU，后续性能调优可接入而不改变文档格式。`NotebookSession` 不常驻整本解码后的笔画，因此页数增长不会线性占用 mesh 内存。

当前 UI 尚未消费 `PrefetchController`/`TileCache`；PDF 背景按 scale bucket 重新生成当前页位图，最长边限制为 4096。这个边界必须用 `MANUAL_TEST.md` 的 40/200 页与高倍缩放条目验证，不能仅依据类已经存在就声称瓦片方案已交付。

## Release

tag `v*` 触发签名 APK workflow；签名材料只从 GitHub Actions secrets 注入。F-Droid/fastlane 元数据随仓库维护。release 启用 R8，并保留 AndroidX Ink JNI；PdfBox 的可选 JPX 引用只做 `dontwarn`，因为导入层已明确拒绝 JPEG2000 PDF。

本地无签名 secrets 时 `assembleRelease` 生成 `app-release-unsigned.apk`；GitHub tag workflow 注入 keystore 后生成签名 `app-release.apk`。当前签名 secret 名称见 `.github/workflows/release.yml`。
