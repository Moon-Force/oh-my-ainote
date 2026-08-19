# `.ainote` Format v1

本文件是持久化格式的权威说明。交换文件是扩展名 `.ainote` 的标准 ZIP；工作副本是同样目录树的未压缩形式。

## Coordinates

- 单位：PDF point（1/72 inch）。
- 原点：旋转并裁切后的显示页左上角。
- x 向右，y 向下。
- 页面尺寸等于显示尺寸；视口缩放和平移从不写入笔画。
- 图片导入时先烘焙 EXIF，再令长边为 792 pt。

Page space 到 PDF 未旋转用户空间的映射如下。CropBox 为 `(clx,cly,clw,clh)`；显示尺寸为 `W,H`。

| rotate | x_user | y_user |
| --- | --- | --- |
| 0 | `clx + x` | `cly + H - y` |
| 90 | `clx + y` | `cly + x` |
| 180 | `clx + W - x` | `cly + y` |
| 270 | `clx + H - y` | `cly + W - x` |

## Layout

```text
manifest.json
media/source.pdf
media/images/{pageId}.{jpg|png}
media/cards/{cardId}.jpg
media/cover.jpg              # 可选、可重建，不是格式必填
pages/{pageId}/page.json
pages/{pageId}/strokes.bin
```

`library.json` 与 notebooks 目录并列，只描述本机单层文件夹，不进入 `.ainote`。

## Manifest

必填：`formatVersion=1`、`minReaderVersion=1`、UUID `id`、`title`、`kind`、UTC 时间、`pageCount`、`pageOrder` 与 `defaultPage`。`kind` 仅为 `template|pdf|image`。模板、PDF source 与 image source 是互斥 tagged data；所有页面背景必须匹配 manifest kind。重复导入时可写 `importedFromId` 记录原包 ID，但当前工作副本的 `id` 必须是新 UUID。

未知的未来字段应忽略；`formatVersion` 或 `minReaderVersion` 高于阅读器能力时拒绝打开。

## Page

页面保存 `id/index/widthPt/heightPt/background/strokes/cards`。PDF 背景必须同时保存 `sourcePath/pdfPageIndex/rotate/mediaBox/cropBox`。`rotate` 只能是 0、90、180、270。

笔画元数据保存 UUID、工具、brush ID、ARGB、size、epsilon、起始时间、二进制 offset/length、point count 与 AABB。AABB 仅用于整笔橡皮粗筛。`stockBrush` 字段保留历史命名，v1 允许：

- `PRESSURE_PEN`：旧笔迹，按 AndroidX Ink 官方 `pressurePen` 原样重建。
- `OMA_PRESSURE_INK_V1`：当前钢笔；以 Ink 1.1.0-alpha07 官方 `pressurePen` 为基底，保留其预测、收笔、速度、方向和高压变宽行为，再追加仅触控笔生效的 30 ms 压力阻尼。压力 `p` 在 `[0,0.8]` 时宽度倍率从 `0.55` 线性到 `1.0`，在 `(0.8,1]` 时沿官方曲线从 `1.0` 到 `1.5`；不透明度倍率在 `[0,1]` 从 `0.35` 线性到 `1.0`。缺失压力按倍率 `1.0`。
- `HIGHLIGHTER`：官方荧光笔。

笔刷 ID 是持久化语义，不得把自定义笔刷冒充 `PRESSURE_PEN`；后续调整曲线必须新增版本化 ID。

已插入卡片保存绑定选区、固定 anchor、问答、缩略图路径、model 与创建时间。未插入 AI 浮层不属于格式。

## OmaInputsV1

全部为小端且无对齐填充：

```text
magic "OMA1" : 4 bytes
version       : u16 = 1
reserved      : u16 = 0
strokeCount   : u32
for each stroke:
  pointCount  : u32
  for each point:
    x,y       : f32 page-pt
    tMs       : u32 relative to stroke start
    flags     : u8 (pressure=1, tilt=2, orientation=4)
    padding   : 3 bytes
    pressure? : f32
    tilt?     : f32 radians
    orient?   : f32 radians
```

点的可选值必须按 flags 读取，不能按定长截断。`strokes.bin` 的 stroke 数必须等于 `page.json.strokes.size`。

## Import and atomicity

每次导入 `.ainote` 都分配新 UUID，不覆盖或合并原 ID，并落入未归档。ZIP entry 必须经过路径规范化，拒绝绝对路径与 `..`。当前阅读器最多接受 10,000 个 entry 和 2 GiB 解压总量。

打包时不包含 `tmp/`、`.bak` 或其他恢复中间态。`media/cover.jpg` 可以随工作目录存在，但阅读器不得依赖它打开笔记；缺失时由客户端重建。

单页提交单位是整个页目录。已有页更新流程：`live -> live.bak`，`tmp -> live`，验证后删除 `.bak`。若仅有 `.bak` 则恢复旧页；live 与 `.bak` 同在且 live 有效时保留 live；live 损坏时尝试有效 `.bak`。

## PencilKit mapping

Oma `(x,y)` 可直接作为左上 y 向下的页面位置；`tMs/1000` 映射到 `timeOffset`；pressure 映射到 force；Android tilt 近似映射为 `altitude = π/2 - tilt`；orientation 映射到 azimuth；pen/highlighter 映射到 `PKInk(.pen/.marker)`。倾斜与压力需要真机校准。
