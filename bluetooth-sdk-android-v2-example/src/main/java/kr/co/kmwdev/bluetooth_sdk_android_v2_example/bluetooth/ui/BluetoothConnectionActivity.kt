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
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.bluetooth.model.ScanResultModel
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.bluetooth.ui.connectable_adapter.BluetoothScanListAdapter
import kr.co.hconnect.snuh.mhd.bluetooth.viewmodel.BluetoothConnectionViewModel
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.util.Logger
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.R
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.databinding.ActivityBluetoothConnectionBinding
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.util.MyPermission


@SuppressLint("MissingPermission")
class BluetoothConnectionActivity : AppCompatActivity() {

    private lateinit var viewModel: BluetoothConnectionViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 권한 획득
        MyPermission.registerPermissionLauncher(this)

        viewModel = ViewModelProvider(this)[BluetoothConnectionViewModel::class.java]

        val binding: ActivityBluetoothConnectionBinding =
            DataBindingUtil.setContentView(this, R.layout.activity_bluetooth_connection)

        binding.vm = viewModel
        binding.lifecycleOwner = this


        BleSdkManager.init()
        setRecyclerView(binding)
        setBondedRecyclerView(binding)
        setOnClickListener(binding)

        viewModel.scanLeDevice()
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


    private fun setRecyclerView(binding: ActivityBluetoothConnectionBinding) {
        val recyclerView: RecyclerView = binding.recyclerView
        viewModel.apply {
            adapter =
                BluetoothScanListAdapter { device ->
                    Logger.d("click: $device")

                    when (HCBle.isConnected(device)) {
                        true -> {
                            updateScanningResultsState(
                                ScanResultModel(
                                    BLEState.STATE_DISCONNECTING,
                                    device.bondState,
                                    device
                                )
                            )
                            HCBle.disconnect(device.address) {
                                updateScanningResultsState(
                                    ScanResultModel(
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
                                updateScanningResultsState(
                                    ScanResultModel(
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


            recyclerView.adapter = adapter
            recyclerView.layoutManager = LinearLayoutManager(this@BluetoothConnectionActivity)

            // Custom Divider 적용
            val customDivider = CustomDividerItemDecoration(
                recyclerView.context,
                (recyclerView.layoutManager as LinearLayoutManager).orientation
            )
            recyclerView.addItemDecoration(customDivider)

            scanResults.observe(this@BluetoothConnectionActivity) { scanResultModels ->
                Logger.d("Scan result updated $scanResultModels")
                adapter.submitList(
                    scanResultModels.toMutableList()
                        .filter { !it.device.name.isNullOrBlank() })
            }


            setInitConnectedItems()
        }
    }

    private fun setOnClickListener(binding: ActivityBluetoothConnectionBinding) {
        binding.scanRefresh.setOnClickListener {
            viewModel.scanLeDevice()
            viewModel.setInitBondedItems()
        }
        binding.backButton.setOnClickListener {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.scanStop()
        viewModel.bondedDevices.value?.clear()
        viewModel.scanResults.value?.clear()
    }
}
