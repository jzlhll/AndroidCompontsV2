package com.allan.androidlearning.copied.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import com.au.module_android.Globals
import com.au.module_android.utils.AndroidSystemOSUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object NotificationUtils {

    @SuppressLint("StaticFieldLeak")
    private var notificationManagerCompat: NotificationManagerCompat? = null

    /**
     * 如果不合适，用户自己修改
     */
    // 通知栏id
    var NOTIFICATION_ID = 88888

    fun getNotificationManagerCompat(): NotificationManagerCompat {
        if (notificationManagerCompat == null) {
            notificationManagerCompat = NotificationManagerCompat.from(Globals.app)
        }
        return notificationManagerCompat!!
    }

    suspend fun downloadIcon(url: String?): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                if (url.isNullOrEmpty()) {
                    return@withContext null
                }
                val file = Glide.with(Globals.app).load(url).diskCacheStrategy(DiskCacheStrategy.ALL).downloadOnly(200, 200).get(5, TimeUnit.SECONDS)
                if (file?.absolutePath.isNullOrEmpty()) {
                    return@withContext null
                }
                return@withContext BitmapFactory.decodeFile(file.absolutePath)
            } catch (e: Exception) {
                return@withContext null
            }
        }
    }


    private val notificationMap = mutableMapOf<String, ProgressNotification>()

    fun showProgressNotification(pkg: String, title: String?, iconUrl: String?, progress: Int, intent: Intent? = null) {
        if (notificationMap[pkg] == null) {
            notificationMap[pkg] = ProgressNotification()
        }
        notificationMap[pkg]?.show(pkg, title, iconUrl, progress, intent)
    }

    fun removeProgressNotification(pkg: String) {
        try {
            val n = notificationMap.remove(pkg)
            n?.removeNotification(pkg, NOTIFICATION_ID)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 显示通知
     */
    fun showNormalNotification(title: String?, content: String?, intent: Intent? = null) {
        MessageNotification().show(title, content, intent)
    }

}

class ProgressNotification {

    private var notificationBuilder: NotificationCompat.Builder? = null
    private var notificationChannel: NotificationChannel? = null

    fun show(pkg: String, title: String?, iconUrl: String?, progress: Int, intent: Intent? = null) {
        // 通知栏，8.0以下不需要兼容
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Globals.mainScope.launch(Dispatchers.Main) {
                if (notificationBuilder != null) {
                    updateProgress(pkg, progress)
                    return@launch
                }
                try {
                    val channelId = "dpc_channel_message_progress_${pkg}"
                    notificationChannel = NotificationChannel(channelId, pkg, NotificationManager.IMPORTANCE_HIGH)
                    notificationChannel?.enableLights(true)
                    notificationChannel?.lightColor = Color.BLUE
                    notificationChannel?.canBypassDnd()
                    notificationChannel?.setBypassDnd(true)

                    notificationChannel?.let { cn ->
                        Globals.app.getSystemService(NotificationManager::class.java).createNotificationChannel(cn)
                    }

                    val largeIcon = NotificationUtils.downloadIcon(iconUrl)

                    notificationBuilder = NotificationCompat.Builder(Globals.app, notificationChannel?.id ?: Globals.app.packageName)
                        .setContentTitle(title ?: "正在下载")
                        .setSmallIcon(IconCompat.createWithBitmap(AndroidSystemOSUtil.getAppIcon(Globals.app)!!.toBitmap(100, 100, null))).setAutoCancel(true)
                        .setWhen(System.currentTimeMillis()).apply {
                            if (largeIcon != null) {
                                setLargeIcon(largeIcon)
                            }
                        }.setPriority(NotificationCompat.PRIORITY_DEFAULT).setSubText("0%").setProgress(100, 0, false).setShowWhen(true).setSilent(true)
                        .setNumber(100).setDefaults(NotificationCompat.FLAG_ONLY_ALERT_ONCE).apply {
                            if (intent != null) {
                                setContentIntent(
                                    PendingIntent.getActivity(
                                        Globals.app,
                                        0,
                                        intent,
                                        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                    )
                                )
                            }
                        }
                    notify(notificationChannel?.name?.toString() ?: Globals.app.packageName, NotificationUtils.NOTIFICATION_ID, notificationBuilder)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * 更新进度
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateProgress(notificationChannel: NotificationChannel, progress: Int) {
        if (ActivityCompat.checkSelfPermission(Globals.app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        notificationBuilder?.let {
            if (progress > 100) {
                it.setProgress(100, 100, false)
                it.setSubText("100%")
            } else {
                it.setProgress(100, progress, false)
                it.setSubText("${progress}%")
            }
            notify(notificationChannel.name.toString(), 1, it)
            if (progress >= 100) {
                NotificationUtils.getNotificationManagerCompat().cancel(notificationChannel.name.toString(), NotificationUtils.NOTIFICATION_ID)
            }
        }
    }

    /**
     * 更新进度
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateProgress(channelName: String, progress: Int) {
        if (ActivityCompat.checkSelfPermission(Globals.app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        notificationBuilder?.let {
            if (progress > 100) {
                it.setProgress(100, 100, false)
                it.setSubText("100%")
            } else {
                it.setProgress(100, progress, false)
                it.setSubText("${progress}%")
            }
            notify(channelName, 1, it)
            if (progress >= 100) {
                removeNotification(channelName, NotificationUtils.NOTIFICATION_ID)
                NotificationUtils.removeProgressNotification(channelName)
            }
        }
    }

    fun removeNotification(channelName: String, notificationId: Int) {
        NotificationUtils.getNotificationManagerCompat().cancel(channelName, notificationId)
    }

    /**
     * 显示通知
     */
    private fun notify(channelName: String, notificationId: Int, notification: NotificationCompat.Builder?) {
        if (ActivityCompat.checkSelfPermission(Globals.app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        notification?.let {
            NotificationUtils.getNotificationManagerCompat().notify(channelName, notificationId, it.build())
        }
    }
}


/**
 * 想改自己改变量吧
 */
class MessageNotification {

    companion object {
        var channelId = "xbase_channel_message"
        var channelName = "xbase_channel_message_name"
    }

    fun show(title: String?, content: String?, intent: Intent? = null) {
        // 通知栏，8.0以下不需要兼容
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Globals.mainScope.launch(Dispatchers.Main) {
                try {
                    val notificationChannel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
                    notificationChannel.enableLights(true)
                    notificationChannel.lightColor = Color.BLUE
                    notificationChannel.canBypassDnd()
                    notificationChannel.setBypassDnd(true)

                    notificationChannel.let { cn ->
                        Globals.app.getSystemService(NotificationManager::class.java).createNotificationChannel(cn)
                    }

                    val largeIcon = AndroidSystemOSUtil.getAppIcon(Globals.app)!!.toBitmap(200, 200, null)

                    val notificationBuilder =
                        NotificationCompat.Builder(Globals.app, notificationChannel.id)
                            .setContentTitle(title ?: "")
                            .setContentText(content ?: "")
                            .setSmallIcon(IconCompat.createWithBitmap(largeIcon))
                            .setAutoCancel(true).setWhen(System.currentTimeMillis())
                            .setLargeIcon(largeIcon)
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setShowWhen(true)
                            .setSilent(false)
                            .setDefaults(NotificationCompat.FLAG_ONLY_ALERT_ONCE).apply {
                                if (intent != null) {
                                    setContentIntent(
                                        PendingIntent.getActivity(
                                            Globals.app,
                                            0,
                                            intent,
                                            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                        )
                                    )
                                }
                            }
                    notify(notificationBuilder)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun removeNotification(channelName: String, notificationId: Int) {
        NotificationUtils.getNotificationManagerCompat().cancel(channelName, notificationId)
    }

    /**
     * 显示通知
     */
    private fun notify(notification: NotificationCompat.Builder?) {
        if (ActivityCompat.checkSelfPermission(Globals.app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        notification?.let {
            NotificationUtils.getNotificationManagerCompat().notify(channelName, NotificationUtils.NOTIFICATION_ID, it.build())
        }
    }

}
