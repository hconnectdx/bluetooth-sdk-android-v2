package kr.co.hconnect.snuh.mhd.bluetooth.viewmodel

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.co.hconnect.bluetooth_sdk_android.gatt.BLEState
import kr.co.hconnect.bluetooth_sdk_android_v2.HCBle
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.MyApplication
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.bluetooth.ble_sdk.BleSdkManager
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.bluetooth.model.BondModel
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.bluetooth.ui.connectable_adapter.BluetoothScanListAdapter
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.bluetooth.model.ScanResultModel
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.bluetooth.ui.CustomDividerItemDecoration
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.bluetooth.ui.bonded_adapter.BluetoothBondedListAdapter
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.util.Logger
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.util.MyPermission
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.util.MySharedPref

class BluetoothConnectionViewModel : ViewModel() {
    val isScanning = MutableLiveData(false)

    val scanResults = MutableLiveData<MutableList<ScanResultModel>>(mutableListOf())
    val bondedDevices = MutableLiveData<MutableList<BondModel>>(mutableListOf())


    lateinit var adapter: BluetoothScanListAdapter
    lateinit var bondedAdapter: BluetoothBondedListAdapter

    private fun updateScanningStatus(isScanning: Boolean) {
        this.isScanning.value = isScanning
    }

    fun updateScanningResultsState(scanResultModel: ScanResultModel) {

        val state = scanResultModel.state
        val scanResult = scanResultModel.scanResult

        scanResults.value?.let { r ->
            val updateDevice = r.find { it.scanResult.device.address == scanResult.device.address }

            if (updateDevice != null) {
                setChangedState(scanResult.device, state)
            }
        }
    }

    private fun updateBondsState(bondModel: BondModel) {

        val device = bondModel.device
        val state = bondModel.state
        val bondState = bondModel.bondState

        setChangedBondState(device, state = state, bondState = bondState)
    }

    @SuppressLint("MissingPermission")
    private fun setChangedState(
        selDevice: BluetoothDevice,
        state: Int
    ) {
        scanResults.value?.let { currentList ->
            val updatedList = currentList.map {
                if (it.scanResult.device.address == selDevice.address) {
                    it.copy(state = state)
                } else {
                    it
                }
            }

            if (updatedList.any { it.scanResult.device.address == selDevice.address }) {
                adapter.submitList(updatedList) {
                    this.scanResults.value = updatedList.toMutableList()
                }
            } else {
                setChangedBondState(selDevice, state, selDevice.bondState)
            }
        }
    }


    private fun setChangedBondState(
        selDevice: BluetoothDevice,
        state: Int,
        bondState: Int
    ) {
        val currentBondedList = bondedDevices.value ?: mutableListOf()

        // 본딩 리스트에서 상태 변경
        val updatedBondedList = currentBondedList.map {
            if (it.device.address == selDevice.address) {
                // 기존 객체를 복사하여 상태를 업데이트
                it.copy(state = state, bondState = bondState)
            } else {
                it
            }
        }.toMutableList()

        // 본딩 리스트에 없고 && 본딩 상태가 아닌 디바이스를 추가
        if (updatedBondedList.none { it.device.address == selDevice.address } && bondState != BLEState.BOND_NONE) {
            updatedBondedList.add(
                BondModel(
                    device = selDevice,
                    state = state,
                    bondState = bondState
                )
            )
        }

        // 본딩 상태가 NONE이면 스캔 리스트를 유지하고 (본딩 기능이 없는 장치)
        // 본딩 상태가 NONE이 아니면 스캔 리스트에서 해당 디바이스 제거 (본딩 기능 있는 장치)
        if (bondState == BLEState.BOND_NONE) {
            Log.d("BluetoothDebug", "Device unbonded and removed: ${selDevice.address}")

        } else {
            setChangedScanningBondState(selDevice, state, bondState)
        }

        // 업데이트된 리스트를 어댑터 및 LiveData에 반영
        bondedAdapter.submitList(updatedBondedList) {
            this.bondedDevices.value = updatedBondedList
        }

        Log.d(
            "BluetoothDebug",
            "Updated bonded list: ${updatedBondedList.map { it.device.address }}"
        )
    }


