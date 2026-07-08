# bluetooth-sdk-android-peripheral 분석

## 개요
`bluetooth-sdk-android-peripheral` 모듈은 Android 앱을 BLE Peripheral(GATT Server)로 동작시키는 라이브러리입니다. Central 장치에서 연결을 받아 데이터를 송수신할 수 있도록 Nordic UART Service 기반 구조를 제공합니다.

## 주요 기능
- BLE GATT 서버 구현
- 광고 시작 / 중지
- Central 연결 / 해제 처리
- 데이터 전송 및 수신
- MTU 기반 청크 분할 및 4바이트 길이 헤더 프레이밍
- 상태 변경을 `StateFlow`로 제공
- 이벤트 리스너 콜백 제공
- 자동 재광고 옵션 지원
- Android 12+ `BLUETOOTH_ADVERTISE` 권한 확인

## 패키지 구조
- `kr.co.hconnect.bluetooth_sdk_android_peripheral.PeripheralConfig`
- `kr.co.hconnect.bluetooth_sdk_android_peripheral.HCBlePeripheral`
- `kr.co.hconnect.bluetooth_sdk_android_peripheral.PeripheralEventListener`
- `kr.co.hconnect.bluetooth_sdk_android_peripheral.PeripheralConnectionState`

## 주요 클래스 및 역할

### `PeripheralConfig`
BLE Peripheral 설정을 담는 데이터 클래스입니다.
- 기본 서비스 UUID: `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`
- TX Characteristic UUID: `6E400003-B5A3-F393-E0A9-E50E24DCCA9E`
- RX Characteristic UUID: `6E400002-B5A3-F393-E0A9-E50E24DCCA9E`
- 광고 모드, TX 전력, 디바이스 이름 포함 여부, 자동 재광고 여부
- `CCCD_UUID` 상수 포함 (Notify 구독/해제 처리)

### `HCBlePeripheral`
라이브러리의 핵심 객체입니다. 싱글턴 `object`로 정의되어 있으며 다음 기능을 제공합니다.

#### 초기화
- `init(context: Context, config: PeripheralConfig = PeripheralConfig())`
- `updateConfig(config: PeripheralConfig)`
- `addEventListener(listener: PeripheralEventListener)` / `removeEventListener(...)`

#### 광고 및 서버 관리
- `start()`: GATT 서버를 열고 BLE 광고 시작
- `startAdvertiseOnly()`: GATT 서버가 없으면 열고 광고만 시작
- `stopAdvertiseOnly()`: 광고만 중지
- `stop()`: 광고 중지, 연결 해제, GATT 서버 종료
- `destroy()`: `stop()` 후 리스너 및 브로드캐스트 리시버 해제

#### 연결 및 데이터 송수신
- `sendData(data: ByteArray)`: 4바이트 length header + MTU 청크 분할 후 NOTIFY 전송
- `sendRawData(data: ByteArray)`: 프레이밍 없이 즉시 전송
- `sendText(text: String)`: UTF-8 문자열 전송
- `disconnect()`: 현재 연결된 Central과 연결 해제

#### 상태 정보
- `connectionStateFlow: StateFlow<PeripheralConnectionState>`
- `isConnected`, `isAdvertising`, `currentDevice`, `negotiatedMtu`
- `getDebugInfo()`: 디버깅용 현재 상태 문자열 반환

#### 내부 로직
- `openGattServer()`: TX/RX Characteristic 구성 및 서비스 등록
- `startAdvertising()`: `BluetoothLeAdvertiser`를 사용해 광고 시작
- `stopAdvertising()`: 광고 중지
- `prependLengthHeader(data)`: 4바이트 big-endian 길이 헤더 추가
- `ByteArray.toChunks(chunkSize)`: 데이터를 MTU 단위로 분할
- `BluetoothAdapter.ACTION_STATE_CHANGED` 브로드캐스트 수신기로 블루투스 꺼짐 처리

### `PeripheralEventListener`
BLE 이벤트를 앱으로 전달하는 콜백 인터페이스입니다.
- `onDeviceConnected(device)`
- `onDeviceDisconnected(device)`
- `onDataReceived(device, data)`
- `onConnectionStateChanged(state)`
- `onAdvertiseStarted()` / `onAdvertiseFailed(errorCode)`
- `onMtuChanged(mtu)`
- `onNotifySubscriptionChanged(device, enabled)`

### `PeripheralConnectionState`
연결 상태 열거형입니다.
- `IDLE`
- `ADVERTISING`
- `CONNECTED`
- `DISCONNECTED`

## AndroidManifest 권한
- `android.permission.BLUETOOTH` (`maxSdkVersion=