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
 * @param probeChunkLadder     엔드투엔드 청크 프로브 후보 크기 (내림차순).
 *                             빈 리스트면 프로브 비활성 (MTU-3 청크만 사용).
 *                             Central SDK가 PROBE 가로챔/PROBE_ACK 회신을 구현해야 동작한다.
 * @param probeAckTimeoutMs    프로브 1회당 PROBE_ACK 대기 시간 (ms)
 * @param txQueueCapacity      [HCBlePeripheral.sendDataAsync] 내부 송신 큐 상한.
 *                             초과분은 enqueue 거부(드롭)되고 false가 반환된다.
 * @param crcFraming           true면 프레임을 4바이트 길이 헤더 대신
 *                             `[0xA5 0x5A][길이 4B BE][CRC32 4B BE]` + payload 로 감싼다.
 *                             수신(Central) 재조립기가 같은 포맷의 재동기화를 지원할 때만 켤 것.
 */
data class PeripheralConfig(
    val serviceUUID: UUID = DEFAULT_SERVICE_UUID,
    val txCharUUID: UUID = DEFAULT_TX_CHAR_UUID,
    val rxCharUUID: UUID = DEFAULT_RX_CHAR_UUID,
    val advertiseMode: Int = AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY,
    val txPowerLevel: Int = AdvertiseSettings.ADVERTISE_TX_POWER_HIGH,
    val includeDeviceName: Boolean = false,
    val autoRestartAdvertise: Boolean = true,
    val probeChunkLadder: List<Int> = listOf(253, 128, 64),
    val probeAckTimeoutMs: Long = 700,
    val txQueueCapacity: Int = 32,
    val crcFraming: Boolean = false
) {
    companion object {
        // Nordic UART Service (NUS) UUIDs
        val DEFAULT_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val DEFAULT_TX_CHAR_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
        val DEFAULT_RX_CHAR_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
    }
}
