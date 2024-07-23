package kr.co.hconnect.polihealth_sdk_android_v2_example.device_detail

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ExpandableListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kr.co.hconnect.polihealth_sdk_android.BLEState
import kr.co.hconnect.polihealth_sdk_android.PoliBLE
import kr.co.hconnect.polihealth_sdk_android.ProtocolType
import kr.co.hconnect.polihealth_sdk_android.api.dto.response.SleepResponse
import kr.co.hconnect.polihealth_sdk_android_v2_example.characteristic_detail.CharacteristicDetailActivity
import kr.co.hconnect.polihealth_sdk_android_v2_example.R

class DeviceDetailActivity : AppCompatActivity() {

    private lateinit var expandableListView: ExpandableListView
    private lateinit var expandableListAdapter: CustomExpandableListAdapter
    private val serviceList = mutableListOf<BluetoothGattService>()
    private val characteristicMap = mutableMapOf<String, List<BluetoothGattCharacteristic>>()
    private lateinit var tvStatus: TextView
    private lateinit var tvDeviceName: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_detail)

        val device: BluetoothDevice? = intent.getParcelableExtra("BLUETOOTH_DEVICE")
        expandableListView = findViewById(R.id.expandableListView)
        tvStatus = findViewById(R.id.tv_status)
        tvDeviceName = findViewById(R.id.tv_device_name)
        val btnConnect: Button = findViewById(R.id.btn_connect)

        // Display device name
        device?.let {
            tvDeviceName.text = "Device: ${it.name ?: "Unknown"}"
        }

        expandableListAdapter = CustomExpandableListAdapter(this, serviceList, characteristicMap)
        expandableListView.setAdapter(expandableListAdapter)

        expandableListView.setOnChildClickListener { _, _, groupPosition, childPosition, _ ->
            val service = serviceList[groupPosition]
            PoliBLE.setServiceUUID(service.uuid.toString())
            val characteristic = characteristicMap[service.uuid.toString()]?.get(childPosition)
            PoliBLE.setCharacteristicUUID(characteristic?.uuid.toString())
            characteristic?.let {
                val intent = Intent(this, CharacteristicDetailActivity::class.java).apply {
                    putExtra("CHARACTERISTIC_UUID", it.uuid.toString())
                }
                startActivity(intent)
            }
            true
        }

        btnConnect.setOnClickListener {
            device?.let {
                connectToDevice(it)
            }
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        PoliBLE.connectDevice(
            this,
            device,
            onReceive = { type: ProtocolType, response: SleepResponse? ->
                // 데이터 수신 처리
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
                            serviceList.addAll(PoliBLE.getGattServiceList())
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
