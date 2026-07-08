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
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

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

    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null

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
            if (characteristic.uuid != config.rxCharUUID) return

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
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
                gattServer?.sendResponse(
                    device, requestId, BluetoothGatt.GATT_SUCCESS,
                    offset, characteristic.value
                )
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
            if (descriptor.uuid != PeripheralConfig.CCCD_UUID) return

            @Suppress("DEPRECATION")
            descriptor.value = value

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }

            val enabled = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            Log.d(TAG, "Notify ${if (enabled) "구독" else "해제"}: ${device.address} descriptor=${descriptor.uuid} value=${bytesSummary(value)}")
            listeners.forEach { it.onNotifySubscriptionChanged(device, enabled) }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            if (descriptor.uuid == PeripheralConfig.CCCD_UUID) {
                gattServer?.sendResponse(
                    device, requestId, BluetoothGatt.GATT_SUCCESS,
                    0, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                )
            }
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
     * Peripheral → Central 데이터 전송.
     *
     * 4바이트 big-endian 길이 헤더 + 데이터를 MTU 크기로 청크 분할 후
     * 순서대로 NOTIFY 전송한다.
     *
     * @param data 전송할 원본 데이터
     * @return 전송 성공 여부
     */
    @Synchronized
    fun sendData(data: ByteArray): Boolean {
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

        if (maxPayload <= 0) {
            Log.e(TAG, "전송 불가: MTU=$currentMtu -> maxPayload=$maxPayload (device=${device.address})")
            return false
        }

        val framed = prependLengthHeader(data)
        val chunks = framed.toChunks(maxPayload)

        Log.d(TAG, "[TX] 원본=${data.size}B  프레임=${framed.size}B  " +
            "청크=${chunks.size}개(MTU-3=${maxPayload}B) device=${device.address}")

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
    @Synchronized
    fun sendRawData(data: ByteArray): Boolean {
        val device = connectedDevice ?: run {
            Log.e(TAG, "연결된 Central 없음, 전송 불가")
            return false
        }
        val server = gattServer ?: return false
        val characteristic = txCharacteristic ?: return false

        if (data.size > maxPayload) {
            Log.w(TAG, "데이터(${data.size}B)가 maxPayload(${maxPayload}B)보다 큼. sendData() 사용 권장.")
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
