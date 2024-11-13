package kr.co.kmwdev.bluetooth_sdk_android_v2_example.bluetooth.ble_sdk

import android.bluetooth.le.ScanResult
import kr.co.hconnect.bluetooth_sdk_android_v2.HCBle
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.MyApplication
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.util.Logger
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.util.MyPermission

object BleSdkManager {

    fun init() {
        val context = MyApplication.getAppContext()
        HCBle.init(context)
    }

    /**
     * Start BLE scan
     */
    fun startBleScan(
        onScanResult: (ScanResult) -> Unit,
        onScanStop: () -> Unit,
        initBondedList: (() -> Unit)? = null
    ) {

        val isGranted = MyPermission.isGrantedPermissions(
            MyApplication.getAppContext(),
            MyPermission.PERMISSION_BLUETOOTH
        )

        when (isGranted) {
            true -> {
                doScan(
                    onScanResult = { scanResult: ScanResult ->
                        onScanResult.invoke(scanResult)
                    },
                    onScanStop = onScanStop
                )
            }

            false -> {
                MyPermission.launchPermissions(MyPermission.PERMISSION_BLUETOOTH) { permissions ->
                    if (permissions.containsValue(false)) {
                        Logger.e("Permission denied")
                    } else {
                        Logger.d("Permission granted")
                        initBondedList?.invoke()
                        doScan(
                            onScanResult = { scanResult: ScanResult ->
                                onScanResult.invoke(scanResult)
                            },
                            onScanStop = onScanStop
                        )
                    }
                }
            }
        }
    }

    private fun doScan(onScanResult: (ScanResult) -> Unit, onScanStop: () -> Unit) {

        HCBle.scanLeDevice(
            onScanResult = { scanResult: ScanResult ->
                onScanResult.invoke(scanResult)
            },
            onScanStop = {
                onScanStop()
            },
        )
    }

    /**
     * Stop BLE scan
     */
    fun stopBleScan() {
        HCBle.scanStop()
    }
}