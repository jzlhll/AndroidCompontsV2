package com.au.module_simplepermission

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.au.module_simplepermission.activity.ActivityForResult
import com.au.module_simplepermission.activity.GetAudioForResult
import com.au.module_simplepermission.activity.GetAudiosForResult
import com.au.module_simplepermission.activity.GetContentForResult
import com.au.module_simplepermission.activity.GetMultipleContentsForResult
import com.au.module_simplepermission.activity.OpenMultipleDocsForResult
import com.au.module_simplepermission.activity.SystemTakePictureForResult
import com.au.module_simplepermission.activity.SystemTakeVideoFaceForResult
import com.au.module_simplepermission.activity.SystemTakeVideoForResult
import com.au.module_simplepermission.permission.AlwaysRunPermissionForResult
import com.au.module_simplepermission.permission.IMultiPermissionsResult
import com.au.module_simplepermission.permission.IOnePermissionResult
import com.au.module_simplepermission.permission.PermissionForResult
import com.au.module_simplepermission.permission.PermissionsForResult
import com.au.module_simplepermission.permission.SelectSysDirForResult

const val REQUEST_OVERLAY_CODE: Int = 1001


/**
 * 专门用于文档操作，系统会自动授予持久化权限，适合需要长期访问的场景
 */
fun LifecycleOwner.openMultipleDocsForResult() = OpenMultipleDocsForResult(this)

/**
 * 主要用于选择内容（如图片、文件），但返回的Uri权限是临时的，应用进程结束后会失效
 * 应用重启后，可以通过 takePersistableUriPermission 来尝试重新获取。
 */
fun LifecycleOwner.getMultipleContentsForResult() = GetMultipleContentsForResult(this)

/**
 * 主要用于选择单个内容（如图片、文件），但返回的Uri权限是临时的，应用进程结束后会失效
 */
fun LifecycleOwner.getContentForResult() = GetContentForResult(this)

/**
 * 获取音频
 */
fun LifecycleOwner.getAudioForResult(audioMime:String = "audio/*") = GetAudioForResult(this, audioMime)

/**
 * 获取多条音频
 */
fun LifecycleOwner.getAudiosForResult(audioMime:String = "audio/*") = GetAudiosForResult(this, audioMime)

/**
 * 这是为了解决 Android 存储访问框架（Storage Access Framework, SAF）的核心问题：
 * 在 Android 11（API 30）及以上版本，应用无法再通过 WRITE_EXTERNAL_STORAGE 权限随意写入公共存储空间。
 * 取而代之的，是更安全的、由用户控制的授权方式。
 * 目标：获取对某个目录（及其所有子目录）的持久访问权限。
 * 结果：系统返回一个表示该目录的 Uri（通常是一个 DocumentFile 对象），应用此后就可以无需再次询问用户，直接在该目录下创建、读取、写入或删除文件。
 *
 * 权限授予后，会记录在系统中。应用重启后，可以通过 takePersistableUriPermission 来尝试重新获取。
 */
fun LifecycleOwner.selectSysDirForResult() = SelectSysDirForResult(this)

fun LifecycleOwner.systemTakePictureForResult() = SystemTakePictureForResult(this)

fun LifecycleOwner.systemTakeVideoForResult() = SystemTakeVideoForResult(this)

fun LifecycleOwner.systemTakeVideo2FrontForResult(isFront:Boolean = false,
                                                       maxSec:Int = 60,
                                                       isLowQuality:Boolean = true)
        = SystemTakeVideoFaceForResult(this, isFront, maxSec, isLowQuality)

/**
 * 多权限的申请
 */
fun LifecycleOwner.createMultiPermissionForResult(permissions:Array<String>)
        : IMultiPermissionsResult
    = PermissionsForResult(this, permissions)


/**
 * 多媒体权限的申请
 */
fun LifecycleOwner.createMediaPermissionForResult(types:Array<PermissionMediaType>)
        : IMultiPermissionsResult
    = createMultiPermissionForResult(PermissionMediaHelper().getRequiredPermissions(types))

