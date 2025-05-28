package kr.co.hconnect.polihealth_sdk_android_v2

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kr.co.hconnect.bluetooth_sdk_android_v2.HCBle
import kr.co.hconnect.polihealth_sdk_android_v2.api.dto.response.PoliResponse
import kr.co.hconnect.polihealth_sdk_android_v2.api.sleep.SleepProtocol06API
import kr.co.hconnect.polihealth_sdk_android_v2.api.sleep.SleepProtocol07API
import kr.co.hconnect.polihealth_sdk_android_v2.api.sleep.SleepProtocol08API
import kr.co.hconnect.polihealth_sdk_android_v2.service.sleep.SleepApiService
import kr.co.hconnect.polihealth_sdk_android_v2.api.daily.DailyProtocol02API
import kr.co.hconnect.polihealth_sdk_android_v2.service.daily.DailyServiceToApp
import kr.co.hconnect.polihealth_sdk_android_v2.utils.toHexString

object PoliBLE {
    private const val TAG = "PoliBLE.kt"
    fun init(context: Context) {
        HCBle.init(context)
    }

    fun startScan(scanDevice: (ScanResult) -> Unit) {
        HCBle.scanLeDevice(
            onScanResult = { device ->
                scanDevice.invoke(device)
            }, onScanStop = {
                Log.d(TAG, "Scan Stop")
            })
    }

    fun stopScan() {
        HCBle.scanStop()
    }

    private var expectedByte: Byte = 0x00
    private var protocol2Count = 0
    private var noMeaningData: Byte = 0x00 // 업데이트를 위한 의미없는 데이터
    private var p2ExpectedOrder: Byte = 0x00.toByte() // Added for Protocol 02 order tracking

    @RequiresApi(Build.VERSION_CODES.Q)
    fun connectDevice(
        context: Context? = null,
        device: BluetoothDevice,
        onConnState: (state: Int) -> Unit,
        onGattServiceState: (gatt: Int, services: List<BluetoothGattService>) -> Unit,
        onBondState: (bondState: Int) -> Unit,
        onSubscriptionState: (state: Boolean) -> Unit,
        onReceive: (type: ProtocolType, response: PoliResponse?) -> Unit,
        onWriteCharacteristic:(state: Int, char: BluetoothGattCharacteristic) -> Unit,
        autoConnect: Boolean
    ) {
        HCBle.connectToDevice(
            isAutoConnect = autoConnect,
            device = device,
            onConnState = { state -> onConnState.invoke(state) },
            onGattServiceState = { gatt, services -> onGattServiceState.invoke(gatt, services) },
            onBondState = { bondState -> onBondState.invoke(bondState) },
            onSubscriptionState = { state -> onSubscriptionState.invoke(state) },
            onReceive = { characteristic ->
                val receivedArray = characteristic.value ?: ByteArray(0)
                processReceivedData(receivedArray, context, onReceive)
            },
            onWriteCharacteristic = { state, char ->
                char?.let {
                    onWriteCharacteristic(state, char)
                }
            }
        )
    }

    private fun processReceivedData(
        byteArray: ByteArray,
        context: Context?,
        onReceive: (type: ProtocolType, response: PoliResponse?) -> Unit
    ) {
        if (byteArray.isEmpty()) return

        val protocolType = byteArray[0]
        val dataOrder = byteArray.getOrNull(1) ?: 0x00.toByte()

        when (protocolType) {
            0x01.toByte() -> handleProtocol01(byteArray, context, onReceive)
            0x02.toByte() -> handleProtocol02(byteArray, dataOrder, context, onReceive)
            0x03.toByte() -> handleProtocol03(byteArray, onReceive)
            0x04.toByte() -> handleProtocol04(context, onReceive)
            0x05.toByte() -> handleProtocol05(context, onReceive)
            0x06.toByte() -> handleProtocol06(byteArray, context, onReceive)
            0x07.toByte() -> handleProtocol07(byteArray, context, onReceive)
            0x08.toByte() -> handleProtocol08(byteArray, context, onReceive)
            0x09.toByte() -> handleProtocol09(byteArray, onReceive)
            else -> logUnknownProtocol(byteArray)
        }
    }

