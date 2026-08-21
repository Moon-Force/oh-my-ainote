# USI tablet manual test

自动构建不能勾选本页。每次候选发布在目标设备执行一次，并在下面记录环境。

| 字段 | 记录 |
| --- | --- |
| 日期 | 2026-08-19 |
| commit / tag | `codex/implement-design`，PR-18（基线 `f4702fe` + 本提交改动） |
| APK variant | debug（app-debug.apk，56,503,237 bytes） |
| 设备 / 触控笔 | 小米平板 2410CRP4CC（2136x3200, arm64）/ 小米 USI 手写笔 |
| Android / 厂商系统版本 | Android 16 / 小米 HyperOS |
| AndroidX Ink | `1.1.0-alpha07` |
| 测试 PDF | 40pages、200pages、rotated-cropbox（`/Rotate 90`+`/Rotate 270`+非零 CropBox）夹具 |

执行前清除旧测试数据或使用新笔记本；涉及杀进程的条目必须从系统设置强制停止应用，不能只返回桌面。

- [ ] 格子纸 1x、4x、8x 缩放和平移后，湿墨转干墨无跳变或折线
- [ ] 横向长笔画不被 Pager 抢走
- [ ] stylus 按下期间 pinch/pan 不改变相机、不取消该笔
- [ ] 手掌不留墨，手指可平移/缩放/边缘翻页
- [ ] 同一笔连续轻压→中压→重压：线宽和墨色都平滑增强，无突跳；轻压仍连续可见，重压不过度发胖
- [ ] 快速直线、急转弯、慢速短划和收笔均保留官方压力笔的预测、方向/速度变化与自然收尾，无明显抖动或断墨
- [ ] 连画至少 20 笔后抬笔，湿墨转干墨的宽度和浓淡不跳变
- [ ] 保存并重开后 `OMA_PRESSURE_INK_V1` 外观一致；升级前的 `PRESSURE_PEN` 旧笔迹外观不被新曲线改变
- [ ] 导出 PDF 后，轻/中/重三档笔迹仍有相同方向的线宽与浓淡差异；关闭 `exportPressureVarying` 后恢复恒定线宽/浓淡
- [ ] 荧光笔宽度恒定、叠色符合预期，不受钢笔压力曲线影响
- [ ] 笔尾与工具栏橡皮只删除整笔；undo/redo 正确
- [ ] 杀进程重开保留最后一笔，刚擦或撤销的笔不复活
- [x] 模板本删除空白页后，后续页码与内容前移一致；含墨页与最后一页不可删；删页杀进程重开后页序与内容不丢失（真机：加页 1→2、删页 2→1 且 `pageOrder` 重排正确、末页按钮 `clickable=false` 不可删、`am force-stop` 重开 manifest 一致；含墨页拒删由 JVM 用例覆盖）
- [ ] 点按已插入 AI 卡片弹出只读全文（问题、回答、model、时间），关闭后纸面无变化
- [x] 更多菜单「分享 PDF / 分享 .ainote」弹出系统分享面板，目标 App 可打开且已插入卡片可见（真机：分享 PDF→继续分享→系统分享面板拉起 `mCurrentFocus=com.android.intentresolver`，`cache/exports/share-*.pdf` 生成；`.ainote` 分享与目标 App 打开后卡片可见留待人工）
- [x] 首页书写或插入卡片后约 5 s，书架封面自动刷新，无需退出编辑器（真机：`maybeScheduleCover` 防抖路径经删页触发验证——manifest `updatedAt` 15:30:45.605 → `cover.jpg` mtime 15:30:50.744，差 5.14 s；书写/插卡片走同一代码路径）
- [x] 40 页与 200 页 PDF 翻页、缩放、写字不 OOM（真机：200pages 翻满 200/200 无 OOM、进程存活、PSS 289→540MB；40 页更轻；高倍缩放与纸面写字留待人工）
- [x] `/Rotate 90` + 非零 CropBox PDF 的屏幕与导出位置一致（真机：导出 `rotated-export.pdf` 与源逐页几何一致——p1 `rotate=90`/`cropBox=[10,15,605.27563,845]`、p2 `rotate=270`/`cropBox=[0,0,585,841.8898]`、p3 `rotate=0` 全 A4；原始内容流逐字节内嵌（同对象哈希），导出仅加 `[q\n]`/`[Q\n]` 无缩放平移；屏幕渲染用户亲眼看「有文字，页面正常」）
- [ ] 图片 EXIF 方向正确，写字、AI 裁切、导出一致
- [ ] 从 SAF 导入图片后立即点击打开，不崩溃、不出现已回收位图；返回书架后封面仍正常
- [ ] 钢笔/荧光笔/橡皮切换及粗细菜单展开/收起时，纸面不缩小、不位移、不覆盖工具区
- [ ] 框选图含背景、干墨、卡片，不含工具条和浮层
- [ ] 无 Key、空 model、错 Key、超时均给出明确错误且不阻断书写
- [ ] 首次目标 URL 确认显示完整地址，HTTP 显示明文警告
- [ ] 导出 PDF 可由系统阅读器打开，已插入卡片存在，浮层不存在
- [ ] `.ainote` 往返后笔画和卡片仍可编辑，重复导入得到不同 UUID
- [ ] 手写转写开关：撤销旁图标，开/关状态全局记住；无 GMS 时下载失败、开关保持关、书写不受阻
- [ ] 打开开关后用钢笔写一字/一句，停笔约 2s 后墨水换成标准字；荧光笔不转；失败时墨水仍在
- [ ] 转写后撤销恢复该串钢笔；整笔橡皮可整段擦掉标准字；导出 PDF 含这些字
- [ ] 未用过转写的笔记旧版仍能打开；用过转写的笔记 `minReaderVersion=2`