/**
 * 单权限的申请
 */
fun LifecycleOwner.createPermissionForResult(permission:String) : IOnePermissionResult
        = PermissionForResult(this, permission)

/**
 * google新要求：尽量不要使用自定义的图片选择器，使用系统的。
 * 请求一张系统图片或者视频
 */
fun LifecycleOwner.pickerForResult() = MultiUriPickerContractResult(this, 1, CompatMultiPickVisualMedia(1))

/**
 * google新要求：尽量不要使用自定义的图片选择器，使用系统的。
 * 请求多张系统图片或视频
 */
fun LifecycleOwner.multiPickerForResult(maxItem:Int)
        = if(maxItem > 0)
    MultiUriPickerContractResult(this, maxItem, CompatMultiPickVisualMedia(maxItem))
else throw RuntimeException("max item must > 0")

/**
 * 跳转到辅助服务
 */
fun LifecycleOwner.gotoAccessibilityPermission() {
    val activity = when (this) {
        is Fragment -> requireActivity()
        is AppCompatActivity -> this
        else -> {
            throw IllegalArgumentException("gotoAccessibilityPermission error call.")
        }
    }
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    activity.startActivityPermissionFix(intent)
}

/**
 * 判断辅助服务是否开启
 */
fun isAccessibilityEnabled(context: Context, applicationID:String) : Boolean {
    val accessibilityMgr = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    if (accessibilityMgr.isEnabled) {
        val enableServices = accessibilityMgr.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (sv in enableServices) {
            if (sv.resolveInfo.serviceInfo.packageName.equals(applicationID)) {
                return true
            }
        }
    }
    return false
}

/**
 * 请求弹窗权限。
 */
fun LifecycleOwner.gotoFloatWindowPermission() {
    val version = true //Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    val activity = when (this) {
        is Fragment -> requireActivity()
        is AppCompatActivity -> this
        else -> {
            throw IllegalArgumentException("requestFloatWindowPermission error call.")
        }
    }
    if (version && !Settings.canDrawOverlays(activity)) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            ("package:" + activity.packageName).toUri())
        ActivityCompat.startActivityForResult(activity, intent, REQUEST_OVERLAY_CODE, null)
    }
}

/**
 * 请求弹窗权限。
 */
fun Context.hasFloatWindowPermission() : Boolean{
    return Settings.canDrawOverlays(this)
}

/**
 * 检查权限
 */
fun Context.hasPermission(vararg permissions:String) : Boolean {
    return checkPermission(*permissions).isEmpty()
}

/**
 * 检查权限 并返回剩余没有的权限
 */
fun Context.checkPermission(vararg permissions:String) : Array<String> {
    val noPermissionList = mutableListOf<String>()
    for (permission in permissions) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            noPermissionList.add(permission)
        }
    }
    return noPermissionList.toTypedArray()
}

/**
 * 是否可以显示权限申请对话框
 */
fun canShowPermissionDialog(activity:Activity, permission:String) : Boolean{
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
}

/**
 * 是否需要跳转到权限管理页面
 */
fun ifGotoMgrAll(showDialogBlock:()->Unit) : Boolean{
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val ex = Environment.isExternalStorageManager()
        if (!ex) {
            showDialogBlock()
        }
        return ex
    }

    return true
}

/**
 * 跳转到权限管理MANAGE APP页面
 */
fun gotoMgrAll(context: Context) {
    val intent = Intent().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            action = Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
        }
        data = "package:${context.packageName}".toUri()
    }
    context.startActivityPermissionFix(intent)
}

/**
 * 兼容性启动activity
 */
internal fun Context.startActivityPermissionFix(intent: Intent, opts:Bundle? = null) {
    if (this !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        startActivity(intent, opts)
    } catch (e:Exception) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Android 10 或更高版本
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            // Android 10 以下版本
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent, opts)
    }
}

/**
 * 申请通知权限
 */
fun LifecycleOwner.createPostNotificationPermissionResult() =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        PermissionForResult(this, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        AlwaysRunPermissionForResult(this)
    }