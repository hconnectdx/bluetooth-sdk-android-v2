package kr.co.hconnect.bluetooth_sdk_android_peripheral_example

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kr.co.hconnect.bluetooth_sdk_android_peripheral.HCBlePeripheral
import kr.co.hconnect.bluetooth_sdk_android_peripheral.PeripheralConfig

class PeripheralExampleApp : Application() {

    companion object {
        private const val TAG = "PeripheralExampleApp"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "앱 시작 — HCBlePeripheral 초기화")

        HCBlePeripheral.init(this, PeripheralConfig())

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            HCBlePeripheral.start()
        } else {
            Log.w(TAG, "BLUETOOTH_ADVERTISE 권한 미승인 — 권한 승인 후 BLE 시작")
        }
    }

    override fun onTerminate() {
        HCBlePeripheral.stop()
        super.onTerminate()
    }
}
