package kr.co.hconnect.bluetooth_sdk_android_v2.gatt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.os.Build
import android.util.Log
import java.util.UUID

@SuppressLint("MissingPermission")
@Deprecated("Use GATTController instead")
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

    fun setCharacteristicNotification(isEnable: Boolean, isIndicate: Boolean = false) {
        // 알림 또는 인디케이션 설정
        bluetoothGatt.setCharacteristicNotification(_selCharacteristic, isEnable)

        // CCCD (Client Characteristic Configuration Descriptor) UUID
        val descriptor =
            _selCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))

        // Descriptor가 존재하는지 체크
        descriptor?.let {
            val value = when {
                isEnable && isIndicate -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                isEnable -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                else -> BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            }

            // API 33 이상인 경우와 이하 버전에 맞게 처리
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bluetoothGatt.writeDescriptor(descriptor, value)
            } else {
                descriptor.value = value
                bluetoothGatt.writeDescriptor(descriptor)
            }
        } ?: Log.e("BluetoothGatt", "Descriptor not found for characteristic")
    }


    fun readCharacteristicNotification() {
        val descriptor =
            _selCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        bluetoothGatt.readDescriptor(descriptor)
    }
}