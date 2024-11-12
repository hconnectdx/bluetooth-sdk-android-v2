package kr.co.kmwdev.bluetooth_sdk_android_v2_example

import android.app.Application
import android.content.Context

class MyApplication : Application() {

    companion object {
        private lateinit var instance: MyApplication

        fun getAppContext(): Context {
            return instance.applicationContext
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 여기서 전역으로 필요한 리소스를 초기화할 수 있습니다.
    }
}
