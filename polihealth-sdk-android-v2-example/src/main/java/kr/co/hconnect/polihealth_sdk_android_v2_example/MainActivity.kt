package kr.co.hconnect.polihealth_sdk_android_v2_example

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.co.hconnect.polihealth_sdk_android.PoliBLE
import kr.co.hconnect.polihealth_sdk_android.PoliClient
import kr.co.hconnect.polihealth_sdk_android.api.daily.DailyProtocol01API
import kr.co.hconnect.polihealth_sdk_android.api.dto.request.HRSpO2
import kr.co.hconnect.polihealth_sdk_android.service.sleep.SleepApiService
import kr.co.hconnect.polihealth_sdk_android_app.service.sleep.DailyApiService

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnTest1: Button = findViewById(R.id.btn_test1)
        btnTest1.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    DailyProtocol01API.apply {
                        val response = DailyApiService().sendProtocol01(parseLTMData(data = testRawData))
                        Log.d("MainActivity", "response: $response")
                    }

                } catch (e: Exception) {
                    Log.e("MainActivity", "error: $e")
                }
            }
        }

        val btnTest2: Button = findViewById(R.id.btn_test2)
        btnTest2.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    DailyProtocol01API.apply {
                        val response = DailyApiService().sendProtocol02()
                        Log.d("MainActivity", "response: $response")
                    }

                } catch (e: Exception) {
                    Log.e("MainActivity", "error: $e")
                }
            }
        }

        val btnTest3: Button = findViewById(R.id.btn_test3)
        btnTest3.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    DailyProtocol01API.apply {
                        val response = DailyApiService().sendProtocol03(HRSpO2(90, 117))
                        Log.d("MainActivity", "response: $response")
                    }

                } catch (e: Exception) {
                    Log.e("MainActivity", "error: $e")
                }
            }
        }

        val btnTest4: Button = findViewById(R.id.btn_test4)
        btnTest4.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = SleepApiService().sendStartSleep()
                    Log.d("MainActivity", "response: $response")
                } catch (e: Exception) {
                    Log.e("MainActivity", "error: $e")
                }
            }
        }
        val btnTest5: Button = findViewById(R.id.btn_test5)
        btnTest5.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = SleepApiService().sendEndSleep()
                    Log.d("MainActivity", "response: $response")
                } catch (e: Exception) {
                    Log.e("MainActivity", "error: $e")
                }
            }
        }
        val btnTest6: Button = findViewById(R.id.btn_test6)
        btnTest6.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = SleepApiService().sendProtocol06(this@MainActivity)
                    Log.d("MainActivity", "response: $response")
                } catch (e: Exception) {
                    Log.e("MainActivity", "error: $e")
                }
            }
        }
        val btnTest7: Button = findViewById(R.id.btn_test7)
        btnTest7.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = SleepApiService().sendProtocol07(this@MainActivity)
                    Log.d("MainActivity", "response: $response")
                } catch (e: Exception) {
                    Log.e("MainActivity", "error: $e")
                }
            }
        }
        val btnTest8: Button = findViewById(R.id.btn_test8)
        btnTest8.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = SleepApiService().sendProtocol08(this@MainActivity)
                    Log.d("MainActivity", "response: $response")
                } catch (e: Exception) {
                    Log.e("MainActivity", "error: $e")
                }
            }
        }
        val btnTest9: Button = findViewById(R.id.btn_test9)
        btnTest9.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = SleepApiService().sendProtocol09(HRSpO2(100, 121))
                    Log.d("MainActivity", "response: $response")
                } catch (e: Exception) {
                    Log.e("MainActivity", "error: $e")
                }
            }
        }


    }

    override fun onStart() {
        super.onStart()

        PoliBLE.init(this)
        PoliClient.init(
            baseUrl = BuildConfig.API_URL,
            clientId = BuildConfig.CLIENT_ID,
            clientSecret = BuildConfig.CLIENT_SECRET
        )
    }
}