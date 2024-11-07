package kr.co.hconnect.bluetooth_sdk_android_v2.gatt

object GATTState {
    const val DISCONNECTED = 0
    const val CONNECTED = 1
    const val CONNECTING = 2

    fun getStateDescription(state: Int): String {
        return when (state) {
            DISCONNECTED -> "DISCONNECTED"
            CONNECTED -> "CONNECTED"
            CONNECTING -> "CONNECTING"
            else -> "UNKNOWN_STATE"
        }
    }
}