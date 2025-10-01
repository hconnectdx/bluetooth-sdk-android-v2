package kr.co.kmwdev.bluetooth_sdk_android_v2_example.ui

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.ScanResult
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kr.co.hconnect.bluetooth_sdk_android_v2.HCBle
import kr.co.hconnect.bluetooth_sdk_android_v2.util.Logger
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.R
import java.nio.charset.StandardCharsets

class BloodActivity : AppCompatActivity() {

    val SERVICE_UUID_BLOOD_PRESSURE = "00001810-0000-1000-8000-00805f9b34fb"
    val CHARACTERISTIC_UUID_BLOOD_PRESSURE = "00002a35-0000-1000-8000-00805f9b34fb"

    val SERVICE_UUID_HEMODIALYSIS = "12634d89-d598-4874-8e86-7d042ee07ba7"
    val CHARACTERISTIC_UUID_HEMODIALYSIS = "4116f8d2-9f66-4f58-a53d-fc7440e7c14e"

    private var repeatJob: Job? = null // 단일 작업을 관리하는 변수
    private var hemoAddress = ""
    private val dummy = """
    {
        "ptNo":"12345678",
        "treatmentSetTime":"20250520093115",
        "ufGoal":1.5,
        "conNa":20,
        "conBic":200,
        "bloodFlowRate":200,
        "heparinMode":"AUTO",
        "heparinSetTime":"120",
        "heparinBolus":11,
        "heparinInfusionRate":11
    }
""".trimIndent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blood)

        HCBle.init(context = this)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        HCBle.init(this)

        setBloodPressureListener()
        setHemodialysusListener()

    }

    private fun setHemodialysusListener() {
        findViewById<Button>(R.id.btnSendPrescription).setOnClickListener {


            val scanId = "투석기"
            HCBle.scanLeDevice(
                scanId = scanId,
                scanPeriod = 300_000,
                onScanStop = {

                }, onScanResult = { result ->
                    if (!result.device.name.isNullOrEmpty()) {
                        Log.d("TAG", result.device.name)
                    }

                    val device = result.device
                    if (!device.name.isNullOrEmpty() && device.name.contains("DESKTOP-")) {

                        HCBle.connectToDevice(
                            sessionId = scanId,
                            device = device,
                            onWriteCharacteristic = { state, char ->
                                Logger.d("state: ${state} / char: ${char?.value.toString()}")

                            },
                            onGattServiceState = { state, serviceList ->

                                hemoAddress = device.address
                                serviceList.forEach { service ->
                                    if (service.uuid.toString() == SERVICE_UUID_HEMODIALYSIS) {
                                        val deviceAddress = device.address
                                        val serviceUUID = service.uuid
                                        val characteristics = service.characteristics
                                        characteristics.forEach { char ->
                                            if (char.uuid.toString() == CHARACTERISTIC_UUID_HEMODIALYSIS) {
                                                HCBle.setTargetServiceUUID(
                                                    deviceAddress = deviceAddress,
                                                    uuid = serviceUUID.toString()
                                                )

                                                HCBle.setTargetWriteCharacteristicUUID(
                                                    deviceAddress = deviceAddress,
                                                    characteristicUUID = char.uuid.toString()
                                                )

                                                HCBle.setTargetReadCharacteristicUUID(
                                                    deviceAddress = deviceAddress,
                                                    characteristicUUID = char.uuid.toString()
                                                )

                                                HCBle.setCharacteristicNotification(
                                                    deviceAddress = deviceAddress,
                                                    isEnable = true
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            onReceive = { characteristic ->
                                Log.d("asdasd", characteristic.value.toString())
                            }
                        )
                    }
                })
        }


        findViewById<Button>(R.id.btnStartHemoDialysisTick).setOnClickListener {


            if (hemoAddress.isEmpty()) {
                Toast.makeText(this, "투석기에 연결해주세요.", Toast.LENGTH_LONG).show()
            }

            HCBle.writeCharacteristic(hemoAddress, dummy.toByteArray())
            Toast.makeText(this, "데이터 전송 완료.", Toast.LENGTH_SHORT).show()

//            HCBle.writeCharacteristic(hemoAddress, "aa".toByteArray())

//            Log.d("BloodActivity", "혈액투석 측정 프로세스를 시작합니다")
//            val scanId = "투석기"
//            HCBle.scanLeDevice(
//                scanId = scanId,
//                scanPeriod = 300_000,
//                onScanStop = {
//
//                }, onScanResult = { result ->
//                    if (!result.device.name.isNullOrEmpty()) {
//                        Log.d("TAG", result.device.name)
//                    }
//
//                    val device = result.device
//
//                    if (!device.name.isNullOrEmpty() && device.name.contains("DESKTOP-")) {
//                        hemoAddress = device.address
//                        HCBle.connectToDevice(
//                            sessionId = scanId,
//                            device = device,
//                            onWriteCharacteristic = { state, char ->
//                                Logger.d("state: ${state} / char: ${char?.value.toString()}")
//
//                            },
//                            onGattServiceState = { state, serviceList ->
//
//                                serviceList.forEach { service ->
//                                    if (service.uuid.toString() == SERVICE_UUID_HEMODIALYSIS) {
//                                        val deviceAddress = device.address
//                                        val serviceUUID = service.uuid
//                                        val characteristics = service.characteristics
//                                        characteristics.forEach { char ->
//                                            if (char.uuid.toString() == CHARACTERISTIC_UUID_HEMODIALYSIS) {
//                                                HCBle.setTargetServiceUUID(
//                                                    deviceAddress = deviceAddress,
//                                                    uuid = serviceUUID.toString()
//                                                )
//
//                                                HCBle.setTargetWriteCharacteristicUUID(
//                                                    deviceAddress = deviceAddress,
//                                                    characteristicUUID = char.uuid.toString()
//                                                )
//
//                                                HCBle.setTargetReadCharacteristicUUID(
//                                                    deviceAddress = deviceAddress,
//                                                    characteristicUUID = char.uuid.toString()
//                                                )
//
//                                                HCBle.setCharacteristicNotification(
//                                                    deviceAddress = deviceAddress,
//                                                    isEnable = true
//                                                )
//                                            }
//                                        }
//                                    }
//                                }
//
//                                // 이미 실행 중인 작업이 있다면 취소
//                                repeatJob?.cancel()
//                                // 새로운 작업 시작
//                                repeatJob = CoroutineScope(Dispatchers.IO).launch {
//
//                                    while (isActive) { // 현재 작업이 활성 상태일 동안 실행
//                                        HCBle.getGattController(device.address)
//                                            ?.let { gattController ->
//
//                                                val data = "JSON_GET"
//                                                val base64Data = Base64.encodeToString(
//                                                    data.toByteArray(StandardCharsets.UTF_8),
//                                                    Base64.NO_WRAP
//                                                ).toByteArray()
//                                                gattController.writeCharacteristic(base64Data)
//                                            }
//                                        delay(1_000L) // 1초마다 실행
//                                    }
//                                }
//                            },
//                            onReceive = { characteristic ->
////                            Log.d("asdasd", characteristic.value.toString())
//                            }
//                        )
//                    }
//
//                })
        }
    }

    private fun setBloodPressureListener() {
        findViewById<Button>(R.id.btnStartBloodPressure).setOnClickListener {
            Log.d("BloodActivity", "혈압 측정 프로세스를 시작합니다")
            val scanId = "혈압계"
            HCBle.scanLeDevice(
                scanId = scanId,
                scanPeriod = 300_000,
                onScanStop = {

                },
                onScanResult = { result ->
                    val device = result.device

                    if (!device.name.isNullOrEmpty() && device.name.contains("A&D")) {
                        HCBle.connectToDevice(
                            sessionId = scanId,
                            onConnState = {
                                HCBle.stopScanSession(scanId)
                            },
                            isPrintReceiveLog = false,
                            device = device,
                            onGattServiceState = { state, serviceList ->

                                serviceList.forEach { service ->
                                    if (service.uuid.toString() == SERVICE_UUID_BLOOD_PRESSURE) {
                                        val deviceAddress = device.address
                                        val serviceUUID = service.uuid
                                        val characteristics = service.characteristics
                                        characteristics.forEach { char ->
                                            if (char.uuid.toString() == CHARACTERISTIC_UUID_BLOOD_PRESSURE) {
                                                HCBle.setTargetServiceUUID(
                                                    deviceAddress = deviceAddress,
                                                    uuid = serviceUUID.toString()
                                                )

                                                HCBle.setTargetReadCharacteristicUUID(
                                                    deviceAddress = deviceAddress,
                                                    characteristicUUID = char.uuid.toString()
                                                )

                                                HCBle.setCharacteristicNotification(
                                                    deviceAddress = deviceAddress,
                                                    isEnable = true,
                                                    isIndicate = true
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            onReceive = { characteristic ->
                                var systolic = 0
                                var diastolic = 0
                                var pulseRate = 0

                                val offset = 0
                                val flag = characteristic.getIntValue(
                                    BluetoothGattCharacteristic.FORMAT_UINT8,
                                    offset
                                )
                                val flagString = Integer.toBinaryString(flag)

                                val bloodPressureUnitFlag = (flag and 0x01) == 0x01
                                val timeStampFlag = (flag and 0x02) == 0x02
                                val pulseRateFlag = (flag and 0x04) == 0x04

                                var _offset = 1

                                if (bloodPressureUnitFlag) {
                                    _offset += 6
                                } else {
                                    // mmHg
                                    systolic = characteristic.getIntValue(
                                        BluetoothGattCharacteristic.FORMAT_UINT16,
                                        _offset
                                    )
                                    diastolic = characteristic.getIntValue(
                                        BluetoothGattCharacteristic.FORMAT_UINT16,
                                        _offset + 2
                                    )
                                    _offset += 6
                                }

                                if (timeStampFlag) {
                                    _offset += 7
                                }

                                if (pulseRateFlag) {
                                    pulseRate = characteristic.getIntValue(
                                        BluetoothGattCharacteristic.FORMAT_UINT16,
                                        _offset
                                    )
                                    _offset += 2
                                }
                                Log.d(
                                    "BloodActivity",
                                    "flag $flagString $bloodPressureUnitFlag  $timeStampFlag  $pulseRateFlag"
                                )
                                Log.d("BloodActivity", "systolic $systolic")
                                Log.d("BloodActivity", "diastolic $diastolic")
                                Log.d("BloodActivity", "pulseRate : $pulseRate")
                            }
                        )
                    }

                })
        }

    }
}


