package kr.co.hconnect.polihealth_sdk_android_v2

import android.bluetooth.BluetoothDevice
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
import kr.co.hconnect.polihealth_sdk_android.HRSpO2Parser
import kr.co.hconnect.polihealth_sdk_android.ProtocolType
import kr.co.hconnect.polihealth_sdk_android.api.dto.response.PoliResponse
import kr.co.hconnect.polihealth_sdk_android.api.dto.response.SleepEndResponse
import kr.co.hconnect.polihealth_sdk_android.api.sleep.SleepProtocol06API
import kr.co.hconnect.polihealth_sdk_android.api.sleep.SleepProtocol07API
import kr.co.hconnect.polihealth_sdk_android.api.sleep.SleepProtocol08API
import kr.co.hconnect.polihealth_sdk_android.service.sleep.SleepApiService
import kr.co.hconnect.polihealth_sdk_android_v2.api.daily.DailyProtocol02API
import kr.co.hconnect.polihealth_sdk_android_v2.api.daily.model.HRSpO2
import kr.co.hconnect.polihealth_sdk_android_v2.api.dto.response.SleepResponse
import kr.co.hconnect.polihealth_sdk_android_v2.service.daily.DailyServiceToApp
import kr.co.hconnect.polihealth_sdk_android_v2.utils.toHexString

object PoliBLE {
    private const val TAG = "PoliBLE.kt"
    fun init(context: Context) {
        HCBle.init(context)
    }

    fun startScan(scanDevice: (ScanResult) -> Unit) {
        HCBle.scanLeDevice { device ->
            scanDevice.invoke(device)
        }
    }

    fun stopScan() {
        HCBle.scanStop()
    }

    private var expectedByte: Byte = 0x00
    private var protocol2Count = 0

