package kr.co.kmwdev.bluetooth_sdk_android_v2_example.device_detail

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ExpandableListView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kr.co.hconnect.bluetooth_sdk_android.gatt.BLEState
import kr.co.hconnect.bluetooth_sdk_android_v2.HCBle
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.characteristic_detail.CharacteristicDetailActivity
import kr.co.hconnect.polihealth_sdk_android_v2_example.device_detail.CustomExpandableListAdapter
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.R

class DeviceDetailActivity : AppCompatActivity() {

    private lateinit var expandableListView: ExpandableListView
    private lateinit var expandableListAdapter: CustomExpandableListAdapter
    private val serviceList = mutableListOf<BluetoothGattService>()
    private val characteristicMap = mutableMapOf<String, List<BluetoothGattCharacteristic>>()
    private lateinit var tvStatus: TextView
    private lateinit var tvDeviceName: TextView
    private var deviceAddress: String = ""

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_detail)

        val device: BluetoothDevice? = intent.getParcelableExtra("BLUETOOTH_DEVICE")
        if (device != null) {
            deviceAddress = device.address
        }
        expandableListView = findViewById(R.id.expandableListView)
        tvStatus = findViewById(R.id.tv_status)
        tvDeviceName = findViewById(R.id.tv_device_name)
        val btnConnect: Button = findViewById(R.id.btn_connect)
        val btnDisconnect: Button = findViewById(R.id.btn_disconnect)

        // Display device name
        device?.let {
            tvDeviceName.text = "Device: ${it.name ?: "Unknown"}"
        }

        setExpandableList()

        btnConnect.setOnClickListener {
            device?.let {
                connectToDevice(it)
            }
        }

        btnDisconnect.setOnClickListener {
            HCBle.disconnect(deviceAddress)
            tvStatus.text = "Status: Disconnected"
        }
    }

    private fun setExpandableList() {
        expandableListAdapter = CustomExpandableListAdapter(this, serviceList, characteristicMap)
        expandableListView.setAdapter(expandableListAdapter)

        expandableListView.setOnChildClickListener { _, _, groupPosition, childPosition, _ ->
            val service = serviceList[groupPosition]
            HCBle.setServiceUUID(deviceAddress, service.uuid.toString())
            val characteristic = characteristicMap[service.uuid.toString()]?.get(childPosition)
            HCBle.setCharacteristicUUID(deviceAddress, characteristic?.uuid.toString())
            characteristic?.let {
                val intent = Intent(this, CharacteristicDetailActivity::class.java).apply {
                    putExtra("DEVICE_ADDRESS", deviceAddress)
                    putExtra("CHARACTERISTIC_UUID", it.uuid.toString())
                }
                startActivity(intent)
            }
            true
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun connectToDevice(device: BluetoothDevice) {
        HCBle.connectToDevice(
            device,
            onReceive = { data ->
                CoroutineScope(Dispatchers.Main).launch {
                    Log.d("DeviceDetailActivity", "onReceive: $data")
                }
            },
            onConnState = { state ->
                CoroutineScope(Dispatchers.Main).launch {
                    when (state) {
                        BLEState.CONNECTED_BONDED -> {
                            tvStatus.text = "Status: Connected"
                        }

                        BLEState.BOND_NONE -> {
                            tvStatus.text = "Status: Disconnected"
                        }
                    }
                }
            },
            onGattServiceState = { gatt ->
                CoroutineScope(Dispatchers.Main).launch {
                    when (gatt) {
                        BluetoothGatt.GATT_SUCCESS -> {
                            tvStatus.text = "Status: GATT Success"
                            serviceList.clear()
                            // 장치의 모든 서비스를 추가
                            serviceList.addAll(HCBle.getGattServiceList(deviceAddress))
                            // 각 서비스의 특성을 매핑
                            for (service in serviceList) {
                                characteristicMap[service.uuid.toString()] = service.characteristics
                            }
                            expandableListAdapter.notifyDataSetChanged()
                        }

                        else -> {
                            tvStatus.text = "Status: GATT Failure"
                        }
                    }
                }
            },
            onBondState = { bondState ->
                CoroutineScope(Dispatchers.Main).launch {
                    when (bondState) {
                        BluetoothDevice.BOND_BONDED -> {
                            tvStatus.text = "Status: Bonded"
                        }

                        BluetoothDevice.BOND_BONDING -> {
                            tvStatus.text = "Status: Bonding"
                        }

                        BluetoothDevice.BOND_NONE -> {
                            tvStatus.text = "Status: None"
                        }
                    }
                }
            },
            onSubscriptionState = { state ->
                CoroutineScope(Dispatchers.Main).launch {
                    if (state) {
                        tvStatus.text = "Status: Subscribed"
                    } else {
                        tvStatus.text = "Status: Unsubscribed"
                    }
                }
            }
        )
    }
}
