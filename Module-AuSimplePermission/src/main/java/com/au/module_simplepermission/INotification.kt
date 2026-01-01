package com.au.module_simplepermission

import android.app.Notification
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.au.module_simplepermission.permission.IOnePermissionResult

interface INotification {
    /**
    @description 检测权限列表是否授权，如果未授权，遍历请求授权
     */
    fun safeRun(
        permissionHelper: IOnePermissionResult,
        block: () -> Unit
    )

    /**
     * 发送通知
     * @param id 通知id，唯一
     * @param notification 通知对象
     */
    fun notification(
        id: Int,
        notification: Notification
    )

    fun isCanNotify(): Boolean

    /**
     * 发送简单文本消息
     * @param id 通知id，唯一
     * @param channelId 通知渠道id
     * @param content 通知内容
     * @param smallIcon logo，不传报错
     * @param largeIcon logo，不传报错
     * @param title 通知标题
     * @param importance 通知重要性
     * @param jumpMainAndClearNotification 是否跳转主界面并清除通知
     * @param jumpActivityClass 跳转的Activity类
     * @param channelBuildAction 渠道构建的额外参数
     * @param notificationBuildAction 构建通知的额外参数
     */
    fun notificationSimpleText(
        id: Int,
        channelId: String,
        content: String,
        @DrawableRes smallIcon: Int,
        @DrawableRes largeIcon: Int? = null,
        title: String? = null,
        importance: Int = NotificationManagerCompat.IMPORTANCE_DEFAULT,
        jumpMainAndClearNotification: Boolean = false,
        jumpActivityClass: Class<*>? = null,
        channelBuildAction: ((NotificationChannelCompat.Builder) -> Unit) = {},
        notificationBuildAction: ((NotificationCompat.Builder) -> Unit) = {}
    )

    /**
     * 发送一个通知，发送通知前必须先创建对应的渠道
     * @param id 通知id，唯一
     * @param channelId 通知渠道id
     * @param smallIcon logo，不传报错
     * @param largeIcon logo，不传报错
     * @param importance 通知重要性
     * @param jumpMainAndClearNotification 是否跳转主界面并清除通知
     * @param jumpActivityClass 跳转的Activity类
     * @param channelBuildAction 渠道构建的额外参数
     * @param notificationBuildAction 构建通知的额外参数
     */
    fun notification(
        id: Int,
        channelId: String,
        @DrawableRes smallIcon: Int,
        @DrawableRes largeIcon: Int? = null,
        importance: Int = NotificationManagerCompat.IMPORTANCE_DEFAULT,
        jumpMainAndClearNotification: Boolean = false,
        jumpActivityClass: Class<*>? = null,
        channelBuildAction: ((NotificationChannelCompat.Builder) -> Unit) = {},
        notificationBuildAction: (NotificationCompat.Builder) -> Unit
    )

    /**
     * 创建一个通知渠道
     * @param channelId 通知渠道id
     * @param smallIcon logo，不传报错
     * @param largeIcon logo，不传报错
     * @param importance 通知重要性
     * @param channelBuildAction 渠道构建的额外参数
     */
    fun notificationBuilder(
        channelId: String,
        @DrawableRes smallIcon: Int,
        @DrawableRes largeIcon: Int?,
        importance: Int = NotificationManagerCompat.IMPORTANCE_DEFAULT,
        channelBuildAction: ((NotificationChannelCompat.Builder) -> Unit) = {}
    ): NotificationCompat.Builder

    /**
     * 创建渠道
     * @param channelId 通知渠道id
     * @param channelName 通知渠道名称
     * @param importance 通知重要性
     * @param channelBuildAction 渠道构建的额外参数
     */
    fun notificationChannel(
        channelId: String,
        channelName: String,
        importance: Int = NotificationManagerCompat.IMPORTANCE_DEFAULT,
        channelBuildAction: ((NotificationChannelCompat.Builder) -> Unit) = {}
    )

    /**
     * 创建渠道组
     * @param groupId 渠道组id
     * @param groupName 渠道组名称
     * @param channelBuildAction 渠道构建的额外参数
     */
    fun notificationCreateChannelGroup(
        groupId: String,
        groupName: String,
        channelBuildAction: ((NotificationChannelGroupCompat.Builder) -> Unit) = {}
    )

    /**
     * 移除通知
     * @param id 通知id
     */
    fun notificationCancel(id: Int)

    /**
     * 移除所有通知
     */
    fun notificationCancelAll()

    /**
     * 删除渠道
     * @param channelId 通知渠道id
     */
    fun notificationDeleteChannel(channelId: String)

    /**
     * 删除渠道组
     * @param groupId 渠道组id
     */
    fun notificationDeleteChannelGroup(groupId: String)

    /**
     * 通知是否可用
     */
    fun isEnabled(): Boolean

    /**
     * 获取创建的渠道
     * @param channelId 通知渠道id
     */
    fun notificationGetChannel(channelId: String): NotificationChannelCompat?

    /**
     * 获取创建的渠道组
     * @param groupId 渠道组id
     */
    fun notificationGetChannelGroup(groupId: String): NotificationChannelGroupCompat?
}