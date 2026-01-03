### Module-AuSimplePermission

<img src="project_logo.png" alt="logo" width="300"/>

**Android权限管理模块，提供简化的权限申请和系统交互API。**

- minSdk: 24   compileSdk: 36
- 基于AndroidX Activity Result API
- 支持Activity、Fragment、View作为上下文

#### 功能特性

- **权限申请**
  - 单权限申请：支持单个运行时权限申请
  - 多权限申请：支持批量权限申请
  - 多媒体权限申请：兼容Android 14+的媒体权限策略（READ_MEDIA_IMAGES/VIDEO/AUDIO）

- **系统功能**
  - 系统拍照：调用系统相机拍照
  - 系统录像：支持普通录像和前置摄像头录像（可配置时长、画质）
  - 文档选择：支持选择多个文档（持久化权限）或内容（临时权限）
  - 系统目录选择：选择系统目录

- **Activity跳转**
  - 启动Activity并获取结果
  - 跳转到应用详情页

- **特殊权限**
  - 辅助功能权限：跳转到辅助服务设置
  - 悬浮窗权限：跳转到悬浮窗权限设置
  - 存储管理权限：跳转到权限管理页面（Android 11+）
  - 通知权限：请求通知权限（Android 13+）

- **通知功能**
  - 创建通知、通知渠道、通知渠道组
  - 发送简单文本通知
  - 取消通知、删除渠道等完整通知管理API

- **工具函数**
  - 权限检查：检查单个或多个权限状态
  - 权限对话框判断：判断是否可以显示权限申请对话框
  - 悬浮窗权限检查
  - 存储管理权限判断



#### 使用方式

**注意：所有ForResult对象需要在onCreate之前声明为全局变量，然后在具体位置使用。**

```kotlin
class MainActivity : AppCompatActivity() {
    // 全局声明ForResult对象
    private val cameraPermission = createPermissionForResult(Manifest.permission.CAMERA)
    private val multiPermissions = createMultiPermissionForResult(arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    ))
    private val mediaPermissions = createMediaPermissionForResult(arrayOf(
        PermissionMediaHelper.MediaType.IMAGE,
        PermissionMediaHelper.MediaType.VIDEO
    ))
    private val takePicture = systemTakePictureForResult()
    private val takeVideo = systemTakeVideoForResult()
    private val takeVideoFront = systemTakeVideo2FrontForResult(
        isFront = true,
        maxSec = 60,
        isLowQuality = true
    )
    private val openDocs = openMultipleDocsForResult()
    private val getContents = getMultipleContentsForResult()
    private val selectDir = selectSysDirForResult()
    private val activityForResult = createActivityForResult()
    private val notificationPermission = createPostNotificationPermissionResult()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // 在这里使用
        requestCameraPermission()
    }
    
    private fun requestCameraPermission() {
        cameraPermission.safeRun(
            block = {
                // 权限已授予
            },
            notGivePermissionBlock = {
                // 权限未授予
            }
        )
    }
}
```

##### 权限申请

```kotlin
// 单权限申请
cameraPermission.safeRun(
    block = {
        // 权限已授予
    },
    notGivePermissionBlock = {
        // 权限未授予
    }
)

// 多权限申请
multiPermissions.safeRun(
    notGivePermissionBlock = {
        // 权限未全部授予
    }
) {
    // 所有权限已授予
}

// 多媒体权限申请（兼容Android 14+）
mediaPermissions.safeRun {
    // 媒体权限已授予
}
```

##### 系统功能

```kotlin
// 系统拍照
takePicture.start(uri) { success ->
    if (success) {
        // 拍照成功
    }
}

// 系统录像
takeVideo.start(uri) { success ->
    if (success) {
        // 录像成功
    }
}

// 前置摄像头录像（可配置时长、画质）
takeVideoFront.start(uri) { success ->
    if (success) {
        // 录像成功
    }
}

// 选择多个文档（持久化权限）
openDocs.start(arrayOf("*/*")) { uris ->
    // 处理选中的文档
}

// 选择多个内容（临时权限）
getContents.start(arrayOf("image/*", "video/*")) { uris ->
    // 处理选中的内容
}

// 选择系统目录
selectDir.start(uri) { success ->
    if (success) {
        // 目录选择成功
    }
}
```

##### Activity跳转

```kotlin
// 启动Activity并获取结果
activityForResult.start(intent) { result ->
    // 处理返回结果
}

// 跳转到应用详情页
activityForResult.jumpToAppDetail(context)
```

##### 特殊权限

```kotlin
// 辅助功能权限
gotoAccessibilityPermission()

// 悬浮窗权限
gotoFloatWindowPermission()

// 存储管理权限（Android 11+）
gotoMgrAll(context)

// 通知权限（Android 13+）
notificationPermission.safeRun {
    // 通知权限已授予
}

// 检查权限状态
hasPermission(Manifest.permission.CAMERA)
hasFloatWindowPermission()

// 判断是否需要跳转到权限管理页面
ifGotoMgrAll {
    // 需要跳转
}
```

##### 通知功能

```kotlin
// 请求通知权限
requestNotificationPermission(notificationPermission) { notification ->
    notification?.notificationSimpleText(
        id = 1,
        channelId = "channel_id",
        content = "通知内容",
        smallIcon = R.drawable.ic_notification,
        largeIcon = null,
        title = "通知标题",
        importance = NotificationManagerCompat.IMPORTANCE_HIGH,
        jumpMainAndClearNotification = true,
        jumpActivityClass = MainActivity::class.java,
        channelBuildAction = { /* 自定义渠道配置 */ },
        notificationBuildAction = { /* 自定义通知配置 */ }
    )
}
```
