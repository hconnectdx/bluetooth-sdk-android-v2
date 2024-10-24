package kr.co.hconnect.polihealth_sdk_android_v2_example.permission

import android.Manifest
import android.os.Build
import androidx.annotation.RequiresApi

object Permissions {
    @RequiresApi(Build.VERSION_CODES.S)
    val PERMISSION_SDK_31 = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
    )


    val PERMISSION_SDK_30 = arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
}