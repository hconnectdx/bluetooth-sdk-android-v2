package kr.co.hconnect.bluetooth_sdk_android

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
import android.os.Handler
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kr.co.hconnect.bluetooth_sdk_android.gatt.BLEState
import kr.co.hconnect.bluetooth_sdk_android.gatt.GATTService
import kr.co.hconnect.bluetooth_sdk_android.scan.BleScanHandler

@SuppressLint("MissingPermission")
object HCBle {
    private val TAG = "HCBle"
    private val TAG_GATT_SERVICE = "GATTService"
    private lateinit var appContext: Context

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private lateinit var bluetoothGatt: BluetoothGatt

    private var scanning = false
    private lateinit var scanHandler: BleScanHandler

    private lateinit var gattService: GATTService

    // Stops scanning after 10 seconds.
    private val SCAN_PERIOD: Long = 10000
    private var scanJob: Job? = null

    /**
     * TODO: BLE를 초기화합니다.
     * @param context
     */
    fun init(context: Context) {
        if (::appContext.isInitialized) {
            Log.e(TAG, "appContext already to initialize")
            return
        }

        this.appContext = context
        bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        Log.d(TAG, "BLE Initialized")
    }

    /**
     * TODO: BLE 스캔을 시작합니다
     * @param onScanResult
     */
    fun scanLeDevice(scanPeriod: Long = SCAN_PERIOD, onScanResult: (ScanResult) -> Unit) {
        scanHandler = BleScanHandler(onScanResult)

        if (!scanning) {
            scanJob = CoroutineScope(Dispatchers.Main).launch {
                scanning = true
                bluetoothLeScanner.startScan(scanHandler.leScanCallback)

                try {
                    withTimeout(scanPeriod) {
                        suspendCancellableCoroutine<Unit> { continuation ->
                            continuation.invokeOnCancellation {
                                Log.d(TAG, "scanLeDevice: Canceled")
                                scanning = false
                                bluetoothLeScanner.stopScan(scanHandler.leScanCallback)
                            }
                        }
                    }
                } finally {
                    Log.d(TAG, "scanLeDevice: Canceled finnaly")
                    scanning = false
                    bluetoothLeScanner.stopScan(scanHandler.leScanCallback)
                }
            }
        } else {
            scanning = false
            scanJob?.cancel()
            bluetoothLeScanner.stopScan(scanHandler.leScanCallback)
        }
    }

    /**
     * TODO: 스캔을 중지합니다.
     */
    fun scanStop() {
        if (scanning) {
            scanning = false
            bluetoothLeScanner.stopScan(scanHandler.leScanCallback)
        }
    }

    fun isConnect(device: BluetoothDevice): Boolean {
        return bluetoothManager.getConnectionState(
            device,
            BluetoothProfile.GATT
        ) == BluetoothProfile.STATE_CONNECTED
    }

    fun getSelService(): BluetoothGattService {
        return gattService.selService
    }