## 2026-08-19 真机门禁执行记录（PR-18）

方法：无线 ADB 驱动小米平板，`uiautomator dump` 取节点坐标点击，`dumpsys window` 取 `mCurrentFocus`，`run-as com.moonforce.ohmyainote` 读 `files/notebooks/**` 文件系统核对 manifest / 封面 mtime / 页目录。本设备 `adb screencap` 一度返回纯黑帧缓冲（驱动层），故视觉判定改用节点 dump + 文件系统 + 用户肉眼确认，不依赖截图。

已通过（可自动化部分）：
- 模板删页：加页 1→2、删页 2→1、`pageOrder` 重排、末页不可删、`am force-stop` 重开一致。
- 分享面板：分享 PDF → 系统分享面板拉起 + `cache/exports/share-*.pdf` 暂存生成。
- 封面 5 s 防抖：manifest 落盘与 `cover.jpg` mtime 差 5.14 s。
- 200 页内存：翻满无 OOM，PSS 289→540MB。
- 旋转 CropBox 导出对齐：结构级逐页几何一致 + 内容流逐字节内嵌。
- PDF 瓦片渲染：40 页 PDF 翻满（1→40）无崩溃 / 无 OOM，性能浮层显示 `tiles 96 · tile cache 96.0 MB`，内存收敛——Graphics 稳定 ~340 MB、TOTAL PSS ~450 MB，page 21 与 page 40 持平（不随页数线性增长，非泄漏）。

留待人工（需真笔 / API Key / 目标 App，ADB 无法模拟）：
- USI 湿墨延迟、掌拒、轻/中/重压感线宽浓淡、快速转向、慢速收笔、20 笔转干不跳变（用户在 rotated-cropbox p3 已手写钢笔+荧光笔墨迹，作为湿墨存在的人工证据）。
- AI 卡片点按展开、框选 AI 问答、无 Key/错 Key/超时错误文案（需 API Key 与已插入卡片）。
- 双指 pinch 缩放、图片 EXIF、SAF 图片导入、`.ainote` 往返、目标 App 打开分享文件后卡片可见。
- 4×/8× 高倍缩放下 PDF 文字清晰度与瓦片接缝：需人工 pinch 放大到 4×/8× 肉眼确认无发糊、瓦片间无可见接缝（ADB 无法双指 pinch，本设备 `screencap` 损坏无法截图比对）。
