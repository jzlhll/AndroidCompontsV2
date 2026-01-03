package com.au.module_android.utils

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Environment
import android.text.TextUtils
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.annotation.RequiresApi
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.io.InputStreamReader

object AndroidSystemOSUtil {
    private const val CPU_NAME = "ro.hardware"
    private const val HW_CPU_INFO = "ro.config.cpu_info_display"
    private const val HARMONY_OS_VERSION = "hw_sc.build.platform.version"
    private const val SN_NO = "ro.serialno"
    private const val BOOT_SN_NO = "ro.boot.serialno"
    private const val DEVICE_NAME = "ro.build.display.id"
    private const val DEVICE_TYPE = "ro.build.id"
    private const val DEVICE_DESC = "ro.build.description"
    private const val CPU_ABI = "ro.product.cpu.abi"
    private const val CPU_ABI_LIST = "ro.product.cpu.abilist"
    private const val EMUI_VERSION = "ro.build.version.emui"

    fun getDeviceType(): String? {
        val type = Build.ID
        return getProp(DEVICE_TYPE, "")
    }

    fun getDeviceDesc(): String? {
        return getProp(DEVICE_DESC, "")
    }

    /**
     * 获取鸿蒙系统版本 1.0.0
     */
    fun getHarmonyVersion(): String? {
        return getProp(HARMONY_OS_VERSION, null)
    }

    /**
     * 获取CPU的名字
     */
    fun getCPUName(): String? {
        return getProp(CPU_NAME, "")
    }

    /**
     * 获取CPU的信息含名字
     */
    fun getHWCPUInfo(): String? {
        return getProp(HW_CPU_INFO, "")
    }

    /**
     * 获取ROM ota版本号
     */
    fun getOTAVersion(): String? {
        val ota = Build.DISPLAY
        return ota ?: getProp(DEVICE_NAME, "")
    }


