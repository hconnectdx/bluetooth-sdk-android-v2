package kr.co.kmwdev.bluetooth_sdk_android_v2_example.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

object MyPermission {

    val PERMISSION_BLUETOOTH: Array<String>
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            } else {
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            }
        }

    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var resultCallback: (permissions: Map<String, Boolean>) -> Unit

    fun registerPermissionLauncher(
        activity: ComponentActivity
    ) {
        permissionLauncher =
            activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                val permissionMap = mapOf<String, Boolean>()
                permissions.forEach { p ->
                    if (p.value) {
                        Log.d("EasyPermission", "Permission Granted: ${p.key}")
                        permissionMap.plus(Pair(p.key, true))
                    } else {
                        Log.e("EasyPermission", "Permission Denied: ${p.key}")
                    }
                }
                resultCallback(permissions)
            }
    }

    fun launchPermissions(
        permissions: Array<String>,
        resultCallback: (permissions: Map<String, Boolean>) -> Unit
    ) {
        MyPermission.resultCallback = resultCallback
        permissionLauncher.launch(permissions)
    }

    fun isGrantedPermissions(context: Context, permissions: Array<String>): Boolean {
        return permissions.all { permission ->
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }
    }
}