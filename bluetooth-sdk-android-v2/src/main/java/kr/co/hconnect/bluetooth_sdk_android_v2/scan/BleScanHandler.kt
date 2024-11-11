package kr.co.hconnect.bluetooth_sdk_android_v2.scan

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.util.Log

class BleScanHandler(onScanResult: (ScanResult) -> Unit) {
    private val _leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            Log.d("BleScanHandler", "onScanResult: $result")
            onScanResult.invoke(result)
        }
    }
    val leScanCallback: ScanCallback
        get() = _leScanCallback
}