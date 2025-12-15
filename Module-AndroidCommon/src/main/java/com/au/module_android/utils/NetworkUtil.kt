package com.au.module_android.utils

import android.net.nsd.NsdServiceInfo
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
 * 返回的是ip to netType
 */
fun getIpAddress() : Pair<String?, NetworkType>{
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val netInterface = interfaces.nextElement()
            val isWifi = netInterface.displayName.equals("wlan0")
            val isAp = netInterface.name.startsWith("ap")
            if (isWifi || isAp) {
                for (addr in netInterface.getInterfaceAddresses()) {
                    val inetAddr = addr.address
                    if (inetAddr is Inet4Address) {
                        val ip = inetAddr.hostAddress
                        return ip to (if(isWifi) NetworkType.WIFI_IPV4 else NetworkType.AP_IPV4)
                    } else if (inetAddr is Inet6Address) {
                        val ip = inetAddr.hostAddress
                        return ip to (if(isWifi) NetworkType.WIFI_IPV6 else NetworkType.AP_IPV6)
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null to NetworkType.UNKNOWN
}

fun getIPAddress(nsd: NsdServiceInfo) : String?{
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 7) {
        getIPAddress(nsd.hostAddresses)
    } else {
        getIPAddress(listOf(nsd.host))
    }
}

fun getIPAddress(addresses: List<InetAddress>): String? {
    for (addr in addresses) {
        val r = getIPAddress(addr)
        if (r != null) {
            return r
        }
    }
    return null
}

/**
 * 获取IP地址，优先返回IPv4地址。
 * 自动过滤回环地址(127.0.0.1, ::1)、链路本地地址(169.254.x.x, fe80::)、多播地址等不可用地址。
 * 对于 IPv4 映射的 IPv6 地址，会自动转换为 IPv4 格式。
 * 对于 IPv6 地址，会去除 scope id (如 %wlan0)。
 */
private fun getIPAddress(address: InetAddress?): String? {
    if (address == null || address.isLoopbackAddress  //回环
        || address.isAnyLocalAddress  // 任意本地地址
        || address.isMulticastAddress // 多播
        || address.isLinkLocalAddress // 排除链路本地地址 (169.254.x.x) (fe80::/10)
    ) {
        return null
    }

    // 2. IPv4 处理
    if (address is Inet4Address) {
        return address.hostAddress
    }

    // 3. IPv6 处理
    if (address is Inet6Address) {
        val bytes = address.address
        // 检查是否是 IPv4 映射的 IPv6 地址 (::ffff:x.x.x.x)
        // 格式：80 bits of 0, followed by 16 bits of 0xFFFF, followed by 32 bits of IPv4
        if (bytes.size == 16 &&
            bytes.take(10).all { it == 0.toByte() } &&
            bytes[10] == (-1).toByte() &&
            bytes[11] == (-1).toByte()) {

            // 提取 IPv4 部分
            val ipv4 = bytes.copyOfRange(12, 16)
                .joinToString(".") { (it.toInt() and 0xFF).toString() }
            return ipv4
        }

        // 纯 IPv6：去掉 Scope ID (例如 %wlan0)
        val fullAddress = address.hostAddress ?: return null
        val percentIndex = fullAddress.indexOf('%')
        return if (percentIndex != -1) {
            fullAddress.take(percentIndex)
        } else {
            fullAddress
        }
    }

    return null
}