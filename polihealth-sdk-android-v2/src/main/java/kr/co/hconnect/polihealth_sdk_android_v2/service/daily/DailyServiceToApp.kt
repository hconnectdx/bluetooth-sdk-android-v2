package kr.co.hconnect.polihealth_sdk_android_v2.service.daily

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kr.co.hconnect.polihealth_sdk_android.HRSpO2Parser
import kr.co.hconnect.polihealth_sdk_android.ProtocolType
import kr.co.hconnect.polihealth_sdk_android.api.daily.DailyProtocol01API
import kr.co.hconnect.polihealth_sdk_android.api.dto.response.PoliResponse
import kr.co.hconnect.polihealth_sdk_android_app.service.sleep.DailyApiService
import kr.co.hconnect.polihealth_sdk_android_v2.PoliBLE
import kr.co.hconnect.polihealth_sdk_android_v2.api.daily.DailyProtocol02API
import kr.co.hconnect.polihealth_sdk_android_v2.api.daily.model.HRSpO2
import kr.co.hconnect.polihealth_sdk_android_v2.api.dto.response.Daily1Response

object DailyServiceToApp {

    const val TAG = "DailyServiceToApp.kt"
    suspend fun sendProtocol03ToApp(
        byteArray: ByteArray,
        onReceive: (type: ProtocolType, response: PoliResponse?) -> Unit
    ) {
        val hrSpO2: HRSpO2 =
            HRSpO2Parser.asciiToHRSpO2(PoliBLE.removeFrontTwoBytes(byteArray, 1))
        try {
            val response = DailyApiService().sendProtocol03(hrSpO2)
            onReceive.invoke(
                ProtocolType.PROTOCOL_3_HR_SpO2,
                response
            )
        } catch (e: Exception) {
            Log.e(TAG, "sendProtocol03: ${e.message}")
            onReceive.invoke(ProtocolType.PROTOCOL_3_HR_SpO2_ERROR, null)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun sendProtocol2ToApp(
        context: Context?,
        onReceive: (type: ProtocolType, response: PoliResponse?) -> Unit
    ) {
        DailyProtocol02API.apply {
            try {
                if (byteArray.size == 264_000) {
                    val response =
                        DailyApiService().sendProtocol02(context)
                    onReceive.invoke(ProtocolType.PROTOCOL_2, response)
                } else {
                    onReceive.invoke(
                        ProtocolType.PROTOCOL_2_ERROR_LACK_OF_DATA,
                        null
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendProtocol02: ${e.message}")
                onReceive.invoke(ProtocolType.PROTOCOL_2_ERROR, null)
            } finally {
                prevByte = 0x00
                byteArray = ByteArray(0)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun sendProtocol01ToApp(
        byteArray: ByteArray,
        context: Context?,
        onReceive: (type: ProtocolType, response: PoliResponse?) -> Unit
    ) {
        DailyProtocol01API.categorizeData(byteArray)
        DailyProtocol01API.collectBytes(byteArray)
        if (byteArray[1] == 0xFF.toByte()) {

            try {
                DailyProtocol01API.createLTMModel()
                val response: Daily1Response =
                    DailyApiService().sendProtocol01New(context)
                onReceive.invoke(ProtocolType.PROTOCOL_1, response)
            } catch (e: Exception) {
                Log.e(TAG, "sendProtocol01: ${e.message}")
                onReceive.invoke(ProtocolType.PROTOCOL_1_ERROR, null)
            } finally {
                DailyProtocol01API.clearCollectedBytes()

            }
        }
    }
}