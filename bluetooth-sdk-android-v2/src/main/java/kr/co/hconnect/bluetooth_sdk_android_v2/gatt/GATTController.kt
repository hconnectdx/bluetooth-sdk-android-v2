package kr.co.hconnect.bluetooth_sdk_android_v2.gatt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.co.hconnect.bluetooth_sdk_android_v2.util.Logger
import java.util.UUID

@SuppressLint("MissingPermission")
class GATTController(val bluetoothGatt: BluetoothGatt) {

    private var gattServiceList: List<BluetoothGattService>? = null // 🔧 수정: nullable로 변경
    private var targetService: BluetoothGattService? = null // 🔧 수정: nullable로 변경
    private var targetReadCharacteristic: BluetoothGattCharacteristic? = null // 🔧 수정: nullable로 변경
    private var targetWriteCharacteristic: BluetoothGattCharacteristic? =
        null // 🔧 수정: nullable로 변경

    // 🆕 추가: 리소스 정리 상태 관리
    private var isDestroyed = false

    // 🆕 추가: 현재 활성 Notification Descriptor 추적
    private var activeNotificationDescriptor: BluetoothGattDescriptor? = null

    // 🆕 추가: MTU 협상 상태 관리
    private var currentMtu: Int = 23
    private var mtuRequestCallback: ((mtu: Int, success: Boolean) -> Unit)? = null

    /** 마지막으로 협상 완료된 MTU 값 (기본 23) */
    val negotiatedMtu: Int
        get() = currentMtu

    // 🔧 수정: disconnect 메소드 개선 - 단계적 정리
    fun disconnect() {
        if (isDestroyed) {
            Logger.d("GATTController already destroyed")
            return
        }

        Logger.d("GATTController disconnect started")

        try {
            // 1. 먼저 notification 비활성화
            disableAllNotifications()

            // 2. GATT 연결 해제 (close는 하지 않음 - HCBle에서 처리)
            bluetoothGatt.disconnect()

            Logger.d("GATTController disconnect completed")

        } catch (e: Exception) {
            Logger.e("Error during GATTController disconnect: ${e.message}")
        }
    }

