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
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kr.co.hconnect.bluetooth_sdk_android.gatt.BLEState
import kr.co.hconnect.bluetooth_sdk_android_v2.gatt.GATTController
import kr.co.hconnect.bluetooth_sdk_android_v2.gatt.GATTState
import kr.co.hconnect.bluetooth_sdk_android_v2.scan.BleScanHandler
import kr.co.hconnect.bluetooth_sdk_android_v2.util.Logger

@SuppressLint("MissingPermission")
object HCBle {
    private val TAG = "HCBle"
    private val TAG_GATT_SERVICE = "GATTService"
    private lateinit var appContext: Context

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeScanner: BluetoothLeScanner

    private var scanning = false
    private var scanHandler: BleScanHandler? = null
    private var mapBLEGatt = mutableMapOf<String, GATTController>()

    // Stops scanning after 10 seconds.
    private val SCAN_PERIOD: Long = 10000
    private var scanJob: Job? = null

    /**
     * TODO: BLE를 초기화합니다.
     * @param context
     */
    fun init(context: Context) {
        if (HCBle::appContext.isInitialized) {
            Log.e(TAG, "appContext already to initialize")
            return
        }

        appContext = context
        bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        Log.d(TAG, "BLE Initialized")
    }

    /**
     * TODO: BLE 스캔을 시작합니다
     * @param onScanResult
     */
    fun scanLeDevice(
        scanPeriod: Long = SCAN_PERIOD,
        onScanResult: (ScanResult) -> Unit,
        onScanStop: () -> Unit
    ) {
        scanHandler = BleScanHandler(onScanResult)

        if (!scanning) {
            scanJob = CoroutineScope(Dispatchers.IO).launch {
                scanning = true
                bluetoothLeScanner.startScan(scanHandler?.leScanCallback)

                try {
                    withTimeout(scanPeriod) {
                        suspendCancellableCoroutine<Unit> { continuation ->
                            continuation.invokeOnCancellation {
                                Log.d(TAG, "scanLeDevice: Canceled")
                                scanning = false
                                bluetoothLeScanner.stopScan(scanHandler?.leScanCallback)
                                onScanStop()
                                scanJob?.cancel()
                            }
                        }
                    }
                } finally {
                    scanStop()
                }
            }
        }
    }

    /**
     * TODO: 스캔을 중지합니다.
     */
    fun scanStop() {
        if (scanning) {
            scanning = false
            bluetoothLeScanner.stopScan(scanHandler?.leScanCallback)
            scanJob?.cancel()
            scanHandler = null // 핸들러 참조 해제
            Logger.d("scanStop: Stop scanning")
        }
    }

    fun isScanning(): Boolean {
        return scanning
    }

    fun isConnect(device: BluetoothDevice): Boolean {
        return bluetoothManager.getConnectionState(
            device,
            BluetoothProfile.GATT
        ) == BluetoothProfile.STATE_CONNECTED
    }

    /**
     * TODO 다르게 구현한 버전
     *
     * @param device
     * @return
     */
    fun isConnected(device: BluetoothDevice): Boolean {
        val bluetoothManager =
            appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return bluetoothManager.getConnectionState(
            device,
            BluetoothProfile.GATT
        ) == BluetoothProfile.STATE_CONNECTED
    }


    fun getSelService(deviceAddress: String): BluetoothGattService? {
        val gattController: GATTController = mapBLEGatt[deviceAddress] ?: return null
        if (mapBLEGatt[deviceAddress] == null) {
            Logger.e("gattController is not initialized")
            return null
        }

        return gattController.targetService
    }

    fun getSelCharacteristic(deviceAddress: String): BluetoothGattCharacteristic? {
        val gattController: GATTController = mapBLEGatt[deviceAddress] ?: return null
        if (mapBLEGatt[deviceAddress] == null) {
            Logger.e("gattController is not initialized")
            return null
        }
        return gattController.targetCharacteristic
    }

