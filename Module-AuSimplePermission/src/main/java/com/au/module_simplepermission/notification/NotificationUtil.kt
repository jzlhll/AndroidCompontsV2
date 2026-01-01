package com.au.module_simplepermission.notification

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleOwner
import com.au.module_simplepermission.INotification
import com.au.module_simplepermission.createPermissionForResult
import com.au.module_simplepermission.permission.IOnePermissionResult

internal class NotificationUtil(private val context: Context) : INotification {
    private val notificationMgr: NotificationManagerCompat = NotificationManagerCompat.from(context)

    /**
    @description 检测权限列表是否授权，如果未授权，遍历请求授权
     */
    override fun safeRun(
        permissionHelper: IOnePermissionResult,
        block: () -> Unit
    ) {
        permissionHelper.safeRun(block)
    }

    override fun notification(
        id: Int,/*通知id，唯一*/
        notification: Notification
    ) {
        val isEnabled = isEnabled()
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                    || ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
            && isEnabled) {
            notificationMgr.notify(id, notification)
        }
    }

    override fun isCanNotify(): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            || ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    override fun notificationSimpleText(
        id: Int,/*通知id，唯一*/
        channelId: String,/*通知渠道id*/
        content: String,
        @DrawableRes smallIcon: Int,
        @DrawableRes largeIcon: Int?,
        title:String?,
        importance: Int,
        jumpMainAndClearNotification:Boolean,
        jumpActivityClass:Class<*>?,
        channelBuildAction: ((NotificationChannelCompat.Builder) -> Unit),
        notificationBuildAction: ((NotificationCompat.Builder) -> Unit),/*构建通知的额外参数*/
    ) {
        notification(id, channelId, smallIcon, largeIcon = largeIcon, importance,
            jumpMainAndClearNotification = jumpMainAndClearNotification,
            jumpActivityClass = jumpActivityClass,
            channelBuildAction,
             notificationBuildAction = {
                it.setContentText(content)
                if (title != null) it.setContentTitle(title)
                notificationBuildAction.invoke(it)
            })
    }

    override fun notification(
        id: Int,/*通知id，唯一*/
        channelId: String,/*通知渠道id*/
        @DrawableRes smallIcon: Int,/*logo，不传报错*/
        @DrawableRes largeIcon: Int?,/*logo，不传报错*/
        importance: Int,
        jumpMainAndClearNotification:Boolean,
        jumpActivityClass:Class<*>?,
        channelBuildAction: ((NotificationChannelCompat.Builder) -> Unit),
        notificationBuildAction: (NotificationCompat.Builder) -> Unit,/*构建通知的额外参数*/
    ) {
        if (notificationGetChannel(channelId) == null) {
            notificationChannel(channelId, channelId, importance, channelBuildAction)
        }
        //构建通知
        val notification = notificationBuilder(channelId, smallIcon, largeIcon)
            .also {
                notificationBuildAction.invoke(it)
                if (jumpMainAndClearNotification) {
                    val intent = Intent(context, jumpActivityClass)
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    val pendingIntent = PendingIntent.getActivity(context,0, intent, PendingIntent.FLAG_IMMUTABLE)
                    it.setContentIntent(pendingIntent)
                }
            }.build()

        if (jumpMainAndClearNotification) {
            notification.flags = notification.flags or Notification.FLAG_AUTO_CANCEL
        }
        //发送通知
        notification(id, notification)
    }

    override fun notificationBuilder(
        channelId: String,/*通知渠道*/
        @DrawableRes smallIcon: Int,/*logo，不传报错*/
        @DrawableRes largeIcon: Int?,
        importance: Int,
        channelBuildAction: ((NotificationChannelCompat.Builder) -> Unit),
    ): NotificationCompat.Builder {
        if (notificationGetChannel(channelId) == null) {
            notificationChannel(channelId, channelId, importance, channelBuildAction)
        }
        val build = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIcon)
        if (largeIcon != null) {
            build.setLargeIcon(BitmapFactory.decodeResource(context.resources, largeIcon))
        }
        return build
    }

    override fun notificationChannel(
        channelId: String,
        channelName: String,
        importance: Int,
        channelBuildAction: ((NotificationChannelCompat.Builder) -> Unit),/*渠道构建的额外参数*/
    ) {
        val channel = NotificationChannelCompat.Builder(channelId, importance)
            .setName(channelName)
            .also {
                channelBuildAction.invoke(it)
            }.build()
        notificationMgr.createNotificationChannel(channel)
    }

    override fun notificationCreateChannelGroup(
        groupId: String,
        groupName: String,
        channelBuildAction: ((NotificationChannelGroupCompat.Builder) -> Unit),/*渠道构建的额外参数*/
    ) {
        val channel = NotificationChannelGroupCompat.Builder(
            groupId
        ).setName(groupName)
            .also {
                channelBuildAction.invoke(it)
            }.build()
        notificationMgr.createNotificationChannelGroup(channel)
    }

    override fun notificationCancel(id: Int) {
        notificationMgr.cancel(id)
    }

    override fun notificationCancelAll() {
        notificationMgr.cancelAll()
    }

    override fun notificationDeleteChannel(channelId: String) {
        notificationMgr.deleteNotificationChannel(channelId)
    }

    override fun notificationDeleteChannelGroup(groupId: String) {
        notificationMgr.deleteNotificationChannelGroup(groupId)
    }

    override fun isEnabled() =
        notificationMgr.areNotificationsEnabled()

    override fun notificationGetChannel(channelId: String) =
        notificationMgr.getNotificationChannelCompat(channelId)

    override fun notificationGetChannelGroup(groupId: String) =
        notificationMgr.getNotificationChannelGroupCompat(groupId)
}

/**
 * 申请通知权限
 */
fun LifecycleOwner.createPostNotificationPermissionResult() =
    createPermissionForResult(Manifest.permission.POST_NOTIFICATIONS)