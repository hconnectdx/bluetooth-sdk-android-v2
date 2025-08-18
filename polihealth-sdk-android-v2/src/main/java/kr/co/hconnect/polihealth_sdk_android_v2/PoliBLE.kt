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

/**
 * 폴리헬스 블루투스 통신을 담당하는 메인 클래스
 *
 * 주요 기능:
 * - 블루투스 스캔 및 연결 관리
 * - 프로토콜별 데이터 처리 (Protocol 01~09)
 * - GATT 서비스 및 특성 관리
 */
object PoliBLE {

    // =============================================================================
    // CONSTANTS
    // =============================================================================

    private const val TAG = "PoliBLE"

    // Protocol 02 관련 상수
    private const val PROTOCOL_LAST_PACKET = 0xFF.toByte()
    private const val PROTOCOL_02_MAX_ORDER = 0xFE.toByte()
    private const val PROTOCOL_02_RESET_ORDER = 0x00.toByte()

    // =============================================================================
    // STATE VARIABLES
    // =============================================================================

    // Protocol 02 순서 추적 변수들
    private var p2ExpectedOrder: Byte = PROTOCOL_02_RESET_ORDER
    private var prevByte: Byte = PROTOCOL_02_RESET_ORDER
    private var p2IsFirstPacket: Boolean = true

    //
    private lateinit var onReceive: (type: ProtocolType, response: PoliResponse?) -> Unit

    // 레거시 변수들 (사용하지 않음)
    @Deprecated("더 이상 사용하지 않음")
    private var expectedByte: Byte = 0x00

    @Deprecated("더 이상 사용하지 않음")
    private var protocol2Count = 0

    // =============================================================================
    // INITIALIZATION
    // =============================================================================

    /**
     * PoliBLE 초기화
     * 앱 시작 시 반드시 호출해야 함
     */
    fun init(context: Context) {
        Log.d(TAG, "PoliBLE 초기화")
        HCBle.init(context)
    }

    // =============================================================================
    // SCAN MANAGEMENT
    // =============================================================================

    /**
     * 블루투스 스캔 시작
     */
    fun startScan(onScanResult: (ScanResult) -> Unit) {
        Log.d(TAG, "블루투스 스캔 시작")
        HCBle.scanLeDevice(
            onScanResult = { device ->
                onScanResult.invoke(device)
            },
            onScanStop = {
                Log.d(TAG, "블루투스 스캔 종료")
            }
        )
    }

    /**
     * 블루투스 스캔 중지
     */
    fun stopScan() {
        Log.d(TAG, "블루투스 스캔 중지")
        HCBle.scanStop()
    }

    // =============================================================================
    // CONNECTION MANAGEMENT
    // =============================================================================

    /**
     * 블루투스 디바이스 연결
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun connectDevice(
        context: Context? = null,
        device: BluetoothDevice,
        onConnState: (state: Int) -> Unit,
        onGattServiceState: (gatt: Int, services: List<BluetoothGattService>) -> Unit,
        onBondState: (bondState: Int) -> Unit,
        onSubscriptionState: (state: Boolean) -> Unit,
        onReceive: (type: ProtocolType, response: PoliResponse?) -> Unit,
        onWriteCharacteristic: (state: Int, char: BluetoothGattCharacteristic) -> Unit,
        autoConnect: Boolean
    ) {
        Log.d(TAG, "디바이스 연결 시작: ${device.address}")
        this.onReceive = onReceive

        HCBle.connectToDevice(
            isAutoConnect = autoConnect,
            device = device,
            onConnState = { state ->
                handleConnectionStateChange(state)
                onConnState.invoke(state)
            },
            onGattServiceState = onGattServiceState,
            onBondState = onBondState,
            onSubscriptionState = onSubscriptionState,
            onReceive = { characteristic ->
                val receivedArray = characteristic.value ?: ByteArray(0)
                processReceivedData(receivedArray, context, onReceive)
            },
            onWriteCharacteristic = { state, char ->
                handleWriteCharacteristic(state, char, onWriteCharacteristic)
            }
        )
    }

    /**
     * 연결 상태 변경 처리
     */
    private fun handleConnectionStateChange(state: Int) {
        Log.d(TAG, "연결 상태 변경: $state")
        // 연결 시 Protocol 02 상태 초기화
        resetProtocol02State()
    }