    /**
     * TODO: 디바이스와 연결합니다.
     *
     * @param device
     * @param onConnState
     * @param onGattServiceState
     * @param onBondState
     * @param onReceive
     */
    fun connectToDevice(
        device: BluetoothDevice,
        onConnState: ((state: Int) -> Unit)? = null,
        onBondState: ((state: Int) -> Unit)? = null,
        onGattServiceState: ((state: Int, List<BluetoothGattService>) -> Unit)? = null,
        onReadCharacteristic: ((status: Int) -> Unit)? = null,
        onWriteCharacteristic: ((status: Int, characteristic: BluetoothGattCharacteristic?) -> Unit)? = null,
        onSubscriptionState: ((state: Boolean) -> Unit)? = null,
        onReceive: ((characteristic: BluetoothGattCharacteristic) -> Unit)? = null,
        useBondingChangeState: Boolean = true
    ) {

        val bondStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action
                if (BluetoothDevice.ACTION_BOND_STATE_CHANGED == action) {
                    val bondState =
                        intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)

                    onBondState?.invoke(bondState)
                }
            }
        }

        if (useBondingChangeState) {
            appContext.registerReceiver(
                bondStateReceiver,
                IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            )
        }

        if (mapBLEGatt.containsKey(device.address)) {
            Logger.e("Already connected to the device. device: ${device.name}")
            Logger.e("디바이스를 지우고 재연결 합니다.")
            mapBLEGatt.remove(device.address)
        }

        mapBLEGatt[device.address] = GATTController(
            getGattConnection(
                device,
                onConnState,
                onGattServiceState,
                onReadCharacteristic,
                onWriteCharacteristic,
                onSubscriptionState,
                onReceive
            )
        )
    }

    fun getGattController(deviceAddress: String): GATTController? {
        return mapBLEGatt[deviceAddress]
    }

