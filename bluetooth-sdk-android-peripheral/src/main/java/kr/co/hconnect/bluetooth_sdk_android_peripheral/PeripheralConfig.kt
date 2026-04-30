package kr.co.hconnect.bluetooth_sdk_android_peripheral

import android.bluetooth.le.AdvertiseSettings
import java.util.UUID

/**
 * BLE Peripheral 설정.
 *
 * @param serviceUUID          GATT 서비스 UUID
 * @param txCharUUID           Peripheral → Central (NOTIFY) 캐릭터리스틱 UUID
 * @param rxCharUUID           Central → Peripheral (WRITE) 캐릭터리스틱 UUID
 * @param advertiseMode        광고 모드 (기본: LOW_LATENCY)
 * @param txPowerLevel         광고 송신 파워 (기본: HIGH)
 * @param includeDeviceName    광고에 디바이스 이름 포함 여부
 * @param autoRestartAdvertise 연결 해제 시 자동 재광고 여부
 */
data class PeripheralConfig(
    val serviceUUID: UUID = DEFAULT_SERVICE_UUID,
    val txCharUUID: UUID = DEFAULT_TX_CHAR_UUID,
    val rxCharUUID: UUID = DEFAULT_RX_CHAR_UUID,
    val advertiseMode: Int = AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY,
    val txPowerLevel: Int = AdvertiseSettings.ADVERTISE_TX_POWER_HIGH,
    val includeDeviceName: Boolean = false,
    val autoRestartAdvertise: Boolean = true
) {
    companion object {
        // Nordic UART Service (NUS) UUIDs
        val DEFAULT_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val DEFAULT_TX_CHAR_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
        val DEFAULT_RX_CHAR_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
    }
}
