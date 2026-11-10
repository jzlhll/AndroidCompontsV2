# AGit 实现结构

## Compose 与导航

`MainActivity` 仅安装系统 Splash、启用 edge-to-edge 并挂载 `AGitTheme` 和 `AGitApp`。业务页面不使用 Fragment。Material 3 处理主题与组件；窄屏使用抽屉，宽屏使用常驻项目列表。

系统 Splash 保留到本地主题读取完成且实际主题的 Compose 首屏已成功组合。主题未就绪时不组合白色回退页面；启动页、过渡窗口使用一致的深色品牌背景，错误页面同样可以结束启动等待，不因配置读取失败停留在 Splash。

`AppTheme` 包含 `Black`、`White`、`Dracula`、`Sage`，保存在 `Catalog` 中。`AGitRoot` 收集主题后组合 `AGitTheme`，主题同时更新 Material 3 和系统栏明暗。色板参考 `Notepadmm/src/main/resources/css/` 的 `colors_ember.css`、`colors_mist.css`、`colors_dracula.css`、`colors_sage.css`。状态前景色和底色集中在同一个 `ThemePalette`，四套主题的二十组状态文字/底色对比度最低约为 5.12:1。

`AGitApp.kt` 仅组装主题、目录加载态、导航宿主和全局弹窗。路由键集中在 `NavKeys.kt`，导航状态与表单完成后的跳转由 `AGitNavigationState.kt` 管理，页面路由绑定和响应式抽屉承载放在 `AGitNavHost.kt`。`ProjectDrawer.kt`、`TokenGroupPickerDialog.kt` 和 `TokenRoute.kt` 分别负责抽屉、分组选择及 Token 表单数据加载。

Navigation 3 `1.2.1` 使用 `@Serializable NavKey`、`rememberNavBackStack`、`NavDisplay` 和两个 `NavEntryDecorator` 保存返回栈、Compose 状态及页面 ViewModel。导航参数只传 `projectId`、目录或文件相对路径，Token 不进入参数或 SavedState。业务状态与一次性界面状态分离，重新组合不启动 Git 任务。

页面切换统一使用 220ms 水平推入动画：进入时新页面从右侧滑入，旧页面向左滑出；返回及预测返回时方向相反。整个页面平移，不叠加文字或页面透明度渐变。

Koin 的 `agitModule` 注册应用级 `CatalogStore`、`TokenVault`、`UserFeedback`、`AGitRepository` 和 Git 依赖。`AppViewModel` 由宿主持有；`RepositoryViewModel` 以当前 `NavEntry` 为 owner，通过 Koin 参数接收 `projectId`。界面优先使用 StateFlow 和 `collectAsStateWithLifecycle`，通过方法调用发起业务操作。

## 数据与 Git

