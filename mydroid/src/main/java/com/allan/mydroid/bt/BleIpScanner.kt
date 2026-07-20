package com.allan.mydroid.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.au.module_android.Globals
import com.au.module_android.log.logdNoFile
import com.au.module_android.log.loge
import com.au.module_android.utils.launchOnIOThread
import com.au.module_simplepermission.BtPermissionHelp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

/** client 端扫描发现的一个 host。port 为 host 的 httpPort。 */
data class DiscoveredHost(val ip: String, val port: Int)

/**
 * client 端 BLE 扫描辅助类。client Tab 共用。包裹 [BtPermissionHelp] + [android.bluetooth.le.BluetoothLeScanner]，
 * 扫描 host 广播并解析 IP+port，通过 [discoveredFlow] 与 [scanningFlow] 暴露状态。
 *
 * - 底层 BLE 扫描 12s 自动停止（避免 Android "2 分钟 4 次" 限制），startScan/stopScan 必须主线程配对调用。
 * - UI 雷达动画 [scanningFlow] 持续 35s 才置 false（假装扫描 35s），底层停止后 UI 动画继续。
 * - discoveredMap 用 synchronized 保护，参照 [com.au.audiorecordplayer.bt.ble.BleScanner] 样板。
 */
class BleIpScanner(private val fragment: Fragment) {
    private val btPermissionHelp = BtPermissionHelp(fragment)
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (Globals.app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val scanner get() = bluetoothAdapter?.bluetoothLeScanner

    private val _discoveredFlow = MutableStateFlow<List<DiscoveredHost>>(emptyList())
    val discoveredFlow: StateFlow<List<DiscoveredHost>> = _discoveredFlow.asStateFlow()

    private val _scanningFlow = MutableStateFlow(false)
    val scanningFlow: StateFlow<Boolean> = _scanningFlow.asStateFlow()

    private val discoveredMap = linkedMapOf<String, DiscoveredHost>()  // key=ip
    private var bleScanStopJob: Job? = null
    private var uiScanningStopJob: Job? = null

    @Volatile private var isBleScanning = false

    @SuppressLint("MissingPermission")
    fun startScan() {
        btPermissionHelp.safeRun(notGivePermissionBlock = {
            _scanningFlow.value = false
            loge { "ble scan permission denied" }
        }) {
            val sc = scanner ?: run {
                _scanningFlow.value = false
                return@safeRun
            }
            if (isBleScanning) return@safeRun
            isBleScanning = true
            _scanningFlow.value = true
            synchronized(discoveredMap) { discoveredMap.clear() }
            _discoveredFlow.value = emptyList()

            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BleIpAdvertiser.BLE_IP_UUID))
                .build()
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build()
            sc.startScan(listOf(filter), settings, scanCallback)

            bleScanStopJob = fragment.lifecycleScope.launchOnIOThread {
                delay(12.seconds)
                withContext(Dispatchers.Main) {
                    sc.stopScan(scanCallback)
                }
                isBleScanning = false
                logdNoFile { "ble scan stopped (12s limit)" }
            }

            uiScanningStopJob = fragment.lifecycleScope.launchOnIOThread {
                delay(35.seconds)
                _scanningFlow.value = false
                logdNoFile { "ui scanning state stopped (35s)" }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        bleScanStopJob?.cancel()
        uiScanningStopJob?.cancel()
        scanner?.stopScan(scanCallback)
        isBleScanning = false
        _scanningFlow.value = false
    }

    @SuppressLint("MissingPermission")
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val bytes = result.scanRecord?.getServiceData(ParcelUuid(BleIpAdvertiser.BLE_IP_UUID)) ?: return
            val (ip, port) = BleIpAdvertiser.decodeIpPort(bytes) ?: return
            val snapshot: List<DiscoveredHost>
            synchronized(discoveredMap) {
                discoveredMap[ip] = DiscoveredHost(ip, port)
                snapshot = discoveredMap.values.toList()
            }
            _discoveredFlow.value = snapshot
        }
    }
}
