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

**注意：所有`ForResult`对象需要在`onCreate`之前声明为全局变量（Fragment或Activity成员变量），然后在具体位置使用。**

##### 1. Activity跳转拿结果
```kotlin
    //全局变量
  private val activityForResult = createActivityForResult()


  activityResult.start(intent, optionsCompat, activityResultCallback)
  activityForResult.jumpToAppDetail(context)
```

##### 2. 基础文件与媒体选择

```kotlin
class MyFragment : Fragment() { //或者Activity也可以

    // 1. SAF目录授权：获取某个目录的长久读写权限
    val selectDirResult = selectSysDirForResult()

    // 2. 媒体选择（图片/视频）
    // 单选
    val origUriPickerResult = pickerForResult()
    // 多选
    val origMultiUriPickerResult = multiPickerForResult(9)

    // 3. 通用文档选择
    // 单选文件（start的时候，传入mimeType）
    val shortDocResult = getContentForResult()
    // 多选文件 （start的时候，传入mimeType）
    val shortDocsResult = getMultipleContentsForResult()

    // 4. 音频选择
    val audioResult = getAudioForResult()
    val audiosResult = getAudiosForResult()

    fun startSelect() {
        // 使用示例：
        
        // 选择目录
        selectDirResult.start(null) { uri -> 
            // uri为获取到的目录URI，可以长久使用。
        }

        // 单选图片/视频
        // PickerType: IMAGE, VIDEO, IMAGE_AND_VIDEO
        origUriPickerResult.start(PickerType.IMAGE) { uri ->
             // uri
        }

        // 多选图片
        origMultiUriPickerResult.start(PickerType.IMAGE) { uris ->
             // List<Uri>
        }

        // 选择PDF文件
        shortDocResult.start("application/pdf") { uri -> }
        
        // 选择音频
        audioResult.start(null) { uri -> }
    }
}
```

##### 2. 相机与相册综合助手

提供了`CameraPermissionHelp`和`CameraAndSelectPhotosPermissionHelper`来简化相机权限申请、FileProvider配置以及拍照/选图流程。

```kotlin
// 需实现 ICameraFileProviderSupply 接口提供 FileProvider
val cameraHelper = CameraPermissionHelp(this, object : ICameraFileProviderSupply {
    override fun createFileProvider(): Pair<File, Uri> {
        // 返回文件对象和对应的Uri
        return createFileProviderMine() 
    }
})

// 综合助手：包含拍照和多选图功能
val cameraAndSelectHelper = CameraAndSelectPhotosPermissionHelper(this, 9, supplier = object : ICameraFileProviderSupply {
    override fun createFileProvider(): Pair<File, Uri> {
        return createFileProviderMine()
    }
}).also {
    // 可选配置：复制文件到应用私有目录等
    it.multiResult.paramsBuilder.asCopyAndStingy()
}

// 使用方式：弹出选择框（拍照/相册）
// cameraAndSelectHelper.showTakeActionDialog(maxCount, PickerType.IMAGE)
```

##### 3. 通知与权限

```kotlin
// 1. 通知权限申请,android13会包裹，android12直接执行
val notificationResult = createPostNotificationPermissionResult()

// 2. 简易通知工具
val notificationUtil = NotificationUtil(this)

fun showNotification() {
    // 申请权限并执行
    notificationResult.safeRun(notGive = {
        // 权限未获取，提示用户去设置开启
        //showToast("请先开启通知权限")
    }) {
        // 权限已获取，发送通知
        notificationUtil.notificationSimpleText(
            id = 1001,
            channelId = "default_channel",
            title = "标题",
            content = "内容",
            smallIcon = R.drawable.ic_notification
        )
    }
}
```

##### 4. 特殊系统权限跳转

提供了便捷的扩展函数用于检查和跳转特殊权限设置页。

```kotlin
// 1. 辅助功能权限 (Accessibility)
fun checkAccessibility() {
    val ac = requireActivity()
    // 检查是否开启
    if (!isAccessibilityEnabled(ac, BuildConfig.APPLICATION_ID)) {
        // 跳转设置页
        ac.gotoAccessibilityPermission()
    }
}

// 2. 悬浮窗权限 (Float Window)
fun checkFloatWindow() {
    val ac = requireActivity()
    // 检查权限
    if (!ac.hasFloatWindowPermission()) {
        // 跳转设置页
        ac.gotoFloatWindowPermission()
    }
}

// 3. 管理所有文件权限 (Manage All Files - Android 11+)
fun checkManageAllFiles() {
    // 检查并决定是否需要跳转（内部封装了版本判断）
    if (ifGotoMgrAll {
        // 回调中执行跳转逻辑，通常配合Dialog提示用户
        gotoMgrAll(requireActivity())
    }) {
        // 已经拥有权限，直接执行业务逻辑
        doSomething()
    }
}
```
