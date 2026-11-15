# 图笺 / PicNote

本地图片与 Markdown 文档工具。用户收集截图、添加文字并调整节点顺序，文档页直接展示这些素材组成的图文笔记。

初版采用标准 Compose 单 `ComponentActivity` + Navigation 3。手动选图使用无需图片库权限的 Photo Picker；用户另行授予完整图片权限后，每次进入前台比较仅含 URI 与加入时间的快照，提取最新新增图片。授权用途弹窗仅主动提示一次，之后从首页图片权限旁的小箭头进入；不实现悬浮窗或后台截图监听。选图后通过 PasteHub AAR 转为质量常量 90 的 WebP，保存到私有 files 目录并持久化源 URI 映射。

主界面只有 `节点 / 文档` 两个 Tab。内容由用户维护，Markdown 由本地规则组织；整个流程不接入大模型，不设置解析、对话轮次或记忆系统。

首次添加素材前填写必填标题与可选引言。节点页包含 4:3 图片卡、固定 72dp 的纯文和章节卡；左侧圆点菜单可在当前节点前后插入普通节点或新章节。节点编辑采用背景模糊的应用内浮层，整个面板左右各留 24dp；普通页面的导航与进退动画参照 agit。

与 agit 共用 Black、White、Dracula、Sage 颜色方案及按钮样式，默认 Sage，可从文档抽屉底部切换并保存。抽屉无文档时隐藏顶部栏，居中显示 32sp 的“空空如也”及“+新建”。

图片检查仅由应用进入前台触发，顶栏不提供刷新入口。公共日志按 `startupId` 和 `foreground_enter` 序号记录进入次数，窗口焦点与多窗口回调另行记录，便于核对窗口模式。可选引言以灰字、24dp 缩进显示，Markdown 导出为 `>` 引用。

| 文档 | 内容 |
| --- | --- |
| [01-总体思路.md](01-总体思路.md) | 产品定位、对象与核心流程 |
| [02-文档与本地存储.md](02-文档与本地存储.md) | 节点到 Markdown 的映射、保存与导出 |
| [03-程序界面设计.md](03-程序界面设计.md) | 两个 Tab、节点图片与备注编辑、Markdown 阅读预览 |
| [原型图/README.md](原型图/README.md) | 原型索引与生成方法 |

![00-原型总览.png](原型图/00-原型总览.png)

初版范围以三份文档为准；原型中的归档、设置等未纳入入口不实现。

应用模块为 `:picnote`，应用 ID 为 `com.allan.picnote`，从仓库根工程打开。入口为 [MainActivity.kt](src/main/java/com/allan/picnote/MainActivity.kt) 与 [PicNoteApp.kt](src/main/java/com/allan/picnote/ui/PicNoteApp.kt)；文档保存见 [DocumentRepository.kt](src/main/java/com/allan/picnote/data/repositories/DocumentRepository.kt)，图片差异检查见 [MediaScanner.kt](src/main/java/com/allan/picnote/media/MediaScanner.kt)。
