package kr.co.hconnect.bluetooth_sdk_android_v2.util

import android.util.Log

object Logger {

    private const val TAG = "BLE_SDK"

    // 디버그 로그
    fun d(message: String) {
        Log.d(TAG, message)
    }

    // 에러 로그
    fun e(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, message, throwable)
        } else {
            Log.e(TAG, message)
        }
    }

    // 정보 로그
    fun i(message: String) {
        Log.i(TAG, message)
    }

    // 경고 로그
    fun w(message: String) {
        Log.w(TAG, message)
    }

    // 자세한 디버그 정보 로그
    fun v(message: String) {
        Log.v(TAG, message)
    }
}
