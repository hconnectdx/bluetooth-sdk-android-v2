package kr.co.hconnect.bluetooth_sdk_android_v2

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kr.co.hconnect.bluetooth_sdk_android.gatt.BLEState
import kr.co.hconnect.bluetooth_sdk_android_v2.gatt.GATTController
import kr.co.hconnect.bluetooth_sdk_android_v2.gatt.GATTState
import kr.co.hconnect.bluetooth_sdk_android_v2.scan.BleScanHandler
import kr.co.hconnect.bluetooth_sdk_android_v2.util.Logger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("MissingPermission")
object HCBle {
    private val TAG = "HCBle"
    private val TAG_GATT_SERVICE = "GATTService"
    private lateinit var appContext: Context

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeScanner: BluetoothLeScanner

    // gatt 메모리 누수 감지용 변수
    private var gattLeakMonitorJob: Job? = null
    private var previousClientIf = 0
    private val gattClientIfHistory = mutableListOf<Int>() // clientIf 값 추적

    private var gattUsageMonitorJob: Job? = null


    // 스캔 세션 관리
    data class ScanSession(
        val id: String,
        val scanHandler: BleScanHandler,
        val scanJob: Job,
        val onScanResult: (ScanResult) -> Unit,
        val onScanStop: () -> Unit,
        val scanPeriod: Long,
        val startTime: Long = System.currentTimeMillis()
    )

    private val activeScanSessions = ConcurrentHashMap<String, ScanSession>()

    private var mapBLEGatt = mutableMapOf<String, GATTController>()
    private var bondStateReceivers = mutableMapOf<String, BroadcastReceiver>()

    // 연결 상태 관리
    private var connectingDevices = mutableSetOf<String>()
    private var disconnectingDevices = mutableSetOf<String>()

    // 스캔된 디바이스 중복 방지 (전역 관리)
    private var recentlyFoundDevices = mutableMapOf<String, Long>()
    private val DEVICE_FOUND_COOLDOWN = 2000L // 2초 쿨다운

    private val DEFAULT_SCAN_PERIOD: Long = 10000

    /**
     * BLE를 초기화합니다.
     * @param context
     */
    fun init(context: Context, memoryLeakMonitoringStatus: Boolean = false) {
        if (HCBle::appContext.isInitialized) {
            Log.e(TAG, "appContext already to initialize")
            return
        }

        appContext = context
        bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        Log.d(TAG, "BLE Initialized")

        // GATT 누수 모니터링 시작
        if (memoryLeakMonitoringStatus)

            startGattClientUsageMonitoring()
    }


