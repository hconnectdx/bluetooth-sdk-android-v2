package kr.co.hconnect.bluetooth_sdk_android_v2.service

import android.content.Context
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager

object BleServiceInterface {
    fun startWorker(context: Context) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequest.from(BleWorker::class.java))
    }
}