package com.au.module_imagecompressed

import android.content.Context
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts

// 检查照片选择器是否可用
fun isPhotoPickerAvailable(context: Context) : Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // 在 Android 13 (API 33) 及以上版本中，照片选择器始终可用
        true
    } else {
        // 对于 Android 11 (API 30) 到 Android 12L (API 32)
        // 需要通过 Google 系统更新接收模块化系统组件的设备才可能可用
        // 对于更旧的版本 (API 19-29) 和某些 Android Go 设备，可能通过 Google Play 服务获得后向移植版本
        // 实际开发中应使用 AndroidX 库中的方法进行检查
        val availability = ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)
        availability
    }
}