package kr.co.kmwdev.bluetooth_sdk_android_v2_example.util

import android.content.Context
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.MyApplication

object MySharedPref {
    private const val BLE_DB = "ble"
    private const val BLE_CONNECTED_ADDRESSES = "ble_connected_addresses"

    fun saveBLEAddress(address: String) {
        val sharedPref = MyApplication.getAppContext()
            .getSharedPreferences(BLE_DB, Context.MODE_PRIVATE)

        val addresses =
            sharedPref.getStringSet(BLE_CONNECTED_ADDRESSES, mutableSetOf())?.toMutableSet()
                ?: mutableSetOf()
        addresses.add(address)

        with(sharedPref.edit()) {
            putStringSet(BLE_CONNECTED_ADDRESSES, addresses)
            apply()
        }
    }

    fun getBLEAddresses(): Set<String> {
        val sharedPref = MyApplication.getAppContext()
            .getSharedPreferences(BLE_DB, Context.MODE_PRIVATE)

        return sharedPref.getStringSet(BLE_CONNECTED_ADDRESSES, emptySet()) ?: emptySet()
    }

    fun removeBLEAddress(address: String) {
        val sharedPref = MyApplication.getAppContext()
            .getSharedPreferences(BLE_DB, Context.MODE_PRIVATE)

        val addresses =
            sharedPref.getStringSet(BLE_CONNECTED_ADDRESSES, mutableSetOf())?.toMutableSet()
                ?: mutableSetOf()
        addresses.remove(address)

        with(sharedPref.edit()) {
            putStringSet(BLE_CONNECTED_ADDRESSES, addresses)
            apply()
        }
    }
}