# 竹节笔记 / Bambootes 原型图

主界面只有 `节点 / 文档` 两个 Tab。节点结构、独立编辑页面、二级插入菜单与拖动确认合并以 [05-节点设计.md](../05-节点设计.md) 为准。本目录图片仅作为内容排布、图片比例与设置显现态参考，不作为当前导航、菜单或节点数量的验收依据。

首次进入直接打开内置教程。“＋新建”打开独立页面，填写必填标题与可选引言，点击“创建”成功后生成固定标题与主引言。无图节点使用固定 72dp 内容区，图片正文卡使用 4:3 预览；所有节点通过设置进入独立编辑页面。

[11-首次使用文档.webp](11-首次使用文档.webp) 展示教程素材与完整 Markdown 阅读内容。

![11-首次使用文档.webp](11-首次使用文档.webp)

| 图片 | 内容 |
| --- | --- |
| [11-首次使用文档.webp](11-首次使用文档.webp) | 引言、手机桌面图、打开程序说明与 TODO |
| [01-节点页.png](01-节点页.png) | 72dp 纯文与章节卡、4:3 图片与 32dp 备注 |
| [02-文档页.png](02-文档页.png) | 按节点顺序显示原图与用户内容 |
| [03-文档抽屉.png](03-文档抽屉.png) | 右上角新建文档 |
| [04-节点编辑.png](04-节点编辑.png) | 完整原图与文字排布参考；页面结构以 05 文档为准 |
| [06-节点插入菜单.png](06-节点插入菜单.png) | 菜单位置参考；菜单内容以 05 文档为准 |
| [07-设置显隐.png](07-设置显隐.png) | 设置显现态；0.5s 渐显、停留 3s、0.5s 渐隐 |
| [08-文字节点编辑.png](08-文字节点编辑.png) | 无图节点编辑完整文字 |
| [09-创建章节.png](09-创建章节.png) | 章节标题输入 |

详细交互见 [03-程序界面设计.md](../03-程序界面设计.md)。图片是静态状态示例，不承载可操作交互。

首次使用原型由 [generate-welcome-prototype.mjs](generate-welcome-prototype.mjs) 读取正式 assets 中的中文教程和配图生成，输出 1080 × 1500 的 WebP。中英文桌面图由内置 `image_gen.imagegen` 生成，缩为 512 × 640；两张配图和原型均使用 `~/bin/pastehub-image` 按质量 90 压缩。完整提示词见 [教程配图提示词.md](教程配图提示词.md)。

在仓库根目录生成首次使用原型（需要 Node.js、npm、`~/bin/pastehub-image` 和中文字体；示例使用 macOS 的 `Hiragino Sans GB`）：

```bash
npm install --prefix /tmp/picnote-prototype-render --cache /tmp/picnote-prototype-npm-cache --no-audit --no-fund @resvg/resvg-js@2.6.2
PICNOTE_RENDER_MODULE_ROOT=/tmp/picnote-prototype-render/node_modules node PicNote/原型图/generate-welcome-prototype.mjs
```

命令生成 `11-首次使用文档.webp`，渲染依赖与中间图片放在临时目录，不修改仓库的包配置。其他节点样式示例的矢量源保存在 [generate-prototypes.mjs](generate-prototypes.mjs)。
