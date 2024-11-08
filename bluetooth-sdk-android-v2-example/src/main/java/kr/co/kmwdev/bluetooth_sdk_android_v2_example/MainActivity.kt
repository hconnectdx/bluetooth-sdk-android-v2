package kr.co.kmwdev.bluetooth_sdk_android_v2_example

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kr.co.hconnect.bluetooth_sdk_android_v2.HCBle
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.device_detail.DeviceDetailActivity
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.permission.PermissionManager
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.permission.Permissions

@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity() {

    private lateinit var deviceListAdapter: DeviceListAdapter
    private val deviceList = mutableListOf<BluetoothDevice>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setDeviceList()
        setBtnScan()
    }

    private fun setDeviceList() {
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
    }

    private fun setBtnScan() {
        val btnStartScan: Button = findViewById(R.id.btn_start_scan)
        btnStartScan.setOnClickListener {
            val permissions =
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) Permissions.PERMISSION_SDK_31 else Permissions.PERMISSION_SDK_30
            val isGranted = PermissionManager.isGrantedPermissions(
                context = this@MainActivity,
                permissions = permissions
            )
            if (!isGranted) {
                PermissionManager.launchPermissions(
                    permissions = permissions,
                    resultCallback = { p ->
                        if (p.all { it.value }) {
                            startScan()
                        } else {
                            Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            } else {
                startScan()
                deviceList.addAll(HCBle.getBondedDevices())
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
                    HCBle.scanLeDevice(onScanResult = { scanItem ->
                        if (scanItem.device.name.isNullOrEmpty()) return@scanLeDevice
                        deviceList.find { device -> device.address == scanItem.device.address }
                            ?: run {
                                deviceList.add(scanItem.device)
                                deviceListAdapter.notifyDataSetChanged()
                            }
                    }, onScanStop = {
                        Log.d("MainActivity", "Scan stopped")

                    })
                } else {
                    println("Permission denied: $deniedItems")
                }
            }


        } catch (e: Exception) {
            println("Error: $e")
        }
    }

    private fun stopScan() {
        HCBle.scanStop()
    }

    override fun onStart() {
        super.onStart()

        HCBle.init(this)
        PermissionManager.registerPermissionLauncher(this)

        val bluetoothManager =
            applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter != null && applicationContext.packageManager.hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE
            )
        ) {
            Log.d("Bluetooth", "This device supports Bluetooth Low Energy (BLE)")
        } else {
            Log.d("Bluetooth", "This device does not support BLE")
        }


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