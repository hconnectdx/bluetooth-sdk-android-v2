package kr.co.hconnect.bluetooth_sdk_android_v2.gatt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothStatusCodes
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.co.hconnect.bluetooth_sdk_android_v2.util.Logger
import java.util.ArrayDeque
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
        synchronized(writeLock) {
            operationQueue.clear()
            isWriteInFlight = false
            awaitingWriteCallback = false
        }
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

    // ── GATT 오퍼레이션 큐 ──────────────────────────────────────────────
    // BLE GATT는 연결 하나당 한 번에 하나의 오퍼레이션(characteristic write, descriptor write,
    // MTU 협상 등)만 진행할 수 있다. 이전 오퍼레이션의 완료 콜백이 오기 전에 새 오퍼레이션을 또
    // 요청하면 OS가 즉시 ERROR_GATT_WRITE_REQUEST_BUSY(201)로 거부하거나, 심지어 로컬에는 접수된
    // 것처럼 보이는데도(반환값 성공) 실제로는 완료 콜백이 영영 안 오는 경우도 있었다.
    // 실측 결과: 연결 직후 requestMtu()가 아직 응답을 못 받은 상태에서 setCharacteristicNotification()의
    // descriptor write가 겹쳐 발사됐고, 그 descriptor write는 "returned: 0"(로컬 접수 성공)까지는
    // 찍혔지만 onDescriptorWrite 콜백이 영영 오지 않아 그 뒤의 모든 오퍼레이션이 큐에 걸린 채로
    // 영구히 멈췄다 — 그래서 MTU 요청까지 포함해 세 종류를 전부 하나의 큐/락으로 묶어 직렬화한다.
    private sealed class QueuedOperation(var retriesLeft: Int) {
        class CharWrite(val data: ByteArray, val writeType: Int, retriesLeft: Int) :
            QueuedOperation(retriesLeft)

        class DescWrite(val descriptor: BluetoothGattDescriptor, val value: ByteArray, retriesLeft: Int) :
            QueuedOperation(retriesLeft)

        class MtuReq(
            val mtu: Int,
            val onResult: ((mtu: Int, success: Boolean) -> Unit)?,
            retriesLeft: Int
        ) : QueuedOperation(retriesLeft)

        // discoverServices()/createBond()처럼 자체 콜백 완료를 기다리지 않아도 되지만,
        // 앞서 큐잉된 MTU 협상 등과 동시에 발사되면 안 되는 오퍼레이션을 위한 타입.
        // 실행만 하고 곧바로 다음 오퍼레이션으로 넘어간다(재시도 없음).
        class Action(val block: () -> Unit) : QueuedOperation(0)
    }

    private val operationQueue = ArrayDeque<QueuedOperation>()
    private val writeLock = Any()
    private var isWriteInFlight = false

    // WRITE_TYPE_NO_RESPONSE는 로컬 접수 즉시 자체 완료 처리를 하기 때문에, 그 이후 도착하는
    // (기기별로 발생 여부가 다른) 지연/유령 콜백이 그다음 큐 항목을 잘못 완료시키지 않도록
    // "지금 콜백을 기다리는 중인지"를 별도로 추적한다.
    private var awaitingWriteCallback = false

    private companion object {
        // 실측: 연결 직후 descriptor write와 write가 겹치면 그 순간부터 커넥션이 계속 BUSY로
        // 고착될 수 있었다(관측치: 6초 이상, 40회 재시도 전부 실패). 오퍼레이션 자체는 드물게
        // 발생하므로 지연에 민감하지 않다고 보고 재시도 예산을 크게 잡는다.
        private const val MAX_WRITE_RETRIES = 40
        private const val WRITE_ISSUE_DELAY_MS = 5L
        private const val WRITE_RETRY_DELAY_MS = 150L
    }

    fun writeCharacteristic(
        data: ByteArray,
        writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    ) {
        if (isDestroyed) {
            Logger.e("GATTController is destroyed")
            return
        }

        synchronized(writeLock) {
            operationQueue.addLast(QueuedOperation.CharWrite(data, writeType, MAX_WRITE_RETRIES))
        }
        Logger.d("writeCharacteristic queued (queueSize=${operationQueue.size}, writeType=$writeType)")
        processNextOperation()
    }

    /**
     * HCBle의 BluetoothGattCallback.onCharacteristicWrite에서 호출된다.
     * 진행 중이던 오퍼레이션을 큐에서 정리하고, 다음 오퍼레이션을 이어서 진행한다.
     */
    internal fun handleCharacteristicWriteResult(status: Int) {
        handleOperationCallback("handleCharacteristicWriteResult", status)
    }

    /**
     * HCBle의 BluetoothGattCallback.onDescriptorWrite에서 호출된다.
     * 진행 중이던 오퍼레이션을 큐에서 정리하고, 다음 오퍼레이션을 이어서 진행한다.
     */
    internal fun handleDescriptorWriteResult(status: Int) {
        handleOperationCallback("handleDescriptorWriteResult", status)
    }

    private fun handleOperationCallback(source: String, status: Int) {
        val shouldProcess = synchronized(writeLock) {
            if (awaitingWriteCallback) {
                awaitingWriteCallback = false
                true
            } else {
                false
            }
        }
        if (!shouldProcess) {
            Logger.d("$source: status=$status — 대기 중인 콜백이 없어 무시(지연된 콜백으로 추정)")
            return
        }
        val success = status == BluetoothGatt.GATT_SUCCESS
        Logger.d("$source: status=$status success=$success")
        onCurrentOperationFinished(success = success, canRetry = true)
    }

    private fun processNextOperation() {
        synchronized(writeLock) {
            if (isWriteInFlight || isDestroyed) return
            if (operationQueue.isEmpty()) return
            isWriteInFlight = true
        }

        // 5ms 딜레이 (기존 로직 유지)
        Handler(Looper.getMainLooper()).postDelayed({
            val queued = synchronized(writeLock) { operationQueue.firstOrNull() }
            if (queued == null) {
                synchronized(writeLock) { isWriteInFlight = false }
                return@postDelayed
            }

            when (queued) {
                is QueuedOperation.CharWrite -> issueCharWrite(queued)
                is QueuedOperation.DescWrite -> issueDescWrite(queued)
                is QueuedOperation.MtuReq -> issueMtuRequest(queued)
                is QueuedOperation.Action -> issueAction(queued)
            }
        }, WRITE_ISSUE_DELAY_MS)
    }

    private fun issueAction(queued: QueuedOperation.Action) {
        try {
            queued.block()
        } catch (e: Exception) {
            Logger.e("Queued action error: ${e.message}")
        }
        onCurrentOperationFinished(success = true, canRetry = false)
    }

    /**
     * discoverServices()/createBond()처럼, 앞서 큐잉된 MTU 협상 등의 오퍼레이션과 동시에
     * 발사되면 안 되는 동작을 안전하게 뒤로 미루기 위한 함수. [block]은 앞선 오퍼레이션들이
     * 모두 처리된 뒤에 메인 스레드에서 실행된다.
     */
    fun enqueueAction(block: () -> Unit) {
        if (isDestroyed) {
            Logger.e("GATTController is destroyed")
            return
        }

        synchronized(writeLock) {
            operationQueue.addLast(QueuedOperation.Action(block))
        }
        Logger.d("action queued (queueSize=${operationQueue.size})")
        processNextOperation()
    }

    private fun issueMtuRequest(queued: QueuedOperation.MtuReq) {
        try {
            Logger.d("Requesting MTU(${queued.mtu})... (queueSize=${operationQueue.size}, retriesLeft=${queued.retriesLeft})")

            val accepted = try {
                bluetoothGatt.requestMtu(queued.mtu)
            } catch (e: Exception) {
                Logger.e("requestMtu(${queued.mtu}) exception: ${e.message}")
                false
            }
            Logger.d("requestMtu returned: $accepted")

            if (!accepted) {
                Logger.e("requestMtu was not accepted by the stack — will retry")
                onCurrentOperationFinished(success = false, canRetry = true, retryDelayMs = WRITE_RETRY_DELAY_MS)
            } else {
                // onMtuChanged 콜백(handleMtuChanged)을 기다린다.
                synchronized(writeLock) { awaitingWriteCallback = true }
            }
        } catch (e: Exception) {
            Logger.e("Exception: ${e.message}")
            e.printStackTrace()
            onCurrentOperationFinished(success = false, canRetry = true, retryDelayMs = WRITE_RETRY_DELAY_MS)
        }
    }

    private fun issueCharWrite(queued: QueuedOperation.CharWrite) {
        val writeChar = targetWriteCharacteristic ?: run {
            Logger.e("targetWriteCharacteristic is not initialized")
            onCurrentOperationFinished(success = false, canRetry = false)
            return
        }

        try {
            Logger.d("Writing characteristic... (queueSize=${operationQueue.size}, retriesLeft=${queued.retriesLeft}, writeType=${queued.writeType})")

            val accepted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val result = bluetoothGatt.writeCharacteristic(writeChar, queued.data, queued.writeType)
                Logger.d("writeCharacteristic returned: $result")
                result == BluetoothStatusCodes.SUCCESS
            } else {
                writeChar.writeType = queued.writeType
                writeChar.value = queued.data
                val result = bluetoothGatt.writeCharacteristic(writeChar)
                Logger.d("writeCharacteristic returned: $result")
                result
            }

            if (!accepted) {
                // OS가 요청 자체를 거부함(BUSY 등) — 이 경우 콜백이 오지 않으므로 곧바로 재시도 처리한다.
                Logger.e("writeCharacteristic was not accepted by the stack — will retry")
                onCurrentOperationFinished(success = false, canRetry = true, retryDelayMs = WRITE_RETRY_DELAY_MS)
            } else if (queued.writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
                // Write Without Response는 원격 ACK가 없다 — onCharacteristicWrite 콜백 발생 여부가
                // API 레벨/기기별로 일관되지 않으므로 콜백을 기다리지 않고 로컬 접수 성공만으로 완료 처리한다.
                Logger.d("write-without-response accepted locally — treating as finished")
                onCurrentOperationFinished(success = true, canRetry = false)
            } else {
                // WRITE_TYPE_DEFAULT 등 응답을 받는 타입 — handleCharacteristicWriteResult(콜백)를 기다린다.
                synchronized(writeLock) { awaitingWriteCallback = true }
            }
        } catch (e: Exception) {
            Logger.e("Exception: ${e.message}")
            e.printStackTrace()
            onCurrentOperationFinished(success = false, canRetry = true, retryDelayMs = WRITE_RETRY_DELAY_MS)
        }
    }

    private fun issueDescWrite(queued: QueuedOperation.DescWrite) {
        try {
            Logger.d("Writing descriptor... (queueSize=${operationQueue.size}, retriesLeft=${queued.retriesLeft})")

            val accepted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val result = bluetoothGatt.writeDescriptor(queued.descriptor, queued.value)
                Logger.d("writeDescriptor returned: $result")
                result == BluetoothStatusCodes.SUCCESS
            } else {
                queued.descriptor.value = queued.value
                val result = bluetoothGatt.writeDescriptor(queued.descriptor)
                Logger.d("writeDescriptor returned: $result")
                result
            }

            if (!accepted) {
                Logger.e("writeDescriptor was not accepted by the stack — will retry")
                onCurrentOperationFinished(success = false, canRetry = true, retryDelayMs = WRITE_RETRY_DELAY_MS)
            } else {
                // onDescriptorWrite 콜백(handleDescriptorWriteResult)을 기다린다.
                synchronized(writeLock) { awaitingWriteCallback = true }
            }
        } catch (e: Exception) {
            Logger.e("Exception: ${e.message}")
            e.printStackTrace()
            onCurrentOperationFinished(success = false, canRetry = true, retryDelayMs = WRITE_RETRY_DELAY_MS)
        }
    }

    private fun onCurrentOperationFinished(success: Boolean, canRetry: Boolean, retryDelayMs: Long = 0L) {
        val hasMore: Boolean
        synchronized(writeLock) {
            val queued = operationQueue.firstOrNull()
            when {
                queued == null -> Unit
                success -> operationQueue.pollFirst()
                canRetry && queued.retriesLeft > 0 -> queued.retriesLeft--
                else -> {
                    Logger.e("GATT operation dropped after exhausting retries")
                    operationQueue.pollFirst()
                }
            }
            isWriteInFlight = false
            awaitingWriteCallback = false
            hasMore = operationQueue.isNotEmpty()
        }

        if (!hasMore) return
        if (retryDelayMs > 0) {
            Handler(Looper.getMainLooper()).postDelayed({ processNextOperation() }, retryDelayMs)
        } else {
            processNextOperation()
        }
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

            // 알림 또는 인디케이션 설정 (로컬 전용 — 원격으로 전송되지 않으므로 큐잉 불필요)
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

                // 실제로 원격에 전송되는 descriptor write는 writeCharacteristic()과 같은 큐를 거친다.
                // 그렇지 않으면 연결 직후 이 descriptor write와 곧이어 걸리는 write가 서로 완료를
                // 기다리지 않고 겹쳐서 커넥션이 BUSY 상태로 고착되는 문제가 있었다.
                synchronized(writeLock) {
                    operationQueue.addLast(QueuedOperation.DescWrite(descriptor, value, MAX_WRITE_RETRIES))
                }
                Logger.d("descriptor write queued for notification ${if (isEnable) "enable" else "disable"} (queueSize=${operationQueue.size})")
                processNextOperation()

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
     * 다른 GATT 오퍼레이션(characteristic write, descriptor write)과 같은 큐를 거치므로,
     * 이 요청이 진행 중인 다른 오퍼레이션과 겹쳐서 응답을 못 받는 일이 없다.
     *
     * @return 큐잉이 성공했는지 여부 (destroy된 경우에만 false)
     */
    fun requestMtu(mtu: Int, onResult: ((mtu: Int, success: Boolean) -> Unit)? = null): Boolean {
        if (isDestroyed) {
            Logger.e("GATTController is destroyed")
            onResult?.invoke(currentMtu, false)
            return false
        }

        synchronized(writeLock) {
            operationQueue.addLast(QueuedOperation.MtuReq(mtu, onResult, MAX_WRITE_RETRIES))
        }
        Logger.d("requestMtu($mtu) queued (queueSize=${operationQueue.size})")
        processNextOperation()
        return true
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

        val pendingCallback = synchronized(writeLock) {
            (operationQueue.firstOrNull() as? QueuedOperation.MtuReq)?.onResult
        }

        handleOperationCallback("handleMtuChanged", status)

        pendingCallback?.invoke(mtu, success)
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