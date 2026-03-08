package com.allan.androidlearning.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import com.allan.androidlearning.copied.notify.NotificationUtils
import com.allan.classnameanno.EntryFrgName
import com.au.module_android.click.onClick
import com.au.module_android.log.logdNoFile
import com.au.module_android.utils.asOrNull
import com.au.module_android.utils.dp
import com.au.module_androidui.ui.views.ViewFragment
import com.au.module_simplepermission.PermissionMediaType
import com.au.module_simplepermission.createMediaPermissionForResult
import com.au.module_simplepermission.createPostNotificationPermissionResult
import com.au.module_simplepermission.permission.IOnePermissionResult

@EntryFrgName(priority = 10)
class PermissionTestFragment : ViewFragment() {

    private val notificationPermissionHelper = createPostNotificationPermissionResult()

    private val mediaPermissionHelper = createMediaPermissionForResult(
        arrayOf(
            PermissionMediaType.IMAGE,
            PermissionMediaType.VIDEO,
            PermissionMediaType.AUDIO
        ))

    override fun onUiCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            
            // 第一个是申请通知权限
            addView(Button(requireContext()).also {
                it.text = "申请通知权限"
                it.textSize = 20f
                it.width = 300.dp
                it.height = 60.dp
                it.onClick {
                    notificationPermissionHelper.safeRun {
                        NotificationUtils.showNormalNotification("aaaa", "dddd")
                    }
                }
            })

            // 第二个是申请媒体权限
            addView(Button(requireContext()).also {
                it.text = "申请媒体权限"
                it.textSize = 20f
                it.width = 300.dp
                it.height = 60.dp
                it.onClick {
                    mediaPermissionHelper.safeRun {
                        NotificationUtils.showNormalNotification("aaaa", "ccc")
                    }
                }
            })


            // 第二个是申请媒体权限
            addView(Button(requireContext()).also {
                it.text = "动态申请通知权限"
                it.textSize = 20f
                it.width = 300.dp
                it.height = 60.dp
                it.onClick {
                }
            })
        }
    }
}