- `CatalogStore` 使用 DataStore 保存 Token 分组、项目、作者、当前分组 `selectedGroupId`、当前项目 `selectedProjectId`、外部编辑会话和待完成的临时分支推送目标。读取失败不当作空数据覆盖。`Catalog.currentGroup` / `currentProject` 统一解析当前上下文；旧数据缺少分组字段时兼容最近项目，显式选中的空分组不回退到其他分组的项目。选择操作取消并等待上一任务，持久化成功后再切换页面；删除分组时一并更新当前分组和项目。
- `TokenVault` 使用 Android Keystore AES-GCM，分组 ID 作为 AAD；凭据存放在共享目录之外，应用禁用备份。
- 没有有效作者邮箱时，添加分组使用两步表单：`Next` 仅把 Token 草稿放在 `AppViewModel` 内存中，作者页 `Save` 再通过一次 DataStore 更新保存加密分组和作者。返回第一步可恢复内存草稿；离开流程或 ViewModel 销毁时清空字符数组，进程恢复时要求重新填写 Token。草稿恢复检查仅在对应 `AuthorKey` 实际位于栈顶时执行，并读取 ViewModel 最新草稿和保存完成态；退场动画中的作者页不触发提示或回退。
- `Author.isConfigured` 仅要求有效邮箱，`Author.displayName` 优先显式姓名、否则完整邮箱；`Author.commitName` 优先显式姓名、否则邮箱 `@` 前的部分，供 JGit `PersonIdent` 使用。
- `AGitRepository` 以 project ID 分配 Mutex，同一仓库的扫描、Git 写入和文件回写串行执行。阻塞操作进入 IO；ViewModel 状态在主线程更新。
- `GitProject.cloneMode` 保存 `Full`、`Depth` 或 `Download`，`depth` 仅保存浅 clone 的初始深度；旧项目默认 `Full`。`hasGit` 由模式推导，不依赖文件系统猜测。
- 三种模式先写入 `filesDir/temp/clones/<projectId>/content/`，完成下载、检出或解压并关闭资源后，原子改名为 `filesDir/shared/<projectId>/`，清理临时归档后登记 Catalog。失败或取消时，在项目锁内等待引擎和进度回写停止，再以不可取消的清理删除未完成目录。`AppViewModel` 启动后延迟 3 秒调用残留检查，复用相同项目锁，清理 temp 和未登记的正式目录；Catalog 读取失败时不把已有目录视作孤立目录。
- `JGitEngine` 固定使用 Java 11 兼容的 JGit `6.10.1.202505221210-r`，包含浅 clone 支持和 CVE-2025-4949 修复，只使用 GitHub HTTPS 和单一 `origin`。clone 传输超时为 180 秒；`Depth` 模式调用 `setDepth`，后续 fetch 保留已有浅边界。缺少共同祖先且仓库仍有浅边界时，明确提示历史深度不足。Git 凭据只提供给 `github.com`，不写入 URL 或 Git 配置。禁用 hooks、外部 filter、JMX 注册和系统 Git 探测。
- `GitHubArchiveDownloader` 通过 GitHub REST ZIP 归档接口获取默认分支，Token 仅发送到 `api.github.com`，重定向限于 API 与 `codeload.github.com` 的 HTTPS 地址。先写入临时 ZIP，再校验路径和文件 CRC，去掉统一的归档顶层目录后解压；拒绝路径穿越和 `.git` 条目，不创建符号链接。取消时关闭当前连接，并等待下载和解压退出。
- `AGitRepository.lockedProject` 为文件读取提供共用项目锁；`locked` 只允许 Git 项目并负责打开 JGit。无Git 项目的刷新跳过 snapshot、候选副本和指纹扫描；文本仅以 `ACTION_VIEW` 和读取授权交给外部应用，不开启编辑会话。
- commit 重新检查选中文件的指纹，使用 `CommitCommand.setOnly` 隔离未选中的暂存项；新文件先加入索引。重命名使用 JGit 的检测结果组合原、新路径。
- 更新先 fetch，再检查共同历史、上游、dirty、ahead 和 behind，只执行 `FF_ONLY`。推送先检查并展示仓库、目标及提交；确认后重新 fetch，仅推送明确的单个目标引用。
- 暂存与文件选择均以整文件为单位；未选中的工作区和暂存项不会隐式进入 commit。Git 操作不执行 stash、rebase、冲突合并、强制推送或自动重试。

## 外部文件与媒体

仓库根目录固定为 `filesDir/shared/<projectId>/`。复用公共模块的 FileProvider 和 `getUriFromFile`，每次仅授权所选文件。拒绝 `.git`、绝对路径、父目录穿越及符号链接；结束编辑时撤销临时 URI 权限。用户必须明确结束编辑，返回 AGit 或收到 Activity 结果不会被视作写入已完成。

候选副本检测仅针对曾经打开编辑的文件、同目录中随后出现的未跟踪文件和明确的后缀规则。提示保存原文件和候选文件指纹，覆盖前再次核对并使用同目录原子替换；保留候选文件，拒绝后对同一内容不重复提示。外部应用保存在仓库之外的副本不在检测范围。

图片复用 `Module-ImageCompressed` 的采样加载并显式限制解码尺寸。视频采用 Media3 Compose `PlayerSurface`，只在页面 STARTED 时持有播放器，停止时释放。文本由外部应用查看和编辑。

## 操作进度与日志

