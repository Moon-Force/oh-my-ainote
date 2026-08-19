# Product

## Promise

oh-my-ainote 是 Android 平板优先、本地优先、无账号的课堂与会议手写笔记本。v1 的首要标准是低延迟笔感，以及可靠地导出扁平 PDF。

## Delivery status

v1 基线功能已经实现并可生成 debug/release APK。图片导入打开、页面显示稳定性和 Material 3 编辑器工具区已完成修复/接入。当前实现差异记录在 `IMPLEMENTATION.md`；发布门禁还包括 `MANUAL_TEST.md` 中尚未执行的 USI 平板、长 PDF 与旋转 CropBox 真机验收。

## v1

- 一本笔记采用且只采用一种纸张来源：模板、PDF 或图片。
- 模板仅支持 blank、lined、grid；PDF 本不增删页；图片本可追加图片页。
- 笔写字，手指平移、缩放和翻页。
- 工具为可调颜色/粗细的钢笔与荧光笔、整笔橡皮、框选提问和手形平移；撤销/重做是独立历史动作。
- AI 为 BYOK：框选页面区域后发送 JPEG 与问题到用户配置的 OpenAI 兼容地址。
- AI 回答默认仅存在于浮层；用户主动 Insert 后才成为页面卡片并参与导出。
- 无 API Key 或离线时，书写、翻页和 PDF 导出不受影响。
- 书架支持单层文件夹，无标签、账号和同步。
- 工作副本保存在应用私有目录；可导出扁平 PDF 与 `.ainote` ZIP。

## Non-goals

iPad、Flutter、PPTX 解析、OCR、全文搜索、录音、形状识别、套索移动、像素橡皮、账号与云同步均不属于 v1。

## Privacy

只有用户主动框选的合成图、问题和 Bearer Key 会发往用户确认的 `baseUrl`。已插入 AI 卡片会进入 PDF 与 `.ainote`；未插入浮层不会。

## Release gate

- JVM 编解码、原子恢复、坐标角点和 OpenAI 兼容请求测试必须通过。
- debug APK 与启用 R8 的 release APK 必须构建成功，且依赖树不得出现 iText。
- `MANUAL_TEST.md` 必须在目标 USI 平板上完成并记录设备、Android 版本和构建 commit。
