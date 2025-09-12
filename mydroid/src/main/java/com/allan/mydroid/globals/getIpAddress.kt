package com.allan.mydroid.globals

import java.net.Inet4Address
import java.net.NetworkInterface

fun getIpAddress() : String? {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val netInterface = interfaces.nextElement()
            if (netInterface.displayName.equals("wlan0") || netInterface.name.startsWith("ap")) {
                for (addr in netInterface.getInterfaceAddresses()) {
                    val inetAddr = addr.address
                    if (inetAddr is Inet4Address) {
                        val ip = inetAddr.hostAddress
                        return ip
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}