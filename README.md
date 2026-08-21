# oh-my-ainote

Android 平板优先、本地优先、无账号的手写课堂与会议笔记本。

核心能力：模板/PDF/图片纸张、AndroidX Ink 1.1.0-alpha07 低延迟手写、带线宽与墨色浓淡的触控笔压感、可选的端侧手写转标准字、框选多模态提问、扁平 PDF 导出，以及可移植的 `.ainote` 文档格式。

## 当前状态

v1 基线代码已在 `codex/implement-design` 分支实现。JVM 格式/存储/AI 请求测试、Ink 1.1.0-alpha07 下的 debug APK 和 release R8 构建均已通过；图片打开、页面显示稳定性与 Material 3 工具区修复已记录在 `docs/IMPLEMENTATION.md`。USI 笔感、掌拒、长 PDF 内存和旋转 CropBox 对齐仍需真机验收，不能用桌面构建结果代替。

## 开发

- Android Studio，JDK 17
- Android SDK 36
- Windows：`gradlew.bat test assembleDebug`
- macOS/Linux：`./gradlew test assembleDebug`

完整环境配置、release 签名和故障排查见 [`docs/BUILDING.md`](docs/BUILDING.md)。

若 Windows 的 Gradle Test Worker 在含中文的仓库路径下报测试类找不到，可先把仓库映射到短盘符再运行，例如 `subst R: "%CD%"`，随后执行 `R:\gradlew.bat test assembleDebug`。这不影响 APK 内容。

文档入口见 [`docs/README.md`](docs/README.md)。真机墨水延迟必须在 USI Android 平板上验收。

## 隐私

笔记默认仅保存在应用私有目录。AI 是用户主动触发的 BYOK 功能，只有被框选的页面区域、问题和 API Key 会发送到用户配置的 OpenAI 兼容地址。

## License

[MIT](LICENSE)