//    private fun disableNotification() {
//        bluetoothGatt?.let { gatt ->
//            gatt.services.find { service ->
//                service.uuid == UUID.fromString("00001810-0000-1000-8000-00805f9b34fb")
//            }?.let { service ->
//                service.characteristics.forEach { char ->
//                    when (char.uuid) {
//                        UUID.fromString("00002a35-0000-1000-8000-00805f9b34fb") -> {
//                            gatt.setCharacteristicNotification(char, false)
//                        }
//                    }
//                }
//            }
//        }
//    }

    private fun logConnStateChange(title: String, gatt: BluetoothGatt?, newState: Int) {
        Logger.d("[${gatt?.device}] ${title}: ${BLEState.getStateString(newState)}")
    }

    private fun logGattStateChange(title: String, gatt: BluetoothGatt?, gattStatus: Int) {
        Logger.d("[${gatt?.device}] ${title}: ${GATTState.getStatusDescription(gattStatus)}")
    }

    private fun getGattConnection(
        device: BluetoothDevice,
        onConnState: ((state: Int) -> Unit)? = null,
        onGattServiceState: ((state: Int, List<BluetoothGattService>) -> Unit)? = null,
        onReadCharacteristic: ((status: Int) -> Unit)? = null,
        onWriteCharacteristic: ((status: Int, characteristic: BluetoothGattCharacteristic?) -> Unit)? = null,
        onSubscriptionState: ((state: Boolean) -> Unit)? = null,
        onReceive: ((characteristic: BluetoothGattCharacteristic) -> Unit)? = null,
        autoConnect: Boolean = false
    ): BluetoothGatt {
        // GATT 연결 전에 페어링 상태 확인
//        if (device.bondState == BluetoothDevice.BOND_BONDED) {
//            Log.d("Bluetooth", "장치가 이미 페어링된 상태입니다.")
//        } else {
//            Log.d("Bluetooth", "장치가 페어링 되지 않았습니다. createBond() 호출.")
//            device.createBond() // 페어링 요청
//        }

        return device.connectGatt(appContext, autoConnect, object : BluetoothGattCallback() {

            /**
             * TODO: 디바이스와 연결 상태가 변경될 때 호출됩니다.
             */
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                logConnStateChange("onConnectionStateChange", gatt, newState)
                val address = gatt?.device?.address ?: ""

                when (newState) {
                    BLEState.STATE_CONNECTED -> {
                        mapBLEGatt[address]?.bluetoothGatt?.discoverServices()
                    }

                    else -> {
//                        disableNotification()
                        gatt?.close()
                    }
                }

                onConnState?.invoke(newState)
            }

            /**
             * TODO: GATT 서비스가 발견되었을 때 호출됩니다.
             */
            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                super.onServicesDiscovered(gatt, status)
                logGattStateChange("onServicesDiscovered", gatt, status)
                val address = gatt?.device?.address
                if (status == GATTState.GATT_SUCCESS) {
                    gatt?.services?.let {
                        mapBLEGatt[address]?.setGattServiceList(gatt.services)
                        onGattServiceState?.invoke(status, gatt.services)
                        if (device.bondState == BluetoothDevice.BOND_NONE) {
                            Log.d("Bluetooth", "장치가 페어링되지 않음. createBond() 호출...")
                        }
                    } ?: run {
                        Logger.e("onServicesDiscovered: gatt.services is null")
                    }
                } else {
                    Logger.e("onServicesDiscovered received: ${GATTState.getStatusDescription(status)}")
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
                Log.d(TAG_GATT_SERVICE, "onCharacteristicChanged: ${characteristic?.value}")
                onReceive?.invoke(characteristic!!)
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
     * TODO: 디바이스와 연결을 해제합니다.
     * TODO: 연결정보도 모두 삭제합니다. 이 메소드를 호출하면 자동연결 까지 해제 됩니다.
     * @param callback
     */
    fun disconnect(address: String, callback: (() -> Unit)? = null) {
        Logger.d("disconnect address: $address")
        val gattController: GATTController? = mapBLEGatt[address]
        gattController?.disconnect()
        gattController?.bluetoothGatt?.close()
        mapBLEGatt.remove(address)

        Logger.d("Bluetooth: GATT 연결 해제 및 리소스 정리 완료")
    }

    fun disconnectAll() {
        mapBLEGatt.forEach { (address, gattController) ->
            gattController.disconnect()
        }
        mapBLEGatt.clear()
    }

    /**
     * TODO: GATT Service 리스트를 반환합니다.
     * 블루투스가 연결되어 onServicesDiscovered 콜백이 호출 돼야 사용가능합니다.
     * @return
     */
    fun getGattServiceList(deviceAddress: String): List<BluetoothGattService>? {
        val gattController: GATTController = mapBLEGatt[deviceAddress] ?: return emptyList()
        return gattController.getGattServiceList()
    }

    /**
     * TODO: 서비스 UUID를 설정합니다.
     * 사용 하고자 하는 서비스 UUID를 설정합니다.
     * @param uuid
     */
    @Deprecated("Use setTargetServiceUUID in GATTController")
    fun setTargetServiceUUID(deviceAddress: String, uuid: String) {
        val gattController: GATTController = mapBLEGatt[deviceAddress] ?: run {
            Log.e(TAG, "gattController is not initialized")
            return
        }
        gattController.setTargetServiceUUID(uuid)
    }

    /**
     * TODO: 캐릭터리스틱 UUID를 설정합니다.
     * 사용 하고자 하는 캐릭터리스틱 UUID를 설정합니다.
     * @param characteristicUUID
     */
    @Deprecated("Use setTargetCharacteristicUUID in GATTController")
    fun setTargetCharacteristicUUID(deviceAddress: String, characteristicUUID: String) {
        val gattController: GATTController = mapBLEGatt[deviceAddress] ?: run {
            Log.e(TAG, "gattController is not initialized")
            return
        }
        gattController.setTargetCharacteristicUUID(characteristicUUID)
    }

    /**
     * TODO: 캐릭터리스틱을 읽습니다.
     * setCharacteristicUUID로 설정된 캐릭터리스틱을 읽습니다.
     */
    fun readCharacteristic(deviceAddress: String) {
        val gattController: GATTController = mapBLEGatt[deviceAddress] ?: run {
            Log.e(TAG, "gattController is not initialized")
            return
        }
        gattController.readCharacteristic()
    }

    /**
     * TODO: 캐릭터리스틱을 쓰기합니다.
     * setCharacteristicUUID로 설정된 캐릭터리스틱에 데이터를 쓰기합니다.
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
     * TODO: 캐릭터리스틱 알림을 설정합니다.
     * setCharacteristicUUID로 설정된 캐릭터리스틱에 알림을 설정합니다.
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
            // BluetoothDevice 클래스의 removeBond 메서드 접근
            val method = device.javaClass.getMethod("removeBond")
            return method.invoke(device) as Boolean
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}