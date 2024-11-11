package kr.co.hconnect.bluetooth_sdk_android_v2.gatt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.os.Build
import android.util.Log
import kr.co.hconnect.bluetooth_sdk_android_v2.util.Logger
import java.util.UUID

@SuppressLint("MissingPermission")
class GATTController(val bluetoothGatt: BluetoothGatt) {

    private lateinit var gattServiceList: List<BluetoothGattService>
    lateinit var selService: BluetoothGattService
    lateinit var selCharacteristic: BluetoothGattCharacteristic

    fun disconnect() {
        bluetoothGatt.disconnect()
//        bluetoothGatt.close()
    }

    fun getGattServiceList(): List<BluetoothGattService> {
        try {
            if (::gattServiceList.isInitialized.not()) {
                throw Exception("getGattServiceList: gattServiceList is not initialized")
            }
            return gattServiceList
        } catch (e: Exception) {
            Logger.e("getGattServiceList(): ${e.message}")
            throw e
        }
    }

    fun setGattServiceList(gattServiceList: List<BluetoothGattService>) {
        this.gattServiceList = gattServiceList
    }

    fun setServiceUUID(uuid: String) {
        try {
            if (::gattServiceList.isInitialized.not()) {
                throw Exception("setServiceUUID: gattServiceList is not initialized")
            }
            gattServiceList.find { it.uuid.toString() == uuid }?.let {
                selService = it
                Logger.d("setServiceUUID: $uuid")
            }
        } catch (e: Exception) {
            Logger.e("setServiceUUID: ${e.message}")
        }

    }

    fun setCharacteristicUUID(characteristicUUID: String) {
        try {
            if (::selService.isInitialized.not()) {
                throw Exception("Service is not initialized")
            }
            selService.characteristics.find { it.uuid.toString() == characteristicUUID }?.let {
                selCharacteristic = it
                Logger.d("setCharacteristicUUID: $characteristicUUID")
            }
        } catch (e: Exception) {
            Logger.e("setCharacteristicUUID: ${e.message}")
        }
    }

    fun readCharacteristic() {
        bluetoothGatt.readCharacteristic(selCharacteristic)
    }

    fun writeCharacteristic(data: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33 이상
            bluetoothGatt.writeCharacteristic(
                selCharacteristic,
                data,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } else { // API 32 이하
            selCharacteristic.value = data
            bluetoothGatt.writeCharacteristic(selCharacteristic)
        }
    }

    fun setCharacteristicNotification(isEnable: Boolean, isIndicate: Boolean = false) {
        // 알림 또는 인디케이션 설정
        bluetoothGatt.setCharacteristicNotification(selCharacteristic, isEnable)

        // CCCD (Client Characteristic Configuration Descriptor) UUID
        val descriptor =
            selCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))

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
            selCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        bluetoothGatt.readDescriptor(descriptor)
    }
}