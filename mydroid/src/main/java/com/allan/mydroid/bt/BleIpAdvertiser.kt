package com.allan.mydroid.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.allan.mydroid.network.GlobalNetworkMonitorObj
import com.allan.mydroid.state.GlobalServerRuntimeObj
import com.au.module_android.Globals
import com.au.module_android.log.logdNoFile
import com.au.module_android.log.loge
import com.au.module_android.log.logEx
import com.au.module_android.simpleflow.StatusState
import com.au.module_simplepermission.BtPermissionHelp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

/**
 * host 端 BLE IP 广播辅助类。三个对等页面（ReceiveFromH5Fragment / TextChatRoomFragment / SendListFilesFragment）
 * 通过 [AbsLiveFragment] 共用本类。订阅 serverIsOpen + IP + ports 三流，全部就绪时自动 startAdvertising，
 * 任一失活则 stopAdvertising。
 *
 * - BtPermissionHelp 必须在构造时初始化（依赖 registerForActivityResult），由 [fragment] 提供生命周期宿主。
 * - 通过 KoinComponent 内部 inject 获取状态对象，避免依赖 Fragment 传递，保持内聚。
 */
class BleIpAdvertiser(private val fragment: Fragment) : KoinComponent {
    private val btPermissionHelp = BtPermissionHelp(fragment)
    private val serverRuntimeState: GlobalServerRuntimeObj by inject()
    private val networkMonitor: GlobalNetworkMonitorObj by inject()

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (Globals.app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val advertiser get() = bluetoothAdapter?.bluetoothLeAdvertiser

    private val lock = Any()
    private var observeJob: Job? = null
    private var desiredEndpoint: Pair<String, Int>? = null
    private var advertisedEndpoint: Pair<String, Int>? = null
    private var activeAdvertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null

    /** 由 AbsLiveFragment.onBindingCreated 启动订阅；服务开启+IP 就绪+端口 Success 时自动广播。 */
    fun start() {
        observeJob?.cancel()
        observeJob = fragment.viewLifecycleOwner.lifecycleScope.launch {
            fragment.viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    serverRuntimeState.serverIsOpenFlow,
                    networkMonitor.networkFlow,
                    serverRuntimeState.portsFlow,
                ) { open, netStatus, ports ->
                    Triple(open, (netStatus as? GlobalNetworkMonitorObj.NetworkStatus.Connected)?.ip, ports)
                }.collect { (open, ip, ports) ->
                    val port = ((ports as? StatusState.Success<*>)?.data as? Pair<Int, Int>)?.first
                    if (open && !ip.isNullOrEmpty() && port != null) {
                        startAdvertise(ip, port)
                    } else {
                        stopAdvertise()
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startAdvertise(ip: String, port: Int) {
        val endpoint = ip to port
        synchronized(lock) { desiredEndpoint = endpoint }
        btPermissionHelp.safeRun(notGivePermissionBlock = {
            loge { "ble advertise permission denied" }
        }) {
            val adv = advertiser ?: run {
                loge { "bluetoothLeAdvertiser is null, not supported" }
                return@safeRun
            }
            if (bluetoothAdapter?.isMultipleAdvertisementSupported != true) {
                loge { "multiple advertisement not supported" }
                return@safeRun
            }
            synchronized(lock) {
                // 权限回调可能晚于页面退出或地址变化，不能重新启动过期的广播。
                if (desiredEndpoint != endpoint) return@safeRun
                if (advertisedEndpoint == endpoint && advertiseCallback != null) return@safeRun
                val previous = advertiseCallback
                advertiseCallback = null
                advertisedEndpoint = null
                if (previous != null) stopAdvertising(activeAdvertiser, previous)
                val settings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setConnectable(false)
                    .setTimeout(0)
                    .build()
                val data = AdvertiseData.Builder()
                    .addServiceUuid(ParcelUuid(BLE_IP_UUID))
                    .addServiceData(ParcelUuid(BLE_IP_UUID), encodeIpPort(ip, port))
                    .setIncludeTxPowerLevel(false)
                    .setIncludeDeviceName(false)
                    .build()
                val callback = object : AdvertiseCallback() {
                    override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                        synchronized(lock) {
                            if (advertiseCallback !== this) {
                                stopAdvertising(adv, this)
                                return
                            }
                            logdNoFile { "ble advertise started: $ip:$port" }
                        }
                    }

                    override fun onStartFailure(errorCode: Int) {
                        synchronized(lock) {
                            if (advertiseCallback !== this) return
                            advertiseCallback = null
                            advertisedEndpoint = null
                            activeAdvertiser = null
                            loge { "ble advertise failed: $errorCode" }
                        }
                    }
                }
                activeAdvertiser = adv
                advertiseCallback = callback
                advertisedEndpoint = endpoint
                try {
                    adv.startAdvertising(settings, data, callback)
                } catch (e: Exception) {
                    advertiseCallback = null
                    advertisedEndpoint = null
                    activeAdvertiser = null
                    loge { "ble advertise start failed: ${e.message}" }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertise() {
        synchronized(lock) {
            desiredEndpoint = null
            val callback = advertiseCallback
            advertiseCallback = null
            advertisedEndpoint = null
            val adv = activeAdvertiser
            activeAdvertiser = null
            if (callback != null) stopAdvertising(adv, callback)
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopAdvertising(adv: BluetoothLeAdvertiser?, callback: AdvertiseCallback) {
        try {
            adv?.stopAdvertising(callback)
        } catch (e: IllegalStateException) {
            logEx(throwable = e) { "Bluetooth became unavailable while stopping advertising" }
        } catch (e: SecurityException) {
            logEx(throwable = e) { "Bluetooth permission revoked while stopping advertising" }
        }
    }

    /** 由 AbsLiveFragment.onDestroyView 调用。 */
    fun stop() {
        observeJob?.cancel()
        stopAdvertise()
    }

    companion object {
        val BLE_IP_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")

        /**
         * 编码：IP(4字节二进制，每段1字节) + port(2字节 BigEndian) = 6 字节固定长度。
         * 远低于 BLE legacy advertising service data 字节上限。
         */
        fun encodeIpPort(ip: String, port: Int): ByteArray {
            val segments = ip.split(".")
            require(segments.size == 4) { "invalid ipv4: $ip" }
            return ByteArray(6).also { out ->
                segments.forEachIndexed { i, s ->
                    val v = s.toInt(radix = 10)
                    require(v in 0..255) { "invalid ipv4 segment: $s" }
                    out[i] = v.toByte()
                }
                require(port in 0..65535) { "invalid port: $port" }
                out[4] = (port shr 8).toByte()
                out[5] = port.toByte()
            }
        }

        /** 解码：固定 6 字节，返回 Pair<ip, port>；长度不匹配返回 null。 */
        fun decodeIpPort(bytes: ByteArray): Pair<String, Int>? {
            if (bytes.size != 6) return null
            val ip = "${bytes[0].toInt() and 0xff}.${bytes[1].toInt() and 0xff}.${bytes[2].toInt() and 0xff}.${bytes[3].toInt() and 0xff}"
            val port = ((bytes[4].toInt() and 0xff) shl 8) or (bytes[5].toInt() and 0xff)
            return ip to port
        }
    }
}
