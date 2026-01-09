package com.allan.autoclickfloat.activities.startup

import androidx.fragment.app.Fragment
import com.allan.autoclickfloat.BuildConfig
import com.au.module_simplepermission.gotoAccessibilityPermission
import com.au.module_simplepermission.gotoFloatWindowPermission
import com.au.module_androidui.dialogs.ConfirmCenterDialog
import com.au.module_simplepermission.hasFloatWindowPermission
import com.au.module_simplepermission.isAccessibilityEnabled

/**
 * @author allan
 * @date :2024/4/17 9:53
 * @description:
 */
class PermissionsHelper {
    companion object {
        fun showGotoSystemAccessibilityPermission(fragment: Fragment) : Boolean{
            val ac = fragment.requireActivity()
            if (!isAccessibilityEnabled(ac, BuildConfig.APPLICATION_ID)) {
                ConfirmCenterDialog.show(fragment.childFragmentManager,
                    "请授予辅助权限",
                    "请点击确定，跳转去辅助服务，找到应用「AShoot辅助点击方案」，并建议开启快捷开关。如果已经存在快捷开关，则可以快捷开关开启。",
                    "确定") {
                    ac.gotoAccessibilityPermission()
                    it.dismissAllowingStateLoss()
                }
                return false
            }
            return true
        }

        fun showGotoSystemFloatWindowPermission(fragment: Fragment) : Boolean{
            val ac = fragment.requireActivity()
            if (!ac.hasFloatWindowPermission()) {
                ConfirmCenterDialog.show(fragment.childFragmentManager,
                    "请授予悬浮窗权限",
                    "请点击确定，跳转去设置，找到应用「AShoot辅助点击方案」开启悬浮窗权限。",
                    "确定") {
                    ac.gotoFloatWindowPermission()
                    it.dismissAllowingStateLoss()
                }
                return false
            }
            return true
        }
    }
}