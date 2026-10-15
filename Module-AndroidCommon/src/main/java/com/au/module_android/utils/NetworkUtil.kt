package com.au.module_android.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.ext.SdkExtensions
import com.au.module_android.log.logEx
import com.au.module_android.Globals
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

fun isWifiConnected(): Boolean {
    val manager = Globals.app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return manager.allNetworks.any { network ->
        val capabilities = manager.getNetworkCapabilities(network)
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }
}

/** 获取局域网地址，优先使用系统 Wi-Fi 链路信息；热点接口作为补充。 */
fun getIpAddress(): Pair<String?, NetworkType> {
    var ipv6Fallback: Pair<String?, NetworkType>? = null
    val managedInterfaces = mutableSetOf<String>()
    try {
        val manager = Globals.app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = manager.activeNetwork
        val networks = manager.allNetworks.sortedBy { if (it == activeNetwork) 0 else 1 }
        for (network in networks) {
            val capabilities = manager.getNetworkCapabilities(network) ?: continue
            val properties = manager.getLinkProperties(network) ?: continue
            properties.interfaceName?.let { managedInterfaces.add(it) }
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            for (link in properties.linkAddresses) {
                val result = getIPAddress(link.address) ?: continue
                if (result.second == NetworkType.WIFI_IPV4) return result
                if (ipv6Fallback == null) ipv6Fallback = result
            }
        }
    } catch (e: Exception) {
        "".logEx(throwable = e) { "Read Wi-Fi link addresses failed" }
    }

    // 热点下行接口通常不属于 ConnectivityManager 的 Network，不能从默认网络推断。
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces != null && interfaces.hasMoreElements()) {
            val netInterface = interfaces.nextElement()
            if (!netInterface.isUp || netInterface.isLoopback || netInterface.name in managedInterfaces) continue
            val name = netInterface.name.lowercase()
            val isAp = name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("softap")
            val isWifi = name.startsWith("wlan") || name.startsWith("wifi")
            if (!isAp && !isWifi) continue
            for (address in netInterface.inetAddresses) {
                val (ip, type) = getIPAddress(address) ?: continue
                val result = ip to when (type) {
                    NetworkType.WIFI_IPV4 -> NetworkType.AP_IPV4
                    else -> NetworkType.AP_IPV6
                }
                if (type == NetworkType.WIFI_IPV4) return result
                if (ipv6Fallback == null) ipv6Fallback = result
            }
        }
    } catch (e: Exception) {
        "".logEx(throwable = e) { "Read hotspot interfaces failed" }
    }
    return ipv6Fallback ?: (null to NetworkType.UNKNOWN)
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
    var ipv6Fallback: String? = null
    for (address in addresses) {
        val (ip, type) = getIPAddress(address) ?: continue
        if (type == NetworkType.WIFI_IPV4) return ip
        if (ipv6Fallback == null) ipv6Fallback = ip
    }
    return ipv6Fallback
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
        getIpAddress().first ?: if (wifiManager.isWifiEnabled) getWifiIP(wifiManager) else null
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
        if (wifiInfo != null && wifiInfo.ipAddress != 0) {
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