    private fun handleProtocol01(
        byteArray: ByteArray,
        context: Context?,
        onReceive: (type: ProtocolType, response: PoliResponse?) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            DailyServiceToApp.sendProtocol01ToApp(byteArray, context, onReceive)
        }
    }

    private fun handleProtocol02(
        byteArray: ByteArray,
        dataOrder: Byte,
        context: Context?,
        onReceive: (type: ProtocolType, response: PoliResponse?) -> Unit
    ) {
        Log.d(TAG, "Received ByteArray: ${byteArray.joinToString(" ") { "%02x".format(it) }}")
        DailyProtocol02API.apply {
            CoroutineScope(Dispatchers.IO).launch {
                Log.d(TAG, "DataOrder_: ${dataOrder.toHexString()}")

                val isLast = dataOrder == 0xFF.toByte()

                if (isLast) {
                    // Last packet (0xFF)
                    p2ExpectedOrder = 0x00.toByte() // Reset for the next sequence
                } else {
                    // Packet is 0x00 to 0xFE
                    if (dataOrder == 0x00.toByte()) {
                        // For 0x00, the next expected packet is 0x01.
                        // Specific error for "bad 0x00 start" is handled by the existing prevByte check below.
                        p2ExpectedOrder = 0x01.toByte()
                    } else {
                        // Packet is 0x01 to 0xFE
                        if (dataOrder != p2ExpectedOrder) {
                            onReceive.invoke(ProtocolType.PROTOCOL_2_ERROR_LACK_OF_DATA, null)
                        }
                        // Update expectation for the next packet, even if there was loss, to resync.
                        p2ExpectedOrder = (dataOrder + 1).toByte()
                    }
                }

                // Existing logic with user's requested modification for specific 0x00 start condition
                if (prevByte != 0xFE.toByte() && dataOrder == 0x00.toByte()) {
                    onReceive.invoke(ProtocolType.PROTOCOL_2_START, null)
                }

                prevByte = dataOrder // Update prevByte for the next call's check
                addByteNew(removeFrontTwoBytes(byteArray, 2), isLast = isLast)
                if (isLast) {
                    DailyServiceToApp.sendProtocol2ToApp(context, onReceive)
                }
            }
        }
    }

    private fun handleProtocol03(
        byteArray: ByteArray,
        onReceive: (type: ProtocolType, response: PoliResponse?) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            DailyServiceToApp.sendProtocol03ToApp(byteArray, onReceive)
        }
    }

    private fun handleProtocol04(
        context: Context?,
        onReceive: (type: ProtocolType, response: PoliResponse?) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = SleepApiService().sendStartSleep()
                val type = if (response.retCd == "0") ProtocolType.PROTOCOL_4_SLEEP_START
                else ProtocolType.PROTOCOL_4_SLEEP_START_ERROR
                onReceive.invoke(type, response)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handleProtocol05(
        context: Context?,
        onReceive: (type: ProtocolType, response: PoliResponse?) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = SleepApiService().sendEndSleep()
                val type = if (response.retCd == "0") ProtocolType.PROTOCOL_5_SLEEP_END
                else ProtocolType.PROTOCOL_5_SLEEP_END_ERROR
                onReceive.invoke(type, response)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handleProtocol06(
        byteArray: ByteArray,
        context: Context?,
        onReceive: (type: ProtocolType, response: PoliResponse?) -> Unit
    ) {
        SleepProtocol06API.addByte(removeFrontTwoBytes(byteArray, 2))
        if (byteArray[1] == 0xFF.toByte()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = SleepApiService().sendProtocol06(context)
                    onReceive.invoke(ProtocolType.PROTOCOL_6, response)
                } catch (e: Exception) {
                    e.printStackTrace()
                    onReceive.invoke(ProtocolType.PROTOCOL_6_ERROR, null)
                }
            }
        } else {
            onReceive.invoke(ProtocolType.PROTOCOL_6, null)
        }
    }

    private fun handleProtocol07(
        byteArray: ByteArray,
        context: Context?,
        onReceive: (type: ProtocolType, response: PoliResponse?) -> Unit
    ) {
        SleepProtocol07API.addByte(removeFrontTwoBytes(byteArray, 2))
        if (byteArray[1] == 0xFF.toByte()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = SleepApiService().sendProtocol07(context)
                    onReceive.invoke(ProtocolType.PROTOCOL_7, response)
                } catch (e: Exception) {
                    e.printStackTrace()
                    onReceive.invoke(ProtocolType.PROTOCOL_7_ERROR, null)
                }
            }
        } else {
            onReceive.invoke(ProtocolType.PROTOCOL_7, null)
        }
    }

    private fun handleProtocol08(
        byteArray: ByteArray,
        context: Context?,
        onReceive: (type: ProtocolType, response: PoliResponse?) -> Unit
    ) {
        SleepProtocol08API.addByte(removeFrontTwoBytes(byteArray, 2))
        if (byteArray[1] == 0xFF.toByte()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = SleepApiService().sendProtocol08(context)
                    onReceive.invoke(ProtocolType.PROTOCOL_8, response)
                } catch (e: Exception) {
                    onReceive.invoke(ProtocolType.PROTOCOL_8_ERROR, null)
                }
            }
        } else {
            onReceive.invoke(ProtocolType.PROTOCOL_8, null)
        }
    }

    private fun handleProtocol09(
        byteArray: ByteArray,
        onReceive: (type: ProtocolType, response: PoliResponse?) -> Unit
    ) {
        val hrSpO2 = HRSpO2Parser.asciiToHRSpO2(removeFrontTwoBytes(byteArray, 1))
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = SleepApiService().sendProtocol09(hrSpO2)
                onReceive.invoke(ProtocolType.PROTOCOL_9_HR_SpO2, response)
            } catch (e: Exception) {
                e.printStackTrace()
                onReceive.invoke(ProtocolType.PROTOCOL_9_ERROR, null)
            }
        }
    }

    private fun logUnknownProtocol(byteArray: ByteArray) {
        Log.e(TAG, "Unknown Protocol: ${byteArray.joinToString(" ") { "%02x".format(it) }}")
    }

    private fun checkProtocol2Validate(it: Byte): Boolean {
        if (it != 0xFF.toByte() && it != expectedByte) {
            Log.e(
                "DataLogger",
                "데이터 손실 감지: 예상 값 ${
                    String.format(
                        "0x%02X",
                        expectedByte
                    )
                } 실제 값 ${String.format("0x%02X", it)}"
            )
            return false
        }
        expectedByte =
            if (it == 0xFE.toByte()) 0x00 else (it + 1).toByte()
        Log.d("Protocol2 Count", "Count: ${++protocol2Count}")
        return true
    }


    fun removeFrontTwoBytes(byteArray: ByteArray, size: Int): ByteArray {
        // 배열의 길이가 2 이상인 경우에만 앞의 2바이트를 제거
        if (byteArray.size > size) {
            return byteArray.copyOfRange(size, byteArray.size)
        }
        // 배열의 길이가 2 이하인 경우 빈 배열 반환
        return ByteArray(0)
    }

    fun disconnectDevice(deviceAddress: String) {
        HCBle.disconnect(deviceAddress)
    }

    /**
     * TODO: GATT Service 리스트를 반환합니다.
     * 블루투스가 연결되어 onServicesDiscovered 콜백이 호출 돼야 사용가능합니다.
     * @return
     */
    fun getGattServiceList(deviceAddress: String): List<BluetoothGattService>? {
        return HCBle.getGattServiceList(deviceAddress)
    }

    /**
     * TODO: 서비스 UUID를 설정합니다.
     * 사용 하고자 하는 서비스 UUID를 설정합니다.
     * @param uuid
     */
    fun setTargetServiceUUID(deviceAddress: String, uuid: String) {
        HCBle.setTargetServiceUUID(deviceAddress, uuid)
    }

    /**
     * TODO: 캐릭터리스틱 UUID를 설정합니다.
     * 사용 하고자 하는 캐릭터리스틱 UUID를 설정합니다.
     * @param characteristicUUID
     */
    fun setTargetCharacteristicUUID(deviceAddress: String, characteristicUUID: String) {
        HCBle.setTargetCharacteristicUUID(deviceAddress, characteristicUUID)
    }

    /**
     * TODO: 캐릭터리스틱을 읽습니다.
     * setCharacteristicUUID로 설정된 캐릭터리스틱을 읽습니다.
     */
    fun readCharacteristic(deviceAddress: String) {
        HCBle.readCharacteristic(deviceAddress)
    }

    /**
     * TODO: 캐릭터리스틱을 쓰기합니다.
     * setCharacteristicUUID로 설정된 캐릭터리스틱에 데이터를 쓰기합니다.
     * @param data
     */
    fun writeCharacteristic(deviceAddress: String, data: ByteArray) {
        HCBle.writeCharacteristic(deviceAddress, data)
    }

    /**
     * TODO: 캐릭터리스틱 알림을 설정합니다.
     * setCharacteristicUUID로 설정된 캐릭터리스틱에 알림을 설정합니다.
     * @param isEnable
     */
    fun setCharacteristicNotification(
        deviceAddress: String,
        isEnable: Boolean,
        isIndicate: Boolean = false
    ) {
        HCBle.setCharacteristicNotification(deviceAddress, isEnable, isIndicate)
    }

    fun getBondedDevices(): List<BluetoothDevice> {
        return HCBle.getBondedDevices()
    }

    fun deconnect(address: String) {
        HCBle.disconnect(address)
    }

    fun disconnectAll() {
        HCBle.disconnectAll()
    }
}
