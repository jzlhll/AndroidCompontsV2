# 工具与弹窗 (utils)

## 概述

系统图片选择器、拍照功能和组合弹窗工具。

## 系统图片选择器

### PhotoPicker 扩展函数

**multiPickForResult(maxItem)** - 多选图片
```kotlin
fun Fragment.multiPickForResult(maxItem: Int)
    : MultiPhotoPickerContractResult
```

**pickForResult()** - 单选图片
```kotlin
fun Fragment.pickForResult()
    : MultiPhotoPickerContractResult
```

**isPhotoPickerAvailable()** - 检查可用性
```kotlin
fun isPhotoPickerAvailable(context: Context): Boolean
```

### MultiPhotoPickerContractResult

封装 ActivityResultContract 的选择器结果类。

**核心方法：**
- `setCurrentMaxItems(max)` - 动态设置最大选择数
- `launchByAll(type, option, callback)` - 启动选择器

**PickerType 枚举：**
- `IMAGE` - 仅图片
- `VIDEO` - 仅视频
- `IMAGE_AND_VIDEO` - 图片 + 视频

**使用示例：**
```kotlin
// 多选图片（最多 9 张）
val picker = fragment.multiPickForResult(9)
picker.launchByAll(PickerType.IMAGE, null) { uris ->
    // 处理选择的图片
}

// 动态修改最大数量
picker.setCurrentMaxItems(5)
```

## 拍照工具

### CameraPermissionHelp

封装相机权限和拍照流程。

**核心方法：**
- `safeRunTakePic()` - 安全拍照（需权限检查）
- `safeRunTakePicMust()` - 强制回调

**回调模式：**
```kotlin
cameraHelper.safeRunTakePicMust(
    compress = true,
    qualityType = "default"
) { mode, uri ->
    when (mode) {
        "takePicAndCompressed" -> // 压缩成功
        "takePicAndCompressFailUseOrig" -> // 压缩失败用原图
        "takePicResultDirect" -> // 未压缩
        "takePicNoResult" -> // 无结果
        "notGivePermission" -> // 拒绝权限
    }
}
```

**自动压缩：**
- 拍照后自动调用 `useCompress()`
- 支持配置压缩质量
- 失败回退到原图

## 组合弹窗 TakePhotoActionDialog

底部弹窗，提供"拍照"和"选择图片"两个选项。

### 接口定义

```kotlin
interface ITakePhotoActionDialogCallback {
    fun onClickTakePic(): Boolean
    fun onClickSelectPhoto()
    fun onNothingTakeDialogClosed()
}
```

### 弹出方法

**pop(owner, callback, cameraText, photosText)**
```kotlin
// Fragment 或 AppCompatActivity 作为 owner
TakePhotoActionDialog.pop(fragment, callback)
```

### 内部流程

1. 弹出底部 Dialog
2. 用户点击"拍照" → `onClickTakePic()` → 触发相机权限
3. 用户点击"选择" → `onClickSelectPhoto()` → 启动选择器
4. 关闭回调 → `onNothingTakeDialogClosed()`

## CameraAndSelectPhotosPermissionHelper

统一封装拍照和选择的高级工具类。

**构造参数：**
```kotlin
CameraAndSelectPhotosPermissionHelper(
    fragment: Fragment,
    maxNum: Int = 9,
    pickerType: PickerType = IMAGE,
    supplier: ICameraFileProviderSupply
)
```

**核心方法：**
- `showTakeActionDialog(maxNum, pickerType)` - 显示组合弹窗
- `launchSelectPhotos(callback)` - 直接启动选择器

**使用示例：**
```kotlin
// 1. 创建助手
val helper = CameraAndSelectPhotosPermissionHelper(
    fragment = this,
    maxNum = 9,
    pickerType = PickerType.IMAGE,
    supplier = cameraFileProvider
)

// 2. 设置拍照回调
helper.takePhotoCallback = object : TakePhotoActionDialog.ITakePhotoActionDialogCallback {
    override fun onClickTakePic(): Boolean {
        // 处理拍照
        return true
    }
    override fun onClickSelectPhoto() {
        // 处理选择
    }
    override fun onNothingTakeDialogClosed() {
        // 弹窗关闭
    }
}

// 3. 显示弹窗
helper.showTakeActionDialog(9, PickerType.IMAGE)

// 4. 或直接启动选择器
helper.launchSelectPhotos { uris ->
    // 处理选择的图片
}
```

## 完整流程示例

```kotlin
class MyFragment : Fragment(), TakePhotoActionDialog.ITakePhotoActionDialogCallback {
    
    private val helper by lazy {
        CameraAndSelectPhotosPermissionHelper(
            fragment = this,
            maxNum = 9,
            supplier = cameraFileProvider
        )
    }
    
    override fun onClickTakePic(): Boolean {
        return helper.cameraHelper.safeRunTakePicMust { mode, uri ->
            if (uri != null) {
                // 使用拍照结果
                loadImage(uri)
            }
        }
    }
    
    override fun onClickSelectPhoto() {
        helper.launchSelectPhotos { uris ->
            // 使用选择的图片
            loadImages(uris)
        }
    }
    
    override fun onNothingTakeDialogClosed() {
        // 清理状态
    }
    
    fun showPicker() {
        helper.showTakeActionDialog(9, PickerType.IMAGE)
    }
}
```

## 最佳实践

1. **复用实例** - `CameraAndSelectPhotosPermissionHelper` 建议全局变量
2. **回调处理** - 必须实现所有回调方法
3. **压缩策略** - 拍照后建议压缩，减少内存占用
4. **权限处理** - 使用 `safeRunTakePicMust` 确保 一定有 回调
5. **最大数量** - 根据业务场景合理设置 maxNum