    /**
     * 특성 쓰기 완료 처리
     */
    private fun handleWriteCharacteristic(
        state: Int,
        char: BluetoothGattCharacteristic?,
        onWriteCharacteristic: (state: Int, char: BluetoothGattCharacteristic) -> Unit
    ) {
        char?.let {
            Log.d(TAG, "특성 쓰기 완료: ${char.uuid}")
            // 재측정 시 상태 초기화
            resetProtocol02State()
            prevByte = 0x00.toByte()
            onWriteCharacteristic(state, char)
        }
    }

    /**
     * 디바이스 연결 해제
     */
    fun disconnectDevice(deviceAddress: String) {
        Log.d(TAG, "디바이스 연결 해제: $deviceAddress")
        HCBle.disconnect(deviceAddress)
    }

    /**
     * 모든 디바이스 연결 해제
     */
    fun disconnectAll() {
        Log.d(TAG, "모든 디바이스 연결 해제")
        HCBle.disconnectAll()
    }

    fun getBondedDevices(): List<BluetoothDevice> {
        return HCBle.getBondedDevices()
    }

    // =============================================================================
    // PROTOCOL DATA PROCESSING
    // =============================================================================

    /**
     * 수신된 데이터 처리
     */
    private fun processReceivedData(
        byteArray: ByteArray,
        context: Context?,
        onReceive: (type: ProtocolType, response: PoliResponse?) -> Unit
    ) {
        if (byteArray.isEmpty()) {
            Log.w(TAG, "빈 데이터 수신")
            return
        }

        val protocolType = byteArray[0]
        val dataOrder = byteArray.getOrNull(1) ?: PROTOCOL_02_RESET_ORDER

        Log.d(TAG, "프로토콜 처리: 0x${String.format("%02X", protocolType)}")

        when (protocolType) {
            0x01.toByte() -> handleProtocol01(byteArray, context, onReceive)
            0x02.toByte() -> handleProtocol02(byteArray, dataOrder, context, onReceive)
            0x03.toByte() -> handleProtocol03(byteArray, onReceive)
            0x04.toByte() -> {
                if (context != null)
                    handleProtocol04(context, onReceive)
                else
                    Log.e(TAG, "SharedPreference를 사용하기 위한 Context가 없습니다.")
            }

            0x05.toByte() -> {
                if (context != null)
                    handleProtocol05(context, onReceive)
                else
                    Log.e(TAG, "SharedPreference를 사용하기 위한 Context가 없습니다.")
            }

            0x06.toByte() -> handleProtocol06(byteArray, context, onReceive)
            0x07.toByte() -> handleProtocol07(byteArray, context, onReceive)
            0x08.toByte() -> handleProtocol08(byteArray, context, onReceive)
            0x09.toByte() -> handleProtocol09(byteArray, onReceive)
            else -> logUnknownProtocol(byteArray)
        }
    }

    // =============================================================================
    // PROTOCOL HANDLERS
    // =============================================================================

    /**
     * Protocol 01 처리 (Daily 시작)
     */
    private fun handleProtocol01(
        byteArray: ByteArray,
        context: Context?,
        onReceive: (type: ProtocolType, response: PoliResponse?) -> Unit
    ) {
        Log.d(TAG, "Protocol 01 처리")
        CoroutineScope(Dispatchers.IO).launch {
            DailyServiceToApp.sendProtocol01ToApp(byteArray, context, onReceive)
        }
    }

