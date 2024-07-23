package kr.co.hconnect.polihealth_sdk_android_v2_example

import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kr.co.hconnect.polihealth_sdk_android.PoliBLE
import kr.co.hconnect.polihealth_sdk_android.PoliClient
import kr.co.hconnect.polihealth_sdk_android.api.daily.DailyProtocol01API
import kr.co.hconnect.polihealth_sdk_android.api.dto.request.HRSpO2
import kr.co.hconnect.polihealth_sdk_android.service.sleep.SleepApiService
import kr.co.hconnect.polihealth_sdk_android_app.service.sleep.DailyApiService

class MainActivity : AppCompatActivity() {

    private lateinit var deviceListAdapter: DeviceListAdapter
    private val deviceList = mutableListOf<BluetoothDevice>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setBtnProtocolTest()
        deviceListAdapter = DeviceListAdapter(deviceList)
        val listView: ListView = findViewById(R.id.lv_devices)
        listView.adapter = deviceListAdapter



        listView.setOnItemClickListener { parent, view, position, id ->
            val selectedItem = deviceList[position]
            val intent = Intent(this, DeviceDetailActivity::class.java).apply {
                putExtra("BLUETOOTH_DEVICE", selectedItem)
            }
            startActivity(intent)
        }

        val btnStartScan: Button = findViewById(R.id.btn_start_scan)
        btnStartScan.setOnClickListener {


            val isGranted = PermissionManager.isGrantedPermissions(
                context = this@MainActivity,
                permissions = Permissions.PERMISSION_SDK_31
            )
            if (!isGranted) {
                PermissionManager.launchPermissions(
                    permissions = Permissions.PERMISSION_SDK_31,
                    resultCallback = { permissions ->
                        if (permissions.all { it.value }) {
                            startScan()
                        } else {
                            Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            } else {
                startScan()

                deviceList.addAll(PoliBLE.getBondedDevices())
            }
        }
    }

    private fun startScan() {

        val blePermissions =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Permissions.PERMISSION_SDK_31
            else Permissions.PERMISSION_SDK_30
        try {
            PermissionManager.launchPermissions(blePermissions) {
                val deniedItems = it.filter { p -> !p.value }
                if (deniedItems.isEmpty()) {
                    PoliBLE.startScan { scanItem ->
                        if (scanItem.device.name.isNullOrEmpty()) return@startScan
                        deviceList.find { device -> device.address == scanItem.device.address }
                            ?: run {
                                deviceList.add(scanItem.device)
                                deviceListAdapter.notifyDataSetChanged()
                            }
                    }
                } else {
                    println("Permission denied: $deniedItems")
                }
            }


        } catch (e: Exception) {
            println("Error: $e")
        }
    }

    private fun setBtnProtocolTest() {
        val btnTest1: Button = findViewById(R.id.btn_test1)
        btnTest1.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    DailyProtocol01API.apply {
                        val response =
                            DailyApiService().sendProtocol01(parseLTMData(data = testRawData))
                        Log.d("MainActivity", "response: $response")
                    }

                } catch (e: Exception) {
                    Log.e("MainActivity", "error: $e")
                }
            }
        }

        val btnTest2: Button = findViewById(R.id.btn_test2)
        btnTest2.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    DailyProtocol01API.apply {
                        val response = DailyApiService().sendProtocol02()
                        Log.d("MainActivity", "response: $response")
                    }

                } catch (e: Exception) {
                    Log.e("MainActivity", "error: $e")
                }
            }
        }

        val btnTest3: Button = findViewById(R.id.btn_test3)
        btnTest3.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    DailyProtocol01API.apply {
                        val response = DailyApiService().sendProtocol03(HRSpO2(90, 117))
                        Log.d("MainActivity", "response: $response")
                    }

                } catch (e: Exception) {
                    Log.e("MainActivity", "error: $e")
                }
            }
        }

        val btnTest4: Button = findViewById(R.id.btn_test4)
        btnTest4.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = SleepApiService().sendStartSleep()
                    Log.d("MainActivity", "response: $response")
                } catch (e: Exception) {
                    Log.e("MainActivity", "error: $e")
                }
            }
        }
        val btnTest5: Button = findViewById(R.id.btn_test5)
        btnTest5.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = SleepApiService().sendEndSleep()
                    Log.d("MainActivity", "response: $response")
                } catch (e: Exception) {
                    Log.e("MainActivity", "error: $e")
                }
            }
        }
        val btnTest6: Button = findViewById(R.id.btn_test6)
        btnTest6.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = SleepApiService().sendProtocol06(this@MainActivity)
                    Log.d("MainActivity", "response: $response")
                } catch (e: Exception) {
                    Log.e("MainActivity", "error: $e")
                }
            }
        }
        val btnTest7: Button = findViewById(R.id.btn_test7)
        btnTest7.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = SleepApiService().sendProtocol07(this@MainActivity)
                    Log.d("MainActivity", "response: $response")
                } catch (e: Exception) {
                    Log.e("MainActivity", "error: $e")
                }
            }
        }
        val btnTest8: Button = findViewById(R.id.btn_test8)
        btnTest8.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = SleepApiService().sendProtocol08(this@MainActivity)
                    Log.d("MainActivity", "response: $response")
                } catch (e: Exception) {
                    Log.e("MainActivity", "error: $e")
                }
            }
        }
        val btnTest9: Button = findViewById(R.id.btn_test9)
        btnTest9.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = SleepApiService().sendProtocol09(HRSpO2(100, 121))
                    Log.d("MainActivity", "response: $response")
                } catch (e: Exception) {
                    Log.e("MainActivity", "error: $e")
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        PoliBLE.init(this)
        PoliClient.init(
            baseUrl = BuildConfig.API_URL,
            clientId = BuildConfig.CLIENT_ID,
            clientSecret = BuildConfig.CLIENT_SECRET
        )
        PermissionManager.registerPermissionLauncher(this)
    }

    private inner class DeviceListAdapter(private val devices: List<BluetoothDevice>) :
        BaseAdapter() {
        override fun getCount(): Int {
            return devices.size
        }

        override fun getItem(position: Int): Any {
            return devices[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getView(
            position: Int,
            convertView: android.view.View?,
            parent: android.view.ViewGroup?
        ): android.view.View {
            val view = convertView ?: layoutInflater.inflate(
                android.R.layout.simple_list_item_1,
                parent,
                false
            )
            val device = devices[position]
            val textView = view.findViewById<TextView>(android.R.id.text1)
            textView.text = device.name ?: "Unknown Device"
            return view
        }
    }
}