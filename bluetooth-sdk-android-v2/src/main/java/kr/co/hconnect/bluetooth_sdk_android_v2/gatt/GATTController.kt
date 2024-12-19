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
    lateinit var targetService: BluetoothGattService
    lateinit var targetCharacteristic: BluetoothGattCharacteristic

    fun disconnect() {
        bluetoothGatt.disconnect()
        bluetoothGatt.close()
    }

    fun getGattServiceList(): List<BluetoothGattService>? {
        try {
            if (::gattServiceList.isInitialized.not()) {
                Logger.e("getGattServiceList(): gattServiceList is not initialized")
                return null
            }
            return gattServiceList
        } catch (e: Exception) {
            Logger.e("getGattServiceList(): ${e.message}")
            throw e
        }
    }

    fun isGattInitialized(): Boolean {
        return ::gattServiceList.isInitialized
    }

    fun setGattServiceList(gattServiceList: List<BluetoothGattService>) {
        if (gattServiceList.isEmpty()) {
            Logger.e("getGattServiceList(): gattServiceList is not initialized")
            return
        }
        this.gattServiceList = gattServiceList

        Logger.d("setGattServiceList: ${gattServiceList.size}")
        gattServiceList.forEach { service ->
            Logger.d("Registered Service UUID: ${service.uuid}")
            service.characteristics.forEach { characteristic ->
                Logger.d("Registered Characteristic UUID: ${characteristic.uuid}")
            }
        }
    }

    fun setTargetServiceUUID(uuid: String) {
        try {
            if (::gattServiceList.isInitialized.not()) {
                Logger.e("setTargetServiceUUID: gattServiceList is not initialized")
                return
            }

            val findService = gattServiceList.find { it.uuid.toString() == uuid }


            Logger.d("내가 선택한 서비스 uuid: ${uuid}")
            gattServiceList.forEach { service ->
                Logger.d("2Registered Service UUID: ${service.uuid}")
                service.characteristics.forEach { characteristic ->
                    Logger.d("2Registered Characteristic UUID: ${characteristic.uuid}")
                }
            }


            if (findService == null) {
                Logger.e("setTargetServiceUUID: Service not found")
                return
            }

            Logger.d("setTargetServiceUUID: Service found $findService")
            targetService = findService

        } catch (e: Exception) {
            Logger.e("setTargetServiceUUID: ${e.message}")
        }

    }

    fun setTargetCharacteristicUUID(characteristicUUID: String) {
        try {
            if (::targetService.isInitialized.not()) {
                Logger.e("setTargetCharacteristicUUID: Service is not initialized")
                return
            }
            targetService.characteristics.find { it.uuid.toString() == characteristicUUID }?.let {
                targetCharacteristic = it
                Logger.d("setTargetCharacteristicUUID: $characteristicUUID")
            }

        } catch (e: Exception) {
            Logger.e("setTargetCharacteristicUUID: ${e.message}")
        }
    }

    fun readCharacteristic() {
        if (::targetCharacteristic.isInitialized.not()) {
            Logger.e("selTargetCharacteristic is not initialized")
            return
        }
        bluetoothGatt.readCharacteristic(targetCharacteristic)

    }

    fun writeCharacteristic(data: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33 이상

            bluetoothGatt.writeCharacteristic(
                targetCharacteristic,
                data,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } else { // API 32 이하
            targetCharacteristic.value = data
            bluetoothGatt.writeCharacteristic(targetCharacteristic)
        }
    }

    fun setCharacteristicNotification(isEnable: Boolean, isIndicate: Boolean = false) {
        if (::gattServiceList.isInitialized.not()) {
            Logger.e("gattServiceList list is empty")
            return
        }
        if (::targetService.isInitialized.not()) {
            Logger.e("targetService is not initialized")
            return
        }
        // 알림 또는 인디케이션 설정
        bluetoothGatt.setCharacteristicNotification(targetCharacteristic, isEnable)

        // CCCD (Client Characteristic Configuration Descriptor) UUID
        val descriptor =
            targetCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))

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
        if (::targetCharacteristic.isInitialized.not()) {
            Logger.e("targetCharacteristic is not initialized")
            return
        }
        val descriptor =
            targetCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        bluetoothGatt.readDescriptor(descriptor)
    }
}