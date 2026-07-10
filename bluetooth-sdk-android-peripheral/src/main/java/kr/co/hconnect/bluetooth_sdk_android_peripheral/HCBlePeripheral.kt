package kr.co.hconnect.bluetooth_sdk_android_peripheral

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.CRC32

/**
 * BLE Peripheral (GATT Server) SDK.
 *
 * 디바이스를 BLE Peripheral로 동작시켜 Central(스마트폰 등)과 통신한다.
 *
 * 데이터 흐름:
 * - Peripheral → Central : TX Characteristic (NOTIFY) — [sendData]
 * - Central → Peripheral : RX Characteristic (WRITE) — [PeripheralEventListener.onDataReceived]
 *
 * 대용량 데이터 전송 시 4바이트 big-endian 길이 헤더를 앞에 붙이고
 * MTU 크기 단위로 청크 분할 후 순서대로 NOTIFY 전송한다.
 *
 * 사용법:
 * ```
 * HCBlePeripheral.init(context)
 * HCBlePeripheral.addEventListener(listener)
 * HCBlePeripheral.start()
 * HCBlePeripheral.sendData(byteArrayOf(...))
 * HCBlePeripheral.stop()
 * ```
 */
@SuppressLint("MissingPermission")
object HCBlePeripheral {

    private const val TAG = "HCBlePeripheral"

    private lateinit var appContext: Context
    private var config = PeripheralConfig()

    private var bluetoothManager: BluetoothManager? = null
    private val bluetoothAdapter get() = bluetoothManager?.adapter

    @Volatile
    private var connectedDevice: BluetoothDevice? = null

    @Volatile
    private var currentMtu: Int = 23
    private val maxPayload: Int get() = currentMtu - 3

    private val notifySemaphore = Semaphore(0)

    /** notify 발사 직렬화용 락. 오래 잡는 락이므로 객체 모니터와 분리한다. */
    private val txLock = Any()

    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null

    // ── 엔드투엔드 청크 프로브 상태 ──
    // notify 반환값/onNotificationSent는 전파에 실린 크기를 보증하지 않으므로(스택 절단 미검출)
    // 실효 청크 크기는 반드시 수신자(Central)의 PROBE_ACK로만 판정한다.
    private const val PROBE_PREFIX = "PROBE:"
    private const val PROBE_ACK_PREFIX = "PROBE_ACK:"
    private const val PROBE_PADDING: Byte = 0xA5.toByte()

    private class PendingProbe(val seq: Int, val size: Int) {
        val latch = CountDownLatch(1)
        @Volatile
        var ackBytes: Int = -1
    }

    private val probeSeq = AtomicInteger(0)

    @Volatile
    private var pendingProbe: PendingProbe? = null

    /** 프로브로 채택된 청크 크기. 0이면 미채택(폴백 MTU-3 사용). 연결 해제 시 리셋. */
    @Volatile
    private var probedChunkSize: Int = 0

    /** 실제 전송에 쓰는 청크 크기: 정식 MTU 협상값과 프로브 채택값 중 큰 쪽 */
    private val effectiveChunkSize: Int get() = maxOf(maxPayload, probedChunkSize)

    // ── 논블로킹 송신 큐 ──
    private sealed interface TxWork {
        class Data(val payload: ByteArray) : TxWork
        object Probe : TxWork
        object Quit : TxWork
    }

    @Volatile
    private var txWorkQueue: LinkedBlockingQueue<TxWork>? = null

    @Volatile
    private var senderThread: Thread? = null

    private val pendingTxCount = AtomicInteger(0)

    private val _connectionState = MutableStateFlow(PeripheralConnectionState.IDLE)

    /** 연결 상태를 관찰할 수 있는 StateFlow */
    val connectionStateFlow: StateFlow<PeripheralConnectionState> = _connectionState.asStateFlow()

    /** 현재 연결 여부 */
    val isConnected: Boolean
        get() = _connectionState.value == PeripheralConnectionState.CONNECTED

    /** 현재 광고 중 여부 */
    val isAdvertising: Boolean
        get() = _connectionState.value == PeripheralConnectionState.ADVERTISING