    private fun setChangedScanningBondState(
        selDevice: BluetoothDevice,
        state: Int,
        bondState: Int
    ) {
        // 스캔된 리스트와 본딩된 리스트 가져오기
        val scannedList = scanResults.value ?: mutableListOf()
        val currentBondedList = bondedDevices.value ?: mutableListOf()

        // 스캔 리스트에서 선택된 디바이스 제거
        val updatedScannedList = scannedList.filterNot {
            it.scanResult.device.address == selDevice.address
        }.toMutableList()

        // 본딩 후, 본딩 리스트에 추가하거나 상태 업데이트
        val updatedBondedList = currentBondedList.map {
            if (it.device.address == selDevice.address) {
                it.copy(state = state, bondState = bondState)
            } else {
                it
            }
        }.toMutableList()

        // 본딩 리스트에 없으면 새로운 장치 추가
        if (updatedBondedList.none { it.device.address == selDevice.address } && bondState != BLEState.BOND_NONE) {
            updatedBondedList.add(
                BondModel(
                    device = selDevice,
                    state = state,
                    bondState = bondState
                )
            )
        }

        // 본딩 상태가 해제된 경우(본딩 후 실패 또는 해제 시)
        if (bondState == BLEState.BOND_NONE) {
            updatedBondedList.removeAll { it.device.address == selDevice.address }
        }

        // LiveData 및 Adapter 업데이트
        bondedAdapter.submitList(updatedBondedList) {
            this.bondedDevices.value = updatedBondedList
        }

        adapter.submitList(updatedScannedList) {
            this.scanResults.value = updatedScannedList
        }

    }


