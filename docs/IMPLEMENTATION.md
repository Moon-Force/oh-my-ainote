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

代码阶段完成不代表真机验收完成。USI 延迟、掌拒、200 页 PDF 内存和旋转 CropBox 导出对齐仍以 `MANUAL_TEST.md` 的未勾选项目为发布门禁。

## Verification snapshot

验证日期：2026-08-18（Windows 11，JVM 目标 17，Android SDK 36）。

| 检查 | 命令 / 结果 |
| --- | --- |
| Oma/Store/坐标 | `:document:test`，6 tests，0 failures |
| OpenAI 请求形状 | `:ai-api:test`，1 test，0 failures |
| Debug APK | `:app:assembleDebug` 成功；约 55.6 MB（debug 不限体积） |
| Release/R8 | `:app:assembleRelease` 成功；未签名 APK 约 16.2 MB |
| 许可红线 | `releaseRuntimeClasspath` 无 iText |

自动测试覆盖 OmaInputsV1 变长 flags、PDF 四种旋转共 16 个角点、Affine 往返、页目录备份/提升崩溃恢复、重复 `.ainote` 导入新 UUID，以及 MockWebServer 的路径、Authorization、JSON 与响应解析。

## Remaining release gates

- USI 实机湿墨/干墨对齐、掌拒、笔尾橡皮和横向长笔画仲裁。
- 40/200 页 PDF 的翻页、缩放和内存稳定性。
- `/Rotate 90` + 非零 CropBox 的屏幕、AI 裁切和导出视觉对齐。
- 真机 Keystore、HTTP 首次警告、错误 Key、超时与 SAF 阅读器互操作。

这些项目只在 `MANUAL_TEST.md` 记录结果，不因代码阶段完成而预先勾选。

## Known implementation deltas

下表描述“原设计目标”与当前分支的差异；它们没有被自动构建结果掩盖。

| 项目 | 当前实现 | 后续条件 |
| --- | --- | --- |
| PDF 高倍瓦片 / ±1 预取 | `TileCache`、`PrefetchController` 和双 renderer 已实现；编辑器当前仅显示当前页单张位图（最长边 4096） | 200 页与 4×/8× 失败则必须在 v1 发布前接入；即使通过，也应单独性能评审 |
| 模板页管理 | 支持追加；尚未提供删页 UI/Store 操作 | 若 v1 保留“模板页可删除”承诺，发布前补齐 |
| 封面刷新 | 退出编辑器时生成最长边 512 JPEG | 设计中的 5 秒防抖尚未接入，属于体验优化 |
| AI 卡片 | 可显示、持久化、导出和随包往返 | 尚未提供点击后的只读全文展开 |
| 导出分享 | SAF `CreateDocument` 可导出 PDF / `.ainote`，且有隐私确认 | FileProvider sharesheet 尚未提供 UI 入口 |
| Debug 性能浮层 | 顶栏 `Perf` 显示 dry handoff、scale、mesh 和位图上限 | 尚未测量最后 move→frame，也未显示真实 tile cache 数据 |
| Observability | 默认无远程日志/崩溃上报 | Timber 与可选 ACRA/Sentry 未接入；这不改变隐私边界 |

其中“模板删页”是功能差异；PDF 瓦片是否成为发布阻断由目标设备的长 PDF 验收决定。其余项目不影响 `.ainote` v1 兼容性。