    @RequiresApi(Build.VERSION_CODES.Q)
    fun connectDevice(
        context: Context? = null,
        device: BluetoothDevice,
        onConnState: (state: Int) -> Unit,
        onGattServiceState: (gatt: Int) -> Unit,
        onBondState: (bondState: Int) -> Unit,
        onSubscriptionState: (state: Boolean) -> Unit,
        onReceive: (type: ProtocolType, response: PoliResponse?) -> Unit
    ) {
        HCBle.connectToDevice(
            device = device,
            onConnState = { state ->
                onConnState.invoke(state)
            },
            onGattServiceState = { gatt ->
                onGattServiceState.invoke(gatt)
            },
            onBondState = { bondState ->
                onBondState.invoke(bondState)
            },
            onSubscriptionState = { state ->
                onSubscriptionState.invoke(state)
            },
            onReceive = { characteristic ->
                val receivedArray = characteristic.value ?: ByteArray(0)
                receivedArray.let { byteArray ->
                    val protocolType = byteArray[0]
                    val dataOrder = byteArray[1]

                    when (protocolType) {
                        0x01.toByte() -> {
                            CoroutineScope(Dispatchers.IO).launch {
                                DailyServiceToApp.sendProtocol01ToApp(byteArray, context, onReceive)
                            }
                        }

                        0x02.toByte() -> {
                            DailyProtocol02API.apply {
                                CoroutineScope(Dispatchers.IO).launch {
                                    // 데이터 순서가 0x00 (처음) 이면 PROTOCOL_2_START 이벤트 발생
                                    // 이전 데이터 순서가 0xFE면 맨 처음이 아님
                                    Log.d(TAG, "DataOrder_: ${dataOrder.toHexString()}")
                                    if (prevByte != 0xFE.toByte() && dataOrder == 0x00.toByte()) {
                                        onReceive.invoke(ProtocolType.PROTOCOL_2_START, null)
                                    }
                                    prevByte = dataOrder
                                    addByte(removeFrontTwoBytes(byteArray, 2))

                                    // 데이터 순서가 0xFF (마지막) 이면 PROTOCOL_2 전송 이벤트 발생
                                    if (dataOrder == 0xFF.toByte()) {
                                        DailyServiceToApp.sendProtocol2ToApp(context, onReceive)
                                    }
                                }
                            }
                        }

                        0x03.toByte() -> {
                            CoroutineScope(Dispatchers.IO).launch {
                                DailyServiceToApp.sendProtocol03ToApp(byteArray, onReceive)
                            }
                        }

                        0x04.toByte() -> {
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val response: SleepResponse =
                                        SleepApiService().sendStartSleep()
                                    onReceive.invoke(
                                        ProtocolType.PROTOCOL_4_SLEEP_START,
                                        response
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }

                        0x05.toByte() -> {
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val response: SleepEndResponse =
                                        SleepApiService().sendEndSleep()
                                    onReceive.invoke(
                                        ProtocolType.PROTOCOL_5_SLEEP_END,
                                        response
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }

                        0x06.toByte() -> {
                            SleepProtocol06API.addByte(removeFrontTwoBytes(byteArray, 2))

                            if (byteArray[1] == 0xFF.toByte()) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        val response = SleepApiService().sendProtocol06(context)
                                        response?.let {
                                            onReceive.invoke(
                                                ProtocolType.PROTOCOL_6,
                                                response
                                            )
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }

                        0x07.toByte() -> {
                            SleepProtocol07API.addByte(removeFrontTwoBytes(byteArray, 2))

                            if (byteArray[1] == 0xFF.toByte()) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        val response = SleepApiService().sendProtocol07(context)
                                        response?.let {
                                            onReceive.invoke(
                                                ProtocolType.PROTOCOL_7,
                                                response
                                            )
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }

                        0x08.toByte() -> {
                            SleepProtocol08API.addByte(removeFrontTwoBytes(byteArray, 2))

                            if (byteArray[1] == 0xFF.toByte()) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        val response: SleepResponse? =
                                            SleepApiService().sendProtocol08(context)
                                        response?.let {
                                            onReceive.invoke(
                                                ProtocolType.PROTOCOL_8,
                                                response
                                            )
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }

                        0x09.toByte() -> {
                            val hrSpO2: HRSpO2 =
                                HRSpO2Parser.asciiToHRSpO2(removeFrontTwoBytes(byteArray, 1))

                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val response = SleepApiService().sendProtocol09(hrSpO2)
                                    response.let {
                                        onReceive.invoke(
                                            ProtocolType.PROTOCOL_9_HR_SpO2,
                                            response
                                        )
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }

                        else -> {
                            Log.e(
                                TAG, "Unknown Protocol: ${
                                    byteArray.joinToString(separator = " ") { byte ->
                                        "%02x".format(
                                            byte
                                        )
                                    }
                                }"
                            )
                        }
                    }
                    val hexString =
                        byteArray.joinToString(separator = " ") { byte -> "%02x".format(byte) }
//                    Log.d("GATTService", "ByteSize: ${byteArray.size}")
                }

            }
        )
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

    fun disconnectDevice() {
        HCBle.disconnect()
    }

    /**
     * TODO: GATT Service 리스트를 반환합니다.
     * 블루투스가 연결되어 onServicesDiscovered 콜백이 호출 돼야 사용가능합니다.
     * @return
     */
    fun getGattServiceList(): List<BluetoothGattService> {
        return HCBle.getGattServiceList()
    }

    /**
     * TODO: 서비스 UUID를 설정합니다.
     * 사용 하고자 하는 서비스 UUID를 설정합니다.
     * @param uuid
     */
    fun setServiceUUID(uuid: String) {
        HCBle.setServiceUUID(uuid)
    }

    /**
     * TODO: 캐릭터리스틱 UUID를 설정합니다.
     * 사용 하고자 하는 캐릭터리스틱 UUID를 설정합니다.
     * @param characteristicUUID
     */
    fun setCharacteristicUUID(characteristicUUID: String) {
        HCBle.setCharacteristicUUID(characteristicUUID)
    }

    /**
     * TODO: 캐릭터리스틱을 읽습니다.
     * setCharacteristicUUID로 설정된 캐릭터리스틱을 읽습니다.
     */
    fun readCharacteristic() {
        HCBle.readCharacteristic()
    }

    /**
     * TODO: 캐릭터리스틱을 쓰기합니다.
     * setCharacteristicUUID로 설정된 캐릭터리스틱에 데이터를 쓰기합니다.
     * @param data
     */
    fun writeCharacteristic(data: ByteArray) {
        HCBle.writeCharacteristic(data)
    }

    /**
     * TODO: 캐릭터리스틱 알림을 설정합니다.
     * setCharacteristicUUID로 설정된 캐릭터리스틱에 알림을 설정합니다.
     * @param isEnable
     */
    fun setCharacteristicNotification(isEnable: Boolean) {
        HCBle.setCharacteristicNotification(isEnable)
    }

    fun getBondedDevices(): List<BluetoothDevice> {
        return HCBle.getBondedDevices()
    }
}