    /** 현재 연결된 Central 디바이스 */
    val currentDevice: BluetoothDevice?
        get() = connectedDevice

    /** 현재 협상된 MTU 값 */
    val negotiatedMtu: Int
        get() = currentMtu

    /** 현재 실효 청크 크기 (프로브 채택값 vs MTU-3 중 큰 쪽) */
    val currentChunkSize: Int
        get() = effectiveChunkSize

    private val listeners = CopyOnWriteArrayList<PeripheralEventListener>()

    private var bluetoothStateReceiverRegistered = false

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                Log.d(TAG, "블루투스 어댑터 꺼짐 감지 — 리소스 정리")
                val prevDevice = connectedDevice
                gattServer?.close()
                gattServer = null
                txCharacteristic = null
                connectedDevice = null
                currentMtu = 23
                resetTxPipeline()
                advertiser = null
                updateState(PeripheralConnectionState.IDLE)
                prevDevice?.let { device ->
                    listeners.forEach { it.onDeviceDisconnected(device) }
                }
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // GATT Server Callback
    // ────────────────────────────────────────────────────────────────────────

    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Central 연결됨: ${device.address}")
                    Log.d(TAG, "연결시 MTU=$currentMtu, maxPayload=$maxPayload")
                    probedChunkSize = 0
                    connectedDevice = device
                    stopAdvertising()
                    updateState(PeripheralConnectionState.CONNECTED)
                    listeners.forEach { it.onDeviceConnected(device) }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Central 연결 끊김: ${device.address}")
                    if (connectedDevice?.address == device.address) {
                        connectedDevice = null
                        currentMtu = 23
                        resetTxPipeline()
                        updateState(PeripheralConnectionState.DISCONNECTED)
                        listeners.forEach { it.onDeviceDisconnected(device) }

                        if (config.autoRestartAdvertise) {
                            startAdvertising()
                        }
                    }
                }
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            val prev = currentMtu
            Log.d(TAG, "MTU 변경: $prev -> $mtu (페이로드 최대 ${mtu - 3}바이트) from ${device.address}")
            currentMtu = mtu
            listeners.forEach { it.onMtuChanged(mtu) }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            notifySemaphore.release()
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "onNotificationSent 실패: status=$status")
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            // ATT는 순차 프로토콜 — 응답이 필요한 요청에 무응답 시 파이프 전체가 정지되므로
            // 대상 UUID가 아니어도 반드시 오류코드로 응답한다.
            if (characteristic.uuid != config.rxCharUUID) {
                Log.w(TAG, "지원하지 않는 characteristic write: ${characteristic.uuid} from ${device.address}")
                if (responseNeeded) {
                    respond(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null)
                }
                return
            }

            if (responseNeeded) {
                respond(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }

            // PROBE_ACK는 SDK 내부 프로토콜 — 앱 리스너로 전달하지 않고 가로챈다.
            if (isProbeAck(value)) {
                handleProbeAck(value)
                return
            }

            Log.d(TAG, "RX 수신: ${value.size}바이트 from ${device.address}")
            listeners.forEach { it.onDataReceived(device, value) }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == config.txCharUUID) {
                respond(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.value)
            } else {
                Log.w(TAG, "지원하지 않는 characteristic read: ${characteristic.uuid} from ${device.address}")
                respond(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (descriptor.uuid != PeripheralConfig.CCCD_UUID) {
                Log.w(TAG, "지원하지 않는 descriptor write: ${descriptor.uuid} from ${device.address}")
                if (responseNeeded) {
                    respond(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null)
                }
                return
            }

            @Suppress("DEPRECATION")
            descriptor.value = value

            if (responseNeeded) {
                respond(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }

            val enabled = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            Log.d(TAG, "Notify ${if (enabled) "구독" else "해제"}: ${device.address} descriptor=${descriptor.uuid} value=${bytesSummary(value)}")

            // 구독 완료 직후, 프레임 스트림 송신 전에 실효 청크 크기 프로브를 수행한다 (재연결 시마다).
            if (enabled && config.probeChunkLadder.isNotEmpty()) {
                ensureSenderThread().offer(TxWork.Probe)
            }

            listeners.forEach { it.onNotifySubscriptionChanged(device, enabled) }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            if (descriptor.uuid == PeripheralConfig.CCCD_UUID) {
                respond(
                    device, requestId, BluetoothGatt.GATT_SUCCESS,
                    0, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                )
            } else {
                Log.w(TAG, "지원하지 않는 descriptor read: ${descriptor.uuid} from ${device.address}")
                respond(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null)
            }
        }

        override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
            // prepared write는 지원하지 않지만 Execute Write 요청도 무응답 시 ATT가 정지된다.
            Log.w(TAG, "onExecuteWrite(execute=$execute) — prepared write 미지원, 응답만 반환 from ${device.address}")
            respond(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }
    }

    /**
     * sendResponse 일원화. ATT는 순차 프로토콜이라 응답 누락 1건이 파이프 전체를 정지시키므로,
     * 서버 핸들이 사라졌거나 호출이 실패하면 반드시 관측 가능한 로그를 남긴다.
     */
    private fun respond(
        device: BluetoothDevice,
        requestId: Int,
        status: Int,
        offset: Int,
        value: ByteArray?
    ) {
        val server = gattServer
        if (server == null) {
            Log.e(TAG, "sendResponse 불가 — gattServer=null (requestId=$requestId, ${device.address}) ATT 무응답 위험")
            return
        }
        val ok = try {
            server.sendResponse(device, requestId, status, offset, value)
        } catch (e: Exception) {
            Log.e(TAG, "sendResponse 예외 — requestId=$requestId: ${e.message}")
            false
        }
        if (!ok) {
            Log.e(TAG, "sendResponse 실패 — requestId=$requestId status=$status (${device.address})")
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Advertise Callback
    // ────────────────────────────────────────────────────────────────────────

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(TAG, "광고 시작 성공")
            listeners.forEach { it.onAdvertiseStarted() }
        }

        override fun onStartFailure(errorCode: Int) {
            // 이미 광고 중(errorCode=3)은 실패가 아니다 — 실패 통지 시 앱이 무한 재시도 루프에 빠진다.
            if (errorCode == ADVERTISE_FAILED_ALREADY_STARTED) {
                Log.w(TAG, "광고 시작 실패(ALREADY_STARTED) — 이미 광고 중이므로 성공 취급")
                if (_connectionState.value != PeripheralConnectionState.CONNECTED) {
                    updateState(PeripheralConnectionState.ADVERTISING)
                }
                listeners.forEach { it.onAdvertiseStarted() }
                return
            }
            Log.e(TAG, "광고 시작 실패: errorCode=$errorCode")
            updateState(PeripheralConnectionState.IDLE)
            listeners.forEach { it.onAdvertiseFailed(errorCode) }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 초기화 / 설정
    // ────────────────────────────────────────────────────────────────────────

    /**
     * SDK를 초기화한다. 앱 시작 시 한 번 호출한다.
     *
     * @param context Application Context
     * @param config  Peripheral 설정 (선택, 기본값은 Nordic UART Service UUID)
     */
    fun init(context: Context, config: PeripheralConfig = PeripheralConfig()) {
        if (HCBlePeripheral::appContext.isInitialized) {
            Log.w(TAG, "이미 초기화됨 — 설정만 업데이트합니다.")
            this.config = config
            return
        }
        appContext = context.applicationContext
        this.config = config
        bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        registerBluetoothStateReceiver()
        Log.d(TAG, "HCBlePeripheral 초기화 완료")
    }

    /**
     * 설정을 변경한다. 이미 동작 중이면 [stop] 후 다시 [start]해야 반영된다.
     */
    fun updateConfig(config: PeripheralConfig) {
        this.config = config
    }

    /**
     * 이벤트 리스너를 등록한다.
     */
    fun addEventListener(listener: PeripheralEventListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    /**
     * 이벤트 리스너를 해제한다.
     */
    fun removeEventListener(listener: PeripheralEventListener) {
        listeners.remove(listener)
    }

    /**
     * 모든 이벤트 리스너를 해제한다.
     */
    fun removeAllEventListeners() {
        listeners.clear()
    }

    // ────────────────────────────────────────────────────────────────────────
    // 공개 API
    // ────────────────────────────────────────────────────────────────────────

    /**
     * GATT 서버를 열고 BLE 광고를 시작한다.
     *
     * @return 시작 성공 여부
     */
    fun start(): Boolean {
        ensureInitialized()

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.e(TAG, "Bluetooth를 사용할 수 없습니다.")
            return false
        }

        if (_connectionState.value == PeripheralConnectionState.CONNECTED) {
            Log.d(TAG, "이미 연결됨 — 무시")
            return true
        }
        if (_connectionState.value == PeripheralConnectionState.ADVERTISING) {
            Log.d(TAG, "이미 광고 중 — 무시")
            return true
        }

        if (gattServer == null) {
            openGattServer()
        }
        startAdvertising()
        return true
    }

    /**
     * Peripheral → Central 데이터 전송 (동기·블로킹).
     *
     * 프레임 헤더 + 데이터를 실효 청크 크기([currentChunkSize])로 분할 후 순서대로 NOTIFY 전송한다.
     * 전송이 끝날 때까지 호출 스레드를 블록하므로, 센서 콜백 등 지연에 민감한 경로에서는
     * [sendDataAsync]를 사용할 것.
     *
     * @param data 전송할 원본 데이터
     * @return 전송 성공 여부
     */
    fun sendData(data: ByteArray): Boolean = doSendData(data)

    /**
     * Peripheral → Central 데이터 전송 (논블로킹).
     *
     * 내부 송신 큐에 넣고 즉시 리턴한다. 실제 전송은 전용 송신 스레드가 [sendData]와 동일한
     * 방식으로 수행한다. 큐가 [PeripheralConfig.txQueueCapacity]에 도달하면 enqueue를 거부하고
     * false를 반환한다(드롭 정책: 신규 거부 — 무한 큐로 인한 메모리 폭주 방지).
     * 연결이 끊기면 큐에 남은 데이터는 폐기된다.
     *
     * @param data 전송할 원본 데이터
     * @return 큐 적재 성공 여부 (전송 완료 여부가 아님)
     */
    fun sendDataAsync(data: ByteArray): Boolean {
        if (connectedDevice == null) {
            Log.e(TAG, "[TX ASYNC] 연결된 Central 없음 — enqueue 거부")
            return false
        }
        if (pendingTxCount.get() >= config.txQueueCapacity) {
            Log.w(TAG, "[TX ASYNC] 송신 큐 가득참(${config.txQueueCapacity}) — ${data.size}B 드롭")
            return false
        }
        pendingTxCount.incrementAndGet()
        ensureSenderThread().offer(TxWork.Data(data))
        return true
    }

    private fun doSendData(data: ByteArray): Boolean = synchronized(txLock) {
        val device = connectedDevice ?: run {
            Log.e(TAG, "연결된 Central 없음, 전송 불가")
            return false
        }
        val server = gattServer ?: run {
            Log.e(TAG, "GATT 서버 미오픈 — 전송 불가 (device=${device.address})")
            return false
        }
        val characteristic = txCharacteristic ?: run {
            Log.e(TAG, "TX Characteristic 미설정 — 전송 불가 (device=${device.address})")
            return false
        }

        val chunkSize = effectiveChunkSize
        if (chunkSize <= 0) {
            Log.e(TAG, "전송 불가: MTU=$currentMtu -> chunkSize=$chunkSize (device=${device.address})")
            return false
        }

        val framed = frame(data)
        val chunks = framed.toChunks(chunkSize)

        Log.d(TAG, "[TX] 원본=${data.size}B  프레임=${framed.size}B  " +
            "청크=${chunks.size}개(${chunkSize}B, probed=$probedChunkSize, mtu=$currentMtu) device=${device.address}")

        notifySemaphore.drainPermits()

        for ((index, chunk) in chunks.withIndex()) {
            @Suppress("DEPRECATION")
            characteristic.value = chunk

            @Suppress("DEPRECATION")
            val ok = server.notifyCharacteristicChanged(device, characteristic, false)

            if (!ok) {
                Log.e(TAG, "[TX] notifyCharacteristicChanged 실패 (청크 $index/${chunks.size}) device=${device.address} chunkLen=${chunk.size}")
                return false
            }

            val sent = notifySemaphore.tryAcquire(2, TimeUnit.SECONDS)
            if (!sent) {
                Log.e(TAG, "[TX] onNotificationSent 타임아웃 (청크 $index) device=${device.address} — 전송 중단 chunkLen=${chunk.size}")
                return false
            }
        }

        Log.d(TAG, "[TX] 전송 완료: 원본=${data.size}B → 청크 ${chunks.size}개")
        return true
    }

    /**
     * 프레이밍 없이 raw 데이터를 한 번에 전송한다.
     * MTU보다 작은 데이터를 빠르게 전송할 때 사용한다.
     *
     * @param data 전송할 데이터 (maxPayload 이하)
     * @return 전송 성공 여부
     */
    fun sendRawData(data: ByteArray): Boolean = synchronized(txLock) {
        val device = connectedDevice ?: run {
            Log.e(TAG, "연결된 Central 없음, 전송 불가")
            return false
        }
        val server = gattServer ?: return false
        val characteristic = txCharacteristic ?: return false

        if (data.size > effectiveChunkSize) {
            Log.w(TAG, "데이터(${data.size}B)가 실효 청크(${effectiveChunkSize}B)보다 큼. sendData() 사용 권장.")
        }

        notifySemaphore.drainPermits()

        @Suppress("DEPRECATION")
        characteristic.value = data

        @Suppress("DEPRECATION")
        val ok = server.notifyCharacteristicChanged(device, characteristic, false)
        if (!ok) {
            Log.e(TAG, "[TX RAW] notifyCharacteristicChanged 실패")
            return false
        }

        val sent = notifySemaphore.tryAcquire(2, TimeUnit.SECONDS)
        if (!sent) {
            Log.e(TAG, "[TX RAW] onNotificationSent 타임아웃")
            return false
        }

        return true
    }

    /**
     * 문자열 데이터를 전송한다.
     *
     * @param text 전송할 문자열
     * @return 전송 성공 여부
     */
    fun sendText(text: String): Boolean {
        return sendData(text.toByteArray(Charsets.UTF_8))
    }

    /**
     * 현재 연결된 Central과의 연결을 끊는다. 재광고는 하지 않는다.
     */
    fun disconnect() {
        val device = connectedDevice ?: return
        gattServer?.cancelConnection(device)
        connectedDevice = null
        currentMtu = 23
        resetTxPipeline()
        updateState(PeripheralConnectionState.IDLE)
        Log.d(TAG, "disconnect() 호출됨")
    }

    /**
     * 광고만 시작한다. GATT 서버가 열려있지 않으면 자동으로 연다.
     */
    fun startAdvertiseOnly() {
        ensureInitialized()

        if (gattServer == null) {
            openGattServer()
        }
        startAdvertising()
    }

    /**
     * 광고만 중지한다. GATT 서버와 연결은 유지된다.
     */
    fun stopAdvertiseOnly() {
        stopAdvertising()
        if (_connectionState.value == PeripheralConnectionState.ADVERTISING) {
            updateState(PeripheralConnectionState.IDLE)
        }
    }

    /**
     * 모든 리소스를 해제한다. 광고 중지, GATT 서버 종료.
     * 앱 종료 또는 BLE 기능 비활성화 시 호출한다.
     */
    fun stop() {
        stopAdvertising()
        val device = connectedDevice
        connectedDevice = null
        currentMtu = 23
        resetTxPipeline()
        stopSenderThread()
        device?.let { gattServer?.cancelConnection(it) }
        gattServer?.clearServices()
        gattServer?.close()
        gattServer = null
        txCharacteristic = null
        advertiser = null
        updateState(PeripheralConnectionState.IDLE)
        Log.d(TAG, "HCBlePeripheral 종료됨")
    }

    /**
     * SDK 완전 해제. 리스너까지 모두 제거한다.
     */
    fun destroy() {
        stop()
        unregisterBluetoothStateReceiver()
        removeAllEventListeners()
        Log.d(TAG, "HCBlePeripheral destroy 완료")
    }

    // ────────────────────────────────────────────────────────────────────────
    // 디버그 / 유틸
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 현재 상태 정보를 반환한다 (디버깅용).
     */
    fun getDebugInfo(): String {
        return """
            State: ${_connectionState.value}
            Connected Device: ${connectedDevice?.address ?: "None"}
            MTU: $currentMtu (payload: $maxPayload)
            Chunk: $effectiveChunkSize (probed: $probedChunkSize)
            TX Queue: ${pendingTxCount.get()}/${config.txQueueCapacity}
            GATT Server: ${if (gattServer != null) "Open" else "Closed"}
            Listeners: ${listeners.size}
        """.trimIndent()
    }

    // ────────────────────────────────────────────────────────────────────────
    // 내부 구현
    // ────────────────────────────────────────────────────────────────────────

    private fun ensureInitialized() {
        check(HCBlePeripheral::appContext.isInitialized) {
            "HCBlePeripheral.init(context)를 먼저 호출해야 합니다."
        }
    }

    private fun openGattServer() {
        val manager = bluetoothManager ?: return
        gattServer = manager.openGattServer(appContext, gattServerCallback)

        val txChar = BluetoothGattCharacteristic(
            config.txCharUUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                    BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        ).apply {
            addDescriptor(
                BluetoothGattDescriptor(
                    PeripheralConfig.CCCD_UUID,
                    BluetoothGattDescriptor.PERMISSION_READ or
                            BluetoothGattDescriptor.PERMISSION_WRITE
                )
            )
        }
        txCharacteristic = txChar

        val rxChar = BluetoothGattCharacteristic(
            config.rxCharUUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        val service = BluetoothGattService(
            config.serviceUUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        ).apply {
            addCharacteristic(txChar)
            addCharacteristic(rxChar)
        }

        gattServer?.addService(service)
        Log.d(TAG, "GATT 서버 오픈됨 (service=${config.serviceUUID})")
    }

    private fun startAdvertising() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            appContext.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "BLUETOOTH_ADVERTISE 권한 없음 — 광고 시작 생략")
            return
        }

        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.e(TAG, "BLE 광고를 지원하지 않는 기기입니다.")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(config.advertiseMode)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(config.txPowerLevel)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(config.includeDeviceName)
            .addServiceUuid(ParcelUuid(config.serviceUUID))
            .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
        updateState(PeripheralConnectionState.ADVERTISING)
        Log.d(TAG, "BLE 광고 시작됨")
    }

    private fun stopAdvertising() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            Log.w(TAG, "광고 중지 중 오류: ${e.message}")
        }
        Log.d(TAG, "BLE 광고 중지됨")
    }

    private fun updateState(newState: PeripheralConnectionState) {
        if (_connectionState.value == newState) return
        _connectionState.value = newState
        Log.d(TAG, "상태 변경 → $newState")
        listeners.forEach { it.onConnectionStateChanged(newState) }
    }

    private fun registerBluetoothStateReceiver() {
        if (bluetoothStateReceiverRegistered) return
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        appContext.registerReceiver(bluetoothStateReceiver, filter)
        bluetoothStateReceiverRegistered = true
    }

    private fun unregisterBluetoothStateReceiver() {
        if (!bluetoothStateReceiverRegistered) return
        try {
            appContext.unregisterReceiver(bluetoothStateReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "블루투스 상태 리시버 해제 중 오류: ${e.message}")
        }
        bluetoothStateReceiverRegistered = false
    }

    // ────────────────────────────────────────────────────────────────────────
    // 송신 스레드 / 청크 프로브
    // ────────────────────────────────────────────────────────────────────────

    @Synchronized
    private fun ensureSenderThread(): LinkedBlockingQueue<TxWork> {
        val existing = txWorkQueue
        if (existing != null && senderThread?.isAlive == true) return existing

        val queue = LinkedBlockingQueue<TxWork>()
        txWorkQueue = queue
        senderThread = Thread {
            while (true) {
                val work = try {
                    queue.take()
                } catch (e: InterruptedException) {
                    return@Thread
                }
                when (work) {
                    is TxWork.Quit -> return@Thread
                    is TxWork.Probe -> runProbe()
                    is TxWork.Data -> try {
                        doSendData(work.payload)
                    } finally {
                        pendingTxCount.updateAndGet { maxOf(0, it - 1) }
                    }
                }
            }
        }.apply {
            name = "HCBlePeripheral-TX"
            isDaemon = true
            start()
        }
        return queue
    }

    @Synchronized
    private fun stopSenderThread() {
        txWorkQueue?.offer(TxWork.Quit)
        txWorkQueue = null
        senderThread = null
    }

    /** 연결 종료/재시작 시 송신 파이프라인 초기화: 대기 데이터 폐기, 프로브 상태 리셋 */
    private fun resetTxPipeline() {
        probedChunkSize = 0
        pendingProbe?.latch?.countDown() // ackBytes=-1 유지 → 진행 중이던 프로브는 기각 처리
        pendingProbe = null
        txWorkQueue?.removeIf { it is TxWork.Data }
        pendingTxCount.set(0)
    }

    /**
     * 엔드투엔드 청크 프로브 (송신 스레드에서 실행).
     *
     * 사다리 후보 크기마다 길이 헤더 없는 raw notify(`PROBE:<seq>:<N>:` + 0xA5 패딩, 정확히 N바이트)를
     * 1건 발사하고 Central의 `PROBE_ACK:<seq>:<수신바이트>`를 기다린다.
     * ack 수신바이트 == N 일 때만 채택 — 절단(20B 도착)은 ack 불일치로 자동 기각되고,
     * 프로브 유실은 타임아웃으로 기각된다. 전부 실패하면 MTU-3 폴백을 유지한다.
     */
    private fun runProbe() {
        if (connectedDevice == null) return
        Log.d(TAG, "[PROBE] 시작 — 사다리=${config.probeChunkLadder} timeout=${config.probeAckTimeoutMs}ms")

        for (size in config.probeChunkLadder) {
            if (connectedDevice == null) {
                probedChunkSize = 0
                return
            }
            val seq = probeSeq.incrementAndGet()
            val header = "$PROBE_PREFIX$seq:$size:".toByteArray(Charsets.US_ASCII)
            if (header.size > size) {
                Log.w(TAG, "[PROBE] 후보 ${size}B가 헤더(${header.size}B)보다 작음 — 건너뜀")
                continue
            }
            val payload = header.copyOf(size).also { it.fill(PROBE_PADDING, header.size, size) }

            val pending = PendingProbe(seq, size)
            pendingProbe = pending

            if (!notifySingle(payload)) {
                pendingProbe = null
                Log.w(TAG, "[PROBE] notify 발사 실패 (${size}B seq=$seq)")
                continue
            }

            pending.latch.await(config.probeAckTimeoutMs, TimeUnit.MILLISECONDS)
            pendingProbe = null

            if (pending.ackBytes == size) {
                probedChunkSize = size
                Log.i(TAG, "[PROBE] 채택: ${size}B (seq=$seq) → 실효 청크=${effectiveChunkSize}B")
                listeners.forEach { it.onChunkSizeDetermined(effectiveChunkSize) }
                return
            }
            Log.w(TAG, "[PROBE] 기각: 요청=${size}B ack=" +
                if (pending.ackBytes >= 0) "${pending.ackBytes}B(불일치)" else "없음(타임아웃/유실)")
        }

        probedChunkSize = 0
        Log.w(TAG, "[PROBE] 전 후보 실패 — 폴백 ${effectiveChunkSize}B 유지")
        listeners.forEach { it.onChunkSizeDetermined(effectiveChunkSize) }
    }

    /**
     * 청크 분할·프레이밍 없이 notify 1건을 그대로 발사한다 (프로브 전용).
     * onNotificationSent 대기는 발사 순서 보장(pacing)용일 뿐, 전달 크기 판정에 쓰지 않는다.
     */
    private fun notifySingle(data: ByteArray): Boolean = synchronized(txLock) {
        val device = connectedDevice ?: return false
        val server = gattServer ?: return false
        val characteristic = txCharacteristic ?: return false

        notifySemaphore.drainPermits()

        @Suppress("DEPRECATION")
        characteristic.value = data

        @Suppress("DEPRECATION")
        val ok = server.notifyCharacteristicChanged(device, characteristic, false)
        if (!ok) return false

        notifySemaphore.tryAcquire(2, TimeUnit.SECONDS)
        return true
    }

    private fun isProbeAck(value: ByteArray): Boolean {
        val prefix = PROBE_ACK_PREFIX.toByteArray(Charsets.US_ASCII)
        if (value.size < prefix.size) return false
        for (i in prefix.indices) {
            if (value[i] != prefix[i]) return false
        }
        return true
    }

    private fun handleProbeAck(value: ByteArray) {
        val text = String(value, Charsets.US_ASCII).trim { it <= ' ' }
        val parts = text.split(":")
        val seq = parts.getOrNull(1)?.trim()?.toIntOrNull()
        val bytes = parts.getOrNull(2)?.trim()?.toIntOrNull()
        val pending = pendingProbe
        Log.d(TAG, "[PROBE] ACK 수신: seq=$seq bytes=$bytes (대기중 seq=${pending?.seq})")
        if (pending != null && seq == pending.seq && bytes != null) {
            pending.ackBytes = bytes
            pending.latch.countDown()
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 프레이밍
    // ────────────────────────────────────────────────────────────────────────

    private fun frame(data: ByteArray): ByteArray =
        if (config.crcFraming) prependCrcHeader(data) else prependLengthHeader(data)

    /**
     * CRC 프레임: `[0xA5 0x5A][길이 4B BE][payload CRC32 4B BE]` + payload.
     * 수신 재조립기가 매직 스캔으로 재동기화하고 CRC로 프레임 무결성을 검증할 수 있다.
     * [PeripheralConfig.crcFraming]이 켜져 있고 Central 쪽이 같은 포맷을 지원할 때만 사용.
     */
    private fun prependCrcHeader(data: ByteArray): ByteArray {
        val len = data.size
        val crc = CRC32().apply { update(data) }.value
        return byteArrayOf(
            0xA5.toByte(), 0x5A.toByte(),
            (len shr 24 and 0xFF).toByte(),
            (len shr 16 and 0xFF).toByte(),
            (len shr 8 and 0xFF).toByte(),
            (len and 0xFF).toByte(),
            (crc shr 24 and 0xFF).toByte(),
            (crc shr 16 and 0xFF).toByte(),
            (crc shr 8 and 0xFF).toByte(),
            (crc and 0xFF).toByte()
        ) + data
    }

    private fun prependLengthHeader(data: ByteArray): ByteArray {
        val len = data.size
        return byteArrayOf(
            (len shr 24 and 0xFF).toByte(),
            (len shr 16 and 0xFF).toByte(),
            (len shr 8 and 0xFF).toByte(),
            (len and 0xFF).toByte()
        ) + data
    }

    private fun ByteArray.toChunks(chunkSize: Int): List<ByteArray> {
        if (isEmpty() || chunkSize <= 0) return emptyList()
        val result = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < size) {
            val end = minOf(offset + chunkSize, size)
            result.add(copyOfRange(offset, end))
            offset = end
        }
        return result
    }

    private fun bytesSummary(b: ByteArray?): String {
        if (b == null) return "null"
        val len = b.size
        val preview = b.take(8).joinToString(" ") { String.format("%02X", it) }
        return "len=$len first=[$preview]${if (len > 8) "..." else ""}"
    }
}
