package kr.co.hconnect.bluetooth_sdk_android_v2.gatt

object GATTState {
    const val DISCONNECTED = 0
    const val CONNECTED = 1
    const val CONNECTING = 2

    /** A GATT operation completed successfully  */
    const val GATT_SUCCESS: Int = 0

    /** GATT read operation is not permitted  */
    const val GATT_READ_NOT_PERMITTED: Int = 0x2

    /** GATT write operation is not permitted  */
    const val GATT_WRITE_NOT_PERMITTED: Int = 0x3

    /** Insufficient authentication for a given operation  */
    const val GATT_INSUFFICIENT_AUTHENTICATION: Int = 0x5

    /** The given request is not supported  */
    const val GATT_REQUEST_NOT_SUPPORTED: Int = 0x6

    /** Insufficient encryption for a given operation  */
    const val GATT_INSUFFICIENT_ENCRYPTION: Int = 0xf

    /** A read or write operation was requested with an invalid offset  */
    const val GATT_INVALID_OFFSET: Int = 0x7

    /** Insufficient authorization for a given operation  */
    const val GATT_INSUFFICIENT_AUTHORIZATION: Int = 0x8

    /** A write operation exceeds the maximum length of the attribute  */
    const val GATT_INVALID_ATTRIBUTE_LENGTH: Int = 0xd

    /** A remote device connection is congested.  */
    const val GATT_CONNECTION_CONGESTED: Int = 0x8f

    /** A GATT operation failed, errors other than the above  */
    const val GATT_FAILURE: Int = 0x101

    /**
     * Get a description for the connection state.
     */
    fun getStateDescription(state: Int): String {
        return when (state) {
            DISCONNECTED -> "DISCONNECTED"
            CONNECTED -> "CONNECTED"
            CONNECTING -> "CONNECTING"
            else -> "UNKNOWN_STATE"
        }
    }

    /**
     * Get a description for the GATT error code.
     */
    fun getStatusDescription(status: Int): String {
        return when (status) {
            GATT_SUCCESS -> "GATT_SUCCESS"
            GATT_READ_NOT_PERMITTED -> "GATT_READ_NOT_PERMITTED"
            GATT_WRITE_NOT_PERMITTED -> "GATT_WRITE_NOT_PERMITTED"
            GATT_INSUFFICIENT_AUTHENTICATION -> "GATT_INSUFFICIENT_AUTHENTICATION"
            GATT_REQUEST_NOT_SUPPORTED -> "GATT_REQUEST_NOT_SUPPORTED"
            GATT_INSUFFICIENT_ENCRYPTION -> "GATT_INSUFFICIENT_ENCRYPTION"
            GATT_INVALID_OFFSET -> "GATT_INVALID_OFFSET"
            GATT_INSUFFICIENT_AUTHORIZATION -> "GATT_INSUFFICIENT_AUTHORIZATION"
            GATT_INVALID_ATTRIBUTE_LENGTH -> "GATT_INVALID_ATTRIBUTE_LENGTH"
            GATT_CONNECTION_CONGESTED -> "GATT_CONNECTION_CONGESTED"
            GATT_FAILURE -> "GATT_FAILURE"
            else -> "UNKNOWN_ERROR_CODE"
        }
    }
}