`AppViewModel` 持有实际操作 Job 与 `OperationState`；逐字动画、光标、省略号与计时只读取步骤状态，不控制任务时序。快速完成的步骤立即补全命令；执行中屏蔽误关闭及重复发起。只在 clone、独立 fetch 阶段显示取消入口，协程等待阻塞调用离开当前阶段后才报告取消。commit、分支创建、快进与 push 阶段不提供取消。

Clone 页面和执行弹窗的标题固定为 `Clone Repository`。`GitDownloadConnectionFactory` 沿用 JGit 的 JDK 连接和 TLS 会话，仅累计 `git-upload-pack` 响应流实际读取的字节；通过有归属的进度协程限频回写 `OperationStep.receivedBytes`，不把对象数量换算成下载总大小。`MainActivity` 在 STARTED 生命周期收集 Clone 是否运行，运行时设置屏幕常亮，结束或退出前台时清除；字节数更新不重复修改窗口标志。

`SettingsKey → LogsKey → LogFileKey` 在同一 Navigation 3 返回栈内展示日志。`LogsViewModel` 由各个 NavEntry 持有，Koin 注入 `LogRepository` 与 `UserFeedback`；只在进入页面和用户刷新时读取，不注册全局 Activity 监听或文件监听。列表按日期分组、显示文件名和大小，沿用模块的查看/删除/压缩分享行为。内容复用 `FileLog.logParser` 解析，可隐藏元信息、切换换行，并按级别着色。

日志读取先等待 `FileLog.logHandler` 中已入队的写入完成，再在 IO 线程读取快照，不设置 `FileLog.ignoreWrite`。内容按 256KB 分页，从最新一页进入并允许加载更早记录；刷新取消并等待上一任务。压缩分享仅包含用户选中的最多三份 `.log` 文件，输出位于 `cacheDir/shared/logs/`，使用既有 FileProvider 临时只读授权，不扩大共享根目录。

实际异常通过 `safeFailure` 转成英中字符串资源和受控英文错误码。用户错误编号由 `AGitErrorCode` 集中定义，`appString` 和 `errorStringResource` 统一在正文末尾追加编号；资源只保留可翻译正文。内部英文错误码继续用于业务判断，不与用户编号混用。Toast 复用模块 `ToastBuilder`，操作日志复用 `FileLog`。不记录原始异常正文、凭据、文件内容或提交说明。应用级操作状态跨配置变化保存；进程结束后只从磁盘恢复仓库与编辑记录，不恢复或自动重跑旧 Job。

## 兼容性边界

仅 `agit` 将最低版本设为 Android 13（API 33），其他模块的最低版本沿用根工程设置。沿用根工程的 compile / target SDK、AGP、Kotlin 和 Compose BOM，保留 Java API desugaring。JGit 通过独立文件系统适配、私有配置和禁用 JMX 避开桌面依赖路径；不要求系统安装 `git`。

Android 13 原生提供 `InputStream.readNBytes` 和 `transferTo`，覆盖当前 JGit 的相关 I/O 调用。JGit 版本使用严格约束，不自动迁移到需要更新 Java API 的主版本。`AndroidGitEnvironment` 保留私有配置隔离，进度监听实现 `showDuration` 在内的完整接口。

JGit 的 Android 真机运行、GitHub 私有仓库认证、网络取消及实际推送仍需要设备联调确认。源码/API 检查不能替代这些验证，当前不能把该闭环视为已经通过设备验收。未覆盖 Git LFS、submodule 操作、SSH、其他托管服务和手机端冲突解决。

## 参考

- [Android 架构建议](https://developer.android.com/topic/architecture/recommendations)
- [系统 Splash 与本地设置加载](https://developer.android.com/develop/ui/views/launch/splash-screen)
- [Navigation 3 状态保存](https://developer.android.com/guide/navigation/navigation-3/save-state)
- [Navigation 3 版本](https://developer.android.com/jetpack/androidx/releases/navigation3)
- [Koin Compose ViewModel](https://insert-koin.io/docs/reference/koin-compose/compose-viewmodel/)
- [JGit CommitCommand](https://download.eclipse.org/jgit/site/6.4.0.202211300538-r/org.eclipse.jgit/apidocs/org.eclipse.jgit/org/eclipse/jgit/api/CommitCommand.html)
- [Media3 Compose](https://developer.android.com/media/media3/ui/compose)
