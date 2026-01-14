package com.au.module_android.utils

import android.app.*
import android.content.Context
import android.os.Build
import androidx.annotation.DrawableRes

object ForeNotificationUtil {
    private const val CHANNEL_ONE_ID = "foreNotifyId"
    private const val NOTIFY_ID = 0x111
    private const val FOREGROUND_ID = 0x112

    private const val NO_SOUND = true

    /**
     * 公开使用
     */
    fun sendNotification(context: Context, channelName: String, channelDesc: String, contentTitle: String, contentText: String, @DrawableRes noBgIcon:Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFY_ID, getNotificationO(context, manager, channelName, channelDesc, contentTitle, contentText, noBgIcon, null))
    }

    /**
     * onStartCommand调用
     * @param foregroundServiceType 查阅ServiceInfo
     *
    常量名	取值 (int)	含义	最低 API	配套权限 / 说明
    FOREGROUND_SERVICE_TYPE_NONE	0	无特定类型（Android 12+ 不建议使用）	31	仅兼容旧逻辑
    FOREGROUND_SERVICE_TYPE_LOCATION	1	定位 / 导航类前台服务	31	需 ACCESS_FINE_LOCATION 等
    FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK	2	音频 / 视频播放类前台服务	31	无强制权限
    FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION	4	屏幕录制 / 投屏类前台服务	31	需 MEDIA_PROJECTION 权限
    FOREGROUND_SERVICE_TYPE_CAMERA	8	相机拍摄 / 录像类前台服务	31	需 CAMERA 权限
    FOREGROUND_SERVICE_TYPE_MICROPHONE	16	录音类前台服务	31	需 RECORD_AUDIO 权限
    FOREGROUND_SERVICE_TYPE_PHONE_CALL	32	通话类前台服务	31	需 READ_PHONE_STATE 等
    FOREGROUND_SERVICE_TYPE_HEALTH	64	健康数据类前台服务	31	需健康相关权限（如 BODY_SENSORS）
    FOREGROUND_SERVICE_TYPE_SHORTCUT	128	快捷方式 / 桌面小部件类前台服务（极少用）	31	无强制权限
    FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED	256	系统豁免类（仅系统应用可用）	31	第三方 APP 禁止使用
    FOREGROUND_SERVICE_TYPE_SPECIAL_USE	512	特殊用途（需谷歌审核）	33	第三方 APP 几乎用不到
    FOREGROUND_SERVICE_TYPE_SHORT_SERVICE	1024	短时前台服务（≤10 分钟）	34	时长受限，无需强制权限
    FOREGROUND_SERVICE_TYPE_DATA_SYNC	2048	数据同步类前台服务（云端 / 本地数据同步）	34	无强制权限，需匹配同步功能
     */
    @JvmStatic
    fun startForeground(service: Service,
                        channelName: String, channelDesc: String, contentTitle: String, contentText: String,
                        foregroundServiceType:Int?,
                        @DrawableRes noBgIcon:Int? = null,
                        pendingIntent: PendingIntent? = null) {
        val manager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = getNotificationO(service, manager, channelName, channelDesc, contentTitle, contentText, noBgIcon, pendingIntent)
        notification.flags = Notification.FLAG_ONGOING_EVENT
        notification.flags = notification.flags or Notification.FLAG_NO_CLEAR
        notification.flags = notification.flags or Notification.FLAG_FOREGROUND_SERVICE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 可指定前台服务类型（Android 13+ 需要 FOREGROUND_SERVICE_DATA_SYNC）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && foregroundServiceType != null) {
                service.startForeground(FOREGROUND_ID, notification, foregroundServiceType)
            } else {
                service.startForeground(FOREGROUND_ID, notification)
            }
        } else {
            service.startForeground(FOREGROUND_ID, notification)
        }
    }

    /**
     * onDestory之前调用
     */
    @JvmStatic
    fun stopForeground(service: Service) {
        service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
    }

    private fun getNotificationO(
        context: Context,
        manager: NotificationManager,
        name: String,
        desc: String,
        contentTitle: String,
        contentText: String,
        @DrawableRes noBgIcon:Int?,
        pendingIntent: PendingIntent?
    ): Notification {
        var channel: NotificationChannel? = null
        channel = NotificationChannel(CHANNEL_ONE_ID, name, if (NO_SOUND) NotificationManager.IMPORTANCE_MIN else NotificationManager.IMPORTANCE_DEFAULT)
        if (NO_SOUND) {
            channel.enableVibration(false) //震动不可用
            channel.setSound(null, null) //设置没有声音
        }
        channel.description = desc
        manager.createNotificationChannel(channel)
        val builder = Notification.Builder(context, CHANNEL_ONE_ID)
        builder.setCategory(Notification.CATEGORY_RECOMMENDATION)
            .setContentTitle(contentTitle)
            .setContentText(contentText) //.setContentIntent(getPendingIntent(context))
        if (noBgIcon != null) {
            builder.setSmallIcon(noBgIcon)
        }
        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }
        return builder.build()

        //others:
        //channel.enableLights(true);
        //channel.setLightColor(color);

        //Uri mUri = Settings.System.DEFAULT_NOTIFICATION_URI;
        //channel.setSound(mUri, Notification.AUDIO_ATTRIBUTES_DEFAULT);

        // Register the channel with system; you can't change the importance
        // or other notification behaviors after this
    }
}