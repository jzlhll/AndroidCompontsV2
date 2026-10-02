# 儿童监控

独立 Android/KMP 工程。Android Studio 打开本目录，不需要把模块加入外层组件仓库。环境为 JDK 17、Android SDK 36、最低 API 26，Wrapper 固定 Gradle 8.14.3。

功能扩展分批交付状态见 [功能扩展](docs/功能扩展.md)。

## 实现范围

`shared` 承载默认配置、Room/DataStore 持久化、应用级 MonitorRuntime、观测判定、提醒调度、统计评价及文件操作协调器。`androidApp` 承载单 Activity、Nav3 页面、CameraX/模型/编码/语音/播放适配器。

已接通以下代码路径：

- 默认头像与配置 → 摄像头授权、空间检查 → 录像和定位 → 持续检测、悬浮调参、暗屏 → 主动停止或中断保存。
- 原始采集时间 → 首个实际编码样本 → 会话时间；视频无音轨，480 短边 / 854 长边上限，分析最低 640×480，模型按 5 Hz 输入且只允许一帧推理在途。
- 座位框坐标转换、空座位基准与正常坐姿校准、低头 / 偏头 / 身体侧倾、离座 / 回座、未知区间、人工重新定位、连续无效超时。
- 固定语音、提醒优先级 / 间隔 / 取消、连续在座休息许可、休息窗口、普通离座与首次合理离座计时。
- 结算统计与建议、历史评价快照、正常结算首次礼花、保存失败重试 / 保留统计 / 放弃。
- 日期分组的三列相册、游标分页、全选 / 部分删除失败、清理模式保护当前会话；视频播放、事件筛选、休息时间轴和定位回看。
- 定位步骤与原因提示、提醒音量/试听、目标时长与可选到点结束、参数恢复和帮助。
- 空间占用、仅删录像保留统计、可选保留期限；日期/状态/备注检索、近 7/30/90 天每日趋势、录像导出及统计卡片。
- 事件误报标记、家长 PIN 与停止防误触；数据库继续保持 v1，以当前实体字段为准。
- 头像草稿提交、暗屏偏好、更多设置；监控设置和偏好分别校验版本，运行中设置与配置边界在同一 Room 事务提交。

## 持久化与恢复

正式内容位于应用 `filesDir/child-monitor/`：

```text
database/monitor.db
preferences/user.preferences_pb
sessions/YYYY-MM-DD/<sessionId>/
  manifest.json
  recording.pending.mp4
  video.mp4
```

日期按启动时的时区固定。数据库只存相对路径；清单经临时文件 fsync、重命名和目录 fsync 提交。保存与删除先记录 OperationIntent，重试复用原会话和文件，删除级联清理数据库行及缩略图。只有完整封装并通过媒体探测的文件才会成为 Saved。

恢复先完成删除，再处理待保存或缺少评价的记录；检查点仅在事实事务成功后推进，评价在最终事实提交后生成。同进程重试优先提交仍在内存中的停止事实；只能恢复旧检查点时，将剩余尾段记为未知，不延续旧单调时钟或重新推算行为。单条恢复失败不阻塞其他记录。没有数据库身份的孤立目录保留，不自动构造完整记录；MP4 不可恢复时可保留统计。

开始、停止、保存和删除由应用级 Runtime 串行接受，页面退出不撤销已接受任务。采集回调按会话与 generation 隔离。相机、Surface、编码器或模型未确认释放时，继续禁用开始；该保护状态需要结束进程后重新进入恢复流程。

## 定位与离座判断

固定设备后框选并确认座位区域，先保持座位空置、无遮挡约 3 秒；提示空座位已确认后，让孩子入座保持正常坐姿约 3 秒。重新定位也执行此顺序，并清除旧目标、离座身份和休息许可；已有连续无效超时不会因点击重新定位而重置。

