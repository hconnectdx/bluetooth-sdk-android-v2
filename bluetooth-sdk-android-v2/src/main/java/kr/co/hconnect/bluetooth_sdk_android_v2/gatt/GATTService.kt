package kr.co.hconnect.bluetooth_sdk_android.gatt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.os.Build
import android.util.Log
import java.util.UUID

@SuppressLint("MissingPermission")
class GATTService(private val bluetoothGatt: BluetoothGatt) {
    private lateinit var gattServiceList: List<BluetoothGattService>
    private lateinit var _selService: BluetoothGattService
    val selService: BluetoothGattService
        get() = _selService

    private lateinit var _selCharacteristic: BluetoothGattCharacteristic
    val selCharacteristic: BluetoothGattCharacteristic
        get() = _selCharacteristic

    fun getGattServiceList(): List<BluetoothGattService> {
        try {
            if (::gattServiceList.isInitialized.not()) {
                Log.e("GATTService", "Service list is empty")
                throw Exception("Service list is empty")
            }
            return gattServiceList
        } catch (e: Exception) {
            Log.e("GATTService", "${e.message}")
            throw e
        }
    }

    fun setGattServiceList(gattServiceList: List<BluetoothGattService>) {
        this.gattServiceList = gattServiceList
    }

    fun setServiceUUID(uuid: String) {
        try {
            if (::gattServiceList.isInitialized.not()) {
                throw Exception("Service list is empty")
            }
            gattServiceList.find { it.uuid.toString() == uuid }?.let {
                Log.d("GATTService", "Service UUID: $uuid")
                _selService = it
            }
        } catch (e: Exception) {
            Log.e("GATTService", "${e.message}")
        }

    }

    fun setCharacteristicUUID(characteristicUUID: String) {
        try {
            if (::_selService.isInitialized.not()) {
                throw Exception("Service is not initialized")
            }
            _selService.characteristics.find { it.uuid.toString() == characteristicUUID }?.let {
                Log.d("GATTService", "Characteristic UUID: $characteristicUUID")
                _selCharacteristic = it
            }
        } catch (e: Exception) {
            Log.e("GATTService", "${e.message}")
        }
    }

    fun readCharacteristic() {
        bluetoothGatt.readCharacteristic(_selCharacteristic)
    }

    fun writeCharacteristic(data: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33 이상
            bluetoothGatt.writeCharacteristic(
                _selCharacteristic,
                data,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } else { // API 32 이하
            _selCharacteristic.value = data
            bluetoothGatt.writeCharacteristic(_selCharacteristic)
        }
    }

    fun setCharacteristicNotification(isEnable: Boolean) {
        bluetoothGatt.setCharacteristicNotification(_selCharacteristic, isEnable)
        val descriptor =
            _selCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33 이상
            if (isEnable) {
                bluetoothGatt.writeDescriptor(
                    descriptor,
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                )
            } else {
                bluetoothGatt.writeDescriptor(
                    descriptor,
                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                )
            }
        } else { // API 32 이하
            if (isEnable) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            } else {
                descriptor.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            }
            bluetoothGatt.writeDescriptor(descriptor)
        }
    }

    fun readCharacteristicNotification() {
        val descriptor =
            _selCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        bluetoothGatt.readDescriptor(descriptor)
    }
}