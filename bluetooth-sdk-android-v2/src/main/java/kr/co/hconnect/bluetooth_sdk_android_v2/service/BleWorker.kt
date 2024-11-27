//package kr.co.hconnect.bluetooth_sdk_android_v2.service
//
//import android.content.Context
//import android.util.Log
//import androidx.work.Worker
//import androidx.work.WorkerParameters
//
//class BleWorker(val context: Context, workerParams: WorkerParameters) :
//    Worker(context, workerParams) {
//    override fun doWork(): Result {
//        performTask()
//        return Result.success()
//    }
//
//    private fun performTask() {
//        for (i in 0..10) {
//            try {
//                Thread.sleep(1000)
//            } catch (e: InterruptedException) {
//                e.printStackTrace()
//            }
//            Log.d("StartedService", "Waiting... $i")
//        }
//    }
//}