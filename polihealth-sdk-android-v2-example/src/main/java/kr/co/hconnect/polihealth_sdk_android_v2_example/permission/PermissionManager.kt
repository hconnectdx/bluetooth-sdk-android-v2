package kr.co.hconnect.polihealth_sdk_android_v2_example.permission

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

object PermissionManager {
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
                        Log.d("PermissionManager", "Permission Granted: ${p.key}")
                        permissionMap.plus(Pair(p.key, true))
                    } else {
                        Log.e("PermissionManager", "Permission Denied: ${p.key}")
                    }
                }
                resultCallback(permissions)
            }
    }

    fun launchPermissions(
        permissions: Array<String>,
        resultCallback: (permissions: Map<String, Boolean>) -> Unit
    ) {
        PermissionManager.resultCallback = resultCallback
        permissionLauncher.launch(permissions)
    }

    fun isGrantedPermissions(context: Context, permissions: Array<String>): Boolean {
        return permissions.all { permission ->
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }
    }
}