    @SuppressLint("MissingPermission")
    private fun addDevice(scanResult: ScanResult) {
        scanResults.value?.let { r ->
            // 이미 본딩된 장치는 추가 하지 않음
            val bondedAddressList =
                HCBle.getBondedDevices().map { bondedDevice -> bondedDevice.address }

            if (bondedAddressList.contains(scanResult.device.address)) {
                return
            }

            // 이미 추가된 장치의 주소 목록을 가져와서 중복 검사
            val existingAddresses = r.map { it.scanResult.device.address }
            val bondState = scanResult.device.bondState
            val connectState = if (HCBle.isConnect(scanResult.device))
                BLEState.STATE_CONNECTED
            else
                BLEState.STATE_DISCONNECTED

            if (!existingAddresses.contains(scanResult.device.address)) {
                val model =
                    ScanResultModel(
                        state = connectState,
                        bondState = bondState,
                        scanResult = scanResult
                    )
                r.add(model)
                scanResults.value = r // 변경된 리스트를 다시 할당
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun scanLeDevice() {
        if (isScanning.value == false) {
            // CONNECTED 상태의 디바이스를 유지하기 위해 필터링
            val connectedDevices =
                scanResults.value?.filter { it.state == BLEState.STATE_CONNECTED }?.toMutableList()
                    ?: mutableListOf()

            // 기존 리스트를 clear하고 CONNECTED 상태의 디바이스만 유지
            scanResults.value = connectedDevices
            adapter.submitList(connectedDevices) {
                updateScanningStatus(true)

                // BLE 스캔 시작
                BleSdkManager.startBleScan(
                    onScanResult = { scanResult ->
                        if (!scanResult.device.name.isNullOrBlank()) {
                            addDevice(scanResult)
                        }
                    },
                    onScanStop = {
                        Logger.d("Scan stopped")
                        updateScanningStatus(false)
                    },
                    initBondedList = {
                        setInitBondedItems()
                        setInitConnectedItems()
                    }
                )
            }
        }
    }

    fun scanStop() {
        BleSdkManager.stopBleScan()
    }

    @SuppressLint("MissingPermission")
    fun connect(selDevice: BluetoothDevice) {
        HCBle.connectToDevice(
            selDevice,
            onReceive = {
                Logger.d("Received data: $it")
            },
            onBondState = { bondState ->
                val strConnState = BLEState.getStateString(bondState)
                Logger.e("onBondState: $strConnState ${selDevice.name}")

                when (bondState) {

                    BLEState.BOND_NONE -> {
                        val isConnect = HCBle.isConnect(selDevice)
                        if (bondedDevices.value?.find { it.device.address == selDevice.address } != null && !isConnect) {
                            updateBondsState(
                                BondModel(
                                    state = BLEState.STATE_DISCONNECTED,
                                    bondState = bondState,
                                    device = selDevice
                                )
                            )
                        }
                    }

                    BLEState.BOND_BONDING -> {
                    }

                    BLEState.BOND_BONDED -> {

                        val bleState = if (HCBle.isConnect(selDevice)) {
                            BLEState.STATE_CONNECTED
                        } else {
                            BLEState.STATE_DISCONNECTED
                        }

                        updateBondsState(
                            BondModel(
                                state = bleState,
                                bondState = bondState,
                                device = selDevice
                            )
                        )
                    }
                }

            },
            onConnState = { connState ->


                val strConnState = BLEState.getStateString(connState)
                Logger.e("onConnState: $strConnState")
                when (connState) {

                    BLEState.STATE_CONNECTED -> {
                        Logger.e("Connected to ${selDevice.name}")
                        setChangedState(selDevice, BLEState.STATE_CONNECTED)
                        // 로컬DB에 BluetoothDevice 객체 저장

                        saveDeviceAddress(selDevice.address)
                    }

                    BLEState.STATE_DISCONNECTED -> {
                        Logger.e("Disconnected from ${selDevice.name}")
                        setChangedState(selDevice, BLEState.STATE_DISCONNECTED)
                    }

                    BLEState.STATE_DISCONNECTING -> {
                        Logger.e("Disconnecting from ${selDevice.name}")
                        setChangedState(selDevice, BLEState.STATE_DISCONNECTING)
                    }

                    BLEState.STATE_CONNECTING -> {
                        Logger.e("Connecting to ${selDevice.name}")
                        setChangedState(selDevice, BLEState.STATE_CONNECTING)
                    }

                }
            },
            onGattServiceState = { gattState ->
                Logger.d("onGattServiceState: ${BLEState.getStateString(gattState)}")
            },
            onSubscriptionState = {
                Logger.e("onSubscriptionState: $it")
            },
            onReadCharacteristic = {
                Logger.e("onReadCharacteristic: $it")
            },
            onWriteCharacteristic = {
                Logger.d("onWriteCharacteristic: $it")
            },
            useBondingChangeState = true,
        )
    }

    fun setInitConnectedItems() {
        if (MyPermission.isGrantedPermissions(
                MyApplication.getAppContext(),
                MyPermission.PERMISSION_BLUETOOTH
            )
        ) {
            val connectedAddressSet = getDeviceAddressSet()
            val connectedAddressList = connectedAddressSet.toMutableList()
            val connectedDeviceList = connectedAddressList.map { address ->
                HCBle.getBluetoothDeviceByAddress(address)
            }

            connectedDeviceList.forEach { device ->
                if (device != null) {
//                    connect(device)
                    HCBle.isConnect(device).let {
                        val state =
                            if (it) BLEState.STATE_CONNECTED else BLEState.STATE_DISCONNECTED
                        Logger.d("Connected device: ${device.name} $state")
                        setChangedState(device, state)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun setInitBondedItems(): Boolean {
        if (MyPermission.isGrantedPermissions(
                MyApplication.getAppContext(),
                MyPermission.PERMISSION_BLUETOOTH
            )
        ) {
            bondedDevices.value?.clear()
            val bondedList = HCBle.getBondedDevices().toMutableList()
            if (bondedList.isEmpty()) return true
            val filteredBondedList = bondedList.map { device ->
                BondModel(
                    state = HCBle.isConnect(device).let {
                        if (it) BLEState.STATE_CONNECTED else BLEState.STATE_DISCONNECTED
                    },
                    bondState = device.bondState,
                    device
                )
            }

            bondedDevices.value = filteredBondedList.toMutableList()
        }
        return false
    }

    fun setBondedRecyclerViewUI(
        recyclerView: RecyclerView
    ) {
        recyclerView.adapter = bondedAdapter
        recyclerView.layoutManager = LinearLayoutManager(MyApplication.getAppContext())

        // Custom Divider 적용
        val customDivider = CustomDividerItemDecoration(
            recyclerView.context,
            (recyclerView.layoutManager as LinearLayoutManager).orientation
        )
        recyclerView.addItemDecoration(customDivider)
    }

    @SuppressLint("MissingPermission")
    fun setBondedAdapterClickListener() {
        bondedAdapter = BluetoothBondedListAdapter { device ->
            Logger.d("click: $device")

            when (HCBle.isConnect(device)) {
                true -> {
                    HCBle.disconnect(device.address) {
                        updateBondsState(
                            BondModel(
                                BLEState.STATE_DISCONNECTED,
                                device.bondState,
                                device
                            )
                        )
                    }
                }

                false -> {
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(300)

                        updateBondsState(
                            BondModel(
                                BLEState.STATE_CONNECTING,
                                device.bondState,
                                device
                            )
                        )
                        connect(device)
                    }
                }
            }
        }
    }

    private fun saveDeviceAddress(address: String) {
        MySharedPref.saveBLEAddress(address = address)
    }

    private fun getDeviceAddressSet(): Set<String> {
        return MySharedPref.getBLEAddresses()
    }

    private fun removeDeviceAddress(address: String) {
        MySharedPref.removeBLEAddress(address = address)
    }


}