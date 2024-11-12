package kr.co.kmwdev.bluetooth_sdk_android_v2_example.bluetooth.model

import android.bluetooth.le.ScanResult

data class ScanResultModel(
    val state: Int,
    val bondState: Int,
    val scanResult: ScanResult,
)