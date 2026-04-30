package kr.co.hconnect.bluetooth_sdk_android_peripheral_example.presentation

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.co.hconnect.bluetooth_sdk_android_peripheral.HCBlePeripheral
import kr.co.hconnect.bluetooth_sdk_android_peripheral.PeripheralConnectionState
import kr.co.hconnect.bluetooth_sdk_android_peripheral.PeripheralEventListener
import kr.co.hconnect.bluetooth_sdk_android_peripheral_example.presentation.theme.PeripheralExampleTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            PeripheralExampleTheme {
                AppContent()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

@Composable
private fun AppContent() {
    var permissionGranted by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val requiredPermissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
        }.toTypedArray()
    }

    val allAlreadyGranted = remember {
        requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    if (allAlreadyGranted) {
        LaunchedEffect(Unit) { permissionGranted = true }
    }

    val connectionState by HCBlePeripheral.connectionStateFlow.collectAsState()

    if (permissionGranted) {
        LaunchedEffect(Unit) {
            HCBlePeripheral.start()
        }

        when (connectionState) {
            PeripheralConnectionState.CONNECTED -> ConnectedScreen()
            else -> WaitingScreen(connectionState)
        }
    } else {
        PermissionScreen(
            permissions = requiredPermissions,
            onGranted = { permissionGranted = true },
            onDenied = {
                Toast.makeText(context, "블루투스 권한이 필요합니다.", Toast.LENGTH_LONG).show()
            }
        )
    }
}

@Composable
private fun PermissionScreen(
    permissions: Array<String>,
    onGranted: () -> Unit,
    onDenied: () -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val essentialGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ).all { results[it] == true }
        } else {
            true
        }

        if (essentialGranted) onGranted() else onDenied()
    }

    LaunchedEffect(Unit) {
        if (permissions.isNotEmpty()) {
            launcher.launch(permissions)
        } else {
            onGranted()
        }
    }

    Scaffold(
        timeText = { TimeText() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "권한 요청 중...\n권한 요청을 허용해주세요.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.onBackground,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun WaitingScreen(connectionState: PeripheralConnectionState) {
    Scaffold(
        timeText = { TimeText() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            androidx.wear.compose.material.ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                item {
                    Text(
                        text = "BLE Peripheral",
                        fontSize = 18.sp,
                        color = MaterialTheme.colors.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    val statusText = when (connectionState) {
                        PeripheralConnectionState.ADVERTISING -> "폰 연결 대기 중..."
                        PeripheralConnectionState.DISCONNECTED -> "연결 끊김 — 재연결 대기 중..."
                        PeripheralConnectionState.IDLE -> "BLE 준비 중..."
                        else -> ""
                    }

                    val statusColor = when (connectionState) {
                        PeripheralConnectionState.ADVERTISING -> Color(0xFF6495ED)
                        PeripheralConnectionState.DISCONNECTED -> Color(0xFFFF5722)
                        else -> MaterialTheme.colors.secondary
                    }

                    Text(
                        text = statusText,
                        fontSize = 14.sp,
                        color = statusColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    Button(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(0.65f),
                        enabled = false,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF6495ED),
                            contentColor = Color.White
                        )
                    ) {
                        Text(text = "대기 중...", fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectedScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var lastReceivedData by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        HCBlePeripheral.addEventListener(object : PeripheralEventListener {
            override fun onDataReceived(device: BluetoothDevice, data: ByteArray) {
                lastReceivedData = data.toString(Charsets.UTF_8).trim()
            }
        })
    }

    Scaffold(
        timeText = { TimeText() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            androidx.wear.compose.material.ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                item {
                    Text(
                        text = "BLE Peripheral",
                        fontSize = 18.sp,
                        color = MaterialTheme.colors.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    Text(
                        text = "폰과 연결됨",
                        fontSize = 14.sp,
                        color = Color(0xFF4CAF50),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    Button(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val ok = HCBlePeripheral.sendText("Hello from Watch!")
                                val msg = if (ok) "전송 성공" else "전송 실패"
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(0.65f),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF6495ED),
                            contentColor = Color.White
                        )
                    ) {
                        Text(text = "테스트 전송", fontSize = 15.sp)
                    }
                }

                lastReceivedData?.let { data ->
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "수신: $data",
                            fontSize = 12.sp,
                            color = Color(0xFF4CAF50),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    val deviceAddress = HCBlePeripheral.currentDevice?.address
                    if (deviceAddress != null) {
                        Text(
                            text = deviceAddress,
                            fontSize = 11.sp,
                            color = MaterialTheme.colors.secondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
