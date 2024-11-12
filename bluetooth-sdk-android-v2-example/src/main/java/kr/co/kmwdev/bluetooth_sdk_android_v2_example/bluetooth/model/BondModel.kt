package kr.co.kmwdev.bluetooth_sdk_android_v2_example.bluetooth.model

import android.bluetooth.BluetoothDevice

data class BondModel(
    val state: Int,
    val bondState: Int,
    val device: BluetoothDevice,
)