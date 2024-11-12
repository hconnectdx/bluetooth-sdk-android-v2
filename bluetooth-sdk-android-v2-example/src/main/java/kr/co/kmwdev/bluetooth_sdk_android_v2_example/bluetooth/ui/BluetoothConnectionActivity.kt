package kr.co.kmwdev.bluetooth_sdk_android_v2_example.bluetooth.ui

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.co.hconnect.bluetooth_sdk_android.gatt.BLEState
import kr.co.hconnect.bluetooth_sdk_android_v2.HCBle
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.bluetooth.ble_sdk.BleSdkManager
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.bluetooth.model.BondModel
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.bluetooth.model.ScanResultModel
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.bluetooth.ui.bonded_adapter.BluetoothBondedListAdapter
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.bluetooth.ui.connectable_adapter.BluetoothScanListAdapter
import kr.co.hconnect.snuh.mhd.bluetooth.viewmodel.BluetoothConnectionViewModel
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.util.Logger
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.util.MyPermission
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.R
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.databinding.ActivityBluetoothConnectionBinding


@SuppressLint("MissingPermission")
class BluetoothConnectionActivity : AppCompatActivity() {

    private lateinit var viewModel: BluetoothConnectionViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[BluetoothConnectionViewModel::class.java]

        val binding: ActivityBluetoothConnectionBinding =
            DataBindingUtil.setContentView(this, R.layout.activity_bluetooth_connection)

        binding.vm = viewModel
        binding.lifecycleOwner = this

        BleSdkManager.init()
        setRecyclerView(binding)
        setBondedRecyclerView(binding)
        setOnClickListener(binding)
    }

    private fun setBondedRecyclerView(binding: ActivityBluetoothConnectionBinding) {
        val recyclerView: RecyclerView = binding.recyclerViewBonded
        viewModel.apply {
            setBondedAdapterClickListener()
            setBondedRecyclerViewUI(recyclerView)
            if (setInitBondedItems()) return

            bondedDevices.observe(this@BluetoothConnectionActivity) { bondedList ->
                Logger.d("Bond result updated $bondedList")
                bondedAdapter.submitList(
                    bondedList.toMutableList()
                        .filter { !it.device.name.isNullOrBlank() })
            }
        }
    }

    private fun BluetoothConnectionViewModel.setInitBondedItems(): Boolean {
        if (MyPermission.isGrantedPermissions(
                this@BluetoothConnectionActivity,
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

    private fun BluetoothConnectionViewModel.setBondedRecyclerViewUI(
        recyclerView: RecyclerView
    ) {
        recyclerView.adapter = bondedAdapter
        recyclerView.layoutManager = LinearLayoutManager(this@BluetoothConnectionActivity)

        // Divider 추가
        val dividerItemDecoration = DividerItemDecoration(
            recyclerView.context,
            (recyclerView.layoutManager as LinearLayoutManager).orientation
        )
        recyclerView.addItemDecoration(dividerItemDecoration)
    }

    private fun BluetoothConnectionViewModel.setBondedAdapterClickListener() {
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

                        val selBondModel =
                            viewModel.bondedDevices.value?.find { it.device.address == device.address }
                        if (selBondModel?.state == BLEState.STATE_CONNECTING) {
                            viewModel.updateBondsState(
                                BondModel(
                                    BLEState.STATE_DISCONNECTED,
                                    device.bondState,
                                    device
                                )
                            )

                        } else {
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

    private fun setRecyclerView(binding: ActivityBluetoothConnectionBinding) {
        val recyclerView: RecyclerView = binding.recyclerView
        viewModel.apply {
            adapter =
                BluetoothScanListAdapter { scanResult ->
                    Logger.d("click: $scanResult")

                    when (HCBle.isConnected(scanResult.device)) {
                        true -> {
                            updateScanningResultsState(
                                ScanResultModel(
                                    BLEState.STATE_DISCONNECTING,
                                    scanResult.device.bondState,
                                    scanResult
                                )
                            )
                            HCBle.disconnect(scanResult.device.address) {
                                updateScanningResultsState(
                                    ScanResultModel(
                                        BLEState.STATE_DISCONNECTED,
                                        scanResult.device.bondState,
                                        scanResult
                                    )
                                )
                            }
                        }

                        false -> {
                            CoroutineScope(Dispatchers.Main).launch {
                                delay(300)
                                updateScanningResultsState(
                                    ScanResultModel(
                                        BLEState.STATE_CONNECTING,
                                        scanResult.device.bondState,
                                        scanResult
                                    )
                                )
                                connect(scanResult.device)
                            }
                        }
                    }


                }


            recyclerView.adapter = adapter
            recyclerView.layoutManager = LinearLayoutManager(this@BluetoothConnectionActivity)

            // Divider 추가
            val dividerItemDecoration = DividerItemDecoration(
                recyclerView.context,
                (recyclerView.layoutManager as LinearLayoutManager).orientation
            )
            recyclerView.addItemDecoration(dividerItemDecoration)
            scanResults.observe(this@BluetoothConnectionActivity) { scanResultModels ->
                Logger.d("Scan result updated $scanResultModels")
                adapter.submitList(
                    scanResultModels.toMutableList()
                        .filter { !it.scanResult.device.name.isNullOrBlank() })
            }
        }
    }

    private fun setOnClickListener(binding: ActivityBluetoothConnectionBinding) {
        binding.scanRefresh.setOnClickListener {

            if (viewModel.isScanning.value == false) {
                viewModel.scanResults.value?.clear()
                viewModel.adapter.submitList(emptyList())
                viewModel.updateScanningStatus(true)
                viewModel.scanLeDevice()
            }


        }
    }
}
