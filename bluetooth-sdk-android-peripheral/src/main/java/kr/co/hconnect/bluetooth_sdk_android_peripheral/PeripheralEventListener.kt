package kr.co.hconnect.bluetooth_sdk_android_peripheral

import android.bluetooth.BluetoothDevice

/**
 * BLE Peripheral 이벤트 리스너.
 *
 * 모든 콜백은 BLE 콜백 스레드에서 호출되므로 UI 작업은 메인 스레드로 전환해야 한다.
 */
interface PeripheralEventListener {

    /** Central 디바이스가 연결되었을 때 */
    fun onDeviceConnected(device: BluetoothDevice) {}

    /** Central 디바이스가 연결 해제되었을 때 */
    fun onDeviceDisconnected(device: BluetoothDevice) {}

    /** Central로부터 데이터를 수신했을 때 (RX Characteristic Write) */
    fun onDataReceived(device: BluetoothDevice, data: ByteArray) {}

    /** 연결 상태가 변경되었을 때 */
    fun onConnectionStateChanged(state: PeripheralConnectionState) {}

    /** 광고 시작에 성공했을 때 */
    fun onAdvertiseStarted() {}

    /** 광고 시작에 실패했을 때 */
    fun onAdvertiseFailed(errorCode: Int) {}

    /** MTU가 변경되었을 때 */
    fun onMtuChanged(mtu: Int) {}

    /**
     * 청크 크기 프로브가 끝나 실효 청크 크기가 결정되었을 때.
     * 프로브 전 후보가 실패한 경우에도 폴백 값(MTU-3)으로 호출된다.
     */
    fun onChunkSizeDetermined(chunkSize: Int) {}

    /** Central이 Notify를 구독/해제했을 때 */
    fun onNotifySubscriptionChanged(device: BluetoothDevice, enabled: Boolean) {}
}
