package kr.co.hconnect.polihealth_sdk_android.api.sleep

import android.os.Build
import androidx.annotation.RequiresApi
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.util.AttributeKey
import io.ktor.util.InternalAPI
import kotlinx.coroutines.runBlocking
import kr.co.hconnect.polihealth_sdk_android.DateUtil
import kr.co.hconnect.polihealth_sdk_android.PoliClient
import kr.co.hconnect.polihealth_sdk_android.api.dto.request.HRSpO2
import kr.co.hconnect.polihealth_sdk_android.api.dto.request.HRSpO2Request
import kr.co.hconnect.polihealth_sdk_android.api.dto.response.SleepResponse
import kr.co.hconnect.polihealth_sdk_android.api.dto.response.toSleepCommResponse

object SleepProtocol09API {
    /**
     * TODO: 심박수와 산소포화도를 서버로 전송하는 API
     *
     * @param reqDate ex) 20240704054513 (yyyyMMddHHmmss)
     * @param hrspo2
     * */
    suspend fun requestPost(
        reqDate: String,
        hrSpO2: HRSpO2
    ): SleepResponse.SleepCommResponse {

        val requestBody = HRSpO2Request(
            reqDate = reqDate,
            userSno = PoliClient.userSno,
            sessionId = PoliClient.sessionId,
            data = HRSpO2Request.Data(
                oxygenVal = hrSpO2.spo2,
                heartRateVal = hrSpO2.heartRate
            )
        )

        val response = PoliClient.client.post("poli/sleep/protocol9") {
            setBody(requestBody)
        }.call.attributes[AttributeKey("body")].toString().toSleepCommResponse()

        return response
    }


    @RequiresApi(Build.VERSION_CODES.Q)
    fun testPost(
        hrSpO2: HRSpO2
    ) = runBlocking {
        try {
            val requestBody = HRSpO2Request(
                reqDate = "20240704054513",
                userSno = PoliClient.userSno,
                sessionId = PoliClient.sessionId,
                data = HRSpO2Request.Data(
                    oxygenVal = hrSpO2.spo2,
                    heartRateVal = hrSpO2.heartRate
                )
            )

            PoliClient.client.post("poli/sleep/protocol9") {
                setBody(requestBody)
            }.call.attributes[AttributeKey("body")].toString()


        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}