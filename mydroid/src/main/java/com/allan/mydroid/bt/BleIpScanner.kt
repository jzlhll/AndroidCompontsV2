package com.allan.mydroid.bt

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.os.Build
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.au.module_android.Globals
import com.au.module_android.log.logdNoFile
import com.au.module_android.log.loge
import com.au.module_android.log.logEx
import com.au.module_simplepermission.createMultiPermissionForResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** client 端扫描发现的一个 host。port 为 host 的 httpPort。 */
data class DiscoveredHost(val ip: String, val port: Int)

/** client 端 BLE 发现，扫描与页面视图生命周期绑定，状态只反映真实扫描过程。 */
class BleIpScanner(private val fragment: Fragment) {
    private val btPermissionHelp = fragment.createMultiPermissionForResult(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION)
        }
    )
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (Globals.app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val scanner get() = bluetoothAdapter?.bluetoothLeScanner

    private val _discoveredFlow = MutableStateFlow<List<DiscoveredHost>>(emptyList())
    val discoveredFlow: StateFlow<List<DiscoveredHost>> = _discoveredFlow.asStateFlow()

    private val _scanningFlow = MutableStateFlow(false)
    val scanningFlow: StateFlow<Boolean> = _scanningFlow.asStateFlow()

    private val discoveredMap = linkedMapOf<String, DiscoveredHost>()
    private var bleScanStopJob: Job? = null
    private var activeScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var scanRequested = false

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (scanRequested) return
        scanRequested = true
        btPermissionHelp.safeRun(notGivePermissionBlock = {
            scanRequested = false
            _scanningFlow.value = false
            loge { "ble scan permission denied" }
        }) {
            val owner = fragment.viewLifecycleOwnerLiveData.value
            if (!scanRequested || owner == null || !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                scanRequested = false
                return@safeRun
            }
            try {
                val sc = scanner ?: run {
                    scanRequested = false
                    return@safeRun
                }
                val callback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        if (scanCallback !== this) return
                        val bytes = result.scanRecord?.getServiceData(ParcelUuid(BleIpAdvertiser.BLE_IP_UUID)) ?: return
                        val (ip, port) = BleIpAdvertiser.decodeIpPort(bytes) ?: return
                        discoveredMap["$ip:$port"] = DiscoveredHost(ip, port)
                        _discoveredFlow.value = discoveredMap.values.toList()
                    }

                    override fun onScanFailed(errorCode: Int) {
                        if (scanCallback !== this) return
                        loge { "ble scan failed: $errorCode" }
                        stopScan()
                    }
                }
                val filter = ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(BleIpAdvertiser.BLE_IP_UUID))
                    .build()
                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .build()
                discoveredMap.clear()
                _discoveredFlow.value = emptyList()
                activeScanner = sc
                scanCallback = callback
                _scanningFlow.value = true
                sc.startScan(listOf(filter), settings, callback)
                bleScanStopJob = owner.lifecycleScope.launch {
                    delay(12000.milliseconds)
                    stopScan()
                    logdNoFile { "ble scan stopped after 12 seconds" }
                }
            } catch (e: SecurityException) {
                logEx(throwable = e) { "Bluetooth scan permission unavailable" }
                stopScan()
            } catch (e: IllegalStateException) {
                logEx(throwable = e) { "Bluetooth unavailable while starting scan" }
                stopScan()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanRequested = false
        bleScanStopJob?.cancel()
        bleScanStopJob = null
        val callback = scanCallback
        val sc = activeScanner
        scanCallback = null
        activeScanner = null
        _scanningFlow.value = false
        if (callback == null || sc == null) return
        try {
            sc.stopScan(callback)
        } catch (e: SecurityException) {
            logEx(throwable = e) { "Bluetooth permission revoked while stopping scan" }
        } catch (e: IllegalStateException) {
            logEx(throwable = e) { "Bluetooth unavailable while stopping scan" }
        }
    }
}
