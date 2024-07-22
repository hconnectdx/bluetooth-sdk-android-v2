package kr.co.hconnect.polihealth_sdk_android_v2_example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.co.hconnect.polihealth_sdk_android.PoliClient
import kr.co.hconnect.polihealth_sdk_android.service.sleep.SleepApiService

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onStart() {
        super.onStart()



        CoroutineScope(Dispatchers.IO).launch {
            PoliClient.init( "\"https://mapi-stg.health-on.co.kr\"", "\"3270e7da-55b1-4dd4-abb9-5c71295b849b\"",
                "\"eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJpbmZyYSI6IkhlYWx0aE9uLVN0YWdpbmciLCJjbGllbnQtaWQiOiIzMjcwZTdkYS01NWIxLTRkZDQtYWJiOS01YzcxMjk1Yjg0OWIifQ.u0rBK-2t3l4RZ113EzudZsKb0Us9PEtiPcFDBv--gYdJf9yZJQOpo41XqzbgSdDa6Z1VDrgZXiOkIZOTeeaEYA\""
            )

            withContext(Dispatchers.IO) {
                SleepApiService().sendStartSleep()
            }
        }
    }
}