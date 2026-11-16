# 数据迁移

- 后续修改均须评估数据迁移与旧版本兼容，涵盖 Room、配置、本地文件及媒体引用。
- Room 结构变更须同步维护版本、schema 和 Migration 或 AutoMigration，保留已有用户数据，禁止以清库或 `fallbackToDestructiveMigration` 代替迁移。
- 迁移须考虑默认值、数据转换、重复执行和中断恢复；交付时说明兼容限制与未解决风险。

# 横屏二级页面布局

- 横屏相关公共组件集中在 UiEx 的 `com.au.module_androiduiex.landscape` 包，不依赖 PicNote 的资源或业务状态。
- 所有二级页面统一复用 UiEx 的 `SecondaryPageScaffold`，包括 `EditorScreen`、`SettingsScreen`、`AppearanceScreen` 和 `LanguageScreen`；返回按钮的无障碍文案由页面通过 `backContentDescription` 传入。
- 首页与二级页面共用 `LandscapePaneLayout` 分配左右区域，页面各自提供侧栏和内容。
- 横屏采用左右分栏：左侧操作列占扣除系统安全区后可用宽度的 30%，右侧内容区占其余宽度；不通过固定最大宽度改变该比例。
- 左列第一行仅放返回按钮；第二行放页面标题，更多按钮或其他附加操作放在标题行最右侧；没有附加操作时不添加占位按钮。
- 创建、保存等主要按钮固定在左列最底部；语言、外观等点选即生效的页面保留即时生效方式，不增加确认步骤。
- 右侧列表、图片和输入内容独立纵向滚动，不在右侧重复放置标题栏或主要按钮。
- 使用系统安全区及 `Modifier.imePadding()` 处理键盘与系统栏；旋转、窗口变化时保留草稿、选中项及内容滚动状态。
- 竖屏保留顶部返回、标题和操作按钮的布局。
