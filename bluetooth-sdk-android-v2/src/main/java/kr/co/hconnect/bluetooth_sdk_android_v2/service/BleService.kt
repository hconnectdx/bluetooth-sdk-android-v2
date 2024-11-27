//package kr.co.hconnect.bluetooth_sdk_android_v2.service
//
//import android.app.Service
//import android.content.Intent
//import android.os.IBinder
//import android.util.Log
//import androidx.work.OneTimeWorkRequest
//import androidx.work.WorkManager
//
//class BleService : Service() {
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//
//        WorkManager.getInstance(this).enqueue(OneTimeWorkRequest.from(BleWorker::class.java))
//        return START_STICKY
//    }
//
//    override fun onBind(intent: Intent?): IBinder? {
//        return null
//    }
//
//
//}