package com.au.module_android.utils

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

// 获取IP地址，优先返回IPv4地址
fun getIPAddress(host: InetAddress?): String? {
    if (host == null) return null

    val hostAddress = host.hostAddress ?: return null

    // 如果是IPv4地址（不包含冒号），直接返回
    if (!hostAddress.contains(":")) {
        return hostAddress
    }

    // 对于IPv6地址，返回标准格式
    return hostAddress
}