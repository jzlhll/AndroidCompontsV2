package com.au.module_simplepermission

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresPermission
import androidx.fragment.app.FragmentActivity
import com.au.module_simplepermission.permission.IMultiPermissionsResult

/**
 * 必须在成员变量初始化。否则permissionHelp不生效
 * Fragment/Activity {
 *   private val wifiPermissionHelp = WifiPermissionHelp(requireActivity())
 * }
 */
class WifiPermissionHelp(private val activity: FragmentActivity) {
    private val wifiPermissions =
        wifiPermissions()

    private val permissionHelp: IMultiPermissionsResult = activity.createMultiPermissionForResult(wifiPermissions)

    /**
     * 在 WiFi 权限已授权时执行 block，否则执行未授权回调。
     */
    fun safeRun(
        notGivePermissionBlock: (() -> Unit),
        block: () -> Unit
    ) {
        permissionHelp.safeRun(
            notGivePermissionBlock = notGivePermissionBlock,
            block = block
        )
    }

    /**
     * 判断当前 WiFi 相关权限是否已授权。
     */
    fun isPermissionGrant(): Boolean {
        return activity.hasPermission(*wifiPermissions)
    }

    /**
     * 打开系统 WiFi 设置页，失败时降级打开系统设置页。
     */
    fun openWifiSetting(): Boolean {
        return runCatching {
            activity.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            true
        }.getOrElse {
            runCatching {
                activity.startActivity(Intent(Settings.ACTION_SETTINGS))
                true
            }.getOrDefault(false)
        }
    }

    /**
     * 获取当前连接的 WiFi 信息，未授权或未连接 WiFi 时返回 null。
     *
     * Android 12 只申请 ACCESS_FINE_LOCATION 可以读取 SSID；Android 13 及以上需要同时申请
     * NEARBY_WIFI_DEVICES 和 ACCESS_FINE_LOCATION，并用 connectionInfo 兜底读取 SSID。
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun getCurrentWifi(): WifiInfoResult? {
        if (!isPermissionGrant()) {
            return null
        }

        val appContext = activity.applicationContext
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) {
            return null
        }

        val transportWifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            capabilities.transportInfo as? WifiInfo
        } else {
            null
        }
        val connectionWifiInfo = wifiManager.connectionInfo
        // Android 16 上 transportInfo 可能返回脱敏 SSID，connectionInfo 作为同步读取兜底。
        val wifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            normalizeSsid(transportWifiInfo?.ssid).isNotEmpty()
        ) {
            transportWifiInfo
        } else {
            connectionWifiInfo
        }
        if (wifiInfo == null) {
            return null
        }
        val ssid = normalizeSsid(wifiInfo.ssid)
        if (ssid.isEmpty()) {
            return null
        }
        val hasPassword = hasPassword(wifiInfo, wifiManager, ssid)

        return WifiInfoResult(
            wifiSSID = ssid,
            hasPassword = hasPassword
        )
    }

    // 统一清理系统返回的 SSID 包裹引号与未知占位值。
    private fun normalizeSsid(rawSsid: String?): String {
        val ssid = rawSsid.orEmpty().trim().removeSurrounding("\"")
        return if (ssid == WifiManager.UNKNOWN_SSID) "" else ssid
    }

    // Android 12 及以上优先使用系统安全类型，低版本通过扫描结果兜底判断。
    @SuppressLint("MissingPermission")
    private fun hasPassword(wifiInfo: WifiInfo, wifiManager: WifiManager, ssid: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return when (wifiInfo.currentSecurityType) {
                WifiInfo.SECURITY_TYPE_OPEN,
                WifiInfo.SECURITY_TYPE_OWE -> false
                else -> true
            }
        }

        val scanResults = runCatching {
            wifiManager.scanResults
        }.getOrElse {
            emptyList()
        }
        val scanResult = scanResults.firstOrNull { it.ssid() == ssid }
        if (scanResult == null) {
            return true
        }
        return scanResult.hasPassword()
    }

    private fun ScanResult.ssid(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            normalizeSsid(wifiSsid?.toString())
        } else {
            @Suppress("DEPRECATION")
            SSID.orEmpty()
        }
    }

    private fun ScanResult.hasPassword(): Boolean {
        val normalizedCapabilities = capabilities.orEmpty()
        return !(normalizedCapabilities.contains("ESS") &&
                !normalizedCapabilities.contains("WEP") &&
                !normalizedCapabilities.contains("WPA") &&
                !normalizedCapabilities.contains("WPA2") &&
                !normalizedCapabilities.contains("WPA3") &&
                !normalizedCapabilities.contains("OWE"))
    }

    companion object {
        /**
         * 返回当前 Android 版本读取 WiFi 信息的主权限。
         */
        fun wifiPermission(): String {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.NEARBY_WIFI_DEVICES
            } else {
                Manifest.permission.ACCESS_FINE_LOCATION
            }
        }

        /**
         * 返回当前 Android 版本读取 WiFi SSID 所需的权限。
         *
         * Android 12 只需要 ACCESS_FINE_LOCATION；Android 13 及以上同时需要
         * NEARBY_WIFI_DEVICES 和 ACCESS_FINE_LOCATION，否则 SSID 可能返回 <unknown ssid>。
         */
        fun wifiPermissions(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(
                    Manifest.permission.NEARBY_WIFI_DEVICES,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

    }
}

/**
 * 当前 WiFi 连接信息。
 */
data class WifiInfoResult(
    /**
     * WiFi 名称。
     */
    val wifiSSID: String,
    /**
     * 当前 WiFi 是否需要密码。
     */
    val hasPassword: Boolean
)