空座位由区域内亮度、色差、纹理和人体占位与空景基准共同判断。已观察到连续离开轨迹后，人物走出画面仍可依据匹配的空座位完成离座确认；仅凭人体未检出或全图亮度正常不能确认离座。遮挡或空景不匹配时记为未知。该方案是保守启发式，光照变化、低纹理背景及复杂遮挡仍需设备样本校准。

休息响应窗口按开始离座时刻判断，候选确认不会清除许可；实际休息豁免、普通离座提醒与结算只计算可靠覆盖。首次离座仍按设置中的允许时长提醒，评价另保留首次合理离座额度。锁屏和其他中断语音独立于保存调度；提醒记录写入失败进入受控停止，不能从异常处理分支再次抛出数据库错误。

相册与详情在前台接收记录变更，返回页面时重新查询；刷新保留已加载分页范围并校验选中项。查询失败和播放器失败分别重试，结果刷新不重复领取或播放礼花。

## Android 适配决策

正式录像使用 CameraX `VideoOutput` 加 MediaCodec / MediaMuxer，Recorder 只提供 SD 质量配置。这样能够在首个实际编码样本写入时固定媒体起点，不把 Recorder Start 通知当作第一帧。方向读取 SurfaceRequest 的变换信息；未知摄像头时钟源明确拒绝启动。

该适配器使用 CameraX 的受限高级接口，版本固定为 1.6.1；其精确版本接口兼容性、GL 处理下的方向与 PTS 映射仍需完整工程编译和目标设备验证。Debug 能力探针使用 Recorder，只检查三用例并发和基础媒体属性，不能替代正式编码链路验收。

模型固定为 ML Kit Pose `18.0.0-beta5` 与打包的 EfficientDet-Lite0 INT8。模型来源、尺寸和 SHA-256 位于 `assets/models/manifest.json`。姿态和场景质量阈值属于当前规则配置，尚无真机准确率结论。

语音为预生成的普通话 AAC 文件，文本、来源和哈希位于 `assets/voice/manifest.json`，运行时直接播放。图像为内置矢量头像。应用仅申请 CAMERA，不申请麦克风权限；不主动上传视频或观测数据；导出仅由用户发起，保存位置由系统选择器指定。

## 验收状态

第 1–11 阶段的主体实现已落在工程中，第 12 阶段的故障保护已接入。不能据此宣称完整 Android 闭环已通过验收：

- 共享业务（不含 KSP 生成的数据库实现）完成过 Kotlin 类型核对；全部 Kotlin 文件完成语法核对。Android 依赖和应用整体编译尚未验证。
- Room `exportSchema = true` 与 KSP 已配置，但 `shared/schemas/` 目前没有生成的 v1 JSON。首次工程构建后需审阅并纳入版本管理，不能把空目录算作 Schema 交付。
- 真机三用例并发、编码时间映射、坐姿/离座误判、遮挡/多人/移动设备、长时资源占用、锁屏与进程恢复均待验收。
- 默认编码目标 2 Mbps，以 30 分钟 × 1.2 估算启动空间和滚动低水位；实际码率、机型能力及长时资源参数需依据设备测量调整。

完整阶段表与设备验收项见原需求目录的 `实现进度V1.md`：`/Users/allan/coz/allan-skills/todos/儿童监控/`。

## 文案维护

`copy/catalog.json` 是界面与固定语音文本的文案来源。修改后在本目录执行 `yarn mobile-copy:generate`，通过 `yarn mobile-copy:check` 核对生成资源；无需安装 Node 依赖。替换语音文件时一并更新 voice manifest 中的内容与哈希。

## 依赖依据

[Android KMP](https://developer.android.com/kotlin/multiplatform/plugin)、[AGP 8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes)、[Room](https://developer.android.com/jetpack/androidx/releases/room)、[Nav3](https://developer.android.com/jetpack/androidx/releases/navigation3)、[ML Kit Pose](https://developers.google.com/ml-kit/vision/pose-detection/android)、[MediaPipe Object Detector](https://ai.google.dev/edge/mediapipe/solutions/vision/object_detector/android)。