    // 🆕 추가: 모든 notification 비활성화
    private fun disableAllNotifications() {
        try {
            activeNotificationDescriptor?.let { descriptor ->
                Logger.d("Disabling active notification")

                val disableValue = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    bluetoothGatt.writeDescriptor(descriptor, disableValue)
                } else {
                    descriptor.value = disableValue
                    bluetoothGatt.writeDescriptor(descriptor)
                }

                // notification 설정도 해제
                targetReadCharacteristic?.let { characteristic ->
                    bluetoothGatt.setCharacteristicNotification(characteristic, false)
                }

                activeNotificationDescriptor = null
            }
        } catch (e: Exception) {
            Logger.e("Error disabling notifications: ${e.message}")
        }
    }

    // 🆕 추가: 완전한 리소스 정리 (HCBle.disconnect에서 호출용)
    fun destroy() {
        if (isDestroyed) return

        Logger.d("GATTController destroy started")

        try {
            // 1. Notification 비활성화
            disableAllNotifications()

            // 2. GATT 연결 해제
            bluetoothGatt.disconnect()

            // 3. 참조 정리
            clearReferences()

            // 4. GATT 리소스 정리 (지연 후)
            CoroutineScope(Dispatchers.IO).launch {
                delay(500) // GATT 해제 완료 대기
                try {
                    bluetoothGatt.close()
                    Logger.d("GATTController GATT closed")
                } catch (e: Exception) {
                    Logger.e("Error closing GATT: ${e.message}")
                }
            }

            isDestroyed = true
            Logger.d("GATTController destroy completed")

        } catch (e: Exception) {
            Logger.e("Error during GATTController destroy: ${e.message}")
        }
    }

    // 🆕 추가: 모든 참조 정리
    private fun clearReferences() {
        gattServiceList = null
        targetService = null
        targetReadCharacteristic = null
        targetWriteCharacteristic = null
        activeNotificationDescriptor = null
    }

    fun getGattServiceList(): List<BluetoothGattService>? {
        if (isDestroyed) {
            Logger.e("GATTController is destroyed")
            return null
        }

        try {
            if (gattServiceList == null) { // 🔧 수정: nullable 체크로 변경
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
        return !isDestroyed && gattServiceList != null // 🔧 수정: nullable 체크로 변경
    }

    fun setGattServiceList(gattServiceList: List<BluetoothGattService>) {
        if (isDestroyed) {
            Logger.e("GATTController is destroyed")
            return
        }

        if (gattServiceList.isEmpty()) {
            Logger.e("setGattServiceList(): gattServiceList is empty")
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
        if (isDestroyed) {
            Logger.e("GATTController is destroyed")
            return
        }

        try {
            if (gattServiceList == null) { // 🔧 수정: nullable 체크로 변경
                Logger.e("setTargetServiceUUID: gattServiceList is not initialized")
                return
            }

            val findService = gattServiceList!!.find { it.uuid.toString() == uuid }

            Logger.d("내가 선택한 서비스 uuid: ${uuid}")
            gattServiceList!!.forEach { service ->
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

    fun setTargetReadCharacteristicUUID(characteristicUUID: String) {
        if (isDestroyed) {
            Logger.e("GATTController is destroyed")
            return
        }

        try {
            if (targetService == null) { // 🔧 수정: nullable 체크로 변경
                Logger.e("setTargetReadCharacteristicUUID: Service is not initialized")
                return
            }

            val findCharacteristic = targetService!!.characteristics.find {
                it.uuid.toString() == characteristicUUID
            }

            if (findCharacteristic == null) {
                Logger.e("setTargetReadCharacteristicUUID: Read Characteristic not found - $characteristicUUID")
                return
            }

            targetReadCharacteristic = findCharacteristic
            Logger.d("setTargetReadCharacteristicUUID: $characteristicUUID")

        } catch (e: Exception) {
            Logger.e("setTargetReadCharacteristicUUID: ${e.message}")
        }
    }

    fun setTargetWriteCharacteristicUUID(characteristicUUID: String) {
        if (isDestroyed) {
            Logger.e("GATTController is destroyed")
            return
        }

        try {
            if (targetService == null) { // 🔧 수정: nullable 체크로 변경
                Logger.e("setTargetWriteCharacteristicUUID: Service is not initialized")
                return
            }

            val findCharacteristic = targetService!!.characteristics.find {
                it.uuid.toString() == characteristicUUID
            }

            if (findCharacteristic == null) {
                Logger.e("setTargetWriteCharacteristicUUID: Write Characteristic not found - $characteristicUUID")
                return
            }

            targetWriteCharacteristic = findCharacteristic
            Logger.d("setTargetWriteCharacteristicUUID: $characteristicUUID")

        } catch (e: Exception) {
            Logger.e("setTargetWriteCharacteristicUUID: ${e.message}")
        }
    }

    fun readCharacteristic() {
        if (isDestroyed) {
            Logger.e("GATTController is destroyed")
            return
        }

        if (targetReadCharacteristic == null) { // 🔧 수정: nullable 체크로 변경
            Logger.e("targetReadCharacteristic is not initialized")
            return
        }

        try {
            bluetoothGatt.readCharacteristic(targetReadCharacteristic!!)
        } catch (e: Exception) {
            Logger.e("Error reading characteristic: ${e.message}")
        }
    }

    // 당신의 코드에 추가
    fun writeCharacteristic(data: ByteArray) {
        if (isDestroyed) {
            Logger.e("GATTController is destroyed")
            return
        }

        // ⭐ 샘플처럼 connect() 호출
        val isConnected = bluetoothGatt.connect()
        Logger.d("bluetoothGatt.connect() returned: $isConnected")

        if (!isConnected) {
            Logger.e("❌ GATT is not connected!")
            return
        }

        val writeChar = targetWriteCharacteristic ?: run {
            Logger.e("targetWriteCharacteristic is not initialized")
            return
        }

        // 5ms 딜레이
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                Logger.d("Writing characteristic...")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val result = bluetoothGatt.writeCharacteristic(
                        writeChar,
                        data,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    )
                    Logger.d("writeCharacteristic returned: $result")
                } else {
                    writeChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    writeChar.value = data
                    val result = bluetoothGatt.writeCharacteristic(writeChar)
                    Logger.d("writeCharacteristic returned: $result")
                }
            } catch (e: Exception) {
                Logger.e("Exception: ${e.message}")
                e.printStackTrace()
            }
        }, 5)
    }

    fun setCharacteristicNotification(isEnable: Boolean, isIndicate: Boolean = false) {
        if (isDestroyed) {
            Logger.e("GATTController is destroyed")
            return
        }

        if (gattServiceList == null) { // 🔧 수정: nullable 체크로 변경
            Logger.e("gattServiceList is not initialized")
            return
        }
        if (targetService == null) { // 🔧 수정: nullable 체크로 변경
            Logger.e("targetService is not initialized")
            return
        }
        if (targetReadCharacteristic == null) { // 🔧 수정: nullable 체크로 변경
            Logger.e("targetReadCharacteristic is not initialized")
            return
        }

        try {

            // 알림 또는 인디케이션 설정
            bluetoothGatt.setCharacteristicNotification(targetReadCharacteristic!!, isEnable)

            // CCCD (Client Characteristic Configuration Descriptor) UUID
            val descriptor =
                targetReadCharacteristic!!.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))

            // Descriptor가 존재하는지 체크
            descriptor?.let {
                val value = when {
                    isEnable && isIndicate -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    isEnable -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    else -> BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                }

                // 🔧 수정: 활성 descriptor 추적
                if (isEnable) {
                    activeNotificationDescriptor = descriptor
                } else {
                    activeNotificationDescriptor = null
                }

                // API 33 이상인 경우와 이하 버전에 맞게 처리
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    bluetoothGatt.writeDescriptor(descriptor, value)
                } else {
                    descriptor.value = value
                    bluetoothGatt.writeDescriptor(descriptor)
                }

                Logger.d("Notification ${if (isEnable) "enabled" else "disabled"} for characteristic")

            } ?: run {
                Logger.e("Descriptor not found for targetReadCharacteristic")
            }

        } catch (e: Exception) {
            Logger.e("Error setting characteristic notification: ${e.message}")
        }
    }

    fun readCharacteristicNotification() {
        if (isDestroyed) {
            Logger.e("GATTController is destroyed")
            return
        }

        if (targetReadCharacteristic == null) { // 🔧 수정: nullable 체크로 변경
            Logger.e("targetReadCharacteristic is not initialized")
            return
        }

        try {
            val descriptor =
                targetReadCharacteristic!!.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            descriptor?.let {
                bluetoothGatt.readDescriptor(it)
            } ?: Logger.e("Descriptor not found for readCharacteristicNotification")
        } catch (e: Exception) {
            Logger.e("Error reading characteristic notification: ${e.message}")
        }
    }

    /**
     * MTU 협상을 요청한다.
     * 결과(성공/실패, 실제 협상된 MTU 값)는 [onResult] 콜백으로 비동기 전달된다.
     * (안드로이드 [BluetoothGatt.requestMtu]의 반환값은 "요청이 큐잉되었는지" 여부일 뿐,
     * 실제 협상 결과가 아니므로 반드시 콜백 또는 [negotiatedMtu]로 결과를 확인해야 한다.)
     *
     * @return 요청이 정상적으로 큐잉되었는지 여부
     */
    fun requestMtu(mtu: Int, onResult: ((mtu: Int, success: Boolean) -> Unit)? = null): Boolean {
        if (isDestroyed) {
            Logger.e("GATTController is destroyed")
            onResult?.invoke(currentMtu, false)
            return false
        }

        val queued = try {
            bluetoothGatt.requestMtu(mtu)
        } catch (e: Exception) {
            Logger.e("requestMtu($mtu) exception: ${e.message}")
            false
        }

        if (queued) {
            mtuRequestCallback = onResult
            Logger.d("requestMtu($mtu) queued, waiting for onMtuChanged")
        } else {
            Logger.e("requestMtu($mtu) failed to queue")
            onResult?.invoke(currentMtu, false)
        }

        return queued
    }

    /** HCBle의 BluetoothGattCallback.onMtuChanged에서 호출되어 협상 결과를 반영한다. */
    internal fun handleMtuChanged(mtu: Int, status: Int) {
        val success = status == BluetoothGatt.GATT_SUCCESS
        if (success) {
            Logger.d("MTU negotiated: $currentMtu -> $mtu")
            currentMtu = mtu
        } else {
            Logger.e("MTU negotiation failed (status=$status), keep current=$currentMtu")
        }

        val callback = mtuRequestCallback
        mtuRequestCallback = null
        callback?.invoke(mtu, success)
    }

    // 🆕 추가: 안전한 getter 메소드들 (HCBle에서 호출용)
    fun getTargetService(): BluetoothGattService? {
        return if (isDestroyed) null else targetService
    }

    fun getTargetReadCharacteristic(): BluetoothGattCharacteristic? {
        return if (isDestroyed) null else targetReadCharacteristic
    }

    fun getTargetWriteCharacteristic(): BluetoothGattCharacteristic? {
        return if (isDestroyed) null else targetWriteCharacteristic
    }

    // 🆕 추가: 상태 확인 메소드
    fun isValid(): Boolean {
        return !isDestroyed
    }

    // 🆕 추가: 디버그 정보
    fun getDebugInfo(): String {
        return """
            IsDestroyed: $isDestroyed
            GattServiceList: ${gattServiceList?.size ?: "null"}
            TargetService: ${targetService?.uuid ?: "null"}
            TargetReadCharacteristic: ${targetReadCharacteristic?.uuid ?: "null"}
            TargetWriteCharacteristic: ${targetWriteCharacteristic?.uuid ?: "null"}
            ActiveNotificationDescriptor: ${activeNotificationDescriptor != null}
        """.trimIndent()
    }
}