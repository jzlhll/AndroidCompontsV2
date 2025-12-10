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

/**
 * 获取IP地址，优先返回IPv4地址。
 * 第二个参数是是否是IPv4，true表示是IPv4，false表示是IPv6
 */
fun getIPAddress(address: InetAddress?): String? {
    if (address == null) return null
    val ipAddress = address.hostAddress ?: return null
    when (address) {
        is Inet4Address -> {
            return ipAddress
        }
        is Inet6Address -> {
            val bytes = address.address

            // 检查是否是 IPv4 映射的 IPv6 地址
            if (bytes.size == 16 &&
                bytes.take(10).all { it == 0.toByte() } &&
                bytes[10] == (-1).toByte() &&
                bytes[11] == (-1).toByte()) {

                // 提取 IPv4 部分
                bytes.copyOfRange(12, 16)
                    .joinToString(".") { (it.toInt() and 0xFF).toString() }
            } else {
                // 纯 IPv6
                return ipAddress
            }
        }
    }

    // 对于IPv6地址，返回标准格式
    return ipAddress
}