
# PoliHealth SDK for Android

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

## 목차
- [PoliHealth SDK](#polihealth-sdk)
  - [주요 기능](#주요-기능)
  - [설치](#설치)
  - [초기화](#초기화)
  - [스캔 사용 예시](#스캔-사용)
  - [연결하기 사용 예시](#연결-및-통신)


## PoliHealth SDK

### 주요 기능
- **데이터 수집:** Poli 밴드를 통해 데이터를 수집합니다.
- **데이터 전송:** 수집된 데이터를 서버로 전송합니다.

### 설치
```groovy

repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/hconnectdx/bluetooth-sdk-android-v2")
            credentials {
                username = "hconnectdx"
                password = "****" // 이메일로 문의를 주시면 토큰을 발급해드립니다.
            
            }
        }
    }

dependencies {
    implementation 'com.polihealth:sdk:0.0.5'
}
```

### 초기화
```kotlin
override fun onStart() {
    super.onStart()

    PoliBLE.init(this)
    PoliClient.init(
        baseUrl = BuildConfig.API_URL,
        clientId = BuildConfig.CLIENT_ID,
        clientSecret = BuildConfig.CLIENT_SECRET
    )
    PermissionManager.registerPermissionLauncher(this)
    PoliClient.userAge = 111
    PoliClient.userSno = 999
}
```

### 스캔 사용
```kotlin
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
```

### 연결 및 통신
```kotlin
private fun connectToDevice(device: BluetoothDevice) {
    PoliBLE.connectDevice(
        this,
        device,
        onReceive = { type: ProtocolType, response: PoliResponse? ->
            Log.d("DeviceDetailActivity", "onReceive: $type, $response")
        },
        onConnState = { state ->
         
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
          
        },
        onSubscriptionState = { state ->
           
        }
    )
}
```

## 문의
문의 사항이 있으시면 [이메일](kmwdev@hconnect.co.kr)로 연락해 주세요.
