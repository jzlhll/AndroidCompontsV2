package com.au.module_simplepermission

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.au.module_simplepermission.activity.ActivityForResult
import com.au.module_simplepermission.activity.GetMultipleContentsForResult
import com.au.module_simplepermission.activity.OpenMultipleDocsForResult
import com.au.module_simplepermission.activity.SystemTakePictureForResult
import com.au.module_simplepermission.activity.SystemTakeVideoFaceForResult
import com.au.module_simplepermission.activity.SystemTakeVideoForResult
import com.au.module_simplepermission.notification.NotificationUtil
import com.au.module_simplepermission.notification.createPostNotificationPermissionResult
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
 */
fun LifecycleOwner.getMultipleContentsForResult() = GetMultipleContentsForResult(this)

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
 * activity 跳转，返回拿结果。
 */
fun LifecycleOwner.createActivityForResult() : ActivityForResult = ActivityForResult(this)

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
            Uri.parse("package:" + activity.packageName))
        ActivityCompat.startActivityForResult(activity, intent, REQUEST_OVERLAY_CODE, null)
    }
}

/**
 * 请求弹窗权限。
 */
fun Context.hasFloatWindowPermission() : Boolean{
    return Settings.canDrawOverlays(this)
}

fun Context.hasPermission(vararg permissions:String) : Boolean {
    return checkPermission(*permissions).isEmpty()
}

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
 * 跳转到权限管理页面
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

fun viewToActivity(view: View) = view.context as? AppCompatActivity

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
 * 在适当的时机调用弹出系统请求权限。android13以下不需要调用。
 * @param permissionHelper 通过 [createPostNotificationPermissionResult] 创建而来。
 */
fun Context.requestNotificationPermission(permissionHelper: IOnePermissionResult,
                                              continueWork:(INotification?)->Unit = {}) { //可以做下cache保存。避免多次请求。
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notify = NotificationUtil(this)
        val isEnable = notify.isEnabled()
        val isCanNotify = notify.isCanNotify()
        if (!isEnable || !isCanNotify) {
            notify.safeRun(permissionHelper) {
                continueWork(notify)
            }
        } else {
            continueWork(notify)
        }
    } else {
        continueWork(null)
    }
}