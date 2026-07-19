package com.allan.mydroid.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
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

    @Volatile private var advertising = false
    private val lock = Any()
    private var observeJob: Job? = null

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
                if (advertising) return@safeRun
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
                adv.startAdvertising(settings, data, advertiseCallback)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertise() {
        synchronized(lock) {
            if (!advertising) return
            advertiser?.stopAdvertising(advertiseCallback)
            advertising = false
        }
    }

    /** 由 AbsLiveFragment.onDestroyView 调用。 */
    fun stop() {
        observeJob?.cancel()
        stopAdvertise()
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            advertising = true
            logdNoFile { "ble advertise started" }
        }

        override fun onStartFailure(errorCode: Int) {
            advertising = false
            loge { "ble advertise failed: $errorCode" }
        }
    }

    companion object {
        val BLE_IP_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")

        /** 编码: IP UTF-8 字节 + 2 字节 port (BigEndian)。 */
        fun encodeIpPort(ip: String, port: Int): ByteArray {
            val ipBytes = ip.toByteArray(Charsets.UTF_8)
            return ByteArray(ipBytes.size + 2).also { out ->
                ipBytes.copyInto(out)
                out[ipBytes.size] = (port shr 8).toByte()
                out[ipBytes.size + 1] = port.toByte()
            }
        }

        /** 解码: 末 2 字节为 port, 其余为 IP 字符串。 */
        fun decodeIpPort(bytes: ByteArray): Pair<String, Int>? {
            if (bytes.size < 3) return null
            val ip = bytes.copyOfRange(0, bytes.size - 2).toString(Charsets.UTF_8)
            val port = ((bytes[bytes.size - 2].toInt() and 0xff) shl 8) or (bytes[bytes.size - 1].toInt() and 0xff)
            return ip to port
        }
    }
}
