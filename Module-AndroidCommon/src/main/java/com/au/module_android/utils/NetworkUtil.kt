package com.au.module_android.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.ext.SdkExtensions
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

enum class NetworkType {
    WIFI_IPV4,
    WIFI_IPV6,
    AP_IPV4,
    AP_IPV6,
    UNKNOWN
}

/**
 * 获取设备的IP地址和网络类型
 * 返回 Pair<IP地址, 网络类型>，IP地址可为空
 */
fun getIpAddress(): Pair<String?, NetworkType> {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val netInterface = interfaces.nextElement()
            val isWifi = netInterface.displayName.equals("wlan0", ignoreCase = true)
            val isAp = netInterface.name.startsWith("ap", ignoreCase = true)

            if (isWifi || isAp) {
                for (addr in netInterface.inetAddresses) {
                    val result = getIPAddress(addr)
                    if (result != null) {
                        val (ip, type) = result
                        // 根据接口类型调整NetworkType
                        val adjustedType = when (type) {
                            NetworkType.WIFI_IPV4 -> if (isWifi) NetworkType.WIFI_IPV4 else NetworkType.AP_IPV4
                            NetworkType.WIFI_IPV6 -> if (isWifi) NetworkType.WIFI_IPV6 else NetworkType.AP_IPV6
                            else -> type
                        }
                        return ip to adjustedType
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null to NetworkType.UNKNOWN
}

/**
 * 从NsdServiceInfo获取IP地址
 */
fun getIPAddress(nsd: NsdServiceInfo): String? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 7) {
        getIPAddress(nsd.hostAddresses)
    } else {
        getIPAddress(listOf(nsd.host))
    }
}

/**
 * 从InetAddress列表获取IP地址
 */
fun getIPAddress(addresses: List<InetAddress>): String? {
    for (addr in addresses) {
        val result = getIPAddress(addr)
        if (result != null) {
            return result.first
        }
    }
    return null
}

/**
 * 获取IP地址，优先返回IPv4地址
 * 返回 Pair<IP地址, 网络类型> 或 null
 */
private fun getIPAddress(address: InetAddress?): Pair<String?, NetworkType>? {
    if (address == null || address.isLoopbackAddress  // 回环地址
        || address.isAnyLocalAddress  // 任意本地地址
        || address.isMulticastAddress // 多播地址
        || address.isLinkLocalAddress // 链路本地地址 (169.254.x.x, fe80::)
    ) {
        return null
    }

    // IPv4 处理
    if (address is Inet4Address) {
        return address.hostAddress to NetworkType.WIFI_IPV4
    }

    // IPv6 处理
    if (address is Inet6Address) {
        val bytes = address.address
        // 检查是否是 IPv4 映射的 IPv6 地址 (::ffff:x.x.x.x)
        if (bytes.size == 16 &&
            bytes.take(10).all { it == 0.toByte() } &&
            bytes[10] == (-1).toByte() &&
            bytes[11] == (-1).toByte()) {

            // 提取 IPv4 部分
            val ipv4 = bytes.copyOfRange(12, 16)
                .joinToString(".") { (it.toInt() and 0xFF).toString() }
            return ipv4 to NetworkType.WIFI_IPV6
        }

        // 纯 IPv6：去掉 Scope ID (例如 %wlan0)
        val fullAddress = address.hostAddress ?: return null
        val percentIndex = fullAddress.indexOf('%')
        val ip = if (percentIndex != -1) {
            fullAddress.take(percentIndex)
        } else {
            fullAddress
        }
        return ip to NetworkType.WIFI_IPV6
    }

    return null
}

/**
 * 获取网络IP地址(优先获取wifi地址)
 *
 * @param ctx Context
 * @return String ip
 */
fun getIPAddressWifiManager(ctx: Context): String? {
    return try {
        val wifiManager = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (wifiManager.isWifiEnabled) getWifiIP(wifiManager) else getIpAddress().first
    } catch (e: java.lang.Exception) {
        e.printStackTrace()
        ""
    }
}

/*
 * 获取WIFI连接下的ip地址
 * 者必须要权限ACCESS_NETWORK_STATE，和wifi的权限
 */
private fun getWifiIP(wifiManager: WifiManager): String? {
    return try {
        @SuppressLint("MissingPermission") val wifiInfo = wifiManager.connectionInfo
        val ip: String
        if (wifiInfo != null) {
            ip = intToIp(wifiInfo.ipAddress)
            return ip
        }
        ""
    } catch (e: java.lang.Exception) {
        e.printStackTrace()
        ""
    }
}

private fun intToIp(i: Int): String {
    return (i and 0xFF).toString() + "." + (i shr 8 and 0xFF) + "." + (i shr 16 and 0xFF) + "." + (i shr 24 and 0xFF)
}