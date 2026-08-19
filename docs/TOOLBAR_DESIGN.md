# 编辑器工具区设计

## 目标

编辑器工具区服务于横屏平板和手写笔：纸面始终是视觉主体，书写工具有明确的选中状态，颜色与粗细可就地修改，页面操作不与书写模式混在一起。工具属性展开时不得导致纸面突然缩放或位移。

选定视觉方向：[`assets/editor-toolbar-material3-concept.png`](assets/editor-toolbar-material3-concept.png)。

实现状态：已完成并通过真机视觉/核心交互检查，对比记录见 [`../design-qa.md`](../design-qa.md)。

## 官方依据

- [Material components in Compose](https://developer.android.com/develop/ui/compose/components)：优先使用 Material 组件表达操作、选择和反馈。
- [Icon buttons](https://developer.android.com/develop/ui/compose/components/icon-button)：常用次级动作采用含无障碍说明的图标按钮，并通过 filled / tonal 状态表达选择。
- [Resources in Compose — Icons](https://developer.android.com/develop/ui/compose/graphics/images/material)：工具图标使用 Google Material Symbols 的 Android Vector Drawable，不引入已停止维护的整套旧 Material Icons 库。
- [Support different display sizes](https://developer.android.com/develop/adaptive-apps/guides/support-different-display-sizes)：布局按可用窗口空间组织，不假设固定设备比例。
- [Quick guide to animations in Compose](https://developer.android.com/develop/ui/compose/animation/quick-guide)：状态变化使用 `AnimatedVisibility`、颜色动画和锚定弹出层；避免为了装饰而持续动画。
- [Stylus input on large screens](https://developer.android.com/guide/topics/large-screens/stylus-tier-1)：书写、擦除、压感和防误触是大屏笔记应用的核心体验。

## 布局

1. 顶部应用栏只保留返回、截断后的文档标题、页码和更多菜单。导出与调试信息进入更多菜单。
2. 应用栏下方保留固定 `140dp` 工具区，避免属性行展开/收起时改变纸面测量尺寸。
3. 第一行由两个浮动 tonal surface 组成：
   - 钢笔、荧光笔、整笔橡皮、框选、手形为互斥模式。
   - 撤销与重做是独立历史动作，不伪装成模式。
4. 钢笔或荧光笔激活时显示第二行：实时笔画预览、五个颜色、四个粗细预设和精细粗细滑杆入口。
5. 上一页、下一页、页码、适合宽度和按类型允许的加页操作独立放在纸面右下角。

## 交互与状态

- 所有主要点击目标不小于 `44–48dp`。
- 当前工具使用 primary container 和圆角形态表示，不只依赖图标颜色。
- 颜色和粗细选择具有单选语义；工具图标均提供中文 `contentDescription`。
- 钢笔与荧光笔分别记住本次编辑会话中的颜色和粗细。
- 颜色与粗细直接进入 AndroidX Ink `Brush`；每一笔仍将实际 ARGB 与 `sizePt` 写入既有笔画记录。
- 整笔橡皮、框选、平移、撤销/重做、翻页、适合宽度和导出继续调用原有业务路径。

## 动效

- 工具选中状态使用短促的颜色与圆角状态过渡。
- 属性行使用淡入/展开与淡出/收起，动画只解释状态变化。
- 精细粗细使用锚定菜单，不改变工具区高度。
- 不使用循环动画、玻璃拟态、渐变背景或会影响 Ink 绘制帧率的模糊效果。

## 保持不变的约束

- 编辑器仍只有一个 Compose `InProgressStrokes`。
- AndroidX Ink 压感笔刷、低压变细和浓淡逻辑不变。
- 页面坐标、持久化格式、原子提交、撤销/重做耐久路径不变。
- 本次调整不新增笔记格式字段，也不改变模块边界。
