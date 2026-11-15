# 竹节笔记 / Bambootes 图标

中文名称为“竹节笔记”，英文名称为“Bambootes”。正式采用“竹节笔”，纯白背景（`#FFFFFF`）搭配竹绿色（`#257956`）笔杆和竹叶。图标不包含文字。

![01-bamboo-pen.png](01-bamboo-pen.png)

| 资源 | 文件 |
| --- | --- |
| 图标设计源 | [01-bamboo-pen.svg](01-bamboo-pen.svg) |
| 正式 SVG | [ic_launcher.svg](../ic_launcher.svg) |
| Android 前景 | [ic_launcher_foreground.xml](../../src/main/res/drawable/ic_launcher_foreground.xml) |
| Android 单色前景 | [ic_launcher_monochrome.xml](../../src/main/res/drawable/ic_launcher_monochrome.xml) |
| 简体中文教程配图 | [launcher.webp](../../src/main/assets/welcome/zh-CN/launcher.webp) |
| 英文教程配图 | [launcher.webp](../../src/main/assets/welcome/en/launcher.webp) |

设计源和正式 SVG 使用相同形状。Android 自适应图标使用纯白背景与透明矢量前景；Android 13 及以上的主题图标使用同形单色前景，笔尖留白以透明镂空实现。教程配图为 512 × 640 的 WebP，中文显示“竹节笔记”，英文显示“Bambootes”，图片校验值保存在对应的 `document.json`。

通过 [render-options.mjs](render-options.mjs) 从 SVG 源重新生成全部 PNG 与对比图，需要 Node.js、`@resvg/resvg-js` 和中文字体。渲染器可以安装在仓库外，通过环境变量指定依赖目录：

```bash
PICNOTE_RENDER_MODULE_ROOT=/tmp/picnote-prototype-render/node_modules node PicNote/artwork/bambootes/render-options.mjs
```