    fun isHarmonyOs(): Boolean {
        return try {
            val cls = Class.forName("ohos.utils.system.SystemCapability")
            cls != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取 SN 号
     */
    @SuppressLint("MissingPermission")
    fun getSN(): String {
        try {
            var sn: String? = "unknown"
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    sn = Build.getSerial()
                } else {
                    sn = Build.SERIAL
                }
                if (!sn.isNullOrEmpty() && "unknown" != sn) {
                    return sn
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            sn = getProp(SN_NO, "unknown")
            if (!sn.isNullOrEmpty() && "unknown" != sn) {
                return sn
            }
            sn = getProp(BOOT_SN_NO, "unknown") ?: "unknown"
//            if (!sn.isNullOrEmpty() && "unknown" != sn) {
//                return sn
//            }
//            sn = getPropCmd(SN_NO)
//            LogUtils.d("获取SN号4：$sn")
//            if (!sn.isNullOrEmpty() && "unknown" != sn) {
//                return sn
//            }
//            sn = getPropCmd(BOOT_SN_NO) ?: "unknown"
//            LogUtils.d("获取SN号5：$sn")
            return sn
        } catch (e: java.lang.Exception) {
            return "unknown"
        }
    }

    fun getPropCmd(prop: String): String? {
        var line = "unknown"
        try {
            val process = Runtime.getRuntime().exec("getprop ${prop}")
            val bis = BufferedReader(InputStreamReader(process.inputStream))
            line = bis.readLine()
            bis.close()
        } catch (e: java.lang.Exception) {
            line = "unknown"
        }
        line = line.trim { it <= ' ' }
        return line
    }

    fun getCPUAbi(): String {
        val abi = Build.CPU_ABI
        return abi ?: getProp(CPU_ABI, "armeabi-v7a") ?: "armeabi-v7a"
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun getCPUAbiList(): String {
        val abis = Build.SUPPORTED_ABIS?.joinToString(",")
        return abis ?: getProp(CPU_ABI_LIST, "armeabi-v7a") ?: "armeabi-v7a"
    }

    fun getEMUIVersion(): String {
        return getProp(EMUI_VERSION, "") ?: ""
    }

    /**
     * 鸿蒙系统是否是3.0及以上
     */
    fun isHarmonyOSVersionAbove3_0(): Boolean {
        val hv = getHarmonyVersion()
        if (hv.isNullOrEmpty()) return false
        val arr = hv.split('.')
        if (arr.size <= 1) {
            return false
        }
        val v1 = arr[0].toIntOrNull() ?: 0
        if (v1 > 2) {
            return true
        }
        return false
    }

    /**
     * 获取系统信息
     */
    @SuppressLint("PrivateApi")
    private fun getProp(property: String, defaultValue: String?): String? {
        try {
            val spClz = Class.forName("android.os.SystemProperties")
            val method = spClz.getDeclaredMethod("get", String::class.java)
            val value = method.invoke(spClz, property) as String
            return if (TextUtils.isEmpty(value)) {
                defaultValue
            } else value
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return defaultValue
    }

    /**
     * 判断service是否在运行
     */
    fun isServiceRunning(context: Context, className: String): Boolean {
        var isRunning = false
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val max = 500
        val serviceList = activityManager.getRunningServices(max)
        if (serviceList.isEmpty()) {
            return false
        }
        for (i in 0 until serviceList.size) {
            if (serviceList[i].service.className == className) {
                isRunning = true
                break
            }
        }
        return isRunning
    }

    fun isAppRunning(context: Context, packageName: String): Boolean {
        if (packageName.isEmpty()) return false
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningAppProcesses = activityManager.runningAppProcesses
        for (runningAppProcessInfo in runningAppProcesses) {
            if (runningAppProcessInfo.processName == packageName) {
                return true
            }
        }
        return false
    }


    fun getWindowManager(context: Context): WindowManager? {
        return context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    fun getPackageManager(context: Context): PackageManager {
        return context.applicationContext.packageManager
    }

    fun getApplicationInfo(context: Context): ApplicationInfo? {
        try {
            return getPackageManager(context).getApplicationInfo(context.packageName, 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun getApplicationInfo(context: Context, packageName: String): ApplicationInfo? {
        try {
            return getPackageManager(context).getApplicationInfo(packageName, 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun getAppFilePath(context: Context): String? {
        try {
            val applicationInfo = getApplicationInfo(context)
            return applicationInfo?.sourceDir
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        return null
    }

    // 获取app的VersionName ，即app的版本号
    fun getVersionName(context: Context): String {
        return getVersionName(context, context.packageName)
    }

    fun getVersionName(context: Context, packageName: String): String {
        return try {
            getPackageManager(context).getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    // 获取app的VersionCode，即app的版本code
    fun getVersionCode(context: Context): Long {
        return getVersionCode(context, context.packageName)
    }

    fun getVersionCode(context: Context, packageName: String?): Long {
        try {
            if (packageName.isNullOrEmpty()) return 0L
            return getPackageManager(context).getPackageInfo(packageName, 0)?.versionCode?.toLong() ?: 0L
        } catch (e: Exception) {
            return 0L
        }
    }

    // 获取app的name
    fun getAppName(context: Context): String {
        return getAppName(context, context.packageName)
    }

    fun getAppName(context: Context, packageName: String): String {
        try {
            val appInfo = getPackageManager(context).getApplicationInfo(packageName, 0)
            return appInfo.loadLabel(getPackageManager(context)).toString()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    // 获取app的Logo图片
    fun getAppIcon(context: Context): Drawable? {
        return getAppIcon(context, context.packageName)
    }

    fun getAppIcon(context: Context, packageName: String): Drawable? {
        if (packageName.isEmpty()) return null
        val applicationInfo = getApplicationInfo(context, packageName) ?: return null
        return getPackageManager(context).getApplicationIcon(applicationInfo)
    }

    // 获取app的rootDir
    fun getAppRootDir(context: Context): String? {
        var root: String? = null
        var cacheDir: File? = null
        // Get external cache directory
        if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()) {
            cacheDir = context.externalCacheDir
        }
        // Get internal cache directory if sdcard is not mounted
        if (cacheDir == null) {
            cacheDir = context.cacheDir
        }
        if (cacheDir == null) {
        } else {
            root = cacheDir.absolutePath
        }
        return root
    }

    /**
     * 获取屏幕宽
     *
     * @param context
     * @return
     */
    fun getScreenHeight(context: Context): Int {
        return context.resources.displayMetrics.heightPixels
    }

    /**
     * 获取屏幕高
     *
     * @param context
     * @return
     */
    fun getScreenWidth(context: Context): Int {
        return context.resources.displayMetrics.widthPixels
    }

    /**
     * 获取屏幕真实宽
     *
     * @param context Context
     * @return RealScreenWidth
     */
    fun getRealScreenWidth(context: Context): Int {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            getScreenWidth(context)
        } else {
            val display = getWindowManager(context)?.defaultDisplay ?: return getScreenWidth(context)
            val displayMetrics = DisplayMetrics()
            val c: Class<*>
            try {
                c = Class.forName("android.view.Display")
                val method = c.getMethod("getRealMetrics", DisplayMetrics::class.java)
                method.invoke(display, displayMetrics)
                displayMetrics.widthPixels
            } catch (e: java.lang.Exception) {
                getScreenWidth(context)
            }
        }
    }

    /**
     * 获取屏幕真实高
     *
     * @param context Context
     * @return RealScreenHeight
     */
    fun getRealScreenHeight(context: Context): Int {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            getScreenHeight(context)
        } else {
            val display = getWindowManager(context)?.defaultDisplay ?: return getScreenWidth(context)
            val displayMetrics = DisplayMetrics()
            val c: Class<*>
            try {
                c = Class.forName("android.view.Display")
                val method = c.getMethod("getRealMetrics", DisplayMetrics::class.java)
                method.invoke(display, displayMetrics)
                displayMetrics.heightPixels
            } catch (e: java.lang.Exception) {
                getScreenHeight(context)
            }
        }
    }

    /**
     * 获取屏幕物理高
     *
     * @param context Context
     * @return getPhysicalHeight
     */
    fun getPhysicalHeight(context: Context): Int {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            getScreenWidth(context)
        } else {
            val display = getWindowManager(context)?.defaultDisplay ?: return getScreenWidth(context)
            val c: Class<*>
            try {
                c = Class.forName("android.view.Display")
                val method = c.getMethod("getPhysicalHeight")
                method.invoke(display) as Int
            } catch (e: java.lang.Exception) {
                getRealScreenHeight(context)
            }
        }
    }

    /**
     * 获取屏幕物理宽
     *
     * @param context Context
     * @return getPhysicalWidth
     */
    fun getPhysicalWidth(context: Context): Int {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            getScreenHeight(context)
        } else {
            val display = getWindowManager(context)!!.defaultDisplay
            val c: Class<*>
            try {
                c = Class.forName("android.view.Display")
                val method = c.getMethod("getPhysicalWidth")
                method.invoke(display) as Int
            } catch (e: java.lang.Exception) {
                getRealScreenWidth(context)
            }
        }
    }

    /**
     * 获取进程号对应的进程名
     *
     * @param pid 进程号
     * @return 进程名
     */
    fun getProcessName(pid: Int): String? {
        var reader: BufferedReader? = null
        try {
            reader = BufferedReader(FileReader("/proc/$pid/cmdline"))
            var processName = reader.readLine()
            if (!TextUtils.isEmpty(processName)) {
                processName = processName.trim { it <= ' ' }
            }
            return processName
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
        } finally {
            try {
                reader?.close()
            } catch (exception: IOException) {
                exception.printStackTrace()
            }
        }
        return null
    }
}