    /**
     * Protocol 02 처리 (Daily 데이터)
     * 순서 검증 및 데이터 수집
     */
    private fun handleProtocol02(
        byteArray: ByteArray,
        dataOrder: Byte,
        context: Context?,
        onReceive: (type: ProtocolType, response: PoliResponse?) -> Unit
    ) {
        Log.d(TAG, "Protocol 02 처리 - 데이터 순서: ${dataOrder.toHexString()}")

        DailyProtocol02API.apply {
            CoroutineScope(Dispatchers.IO).launch {
                // 시작 조건 검증
                checkStartCondition(onReceive)
                // 패킷 처리
                handleDataPacket(dataOrder, onReceive)

                // 데이터 추가 및 완료 처리
                prevByte = dataOrder

                val isLastPacket = (dataOrder == PROTOCOL_LAST_PACKET)
                addByteNew(removeFrontTwoBytes(byteArray, 2), isLast = isLastPacket)

                if (isLastPacket) {
                    Log.d(TAG, "Protocol 02 완료 - 앱으로 전송")
                    DailyServiceToApp.sendProtocol2ToApp(context, onReceive)
                    handleLastPacket()
                }
            }
        }
    }

    /**
     * 마지막 패킷 처리 (0xFF)
     */
    private fun handleLastPacket() {
        Log.d(TAG, "프로세스 종료 패킷 수신 (0xFF)")
        resetProtocol02State()
    }

    /**
     * 일반 데이터 패킷 처리 (0x00~0xFE)
     */
    private fun handleDataPacket(
        dataOrder: Byte,
        onReceive: (type: ProtocolType, response: PoliResponse?) -> Unit
    ) {
        if (p2IsFirstPacket) {
            handleFirstPacket(dataOrder)
        } else {
            validatePacketOrder(dataOrder, onReceive)
        }
    }

    /**
     * 첫 번째 패킷 처리
     */
    private fun handleFirstPacket(dataOrder: Byte) {
        p2ExpectedOrder = calculateNextOrder(dataOrder)
        p2IsFirstPacket = false
        Log.d(TAG, "첫 패킷 감지: ${dataOrder.toHexString()}, 다음 예상: ${p2ExpectedOrder.toHexString()}")
    }

    /**
     * 패킷 순서 검증
     */
    private fun validatePacketOrder(
        dataOrder: Byte,
        onReceive: (type: ProtocolType, response: PoliResponse?) -> Unit
    ) {
        if ((dataOrder != p2ExpectedOrder) && dataOrder != PROTOCOL_LAST_PACKET) {
            Log.w(
                TAG,
                "패킷 순서 오류 - 예상: ${p2ExpectedOrder.toHexString()}, 실제: ${dataOrder.toHexString()}"
            )
            resetProtocol02State()
            onReceive.invoke(ProtocolType.PROTOCOL_2_ERROR_LACK_OF_DATA, null)
            return
        }

        p2ExpectedOrder = calculateNextOrder(dataOrder)
        Log.v(TAG, "패킷 순서 정상 - 다음 예상: ${p2ExpectedOrder.toHexString()}")
    }

    /**
     * 시작 조건 검증
     */
    private fun checkStartCondition(
        onReceive: (type: ProtocolType, response: PoliResponse?) -> Unit
    ) {
        Log.v(DailyProtocol02API.TAG, "prevByte: ${DailyProtocol02API.prevByte.toHexString()}")

        // 새 시퀀스 시작 조건:
        // 모인 바이트 수가 0일 때
        val isNewSequenceStart = DailyProtocol02API.byteArray.isEmpty()

        if (isNewSequenceStart) {
            Log.d(TAG, "새 시퀀스 시작 감지")
            resetProtocol02State()
            onReceive.invoke(ProtocolType.PROTOCOL_2_START, null)
        }
    }

    /**
     * 다음 순서 계산 (0xFE 다음은 0x00)
     */
    private fun calculateNextOrder(currentOrder: Byte): Byte {
        return if (currentOrder == PROTOCOL_02_MAX_ORDER) {
            PROTOCOL_02_RESET_ORDER
        } else {
            (currentOrder + 1).toByte()
        }
    }

