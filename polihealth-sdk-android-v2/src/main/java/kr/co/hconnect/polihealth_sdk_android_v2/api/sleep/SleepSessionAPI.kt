package kr.co.hconnect.polihealth_sdk_android.api.sleep

import android.util.Log
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.AttributeKey
import kotlinx.coroutines.runBlocking
import kr.co.hconnect.polihealth_sdk_android.DateUtil
import kr.co.hconnect.polihealth_sdk_android.PoliClient
import kr.co.hconnect.polihealth_sdk_android.api.dto.request.RequestBody
import kr.co.hconnect.polihealth_sdk_android.api.dto.response.SleepEndResponse
import kr.co.hconnect.polihealth_sdk_android.api.dto.response.toSleepEndResponse
import kr.co.hconnect.polihealth_sdk_android_v2.api.dto.response.SleepResponse
import kr.co.hconnect.polihealth_sdk_android_v2.api.dto.response.toSleepResponse

object SleepSessionAPI {

    /**
     * TODO: 수면 시작 요청 API
     *
     * @return SleepStartResponse (sessionId)
     */
    suspend fun requestSleepStart(): SleepResponse {
        val requestBody = RequestBody(
            reqDate = DateUtil.getCurrentDateTime(),
            userSno = PoliClient.userSno,
        )
        val response: SleepResponse =
            PoliClient.client.post("/poli/sleep/start") {
                contentType(ContentType.Application.Json)

                setBody(requestBody)
            }
                .call.attributes[AttributeKey("body")].toString()
                .toSleepResponse()

        PoliClient.sessionId = response.data?.sessionId ?: ""
        Log.d("SleepSessionAPI", "userSno: $PoliClient.userSno")
        Log.d("SleepSessionAPI", "sessionId: $PoliClient.sessionId")

        return response
    }

    fun testSleepStart() = runBlocking {
        requestSleepStart()
    }

    /**
     * TODO: 수면 종료 요청 API
     *
     * @return SleepEndResponse (sleepQuality)
     */
    suspend fun requestSleepEnd(): SleepEndResponse {
        val requestBody = RequestBody(
            reqDate = DateUtil.getCurrentDateTime(),
            userSno = PoliClient.userSno,
            sessionId = PoliClient.sessionId
        )

        val response: SleepEndResponse =
            PoliClient.client.post("/poli/sleep/stop") { setBody(requestBody) }
                .call.attributes[AttributeKey("body")].toString()
                .toSleepEndResponse()

        return response
    }

    fun testSleepEnd() = runBlocking {
        requestSleepEnd()
    }
}