    /**
     * GATT 클라이언트 사용량 모니터링 시작
     */
    fun startGattClientUsageMonitoring() {
        stopGattClientUsageMonitoring() // 기존 모니터링 중지

        gattUsageMonitorJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    checkGattClientUsage()
                    delay(1000) // 1초 대기
                } catch (e: Exception) {
                    Log.e("GATT_CHECK", "Error monitoring GATT usage: ${e.message}")
                }
            }
        }
        Log.d("GATT_CHECK", "GATT client usage monitoring started")
    }

    /**
     * GATT 클라이언트 사용량 체크
     */
    private fun checkGattClientUsage() {
        val bluetoothManager =
            appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val connectedDevices = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)

        // 타임스탬프 추가
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        Log.d("GATT_CHECK", "[$timestamp] ===== GATT Client Usage =====")
        Log.d("GATT_CHECK", "System connected GATT devices: ${connectedDevices.size}")
        Log.d("GATT_CHECK", "App managed GATT controllers: ${mapBLEGatt.size}")

        if (connectedDevices.isNotEmpty()) {
            Log.d("GATT_CHECK", "--- Connected Devices ---")
            connectedDevices.forEach { device ->
                val deviceName = device.name ?: "Unknown"
                val isManaged = mapBLEGatt.containsKey(device.address)
                val managedStatus = if (isManaged) "[MANAGED]" else "[EXTERNAL]"
                Log.d("GATT_CHECK", "$managedStatus $deviceName (${device.address})")
            }
        }

        // 추가 정보: 앱이 관리하는 디바이스 중 시스템에 없는 것 찾기
        val orphanedControllers = mapBLEGatt.keys.filter { address ->
            !connectedDevices.any { it.address == address }
        }

        if (orphanedControllers.isNotEmpty()) {
            Log.w("GATT_CHECK", "⚠️ Orphaned controllers (not in system): $orphanedControllers")
        }

        // GATT 리소스 사용률
        val usagePercentage = (connectedDevices.size / 32.0 * 100).toInt()
        val usageBar = "█".repeat(usagePercentage / 5).padEnd(20, '░')
        Log.d("GATT_CHECK", "Usage: [$usageBar] ${connectedDevices.size}/32 ($usagePercentage%)")

        // 경고 레벨
        when {
            connectedDevices.size >= 30 -> {
                Log.e("GATT_CHECK", "🚨 CRITICAL: Near GATT limit!")
            }

            connectedDevices.size >= 25 -> {
                Log.w("GATT_CHECK", "⚠️ WARNING: High GATT usage!")
            }
        }

        Log.d("GATT_CHECK", "=====================================")
    }

    /**
     * GATT 클라이언트 사용량 모니터링 중지
     */
    fun stopGattClientUsageMonitoring() {
        gattUsageMonitorJob?.cancel()
        gattUsageMonitorJob = null
        Log.d("GATT_CHECK", "GATT client usage monitoring stopped")
    }

    /**
     * 모니터링 상태 확인
     */
    fun isGattUsageMonitoring(): Boolean = gattUsageMonitorJob?.isActive == true

    /**
     * GATT 리소스 누수 모니터링 시작
     */
    fun startGattLeakMonitoring() {
        gattLeakMonitorJob?.cancel()

        gattLeakMonitorJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                checkGattResourceLeak()
                delay(1000) // 1초마다
            }
        }
    }

    /**
     * GATT 리소스 누수 체크
     */
    private fun checkGattResourceLeak() {
        try {
            // 1. 시스템 GATT 연결 수와 내부 관리 수 비교
            val systemConnected = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)
            val internalCount = mapBLEGatt.size
            val systemCount = systemConnected.size

            Log.d("GATT_LEAK", "===== GATT Status =====")
            Log.d("GATT_LEAK", "Internal controllers: $internalCount")
            Log.d("GATT_LEAK", "System connections: $systemCount")

            // 2. close() 호출 안 된 GATT 찾기
            mapBLEGatt.forEach { (address, controller) ->
                val device = bluetoothAdapter.getRemoteDevice(address)
                val state = bluetoothManager.getConnectionState(device, BluetoothProfile.GATT)

                // 시스템은 연결 끊겼는데 컨트롤러가 남아있는 경우 = close() 안 됨
                if (state == BluetoothProfile.STATE_DISCONNECTED &&
                    !connectingDevices.contains(address) &&
                    !disconnectingDevices.contains(address)
                ) {

                    Log.e(
                        "GATT_LEAK",
                        "❌ GATT NOT CLOSED: $address (disconnected but controller exists)"
                    )
                    Log.e(
                        "GATT_LEAK",
                        "  → Solution: Must call bluetoothGatt.close() after disconnect"
                    )
                }
            }

            // 3. 시스템에만 있고 내부에 없는 연결 (orphaned GATT)
            systemConnected.forEach { device ->
                if (!mapBLEGatt.containsKey(device.address)) {
                    Log.e(
                        "GATT_LEAK",
                        "⚠️ ORPHANED GATT: ${device.address} (system has it but no controller)"
                    )
                    Log.e("GATT_LEAK", "  → This GATT was not properly closed")
                }
            }

            // 4. GATT 클라이언트 한계 체크 (Android 최대 32개)
            when {
                internalCount >= 30 -> {
                    Log.e("GATT_LEAK", "🚨 CRITICAL: Near GATT limit! ($internalCount/32)")
                    Log.e("GATT_LEAK", "🚨 Bluetooth service may crash soon!")
                }

                internalCount >= 25 -> {
                    Log.e("GATT_LEAK", "⚠️ WARNING: High GATT count ($internalCount/32)")
                }

                internalCount >= 20 -> {
                    Log.w("GATT_LEAK", "📊 GATT count getting high: $internalCount/32")
                }
            }

            // 5. 연속 증가 패턴 감지 (GATT가 계속 쌓이는지)
            gattClientIfHistory.add(internalCount)
            if (gattClientIfHistory.size > 10) {
                gattClientIfHistory.removeAt(0)

                // 최근 5개가 계속 증가했는지 체크
                val recent = gattClientIfHistory.takeLast(5)
                if (recent.size == 5) {
                    val isLeaking = recent.zipWithNext().all { (prev, curr) -> curr > prev }
                    if (isLeaking) {
                        Log.e(
                            "GATT_LEAK",
                            "🔴 MEMORY LEAK DETECTED! GATT count keeps increasing: $recent"
                        )
                        Log.e("GATT_LEAK", "🔴 bluetoothGatt.close() is NOT being called properly!")
                    }
                }
            }

            // 6. 로그에서 본 clientIf 값 추적 (registerApp 로그의 clientIf=14 같은 값)
            // 이 값이 계속 증가하면 GATT 클라이언트가 해제 안 되고 쌓이는 것
            if (internalCount > 0 && internalCount > previousClientIf) {
                Log.w("GATT_LEAK", "📈 New GATT client registered (count increased)")
                // clientIf 값은 로그에서만 볼 수 있으므로 간접적으로 추적
            }
            previousClientIf = internalCount

            // 7. 블루투스 서비스 다운 위험도 체크
            if (systemCount >= 30 || internalCount >= 30) {
                Log.e("GATT_LEAK", "💀 BLUETOOTH SERVICE CRASH IMMINENT!")
                Log.e("GATT_LEAK", "💀 onBluetoothServiceDown will be triggered soon!")
            }

        } catch (e: Exception) {
            Log.e("GATT_LEAK", "Error checking GATT leak: ${e.message}")
        }
    }

    /**
     * GATT 누수 모니터링 중지
     */
    fun stopGattLeakMonitoring() {
        gattLeakMonitorJob?.cancel()
        gattLeakMonitorJob = null
        gattClientIfHistory.clear()
    }

    /**
     * BLE 스캔을 시작합니다 (멀티 스캔 지원)
     * @param scanPeriod 스캔 지속 시간
     * @param onScanResult 스캔 결과 콜백
     * @param onScanStop 스캔 종료 콜백
     * @param scanId 스캔 세션 ID (선택사항, null이면 자동 생성)
     * @return 생성된 스캔 세션 ID
     */
    fun scanLeDevice(
        scanId: String = "general",
        scanPeriod: Long = DEFAULT_SCAN_PERIOD,
        onScanResult: (ScanResult) -> Unit,
        onScanStop: () -> Unit
    ): String {


        // 이미 같은 ID의 스캔이 실행 중이면 중지하고 새로 시작
        if (activeScanSessions.containsKey(scanId)) {
            Logger.w("Scan session $scanId already exists, stopping it first")
            stopScanSession(scanId)
        }

        // 스캔 결과 중복 필터링 (전역 중복 방지)
        val filteredOnScanResult: (ScanResult) -> Unit = { result ->
            val address = result.device.address
            val currentTime = System.currentTimeMillis()
            val lastFoundTime = recentlyFoundDevices[address] ?: 0L

            if (currentTime - lastFoundTime > DEVICE_FOUND_COOLDOWN) {
                recentlyFoundDevices[address] = currentTime
                onScanResult(result)
            }
        }

        val scanHandler = BleScanHandler(filteredOnScanResult)

        val scanJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                Logger.d("Starting scan session: $scanId for ${scanPeriod}ms")
                bluetoothLeScanner.startScan(scanHandler.leScanCallback)

                withTimeout(scanPeriod) {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        continuation.invokeOnCancellation {
                            Logger.d("Scan session $scanId: Canceled")
                            stopScanSession(scanId)
                            cleanupScanSession(scanId)
                            onScanStop()
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e("Scan session $scanId error: ${e.message}")
            } finally {
                // 스캔 종료 처리
                try {
                    stopScanSession(scanId)
                } catch (e: Exception) {
                    Logger.e("Error stopping scan for session $scanId: ${e.message}")
                }

                cleanupScanSession(scanId)
                onScanStop()
                Logger.d("Scan session $scanId completed")
            }
        }

        // 스캔 세션 등록
        val scanSession = ScanSession(
            id = scanId,
            scanHandler = scanHandler,
            scanJob = scanJob,
            onScanResult = filteredOnScanResult,
            onScanStop = onScanStop,
            scanPeriod = scanPeriod
        )

        activeScanSessions[scanId] = scanSession

        Logger.d("Scan session $scanId started. Active sessions: ${activeScanSessions.size}")
        return scanId
    }

    /**
     * 특정 스캔 세션을 중지합니다.
     * @param sessionId 중지할 스캔 세션 ID
     */
    fun stopScanSession(sessionId: String = "general"): Boolean {
        val session = activeScanSessions[sessionId]
        if (session == null) {
            Logger.w("Scan session $sessionId not found")
            return false
        }

        try {
            Logger.d("Stopping scan session: $sessionId")

            // 스캔 중지
            bluetoothLeScanner.stopScan(session.scanHandler.leScanCallback)

            // Job 취소
            session.scanJob.cancel()

            // 정리
            cleanupScanSession(sessionId)

            // 콜백 호출
            session.onScanStop()

            Logger.d("Scan session $sessionId stopped successfully")
            return true

        } catch (e: Exception) {
            Logger.e("Error stopping scan session $sessionId: ${e.message}")
            cleanupScanSession(sessionId)
            return false
        }
    }

    /**
     * 모든 스캔 세션을 중지합니다.
     */
    fun stopAllScans() {
        Logger.d("Stopping all scan sessions. Count: ${activeScanSessions.size}")

        val sessionIds = activeScanSessions.keys.toList()
        sessionIds.forEach { sessionId ->
            stopScanSession(sessionId)
        }

        // 전역 중복 방지 맵도 정리
        recentlyFoundDevices.clear()

        Logger.d("All scan sessions stopped")
    }

    /**
     * 스캔 세션 정리
     */
    private fun cleanupScanSession(sessionId: String) {
        activeScanSessions.remove(sessionId)
        Logger.d("Cleaned up scan session: $sessionId. Remaining: ${activeScanSessions.size}")
    }

    /**
     * 스캔 세션 ID 자동 생성
     */
    private fun generateScanSessionId(): String {
        return "scan_${UUID.randomUUID().toString().substring(0, 8)}"
    }

    /**
     * 현재 활성화된 스캔 세션 수 반환
     */
    fun getActiveScanCount(): Int = activeScanSessions.size

    /**
     * 활성화된 스캔 세션 정보 반환
     */
    fun getActiveScanSessions(): List<String> = activeScanSessions.keys.toList()

    /**
     * 특정 스캔 세션이 활성화되어 있는지 확인
     */
    fun isScanSessionActive(sessionId: String): Boolean = activeScanSessions.containsKey(sessionId)

    /**
     * 전체 스캔 상태 확인 (하나라도 실행 중이면 true)
     */
    fun isScanning(): Boolean = activeScanSessions.isNotEmpty()

    /**
     * 레거시 scanStop 메소드 (모든 스캔 중지)
     */
    @Deprecated("Use stopAllScans() or stopScanSession(sessionId) instead")
    fun scanStop(sessionId: String) {
        stopScanSession(sessionId = sessionId)
    }

    fun isConnect(device: BluetoothDevice): Boolean {
        return bluetoothManager.getConnectionState(
            device,
            BluetoothProfile.GATT
        ) == BluetoothProfile.STATE_CONNECTED
    }

    /**
     * 다르게 구현한 버전
     */
    fun isConnected(device: BluetoothDevice): Boolean {
        val bluetoothManager =
            appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return bluetoothManager.getConnectionState(
            device,
            BluetoothProfile.GATT
        ) == BluetoothProfile.STATE_CONNECTED
    }

    /**
     * 연결 중인지 확인하는 함수
     */
    fun isConnecting(deviceAddress: String): Boolean {
        return connectingDevices.contains(deviceAddress)
    }

    /**
     * 연결 해제 중인지 확인하는 함수
     */
    fun isDisconnecting(deviceAddress: String): Boolean {
        return disconnectingDevices.contains(deviceAddress)
    }

    /**
     * 연결 상태 확인을 위한 개선된 함수
     */
    fun isDeviceActuallyConnected(deviceAddress: String): Boolean {
        val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
        val systemConnected = isConnected(device)
        val hasGattController = mapBLEGatt.containsKey(deviceAddress)

        Logger.d("Connection check for $deviceAddress: system=$systemConnected, hasController=$hasGattController")

        return systemConnected && hasGattController
    }

    /**
     * 디바이스와 연결합니다.
     *
     * @param device
     * @param onConnState
     * @param onGattServiceState
     * @param onBondState
     * @param onReceive
     */
    fun connectToDevice(
        sessionId: String,
        device: BluetoothDevice,
        onConnState: ((state: Int) -> Unit)? = null,
        onBondState: ((state: Int) -> Unit)? = null,
        onGattServiceState: ((state: Int, List<BluetoothGattService>) -> Unit)? = null,
        onReadCharacteristic: ((status: Int) -> Unit)? = null,
        onWriteCharacteristic: ((status: Int, characteristic: BluetoothGattCharacteristic?) -> Unit)? = null,
        onSubscriptionState: ((state: Boolean) -> Unit)? = null,
        onReceive: ((characteristic: BluetoothGattCharacteristic) -> Unit)? = null,
        useBondingChangeState: Boolean = true,
        isAutoConnect: Boolean = false,
        isPrintReceiveLog: Boolean = false,
    ) {
        val deviceAddress = device.address

        // 연결 상태 체크 강화
        if (isConnecting(deviceAddress)) {
            Logger.e("Device is already connecting: ${device.name} ($deviceAddress)")
            return
        }

        if (isDisconnecting(deviceAddress)) {
            Logger.e("Device is disconnecting, please wait: ${device.name} ($deviceAddress)")
            return
        }

        // 실제 연결 상태와 맵 상태 모두 체크
        if (isConnected(device) && mapBLEGatt.containsKey(deviceAddress)) {
            Logger.d("Device is already connected and has valid GATT controller: ${device.name}")
            return
        }

        // 기존 GATT 정보가 있으면 강제로 완전 정리 후 재연결
        if (mapBLEGatt.containsKey(deviceAddress) || isConnected(device)) {
            Logger.w("Found existing connection for $deviceAddress, performing force disconnect...")

            // 강제 연결 해제
            forceDisconnect(deviceAddress)

            // 연결 해제 완료 대기
            Thread.sleep(500)

            // 연결이 아직 남아있다면 추가 대기
            if (isConnected(device)) {
                Logger.w("Connection still exists, waiting longer...")
                Thread.sleep(1000)
            }
        }

        // 직접 연결 시도
        connectToDeviceInternal(
            sessionId,
            device, onConnState, onBondState, onGattServiceState,
            onReadCharacteristic, onWriteCharacteristic, onSubscriptionState,
            onReceive, useBondingChangeState, isAutoConnect, isPrintReceiveLog,
        )
    }

    /**
     * 실제 연결 로직 분리
     */
    private fun connectToDeviceInternal(
        sessionId: String,
        device: BluetoothDevice,
        onConnState: ((state: Int) -> Unit)? = null,
        onBondState: ((state: Int) -> Unit)? = null,
        onGattServiceState: ((state: Int, List<BluetoothGattService>) -> Unit)? = null,
        onReadCharacteristic: ((status: Int) -> Unit)? = null,
        onWriteCharacteristic: ((status: Int, characteristic: BluetoothGattCharacteristic?) -> Unit)? = null,
        onSubscriptionState: ((state: Boolean) -> Unit)? = null,
        onReceive: ((characteristic: BluetoothGattCharacteristic) -> Unit)? = null,
        useBondingChangeState: Boolean = true,
        isAutoConnect: Boolean = false,
        isPrintReceiveLog: Boolean = false
    ) {
        val deviceAddress = device.address

        // 중복 체크 강화
        if (connectingDevices.contains(deviceAddress)) {
            Logger.e("Device is already in connecting state: $deviceAddress")
            return
        }

        // 연결 중 상태로 설정
        connectingDevices.add(deviceAddress)
        Logger.d("Added $deviceAddress to connecting devices")

        // BroadcastReceiver 처리 개선
        val bondStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action
                if (BluetoothDevice.ACTION_BOND_STATE_CHANGED == action) {
                    val receivedDevice =
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    // 해당 디바이스의 본딩 상태 변경인지 확인
                    if (receivedDevice?.address == deviceAddress) {
                        val bondState = intent.getIntExtra(
                            BluetoothDevice.EXTRA_BOND_STATE,
                            BluetoothDevice.ERROR
                        )
                        onBondState?.invoke(bondState)
                    }
                }
            }
        }

        if (useBondingChangeState) {
            // 기존 리시버가 있다면 해제
            bondStateReceivers[deviceAddress]?.let { oldReceiver ->
                try {
                    appContext.unregisterReceiver(oldReceiver)
                    Logger.d("Unregistered old bondStateReceiver for $deviceAddress")
                } catch (e: Exception) {
                    Logger.e("Failed to unregister old bondStateReceiver: ${e.message}")
                }
            }

            // 새 리시버 등록
            try {
                appContext.registerReceiver(
                    bondStateReceiver,
                    IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                )
                bondStateReceivers[deviceAddress] = bondStateReceiver
                Logger.d("Registered new bondStateReceiver for $deviceAddress")
            } catch (e: Exception) {
                Logger.e("Failed to register bondStateReceiver: ${e.message}")
                // 등록 실패 시 연결 중 상태 해제
                connectingDevices.remove(deviceAddress)
                return
            }
        }

        try {
            // GATT 연결 생성 (GATTController는 연결 성공 후 onConnectionStateChange에서 생성됨)
            val gatt = getGattConnection(
                sessionId,
                device,
                onConnState,
                onGattServiceState,
                onReadCharacteristic,
                onWriteCharacteristic,
                onSubscriptionState,
                onReceive,
                isAutoConnect,
                isPrintReceiveLog
            )

            // 여기서는 GATTController를 생성하지 않음
            // mapBLEGatt[deviceAddress] = GATTController(gatt) <- 이 줄 제거됨

            Logger.d("GATT connection initiated for $deviceAddress")

        } catch (e: Exception) {
            Logger.e("Failed to create GATT connection for $deviceAddress: ${e.message}")

            // 실패 시 정리
            connectingDevices.remove(deviceAddress)

            // BroadcastReceiver 정리
            if (useBondingChangeState) {
                bondStateReceivers[deviceAddress]?.let { receiver ->
                    try {
                        appContext.unregisterReceiver(receiver)
                        bondStateReceivers.remove(deviceAddress)
                    } catch (ex: Exception) {
                        Logger.e("Failed to cleanup receiver after connection failure: ${ex.message}")
                    }
                }
            }
            throw e
        }
    }

    fun getGattController(deviceAddress: String): GATTController? {
        return mapBLEGatt[deviceAddress]
    }

    private fun logConnStateChange(title: String, gatt: BluetoothGatt?, newState: Int) {
        Logger.d("[${gatt?.device}] ${title}: ${BLEState.getStateString(newState)}")
    }

    private fun logGattStateChange(title: String, gatt: BluetoothGatt?, gattStatus: Int) {
        Logger.d("[${gatt?.device}] ${title}: ${GATTState.getStatusDescription(gattStatus)}")
    }

    private fun getGattConnection(
        sessionId: String,
        device: BluetoothDevice,
        onConnState: ((state: Int) -> Unit)? = null,
        onGattServiceState: ((state: Int, List<BluetoothGattService>) -> Unit)? = null,
        onReadCharacteristic: ((status: Int) -> Unit)? = null,
        onWriteCharacteristic: ((status: Int, characteristic: BluetoothGattCharacteristic?) -> Unit)? = null,
        onSubscriptionState: ((state: Boolean) -> Unit)? = null,
        onReceive: ((characteristic: BluetoothGattCharacteristic) -> Unit)? = null,
        autoConnect: Boolean = false,
        isPrintReceiveLog: Boolean = false
    ): BluetoothGatt {
        stopScanSession(sessionId = sessionId)
        return device.connectGatt(appContext, autoConnect, object : BluetoothGattCallback() {

            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                logConnStateChange("onConnectionStateChange", gatt, newState)
                val address = gatt?.device?.address ?: ""

                // status 체크 (에러 처리)
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        // 정상 상태, newState 처리 진행
                        Logger.d("GATT status success for $address")
                    }

                    133, 129, 257 -> {  // 일반적인 연결 실패 에러 코드들
                        Logger.e("Connection failed with error $status for $address")
                        gatt?.close()
                        mapBLEGatt.remove(address)
                        connectingDevices.remove(address)
                        disconnectingDevices.remove(address)
                        onConnState?.invoke(BLEState.STATE_DISCONNECTED)
                        return
                    }

                    else -> {
                        Logger.e("Unexpected GATT status: $status for $address")
                        gatt?.close()
                        mapBLEGatt.remove(address)
                        connectingDevices.remove(address)
                        disconnectingDevices.remove(address)
                        onConnState?.invoke(BLEState.STATE_DISCONNECTED)
                        return
                    }
                }

                // newState 처리
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Logger.d("Device connected to $address")
                        connectingDevices.remove(address)

                        // 연결 성공 시에만 GATTController 생성 (중복 방지)
                        if (!mapBLEGatt.containsKey(address) && gatt != null) {
                            mapBLEGatt[address] = GATTController(gatt)
                            Logger.d("Created GATT controller for $address")
                        }

                        // 서비스 검색 시작
                        gatt?.discoverServices()
                        stopScanSession(sessionId)

                        onConnState?.invoke(BLEState.STATE_CONNECTED)
                    }

                    BluetoothProfile.STATE_CONNECTING -> {
                        Logger.d("Connecting to $address")
                        onConnState?.invoke(BLEState.STATE_CONNECTING)
                    }

                    BluetoothProfile.STATE_DISCONNECTING -> {
                        Logger.d("Disconnecting from $address")
                        disconnectingDevices.add(address)
                        onConnState?.invoke(BLEState.STATE_DISCONNECTING)
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Logger.d("Device disconnected from $address")

                        // autoConnect 가 아닐때 해제
                        if (!autoConnect) {
                            gatt?.close()
                            Logger.d("GATT closed for $address")

                            // 맵에서 컨트롤러 제거
                            mapBLEGatt.remove(address)

                            // 상태 정리
                            connectingDevices.remove(address)
                            disconnectingDevices.remove(address)

                            onConnState?.invoke(BLEState.STATE_DISCONNECTED)
                        }

                    }

                    else -> {
                        Logger.w("Unknown connection state: $newState for $address")
                        // 알 수 없는 상태에서도 안전하게 정리
                        gatt?.close()
                        mapBLEGatt.remove(address)
                        connectingDevices.remove(address)
                        disconnectingDevices.remove(address)

                        onConnState?.invoke(BLEState.STATE_DISCONNECTED)
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                super.onServicesDiscovered(gatt, status)
                logGattStateChange("onServicesDiscovered", gatt, status)
                val address = gatt?.device?.address

                if (status == GATTState.GATT_SUCCESS) {
                    gatt?.services?.let { services ->
                        mapBLEGatt[address]?.setGattServiceList(services)
                        onGattServiceState?.invoke(status, services)
                        Logger.d("Services discovered successfully for $address")

                        if (device.bondState == BluetoothDevice.BOND_NONE) {
                            Log.d("Bluetooth", "장치가 페어링되지 않음. createBond() 호출...")
                        }
                    } ?: run {
                        Logger.e("onServicesDiscovered: gatt.services is null for $address")
                    }
                } else {
                    Logger.e(
                        "onServicesDiscovered failed for $address: ${
                            GATTState.getStatusDescription(status)
                        }"
                    )
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                super.onCharacteristicWrite(gatt, characteristic, status)
                Log.d(TAG_GATT_SERVICE, "onCharacteristicWrite: ${characteristic?.value}")
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG_GATT_SERVICE, "onCharacteristicWrite: ${getGattStateString(status)}")
                }
                onWriteCharacteristic?.invoke(status, characteristic)
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                super.onCharacteristicRead(gatt, characteristic, status)
                Log.d(TAG_GATT_SERVICE, "onCharacteristicRead: ${getGattStateString(status)}")
                onReadCharacteristic?.invoke(status)
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?
            ) {
                super.onCharacteristicChanged(gatt, characteristic)
                if (isPrintReceiveLog)
                    Log.d(TAG_GATT_SERVICE, "onCharacteristicChanged: ${characteristic?.value}")
                characteristic?.let { onReceive?.invoke(it) }
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt?,
                descriptor: BluetoothGattDescriptor?,
                status: Int
            ) {
                super.onDescriptorWrite(gatt, descriptor, status)
                onSubscriptionState?.invoke(status == BluetoothGatt.GATT_SUCCESS)
            }
        })
    }

    /**
     * 디바이스와 연결을 해제합니다.
     * 연결정보도 모두 삭제합니다. 이 메소드를 호출하면 자동연결 까지 해제 됩니다.
     * @param callback
     */
    fun disconnect(address: String, callback: (() -> Unit)? = null) {
        Logger.d("disconnect address: $address")

        val gattController = mapBLEGatt[address]
        if (gattController == null) {
            Logger.d("No GATT controller found for $address")
            cleanupRemainingResources(address)
            callback?.invoke()
            return
        }

        disconnectingDevices.add(address)
        connectingDevices.remove(address)

        try {
            // GATTController 정리
            gattController.destroy()

        } catch (e: Exception) {
            Logger.e("Error during disconnect: ${e.message}")
        }

        cleanupRemainingResources(address)
        disconnectingDevices.remove(address)

        callback?.invoke()
    }

    /**
     * 나머지 리소스 정리 함수
     */
    private fun cleanupRemainingResources(address: String) {
        try {
            // Map에서 제거
            mapBLEGatt.remove(address)

            // BroadcastReceiver 해제
            bondStateReceivers[address]?.let { receiver ->
                try {
                    appContext.unregisterReceiver(receiver)
                    bondStateReceivers.remove(address)
                    Logger.d("BondStateReceiver unregistered for $address")
                } catch (e: Exception) {
                    Logger.e("Failed to unregister bondStateReceiver: ${e.message}")
                }
            }

            // 상태 정리
            connectingDevices.remove(address)
            disconnectingDevices.remove(address)

            // 최근 발견 디바이스 목록에서도 제거
            recentlyFoundDevices.remove(address)

            Logger.d("Remaining resources cleaned for $address")

        } catch (e: Exception) {
            Logger.e("Error during resource cleanup: ${e.message}")
        }
    }

    /**
     * 강제 연결 해제 함수
     */
    fun forceDisconnect(address: String) {
        Logger.d("forceDisconnect address: $address")

        val device = bluetoothAdapter.getRemoteDevice(address)
        val gattController = mapBLEGatt[address]

        // 1. 실제 GATT 연결부터 강제 해제
        gattController?.bluetoothGatt?.let { gatt ->
            try {
                gatt.disconnect()
                Thread.sleep(200) // 짧은 대기
                gatt.close()
                Logger.d("Force disconnected and closed GATT for $address")
            } catch (e: Exception) {
                Logger.e("Error during force GATT disconnect: ${e.message}")
            }
        }

        // 2. GATTController 정리
        gattController?.destroy()

        // 3. 모든 리소스 즉시 정리
        cleanupRemainingResources(address)

        Logger.d("Force disconnect completed for $address")
    }

    /**
     * 모든 연결을 해제합니다.
     */
    fun disconnectAll() {
        Logger.d("disconnectAll: Starting disconnect all devices")

        // 모든 스캔 중지
        stopAllScans()

        val addressList = mapBLEGatt.keys.toList()

        // 각 디바이스를 순차적으로 연결 해제
        addressList.forEach { address ->
            try {
                Logger.d("Disconnecting device: $address")
                disconnect(address)
                // 각 디바이스 연결 해제 간에 짧은 지연
                Thread.sleep(100)
            } catch (e: Exception) {
                Logger.e("Error disconnecting $address: ${e.message}")
            }
        }

        // 남은 BroadcastReceiver들 정리
        bondStateReceivers.forEach { (address, receiver) ->
            try {
                appContext.unregisterReceiver(receiver)
                Logger.d("Cleaned up remaining bondStateReceiver for $address")
            } catch (e: Exception) {
                Logger.e("Failed to cleanup bondStateReceiver for $address: ${e.message}")
            }
        }
        bondStateReceivers.clear()

        // 상태 관리 변수들 정리
        connectingDevices.clear()
        disconnectingDevices.clear()
        recentlyFoundDevices.clear()
        mapBLEGatt.clear()

        Logger.d("disconnectAll: All devices disconnected and resources cleaned")
    }

    fun getSelService(deviceAddress: String): BluetoothGattService? {
        val gattController: GATTController = mapBLEGatt[deviceAddress] ?: return null
        if (mapBLEGatt[deviceAddress] == null) {
            Logger.e("gattController is not initialized")
            return null
        }

        return gattController.getTargetService()
    }

    /**
     * targetCharacteristic → targetReadCharacteristic
     */
    fun getSelReadCharacteristic(deviceAddress: String): BluetoothGattCharacteristic? {
        val gattController: GATTController = mapBLEGatt[deviceAddress] ?: return null
        return gattController.getTargetReadCharacteristic()
    }

    /**
     * Write Characteristic 조회 함수
     */
    fun getSelWriteCharacteristic(deviceAddress: String): BluetoothGattCharacteristic? {
        val gattController: GATTController = mapBLEGatt[deviceAddress] ?: return null
        return gattController.getTargetWriteCharacteristic()
    }

    /**
     * GATT Service 리스트를 반환합니다.
     * 블루투스가 연결되어 onServicesDiscovered 콜백이 호출 돼야 사용가능합니다.
     * @return
     */
    fun getGattServiceList(deviceAddress: String): List<BluetoothGattService>? {
        val gattController: GATTController = mapBLEGatt[deviceAddress] ?: return emptyList()
        return gattController.getGattServiceList()
    }

    /**
     * 서비스 UUID를 설정합니다.
     * 사용 하고자 하는 서비스 UUID를 설정합니다.
     * @param uuid
     */
    fun setTargetServiceUUID(deviceAddress: String, uuid: String) {
        val gattController: GATTController = mapBLEGatt[deviceAddress] ?: run {
            Log.e(TAG, "gattController is not initialized")
            return
        }
        gattController.setTargetServiceUUID(uuid)
    }

    /**
     * Read 캐릭터리스틱 UUID를 설정합니다.
     * 읽기용 캐릭터리스틱 UUID를 설정합니다.
     * @param characteristicUUID
     */
    fun setTargetReadCharacteristicUUID(deviceAddress: String, characteristicUUID: String) {
        val gattController: GATTController = mapBLEGatt[deviceAddress] ?: run {
            Log.e(TAG, "gattController is not initialized")
            return
        }
        gattController.setTargetReadCharacteristicUUID(characteristicUUID)
    }

    /**
     * Write 캐릭터리스틱 UUID를 설정합니다.
     * 쓰기용 캐릭터리스틱 UUID를 설정합니다.
     * @param characteristicUUID
     */
    fun setTargetWriteCharacteristicUUID(deviceAddress: String, characteristicUUID: String) {
        val gattController: GATTController = mapBLEGatt[deviceAddress] ?: run {
            Log.e(TAG, "gattController is not initialized")
            return
        }
        gattController.setTargetWriteCharacteristicUUID(characteristicUUID)
    }

    /**
     * 캐릭터리스틱을 읽습니다.
     * setTargetReadCharacteristicUUID로 설정된 캐릭터리스틱을 읽습니다.
     */
    fun readCharacteristic(deviceAddress: String) {
        val gattController: GATTController = mapBLEGatt[deviceAddress] ?: run {
            Log.e(TAG, "gattController is not initialized")
            return
        }
        gattController.readCharacteristic()
    }

    /**
     * 캐릭터리스틱에 데이터를 씁니다.
     * setTargetWriteCharacteristicUUID로 설정된 캐릭터리스틱에 데이터를 씁니다.
     * @param data
     */
    fun writeCharacteristic(deviceAddress: String, data: ByteArray) {
        val gattController: GATTController = mapBLEGatt[deviceAddress] ?: run {
            Log.e(TAG, "gattController is not initialized")
            return
        }
        gattController.writeCharacteristic(data)
    }

    /**
     * 캐릭터리스틱 알림을 설정합니다.
     * setTargetReadCharacteristicUUID로 설정된 캐릭터리스틱에 알림을 설정합니다.
     * @param isEnable
     */
    fun setCharacteristicNotification(
        deviceAddress: String,
        isEnable: Boolean,
        isIndicate: Boolean = false
    ) {
        val gattController: GATTController = mapBLEGatt[deviceAddress] ?: run {
            Log.e(TAG, "gattController is not initialized")
            return
        }
        gattController.setCharacteristicNotification(isEnable, isIndicate)
    }

    fun readCharacteristicNotification(deviceAddress: String) {
        val gattController: GATTController = mapBLEGatt[deviceAddress] ?: run {
            Log.e(TAG, "gattController is not initialized")
            return
        }
        gattController.readCharacteristicNotification()
    }

    fun getBondedDevices(): List<BluetoothDevice> {
        if (HCBle::bluetoothAdapter.isInitialized.not()) {
            Log.e(TAG, "bluetoothAdapter is not initialized")
            return emptyList()
        }
        return bluetoothAdapter.bondedDevices.toList()
    }

    fun getDevice(address: String): BluetoothDevice? {
        if (HCBle::bluetoothAdapter.isInitialized.not()) {
            Log.e(TAG, "bluetoothAdapter is not initialized")
            return null
        }
        return bluetoothAdapter.getRemoteDevice(address)
    }

    fun getGattStateString(state: Int): String {
        return when (state) {
            BLEState.GATT_FAILURE -> "GATT_FAILURE"
            BLEState.GATT_SUCCESS -> "GATT_SUCCESS"
            else -> "UNKNOWN_STATE"
        }
    }

    fun getBluetoothDeviceByAddress(address: String): BluetoothDevice? {
        return bluetoothAdapter.getRemoteDevice(address)
    }

    fun unpairDevice(device: BluetoothDevice): Boolean {
        try {
            // 언페어링 전에 연결 해제
            if (isConnected(device)) {
                disconnect(device.address)
                // 연결 해제 완료까지 잠시 대기
                Thread.sleep(500)
            }

            // BluetoothDevice 클래스의 removeBond 메서드 접근
            val method = device.javaClass.getMethod("removeBond")
            return method.invoke(device) as Boolean
        } catch (e: Exception) {
            Logger.e("unpairDevice error: ${e.message}")
            e.printStackTrace()
        }
        return false
    }

    /**
     * Bluetooth를 켜거나 끕니다. (Android 9(P) 이하에서만 사용 가능)
     */
    fun setBluetoothOnOff(isOn: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val message = "Android 10(Q) 미만에서만 Bluetooth를 켤 수 있습니다."
            Logger.d("setBluetoothOn: $message")
            return
        }

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            val message = "Bluetooth is not supported on this device."
            Logger.d("setBluetoothOn: $message")
            return
        }

        if (isOn) {
            val message = "Bluetooth ON."
            Logger.d("setBluetoothOn: $message")
            bluetoothAdapter.enable() // Bluetooth 켜기
        } else {
            val message = "Bluetooth OFF."
            Logger.d("setBluetoothOn: $message")
            // Bluetooth 끄기 전에 모든 연결 해제
            disconnectAll()
            bluetoothAdapter.disable() // Bluetooth 끄기
        }
    }

    /**
     * destroy 메소드 강화
     */
    fun destroy() {
        Logger.d("destroy: Starting cleanup")

        // 1. 모든 스캔 중지
        stopAllScans()

        // 2. 모든 GATT 연결 해제
        disconnectAll()

        Logger.d("destroy: Cleanup completed")
    }

    /**
     * 연결 상태 디버깅용 함수
     */
    fun getConnectionDebugInfo(deviceAddress: String): String {
        val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
        return """
        Device: $deviceAddress
        Is Connected (manager): ${isConnect(device)}
        Is Connected (service): ${isConnected(device)}
        Is Connecting: ${isConnecting(deviceAddress)}
        Is Disconnecting: ${isDisconnecting(deviceAddress)}
        Has GATT Controller: ${mapBLEGatt.containsKey(deviceAddress)}
        Has BondState Receiver: ${bondStateReceivers.containsKey(deviceAddress)}
        Connection State: ${bluetoothManager.getConnectionState(device, BluetoothProfile.GATT)}
    """.trimIndent()
    }

    /**
     * 스캔 상태 디버깅용 함수
     */
    fun getScanDebugInfo(): String {
        val sessionDetails = activeScanSessions.map { (id, session) ->
            val elapsed = System.currentTimeMillis() - session.startTime
            "$id (${elapsed}ms elapsed, ${session.scanPeriod}ms total)"
        }

        return """
        Active Scan Sessions: ${activeScanSessions.size}
        Session Details: ${sessionDetails.joinToString(", ")}
        Recently Found Devices: ${recentlyFoundDevices.size}
        Is Scanning: ${isScanning()}
        """.trimIndent()
    }

    /**
     * 메모리 상태 확인용 디버그 메소드
     */
    fun getDebugInfo(): String {
        return """
            Active Scan Sessions: ${activeScanSessions.size}
            Connected Devices: ${mapBLEGatt.size}
            Connecting Devices: ${connectingDevices.size}
            Disconnecting Devices: ${disconnectingDevices.size}
            Active BondState Receivers: ${bondStateReceivers.size}
            Recently Found Devices: ${recentlyFoundDevices.size}
            Is Scanning: ${isScanning()}
        """.trimIndent()
    }
}