    /**
     * Protocol 02 상태 초기화
     */
    private fun resetProtocol02State() {
        DailyProtocol02API.byteArray = byteArrayOf()
        p2ExpectedOrder = PROTOCOL_02_RESET_ORDER
        prevByte = PROTOCOL_02_RESET_ORDER
        p2IsFirstPacket = true

        Log.v(TAG, "Protocol 02 상태 초기화")
    }

    /**
     * Protocol 03 처리
     */
    private fun handleProtocol03(
        byteArray: ByteArray,
        onReceive: (type: ProtocolType, response: PoliResponse?) -> Unit
    ) {
        Log.d(TAG, "Protocol 03 처리")
        CoroutineScope(Dispatchers.IO).launch {
            DailyServiceToApp.sendProtocol03ToApp(byteArray, onReceive)
        }
    }

    /**
     * Protocol 04 처리 (수면 시작)
     */
    private fun handleProtocol04(
        context: Context,
        onReceive: (type: ProtocolType, response: PoliResponse?) -> Unit
    ) {
        Log.d(TAG, "Protocol 04 처리 - 수면 시작")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = SleepApiService().sendStartSleep(context)
                val type = if (response.retCd == "0") {
                    ProtocolType.PROTOCOL_4_SLEEP_START
                } else {
                    ProtocolType.PROTOCOL_4_SLEEP_START_ERROR
                }
                onReceive.invoke(type, response)
            } catch (e: Exception) {
                Log.e(TAG, "Protocol 04 처리 중 오류", e)
                onReceive.invoke(ProtocolType.PROTOCOL_4_SLEEP_START_ERROR, null)
            }
        }
    }

    /**
     * Protocol 05 처리 (수면 종료)
     */
    private fun handleProtocol05(
        context: Context,
        onReceive: (type: ProtocolType, response: PoliResponse?) -> Unit
    ) {
        Log.d(TAG, "Protocol 05 처리 - 수면 종료")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = SleepApiService().sendEndSleep(context)
                val type = if (response.retCd == "0") {
                    ProtocolType.PROTOCOL_5_SLEEP_END
                } else {
                    ProtocolType.PROTOCOL_5_SLEEP_END_ERROR
                }
                onReceive.invoke(type, response)
            } catch (e: Exception) {
                Log.e(TAG, "Protocol 05 처리 중 오류", e)
                onReceive.invoke(ProtocolType.PROTOCOL_5_SLEEP_END_ERROR, null)
            }
        }
    }

    /**
     * Protocol 06 처리 (수면 데이터)
     */
    private fun handleProtocol06(
        byteArray: ByteArray,
        context: Context?,
        onReceive: (type: ProtocolType, response: PoliResponse?) -> Unit
    ) {
        Log.d(TAG, "Protocol 06 처리")
        SleepProtocol06API.addByte(removeFrontTwoBytes(byteArray, 2))

        if (byteArray[1] == PROTOCOL_LAST_PACKET) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = SleepApiService().sendProtocol06(context)
                    onReceive.invoke(ProtocolType.PROTOCOL_6, response)
                } catch (e: Exception) {
                    Log.e(TAG, "Protocol 06 처리 중 오류", e)
                    onReceive.invoke(ProtocolType.PROTOCOL_6_ERROR, null)
                }
            }
        } else {
            onReceive.invoke(ProtocolType.PROTOCOL_6, null)
        }
    }

    /**
     * Protocol 07 처리
     */
    private fun handleProtocol07(
        byteArray: ByteArray,
        context: Context?,
        onReceive: (type: ProtocolType, response: PoliResponse?) -> Unit
    ) {
        Log.d(TAG, "Protocol 07 처리")
        SleepProtocol07API.addByte(removeFrontTwoBytes(byteArray, 2))

        if (byteArray[1] == PROTOCOL_LAST_PACKET) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = SleepApiService().sendProtocol07(context)
                    onReceive.invoke(ProtocolType.PROTOCOL_7, response)
                } catch (e: Exception) {
                    Log.e(TAG, "Protocol 07 처리 중 오류", e)
                    onReceive.invoke(ProtocolType.PROTOCOL_7_ERROR, null)
                }
            }
        } else {
            onReceive.invoke(ProtocolType.PROTOCOL_7, null)
        }
    }

    /**
     * Protocol 08 처리
     */
    private fun handleProtocol08(
        byteArray: ByteArray,
        context: Context?,
        onReceive: (type: ProtocolType, response: PoliResponse?) -> Unit
    ) {
        Log.d(TAG, "Protocol 08 처리")
        SleepProtocol08API.addByte(removeFrontTwoBytes(byteArray, 2))

        if (byteArray[1] == PROTOCOL_LAST_PACKET) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = SleepApiService().sendProtocol08(context)
                    onReceive.invoke(ProtocolType.PROTOCOL_8, response)
                } catch (e: Exception) {
                    Log.e(TAG, "Protocol 08 처리 중 오류", e)
                    onReceive.invoke(ProtocolType.PROTOCOL_8_ERROR, null)
                }
            }
        } else {
            onReceive.invoke(ProtocolType.PROTOCOL_8, null)
        }
    }

    /**
     * Protocol 09 처리 (HR, SpO2)
     */
    private fun handleProtocol09(
        byteArray: ByteArray,
        onReceive: (type: ProtocolType, response: PoliResponse?) -> Unit
    ) {
        Log.d(TAG, "Protocol 09 처리 - HR, SpO2")
        val hrSpO2 = HRSpO2Parser.asciiToHRSpO2(removeFrontTwoBytes(byteArray, 1))

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = SleepApiService().sendProtocol09(hrSpO2)
                onReceive.invoke(ProtocolType.PROTOCOL_9_HR_SpO2, response)
            } catch (e: Exception) {
                Log.e(TAG, "Protocol 09 처리 중 오류", e)
                onReceive.invoke(ProtocolType.PROTOCOL_9_ERROR, null)
            }
        }
    }

    private fun handleStopBandProtocol(deviceAddress: String) {
        writeCharacteristic(deviceAddress, "POLICE_STOP".toByteArray())
    }

    /**
     * 알 수 없는 프로토콜 로깅
     */
    private fun logUnknownProtocol(byteArray: ByteArray) {
        Log.e(TAG, "알 수 없는 프로토콜: ${byteArray.joinToString(" ") { "0x%02X".format(it) }}")
    }

    // =============================================================================
    // UTILITY METHODS
    // =============================================================================

    /**
     * 바이트 배열 앞부분 제거
     */
    fun removeFrontTwoBytes(byteArray: ByteArray, size: Int): ByteArray {
        return if (byteArray.size > size) {
            byteArray.copyOfRange(size, byteArray.size)
        } else {
            ByteArray(0)
        }
    }

    /**
     * 레거시 함수 (사용하지 않음)
     */
    @Deprecated("새로운 순서 검증 로직으로 대체됨")
    private fun checkProtocol2Validate(it: Byte): Boolean {
        if (it != PROTOCOL_LAST_PACKET && it != expectedByte) {
            Log.e(TAG, "데이터 손실 감지: 예상 값 0x%02X 실제 값 0x%02X".format(expectedByte, it))
            return false
        }
        expectedByte =
            if (it == PROTOCOL_02_MAX_ORDER) PROTOCOL_02_RESET_ORDER else (it + 1).toByte()
        Log.d("Protocol2 Count", "Count: ${++protocol2Count}")
        return true
    }

    // =============================================================================
    // GATT SERVICE MANAGEMENT
    // =============================================================================

    /**
     * GATT 서비스 리스트 조회
     * 블루투스 연결 후 서비스 발견이 완료된 후 사용 가능
     */
    fun getGattServiceList(deviceAddress: String): List<BluetoothGattService>? {
        return HCBle.getGattServiceList(deviceAddress)
    }

    /**
     * 대상 서비스 UUID 설정
     */
    fun setTargetServiceUUID(deviceAddress: String, uuid: String) {
        Log.d(TAG, "서비스 UUID 설정: $uuid")
        HCBle.setTargetServiceUUID(deviceAddress, uuid)
    }

    /**
     * Read 특성 UUID 설정 (알림 수신용)
     * 데이터를 수신받을 특성을 설정합니다.
     */
    fun setTargetReadCharacteristicUUID(deviceAddress: String, characteristicUUID: String) {
        Log.d(TAG, "Read 특성 UUID 설정: $characteristicUUID")
        HCBle.setTargetReadCharacteristicUUID(deviceAddress, characteristicUUID)
    }

    /**
     * Write 특성 UUID 설정 (데이터 전송용)
     * 디바이스로 명령을 전송할 특성을 설정합니다.
     */
    fun setTargetWriteCharacteristicUUID(deviceAddress: String, characteristicUUID: String) {
        Log.d(TAG, "Write 특성 UUID 설정: $characteristicUUID")
        HCBle.setTargetWriteCharacteristicUUID(deviceAddress, characteristicUUID)
    }

    /**
     * 레거시 함수 - 하위 호환성을 위해 유지
     * @deprecated setTargetReadCharacteristicUUID 또는 setTargetWriteCharacteristicUUID 사용 권장
     */
    @Deprecated(
        message = "Read/Write를 명확히 구분해서 사용하세요",
        replaceWith = ReplaceWith("setTargetReadCharacteristicUUID(deviceAddress, characteristicUUID)")
    )
    fun setTargetCharacteristicUUID(deviceAddress: String, characteristicUUID: String) {
        Log.w(TAG, "⚠️ Deprecated: setTargetCharacteristicUUID 사용됨. Read/Write 구분 사용 권장")
        // 기본적으로 Read Characteristic으로 설정 (하위 호환성)
        setTargetReadCharacteristicUUID(deviceAddress, characteristicUUID)
    }

    // =============================================================================
    // CHARACTERISTIC OPERATIONS
    // =============================================================================

    /**
     * 특성 읽기 (Read Characteristic 사용)
     */
    fun readCharacteristic(deviceAddress: String) {
        Log.d(TAG, "특성 읽기: $deviceAddress")
        HCBle.readCharacteristic(deviceAddress)
    }

    /**
     * 특성 쓰기 (Write Characteristic 사용)
     */
    fun writeCharacteristic(deviceAddress: String, data: ByteArray) {
        Log.d(TAG, "특성 쓰기: $deviceAddress, 데이터: ${data.joinToString(" ") { "0x%02X".format(it) }}")
        HCBle.writeCharacteristic(deviceAddress, data)
    }

    /**
     * 특성 알림 설정 (Read Characteristic 사용)
     * 데이터 수신을 위한 알림을 활성화/비활성화합니다.
     */
    fun setCharacteristicNotification(
        deviceAddress: String,
        isEnable: Boolean,
        isIndicate: Boolean = false
    ) {
        Log.d(TAG, "특성 알림 설정: $deviceAddress, 활성화: $isEnable, 표시: $isIndicate")
        HCBle.setCharacteristicNotification(deviceAddress, isEnable, isIndicate)
    }

    /**
     * Read Characteristic 조회
     */
    fun getReadCharacteristic(deviceAddress: String): BluetoothGattCharacteristic? {
        return HCBle.getSelReadCharacteristic(deviceAddress)
    }

    /**
     * Write Characteristic 조회
     */
    fun getWriteCharacteristic(deviceAddress: String): BluetoothGattCharacteristic? {
        return HCBle.getSelWriteCharacteristic(deviceAddress)
    }


}