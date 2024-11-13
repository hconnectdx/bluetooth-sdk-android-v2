package kr.co.hconnect.snuh.mhd.bluetooth.viewmodel

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.DividerItemDecoration
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
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.bluetooth.ui.bonded_adapter.BluetoothBondedListAdapter
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.util.Logger
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.util.MyPermission

class BluetoothConnectionViewModel : ViewModel() {
    val isScanning = MutableLiveData(false)

    val scanResults = MutableLiveData<MutableList<ScanResultModel>>(mutableListOf())
    val bondedDevices = MutableLiveData<MutableList<BondModel>>(mutableListOf())


    lateinit var adapter: BluetoothScanListAdapter
    lateinit var bondedAdapter: BluetoothBondedListAdapter

    fun updateScanningStatus(isScanning: Boolean) {
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

    fun updateBondsState(bondModel: BondModel) {

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
//        if (state == BLEState.STATE_DISCONNECTED) {
//            HCBle.disconnect(selDevice.address)
//        }
        scanResults.value?.let { scanResults ->
            val updatedList = scanResults.map {
                if (it.scanResult.device.address == selDevice.address) {
                    it.copy(state = state)
                } else {
                    it
                }
            }

            // 스캔 리스트에 없다면 본딩 리스트를 탐색
            if (updatedList.find { it.scanResult.device.address == selDevice.address } == null) {
                setChangedBondState(selDevice, state, selDevice.bondState)
            } else {
                adapter.submitList(updatedList) {
                    this.scanResults.value = updatedList.toMutableList()
                }
            }
        }
    }

    private fun setChangedBondState(
        selDevice: BluetoothDevice,
        state: Int,
        bondState: Int
    ) {
        val scannedList = scanResults.value ?: mutableListOf()
        val currentBondedList = bondedDevices.value ?: mutableListOf()

        // 본딩리스트에 남아 있으면서 본딩이 해지된 경우
        // > 본딩 리스트에서 제거 한다.
        if (currentBondedList.find { it.device.address == selDevice.address } != null &&
            bondState == BLEState.BOND_NONE) {

            currentBondedList.let { currentList ->
                val updatedList = currentList.filter { it.device.address != selDevice.address }
                bondedAdapter.submitList(updatedList)
                this.bondedDevices.value?.clear()
                this.bondedDevices.value = updatedList.toMutableList()

            }
        }

        // 스캔 리스트에 아직 남아있는데 본딩 작업을 처리 해야 할 경우
        val scannedAddressList = scannedList.map { it.scanResult.device.address }

        if (scannedAddressList.contains(selDevice.address)) {
            setChangedScanningBondState(selDevice, state, bondState)
            return
        }


        // 스캔 리스트에 없고 경우 본딩 리스트에 있는 경우
        // 본딩 리스트에서 변경된 디바이스를 찾아서 상태 변경
        currentBondedList.let { currentList ->
            // 기존 리스트에서 동일한 디바이스를 제외하고 새로운 상태의 아이템 추가
            val updatedList = currentList.map {
                if (it.device.address == selDevice.address) {
                    it.copy(
                        state = state,
                        bondState = bondState
                    )
                } else {
                    it
                }
            }.toMutableList()

            // Adapter에 새로운 리스트 전달 및 LiveData 업데이트
            bondedAdapter.submitList(updatedList)
            this.bondedDevices.value = updatedList // 두번째 작동 갑자기 안함ㄴ
        }


    }

    private fun setChangedScanningBondState(
        selDevice: BluetoothDevice,
        state: Int,
        bondState: Int
    ) {
        // 스캔 리스트에서 변경된 디바이스를 찾아서 상태 변경
        scanResults.value?.let { currentList ->
            // 기존 리스트를 복사하고 동일한 디바이스가 존재하면 업데이트, 없으면 추가
            val updatedBondList = currentList.mapNotNull {
                if (it.scanResult.device.address == selDevice.address) {
                    BondModel(
                        state = state,
                        bondState = bondState,
                        device = selDevice
                    )
                } else {
                    null
                }
            }.toMutableList()


            // Adapter에 새로운 리스트 전달 및 LiveData 업데이트
            updatedBondList.addAll(bondedDevices.value ?: mutableListOf())
            bondedAdapter.submitList(updatedBondList) {
                this.bondedDevices.value = updatedBondList
            }


            val updatedScannedList = currentList.mapNotNull {
                if (it.scanResult.device.address == selDevice.address) {
                    null
                } else {
                    it
                }
            }.toMutableList()

            adapter.submitList(updatedScannedList) {
                this.scanResults.value = updatedScannedList
            }
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
        BleSdkManager.startBleScan(
            onScanResult = { scanResult ->
                if (!scanResult.device.name.isNullOrBlank()) {
                    addDevice(scanResult)
                }
            },
            onScanStop = {
                Logger.d("Scan stopped")
                updateScanningStatus(false)
            }
        )
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
                        if (bondedDevices.value?.find { it.device.address == selDevice.address } != null) {
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
                    }

                    BLEState.STATE_DISCONNECTED -> {
                        Logger.e("Disconnected from ${selDevice.name}")
                        setChangedState(selDevice, BLEState.STATE_DISCONNECTED)

                    }

                    BLEState.STATE_CONNECTING -> {
                        Logger.e("Connecting to ${selDevice.name}")
                        setChangedState(selDevice, BLEState.STATE_CONNECTING)
                    }

                }
            },
            onGattServiceState = { gattState ->
                Logger.d("onGattServiceState: ${BLEState.getStateString(gattState)}")
//                if (gattState == BLEState.STATE_CONNECTED) {
//                    setChangedState(selDevice, BLEState.STATE_CONNECTED)
//                } else {
//                    setChangedState(selDevice, BLEState.STATE_DISCONNECTED)
//                }
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

    @SuppressLint("MissingPermission")
    fun setInitBondedItems(): Boolean {
        if (MyPermission.isGrantedPermissions(
                MyApplication.getAppContext(),
                MyPermission.PERMISSION_BLUETOOTH
            )
        ) {
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

        // Divider 추가
        val dividerItemDecoration = DividerItemDecoration(
            recyclerView.context,
            (recyclerView.layoutManager as LinearLayoutManager).orientation
        )
        recyclerView.addItemDecoration(dividerItemDecoration)
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


}