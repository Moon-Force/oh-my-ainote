# Documentation

| 文档 | 用途 | 当前角色 |
| --- | --- | --- |
| [`PRODUCT.md`](PRODUCT.md) | v1 产品边界、隐私与非目标 | 产品事实源 |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | 模块、运行时数据流、持久化和发布结构 | 实现概览 |
| [`BUILDING.md`](BUILDING.md) | 本地环境、debug/release 编译与签名 | 构建手册 |
| [`FORMAT.md`](FORMAT.md) | `.ainote` v1、坐标与原子提交 | 格式事实源 |
| [`IMPLEMENTATION.md`](IMPLEMENTATION.md) | PR 阶段、构建验证与剩余门禁 | 当前状态 |
| [`MANUAL_TEST.md`](MANUAL_TEST.md) | USI 平板与 PDF 真机验收 | 发布前必须执行 |
| [`TOOLBAR_DESIGN.md`](TOOLBAR_DESIGN.md) | Material 3 编辑器工具区、状态与动效 | 已实现 UI 规范 |
| [`DESIGN.md`](DESIGN.md) | 完整技术决策、权衡、风险和原始 PR 计划 | 设计依据 |

## 状态约定

- “代码完成”表示功能已经接线且工程可构建。
- “自动验证完成”只覆盖 Windows/JVM、Android 编译、APK 打包和 R8 可检查的内容。
- `MANUAL_TEST.md` 未勾选项仍是发布门禁，尤其是墨水延迟、掌拒、Ink 1.1 线宽/浓淡压感、长 PDF 内存和旋转 CropBox 导出对齐。

更新实现时，若改变模块边界、坐标、`.ainote` 布局、隐私边界或发布流程，必须同步更新对应文档。
