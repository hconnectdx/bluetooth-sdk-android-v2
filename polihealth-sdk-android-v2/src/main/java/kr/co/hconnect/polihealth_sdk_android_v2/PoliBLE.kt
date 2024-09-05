package kr.co.hconnect.polihealth_sdk_android

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattService
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kr.co.hconnect.bluetooth_sdk_android.HCBle
import kr.co.hconnect.polihealth_sdk_android.api.daily.DailyProtocol01API
import kr.co.hconnect.polihealth_sdk_android.api.dto.response.PoliResponse
import kr.co.hconnect.polihealth_sdk_android.api.dto.response.SleepEndResponse
import kr.co.hconnect.polihealth_sdk_android.api.sleep.SleepProtocol06API
import kr.co.hconnect.polihealth_sdk_android.api.sleep.SleepProtocol07API
import kr.co.hconnect.polihealth_sdk_android.api.sleep.SleepProtocol08API
import kr.co.hconnect.polihealth_sdk_android.service.sleep.SleepApiService
import kr.co.hconnect.polihealth_sdk_android_app.api.sleep.DailyProtocol02API
import kr.co.hconnect.polihealth_sdk_android_app.service.sleep.DailyApiService
import kr.co.hconnect.polihealth_sdk_android_v2.api.daily.model.HRSpO2
import kr.co.hconnect.polihealth_sdk_android_v2.api.dto.response.Daily1Response
import kr.co.hconnect.polihealth_sdk_android_v2.api.dto.response.SleepResponse

object PoliBLE {
    private const val TAG = "PoliBLE"
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
        context: Context? = null, // bin파일 저장을 위한 임시 컨텍스트
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
                val byteArray = characteristic.value ?: ByteArray(0)
                byteArray.let {

                    when (it[0]) {
                        0x01.toByte() -> {
                            CoroutineScope(Dispatchers.IO).launch {
                                val parsedData = DailyProtocol01API.parseLTMData(it, context)

                                val response: Daily1Response =
                                    DailyApiService().sendProtocol01(parsedData)
                                onReceive.invoke(ProtocolType.PROTOCOL_1, response)
                            }
                        }

                        0x02.toByte() -> {
                            checkProtocol2Validate(it[1])
                            DailyProtocol02API.addByte(removeFrontTwoBytes(it, 2))

                            if (it[1] == 0xFF.toByte()) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    val response = DailyApiService().sendProtocol02(context)
                                    onReceive.invoke(ProtocolType.PROTOCOL_2, response)

                                    protocol2Count = 0
                                    expectedByte = 0x00
                                }
                            }
                        }

                        0x03.toByte() -> {
                            CoroutineScope(Dispatchers.IO).launch {
                                val hrSpO2: HRSpO2 =
                                    HRSpO2Parser.asciiToHRSpO2(removeFrontTwoBytes(it, 1))
                                val response = DailyApiService().sendProtocol03(hrSpO2)
                                onReceive.invoke(
                                    ProtocolType.PROTOCOL_3_HR_SpO2,
                                    response
                                )
                            }
                        }

                        0x04.toByte() -> {
                            CoroutineScope(Dispatchers.IO).launch {
                                val response: SleepResponse = SleepApiService().sendStartSleep()
                                onReceive.invoke(ProtocolType.PROTOCOL_4_SLEEP_START, response)
                            }
                        }

                        0x05.toByte() -> {
                            CoroutineScope(Dispatchers.IO).launch {
                                val response: SleepEndResponse = SleepApiService().sendEndSleep()
                                onReceive.invoke(ProtocolType.PROTOCOL_5_SLEEP_END, response)
                            }
                        }

                        0x06.toByte() -> {
                            SleepProtocol06API.addByte(removeFrontTwoBytes(it, 2))

                            if (it[1] == 0xFF.toByte()) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    val response = SleepApiService().sendProtocol06(context)
                                    response?.let {
                                        onReceive.invoke(
                                            ProtocolType.PROTOCOL_6,
                                            response
                                        )
                                    }
                                }
                            }
                        }

                        0x07.toByte() -> {
                            SleepProtocol07API.addByte(removeFrontTwoBytes(it, 2))

                            if (it[1] == 0xFF.toByte()) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    val response = SleepApiService().sendProtocol07(context)
                                    response?.let {
                                        onReceive.invoke(
                                            ProtocolType.PROTOCOL_7,
                                            response
                                        )
                                    }
                                }
                            }
                        }

                        0x08.toByte() -> {
                            SleepProtocol08API.addByte(removeFrontTwoBytes(it, 2))

                            if (it[1] == 0xFF.toByte()) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    val response: SleepResponse? =
                                        SleepApiService().sendProtocol08(context)
                                    response?.let {
                                        onReceive.invoke(
                                            ProtocolType.PROTOCOL_8,
                                            response
                                        )
                                    }
                                }
                            }
                        }

                        0x09.toByte() -> {
                            val hrSpO2: HRSpO2 =
                                HRSpO2Parser.asciiToHRSpO2(removeFrontTwoBytes(it, 1))

                            CoroutineScope(Dispatchers.IO).launch {
                                val response = SleepApiService().sendProtocol09(hrSpO2)
                                response.let {
                                    onReceive.invoke(
                                        ProtocolType.PROTOCOL_9_HR_SpO2,
                                        response
                                    )
                                }
                            }
                        }

                        else -> {
                            Log.e(TAG, "Unknown Protocol: ${
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
                    Log.d("GATTService", "onCharacteristicChanged: $hexString")
                }
            }
        )
    }

    private fun checkProtocol2Validate(it: Byte) {
        if (it != expectedByte) {
            Log.e(
                "DataLogger",
                "데이터 손실 감지: 예상 값 ${
                    String.format(
                        "0x%02X",
                        expectedByte
                    )
                } 실제 값 ${String.format("0x%02X", it)}"
            )
        }
        expectedByte =
            if (it == 0xFE.toByte()) 0x00 else (it + 1).toByte()
        Log.d("Protocol2 Count", "Count: ${++protocol2Count}")
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