package com.allan.mydroid.globals

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * 返回的是ip to netType
 */
fun getIpAddress() : Pair<String?, String?>{
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
                        return ip to (if(isWifi) "wifi" else "ap")
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null to null
}