    fun getSelCharacteristic(): BluetoothGattCharacteristic {
        return gattService.selCharacteristic
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
        onGattServiceState: ((state: Int) -> Unit)? = null,
        onReadCharacteristic: ((status: Int) -> Unit)? = null,
        onWriteCharacteristic: ((status: Int) -> Unit)? = null,
        onSubscriptionState: ((state: Boolean) -> Unit)? = null,
        onReceive: ((characteristic: BluetoothGattCharacteristic) -> Unit)? = null,
        useBondingChangeState: Boolean = true
    ) {
        onBondState?.invoke(device.bondState)
        val bondStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action
                if (BluetoothDevice.ACTION_BOND_STATE_CHANGED == action) {
                    val bondState =
                        intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                    onBondState?.invoke(bondState)
                    BLEState.BOND_BONDED
                }
            }
        }

        if (useBondingChangeState) {
            appContext.registerReceiver(
                bondStateReceiver,
                IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            )
        }

        bluetoothGatt = device.connectGatt(appContext, true, object : BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                Log.d(TAG_GATT_SERVICE, "${newState}321312")
                if (newState == BLEState.STATE_CONNECTED) {
                    Log.d(TAG, "Connected to GATT server.")
                    Log.d(
                        TAG_GATT_SERVICE,
                        "Attempting to start service discovery: " + bluetoothGatt.discoverServices()
                    )
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG_GATT_SERVICE, "${newState}")
                } else {
                    Log.d(TAG_GATT_SERVICE, "${newState}321312")
                }
                onConnState?.invoke(newState)
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                super.onServicesDiscovered(gatt, status)
                if (status == BLEState.GATT_SUCCESS) {
                    Log.d(TAG_GATT_SERVICE, "onServicesDiscovered: $status")

                    gatt?.services?.let {
                        gattService.setGattServiceList(it)

                    } ?: run {
                        Log.e(TAG_GATT_SERVICE, "onServicesDiscovered: gatt.services is null")
                    }

                } else {
                    Log.w(TAG_GATT_SERVICE, "onServicesDiscovered received: $status")
                }
                onGattServiceState?.invoke(status)

            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                super.onCharacteristicWrite(gatt, characteristic, status)
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG_GATT_SERVICE, "onCharacteristicWrite: $status")
                }
                onWriteCharacteristic?.invoke(status)
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                super.onCharacteristicRead(gatt, characteristic, status)
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG_GATT_SERVICE, "onCharacteristicRead: $status")
                }
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

        gattService = GATTService(bluetoothGatt)
    }

    /**
     * TODO: 디바이스와 연결을 해제합니다.
     * @param callback
     */
    fun disconnect(callback: (() -> Unit)? = null) {
        Log.d(TAG, "Disconnecting from device")
        bluetoothGatt.disconnect()

        CoroutineScope(Dispatchers.Main).launch {
            withTimeout(100) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    continuation.invokeOnCancellation {
                        Log.d(TAG, "disconnect: Canceled")
                        bluetoothGatt.close()
                        callback?.invoke()
                    }
                }
            }
        }
    }

    /**
     * TODO: GATT Service 리스트를 반환합니다.
     * 블루투스가 연결되어 onServicesDiscovered 콜백이 호출 돼야 사용가능합니다.
     * @return
     */
    fun getGattServiceList(): List<BluetoothGattService> {
        return gattService.getGattServiceList()
    }

    /**
     * TODO: 서비스 UUID를 설정합니다.
     * 사용 하고자 하는 서비스 UUID를 설정합니다.
     * @param uuid
     */
    fun setServiceUUID(uuid: String) {
        gattService.setServiceUUID(uuid)
    }

    /**
     * TODO: 캐릭터리스틱 UUID를 설정합니다.
     * 사용 하고자 하는 캐릭터리스틱 UUID를 설정합니다.
     * @param characteristicUUID
     */
    fun setCharacteristicUUID(characteristicUUID: String) {
        gattService.setCharacteristicUUID(characteristicUUID)
    }

    /**
     * TODO: 캐릭터리스틱을 읽습니다.
     * setCharacteristicUUID로 설정된 캐릭터리스틱을 읽습니다.
     */
    fun readCharacteristic() {
        gattService.readCharacteristic()
    }

    /**
     * TODO: 캐릭터리스틱을 쓰기합니다.
     * setCharacteristicUUID로 설정된 캐릭터리스틱에 데이터를 쓰기합니다.
     * @param data
     */
    fun writeCharacteristic(data: ByteArray) {
        gattService.writeCharacteristic(data)
    }

    /**
     * TODO: 캐릭터리스틱 알림을 설정합니다.
     * setCharacteristicUUID로 설정된 캐릭터리스틱에 알림을 설정합니다.
     * @param isEnable
     */
    fun setCharacteristicNotification(isEnable: Boolean) {
        gattService.setCharacteristicNotification(isEnable)
    }

    fun readCharacteristicNotification() {
        gattService.readCharacteristicNotification()
    }

    fun getBondedDevices(): List<BluetoothDevice> {
        return bluetoothAdapter.bondedDevices.toList()
    }

    fun getDevice(address: String): BluetoothDevice {
        return bluetoothAdapter.getRemoteDevice(address)
    }

}